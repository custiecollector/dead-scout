package org.deadscout.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.deadscout.core.PcapWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AndroidPacketCaptureService extends VpnService {
    public static final String ACTION_START = "org.deadscout.app.packet.START";
    public static final String ACTION_STOP = "org.deadscout.app.packet.STOP";
    public static final String ACTION_STATE = "org.deadscout.app.packet.STATE";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_CAPTURE_PATH = "capturePath";
    public static final String EXTRA_PACKET_COUNT = "packetCount";
    public static final String EXTRA_BYTE_COUNT = "byteCount";
    private static final String CHANNEL_ID = "deadscout_android_packet_capture";
    private static final int NOTIFICATION_ID = 4320;
    private static final int MAX_CAPTURED_PACKETS = 2000;
    private static final int MAX_PCAP_BYTES = 8 * 1024 * 1024;

    private static volatile boolean running;
    private static volatile String status = "Built-in packet capture stopped.";
    private static volatile File lastCaptureFile;
    private static volatile int packetCount;
    private static volatile long byteCount;
    private ParcelFileDescriptor vpnInterface;
    private Thread captureThread;

    public static void startCapture(Context context) {
        Intent intent = new Intent(context, AndroidPacketCaptureService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stopCapture(Context context) {
        context.startService(new Intent(context, AndroidPacketCaptureService.class).setAction(ACTION_STOP));
    }

    public static boolean isRunning() { return running; }
    public static String statusLine() { return status; }
    public static File lastCaptureFile() { return lastCaptureFile; }
    public static int packetCount() { return packetCount; }
    public static long byteCount() { return byteCount; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopInternal("Built-in packet capture stopped by operator.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action) && !running) startInternal();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        stopInternal("Built-in packet capture service destroyed.");
        super.onDestroy();
    }

    private void startInternal() {
        ensureChannel();
        running = true;
        packetCount = 0;
        byteCount = 0;
        status = "Starting DeadScout local VPN/TUN packet capture.";
        startForeground(NOTIFICATION_ID, notification());
        try {
            Builder builder = new Builder()
                    .setSession("DeadScout packet capture")
                    .setMtu(1500)
                    .addAddress("10.88.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fd00:dead:5c00::2", 128)
                    .addRoute("::", 0);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            vpnInterface = builder.establish();
            if (vpnInterface == null) throw new IOException("Android did not establish a TUN interface");
            File dir = new File(getFilesDir(), "packet-captures");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create packet-captures directory");
            File out = new File(dir, String.format(Locale.US, "deadscout-android-packet-%d.pcap", System.currentTimeMillis()));
            lastCaptureFile = out;
            captureThread = new Thread(() -> captureLoop(out), "deadscout-android-packet-capture");
            captureThread.start();
            status = "Built-in packet capture running. Android VPN icon means local TUN capture, not a remote VPN.";
            broadcastState();
        } catch (Exception ex) {
            stopInternal("Built-in packet capture could not start: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void captureLoop(File outFile) {
        ArrayList<byte[]> packets = new ArrayList<>();
        byte[] buffer = new byte[32767];
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(vpnInterface)) {
            while (running && packets.size() < MAX_CAPTURED_PACKETS && byteCount < MAX_PCAP_BYTES) {
                int read = in.read(buffer);
                if (read <= 0) continue;
                if (!looksLikeIpPacket(buffer, read)) continue;
                byte[] packet = new byte[read];
                System.arraycopy(buffer, 0, packet, 0, read);
                packets.add(packet);
                packetCount = packets.size();
                byteCount += read;
                if (packetCount == 1 || packetCount % 25 == 0) {
                    status = String.format(Locale.US, "Built-in packet capture running: %d raw IP packet(s), %d byte(s).", packetCount, byteCount);
                    broadcastState();
                }
            }
            writePcap(outFile, packets);
            status = String.format(Locale.US, "Built-in packet capture saved %d raw IP packet(s), %d byte(s).", packetCount, byteCount);
        } catch (IOException ex) {
            if (!running && packets.size() > 0) {
                try {
                    writePcap(outFile, packets);
                    status = String.format(Locale.US, "Built-in packet capture saved %d raw IP packet(s), %d byte(s).", packetCount, byteCount);
                } catch (IOException writeEx) {
                    status = "Built-in packet capture write failed: " + writeEx.getMessage();
                }
            } else if (running) {
                status = "Built-in packet capture stopped on read error: " + ex.getMessage();
            }
        } finally {
            running = false;
            closeVpnInterface();
            broadcastState();
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
            stopSelf();
        }
    }

    private void writePcap(File outFile, List<byte[]> packets) throws IOException {
        try (OutputStream out = new FileOutputStream(outFile)) {
            out.write(PcapWriter.writeRawIp(packets));
        }
    }

    private static boolean looksLikeIpPacket(byte[] packet, int length) {
        if (length < 20) return false;
        int version = (packet[0] >> 4) & 0x0f;
        return version == 4 || version == 6;
    }

    private void stopInternal(String message) {
        boolean wasRunning = running;
        running = false;
        if (message != null && !message.isEmpty()) status = message;
        closeVpnInterface();
        if (captureThread != null) captureThread.interrupt();
        broadcastState();
        if (wasRunning) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        }
    }

    private void closeVpnInterface() {
        if (vpnInterface == null) return;
        try { vpnInterface.close(); } catch (IOException ignored) { }
        vpnInterface = null;
    }

    private void broadcastState() {
        Intent state = new Intent(ACTION_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RUNNING, running)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_PACKET_COUNT, packetCount)
                .putExtra(EXTRA_BYTE_COUNT, byteCount);
        File f = lastCaptureFile;
        if (f != null) state.putExtra(EXTRA_CAPTURE_PATH, f.getAbsolutePath());
        sendBroadcast(state);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, pendingIntentFlags());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_deadscout_capture)
                .setContentTitle("DeadScout packet capture")
                .setContentText(shorten(status, 96))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 21) b.setCategory(Notification.CATEGORY_SERVICE);
        return b.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "DeadScout packet capture", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Visible while DeadScout captures own-device packet data through Android VPN/TUN.");
        nm.createNotificationChannel(ch);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
