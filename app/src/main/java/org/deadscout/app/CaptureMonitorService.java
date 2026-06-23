package org.deadscout.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class CaptureMonitorService extends Service {
    public static final String ACTION_START = "org.deadscout.app.capture.START";
    public static final String ACTION_UPDATE = "org.deadscout.app.capture.UPDATE";
    public static final String ACTION_STOP = "org.deadscout.app.capture.STOP";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_STATUS = "status";
    private static final String CHANNEL_ID = "deadscout_capture_monitor";
    private static final int NOTIFICATION_ID = 4310;

    private static volatile boolean active;
    private static volatile String activeMode = "Idle";
    private static volatile String activeStatus = "No capture monitor running.";

    public static void start(Context context, String mode, String status) {
        Intent intent = new Intent(context, CaptureMonitorService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, safe(mode, "Capture"))
                .putExtra(EXTRA_STATUS, safe(status, "DeadScout capture monitor running."));
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void update(Context context, String mode, String status) {
        Intent intent = new Intent(context, CaptureMonitorService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_MODE, safe(mode, activeMode))
                .putExtra(EXTRA_STATUS, safe(status, activeStatus));
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, CaptureMonitorService.class).setAction(ACTION_STOP));
    }

    public static boolean isActive() { return active; }
    public static String statusLine() { return active ? activeMode + ": " + activeStatus : "Service idle"; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            active = false;
            activeMode = "Idle";
            activeStatus = "No capture monitor running.";
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        active = true;
        activeMode = intent.getStringExtra(EXTRA_MODE) == null ? "Capture" : intent.getStringExtra(EXTRA_MODE);
        activeStatus = intent.getStringExtra(EXTRA_STATUS) == null ? "DeadScout capture monitor running." : intent.getStringExtra(EXTRA_STATUS);
        ensureChannel();
        startForeground(NOTIFICATION_ID, notification());
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, pendingIntentFlags());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_deadscout_capture)
                .setContentTitle("DeadScout capture monitor")
                .setContentText(shorten(activeMode + " · " + activeStatus, 96))
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
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "DeadScout capture monitor", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Visible while DeadScout is actively monitoring RF, USB, audio, or radio metadata.");
        nm.createNotificationChannel(ch);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
