package org.deadscout.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.deadscout.core.AlertEngine;
import org.deadscout.core.CaptureCapability;
import org.deadscout.core.CaptureImportResult;
import org.deadscout.core.CaptureImporter;
import org.deadscout.core.CaptureRecipe;
import org.deadscout.core.CaptureSession;
import org.deadscout.core.CaptureSourceDescriptor;
import org.deadscout.core.CrossProtocolCorrelationEngine;
import org.deadscout.core.DecoderPlugin;
import org.deadscout.core.DecoderPluginRegistry;
import org.deadscout.core.FrequencyPlan;
import org.deadscout.core.HardwareCompatibilityDatabase;
import org.deadscout.core.HexInspector;
import org.deadscout.core.Ieee802154SnifferReader;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.RemoteNodeManager;
import org.deadscout.core.ReportGenerator;
import org.deadscout.core.Rtl433Module;
import org.deadscout.core.RtlSdrAudioDemodulator;
import org.deadscout.core.RtlSdrIqPipeline;
import org.deadscout.core.SignalObservation;
import org.deadscout.core.SourceRegistry;
import org.deadscout.core.SpectrumSnapshot;
import org.deadscout.core.SurroundingCapturePlanner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    static final int BG = Color.rgb(4, 6, 12);
    static final int PANEL = Color.rgb(13, 18, 29);
    static final int PANEL_2 = Color.rgb(20, 29, 45);
    static final int PANEL_3 = Color.rgb(28, 41, 62);
    static final int SURFACE = Color.rgb(8, 12, 20);
    static final int STROKE = Color.rgb(42, 61, 82);
    static final int TEXT = Color.rgb(232, 240, 247);
    static final int MUTED = Color.rgb(143, 160, 177);
    static final int ACCENT = Color.rgb(82, 232, 195);
    static final int ACCENT_2 = Color.rgb(92, 162, 255);
    static final int WARN = Color.rgb(255, 195, 92);
    static final int QUIET = Color.rgb(92, 113, 132);
    private static final int REQ_IMPORT_CAPTURE = 4302;
    private static final int REQ_RTL_DRIVER = 4303;
    private static final int REQ_ANDROID_PACKET_VPN = 4304;
    private static final int RTL_LIVE_MIN_CHUNK_BYTES = 512 * 1024;
    private static final int RTL_LIVE_MAX_CHUNK_BYTES = 4 * 1024 * 1024;
    private static final int RTL_LIVE_TRANSFER_TIMEOUT_MS = 250;
    private static final long USB_MONITOR_REFRESH_INTERVAL_MS = 1500;
    private static final long RTL_TCP_REFRESH_INTERVAL_MS = 1500;
    private static final int MAX_SESSION_NOTES = 24;
    private static final int MAX_SESSION_PACKETS = 300;
    private static final int MAX_SESSION_OBSERVATIONS = 400;
    private static final int MAX_SESSION_SNAPSHOTS = 16;
    private static final int MAX_SAVED_IQ_BYTES = 4 * 1024 * 1024;

    LinearLayout root;
    private ScrollView currentScroll;
    private int lastScrollY = 0;
    String mode = "Capture";
    private String lastRenderedMode = "Capture";
    AndroidUsbAdapterScanner usbAdapterScanner;
    private AndroidRtlSdrUsbProbe rtlUsbProbe;
    RtlSdrIqPipeline.Config rtlConfig = RtlSdrIqPipeline.Config.default915();
    CaptureSession rtlSession;
    private CaptureSession rtlContinuousSession;
    private CaptureSession rtlTcpContinuousSession;
    SpectrumSnapshot rtlSnapshot;
    byte[] lastRtlIqBytes = new byte[0];
    volatile boolean rtlLiveCapture = false;
    volatile boolean rtlTcpCapture = false;
    volatile boolean rtlAudioMonitor = false;
    private volatile boolean androidPacketCapture = false;
    private volatile boolean usbAdapterMonitor = false;
    private volatile boolean activityDestroyed = false;
    private Thread rtlLiveThread;
    private Thread rtlTcpThread;
    private Thread rtlAudioMonitorThread;
    private Thread usbAdapterThread;
    int rtlLiveRefreshCount = 0;
    int rtlTcpRefreshCount = 0;
    int usbAdapterRefreshCount = 0;
    private AudioTrack rtlAudioTrack;
    String rtlAudioStatus = "No live audio monitor yet. Start AM monitor or Start FM monitor after USB permission.";
    String rtlAudioMode = "AM";
    String rtlStatus = "RTL-SDR not started. Start USB, choose a preset, then Start RTL capture or live audio monitor.";
    private String androidPacketStatus = "Built-in packet capture stopped. Start Packet to capture own-device IPv4/IPv6 into an app-private PCAP.";
    private String lastAndroidPacketCapturePath = "";
    private int androidPacketCount = 0;
    private long androidPacketBytes = 0L;
    private String usbStatus = "Not monitoring. Connect USB SDR/sniffer hardware, then Start USB.";
    private String importStatus = "No import capture active. Tap Start import to load a file.";
    CaptureImportResult lastImport;
    byte[] lastImportBytes;
    boolean rtlControlsVisible = false;
    boolean showExternalRtlFallback = false;
    boolean showCaptureDetails = false;
    boolean showAllCapturePaths = false;
    private boolean showAdvancedSources = false;
    boolean showRawPacketDetails = false;
    boolean showAdvancedTools = false;
    boolean showRtlPresets = false;
    boolean showRtlRadioSetup = false;
    boolean showCaptureRecipes = false;
    int selectedPacketIndex = 0;
    String labHexText = "";
    String labHexResult = "Select packet hex or enter bytes below, then tap Decode hex. DeadScout will say whether it looks like IP/802.15.4, unknown bytes, or raw RTL IQ data.";
    String advancedTool = "";
    String packetFilter = "";
    CaptureSession trainingSession;
    private boolean gpsSurveyEnabled = false;
    private long lastUsbPermissionRequestMs = 0L;
    private long lastRenderWallMs = 0L;
    private boolean renderQueued = false;
    private final CaptureMonitorCoordinator captureMonitorCoordinator = new CaptureMonitorCoordinator();
    private final RtlControlsPanel rtlControlsPanel = new RtlControlsPanel(this);
    private final SpectrumReviewPanel spectrumReviewPanel = new SpectrumReviewPanel(this);
    final PacketReviewPanel packetReviewPanel = new PacketReviewPanel(this);
    private final LabPanel labPanel = new LabPanel(this);
    private final SessionLibraryPanel sessionLibraryPanel = new SessionLibraryPanel(this);
    private final StatusHeaderPanel statusHeaderPanel = new StatusHeaderPanel(this);
    private final CaptureModePanel captureModePanel = new CaptureModePanel(this);
    private final SweepControlsPanel sweepControlsPanel = new SweepControlsPanel(this);
    private final BroadcastReceiver androidPacketStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !AndroidPacketCaptureService.ACTION_STATE.equals(intent.getAction())) return;
            androidPacketCapture = intent.getBooleanExtra(AndroidPacketCaptureService.EXTRA_RUNNING, false);
            androidPacketStatus = intent.getStringExtra(AndroidPacketCaptureService.EXTRA_STATUS) == null
                    ? AndroidPacketCaptureService.statusLine()
                    : intent.getStringExtra(AndroidPacketCaptureService.EXTRA_STATUS);
            androidPacketCount = intent.getIntExtra(AndroidPacketCaptureService.EXTRA_PACKET_COUNT, androidPacketCount);
            androidPacketBytes = intent.getLongExtra(AndroidPacketCaptureService.EXTRA_BYTE_COUNT, androidPacketBytes);
            String path = intent.getStringExtra(AndroidPacketCaptureService.EXTRA_CAPTURE_PATH);
            if (path != null) lastAndroidPacketCapturePath = path;
            if (!androidPacketCapture && lastAndroidPacketCapturePath != null && !lastAndroidPacketCapturePath.isEmpty()) {
                importBuiltInPacketCapture(new File(lastAndroidPacketCapturePath));
            }
            renderThrottled(350L);
        }
    };
    boolean showTechnicalErrors = false;
    String lastErrorTitle = "";
    String lastErrorOperator = "";
    String lastErrorTechnical = "";

    boolean spectrumFrozen = false;
    boolean spectrumShowPeakHold = false;
    boolean spectrumHighContrast = false;
    int spectrumZoomLevel = 0;
    int spectrumPanStep = 0;
    int selectedMarkerIndex = 0;
    SpectrumSnapshot frozenSpectrumSnapshot;
    String snapshotSaveStatus = "No spectrum snapshot saved yet.";

    final RtlTunerMemory rtlTunerMemory = new RtlTunerMemory();
    String presetSearch = "";
    boolean showPresetVhf = true;
    boolean showPresetUhf = true;
    boolean showPresetIsm = true;
    boolean showPresetAll = false;

    volatile boolean sweepRunning = false;
    private Thread sweepThread;
    CaptureSession sweepSession;
    final ArrayList<SweepHit> sweepHits = new ArrayList<>();
    String sweepStatus = "Sweep stopped. Configure start/stop/step/dwell and tap Start sweep.";
    double sweepStartMhz = 902.0;
    double sweepStopMhz = 928.0;
    double sweepStepMhz = 1.0;
    int sweepDwellMs = 350;
    double sweepThresholdPercent = 8.0;

    String burstPulseText = "480 510 500 1510 495 505 505 1515 490 520 500 1500";
    String burstFskText = "-2400 2400 -2300 2500 2450 -2350 2400 -2450";
    int burstSymbolRate = 9600;
    String burstWorkbenchResult = "Enter pulse/gap timings or FSK symbol estimates, then analyze. Results stay in Lab until you save a candidate to the active session.";
    PacketRecord burstCandidate;

    String sessionLibraryStatus = "No saved session loaded in this run.";
    CaptureSession loadedLibrarySession;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityDestroyed = false;
        usbAdapterScanner = new AndroidUsbAdapterScanner(this);
        rtlUsbProbe = new AndroidRtlSdrUsbProbe(this);
        registerAndroidPacketReceiver();
        render();
    }

    @Override protected void onDestroy() {
        activityDestroyed = true;
        rtlLiveCapture = false;
        rtlTcpCapture = false;
        rtlAudioMonitor = false;
        usbAdapterMonitor = false;
        sweepRunning = false;
        if (rtlLiveThread != null) rtlLiveThread.interrupt();
        if (rtlTcpThread != null) rtlTcpThread.interrupt();
        if (rtlAudioMonitorThread != null) rtlAudioMonitorThread.interrupt();
        if (usbAdapterThread != null) usbAdapterThread.interrupt();
        if (sweepThread != null) sweepThread.interrupt();
        stopRtlAudio();
        try { unregisterReceiver(androidPacketStateReceiver); } catch (RuntimeException ignored) { }
        try { AndroidPacketCaptureService.stopCapture(this); } catch (RuntimeException ignored) { }
        try { CaptureMonitorService.stop(this); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerAndroidPacketReceiver() {
        IntentFilter filter = new IntentFilter(AndroidPacketCaptureService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(androidPacketStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(androidPacketStateReceiver, filter);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RTL_DRIVER) {
            if (resultCode == RESULT_OK) {
                rtlStatus = "Local RTL-SDR driver reports ready. Starting external rtl_tcp capture monitor now…";
                render();
                startRtlTcpMonitor();
            } else {
                rtlStatus = "Local RTL-SDR driver did not start.";
                setActionaauxError("External RTL-SDR driver unavailable", "Open setup, install/launch the Android rtl_tcp/SDR driver, or use built-in USB RTL capture if permission is granted.", "Driver activity returned resultCode=" + resultCode);
                if (data != null) {
                    String detail = data.getStringExtra("detailed_exception_message");
                    if (detail != null && !detail.isEmpty()) rtlStatus += " " + compactStatus(detail);
                }
                render();
            }
            return;
        }
        if (requestCode == REQ_ANDROID_PACKET_VPN) {
            if (resultCode == RESULT_OK) {
                startBuiltInAndroidPacketCaptureAfterConsent();
            } else {
                androidPacketCapture = false;
                androidPacketStatus = "Android VPN consent was denied. Built-in packet capture did not start.";
                finishFailedMonitor(CaptureMonitorCoordinator.ANDROID_PACKET,
                        "Packet capture permission denied",
                        "Android requires VPN consent for no-root own-device packet capture. Tap Start Packet and approve the local DeadScout VPN/TUN prompt to capture packets.",
                        "VpnService consent resultCode=" + resultCode);
                render();
            }
            return;
        }
        if (requestCode == REQ_IMPORT_CAPTURE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    byte[] raw = readAll(in);
                    String name = uri.getLastPathSegment() == null ? "capture" : uri.getLastPathSegment();
                    lastImport = CaptureImporter.importCapture(name, raw);
                    lastImportBytes = raw;
                    clearActionaauxError();
                    importStatus = "Import capture active: " + name + " · " + lastImport.summary() + ". Tap Stop import to clear it.";
                    rtlSession = null;
                    rtlContinuousSession = null;
                    rtlTcpContinuousSession = null;
                    rtlSnapshot = null;
                    lastRtlIqBytes = new byte[0];
                    rtlControlsVisible = false;
                } catch (Exception ex) {
                    importStatus = "Import failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
                    setActionaauxError("Import failed", "DeadScout could not read or decode that file. Try another capture file, or open setup for supported import paths.", ex.getClass().getName() + ": " + ex.getMessage());
                }
            } else if (!hasImportCapture()) {
                importStatus = "Import capture stopped before a file was selected.";
            }
            render();
        }
    }

    void render() {
        if (activityDestroyed || isFinishing()) return;
        renderQueued = false;
        lastRenderWallMs = System.currentTimeMillis();
        int restoreY = currentScroll == null ? lastScrollY : currentScroll.getScrollY();
        if (currentScroll == null || root == null) {
            currentScroll = new ScrollView(this);
            currentScroll.setFillViewport(true);
            currentScroll.setBackgroundColor(BG);
            root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            currentScroll.addView(root);
            setContentView(currentScroll);
        } else {
            root.removeAllViews();
        }
        boolean modeChanged = !mode.equals(lastRenderedMode);
        if (modeChanged) restoreY = 0;
        root.setPadding(dp(16), dp(12) + statusBarInset(), dp(16), dp(96) + navigationBarInset());
        statusHeaderPanel.render();
        if ("Capture".equals(mode)) captureModePanel.render();
        else if ("Packets".equals(mode)) packetsMode();
        else if ("Lab".equals(mode)) labMode();
        else moreMode();
        lastRenderedMode = mode;
        lastScrollY = Math.max(0, restoreY);
        if (modeChanged) {
            currentScroll.post(() -> {
                if (!activityDestroyed && currentScroll != null) currentScroll.scrollTo(0, 0);
            });
        } else if (lastScrollY > 0) currentScroll.post(() -> {
            if (!activityDestroyed && currentScroll != null) currentScroll.scrollTo(0, lastScrollY);
        });
    }

    private void renderThrottled(long minIntervalMs) {
        if (activityDestroyed || isFinishing()) return;
        long now = System.currentTimeMillis();
        if (now - lastRenderWallMs >= minIntervalMs) {
            render();
            return;
        }
        if (renderQueued) return;
        renderQueued = true;
        long delay = Math.max(50L, minIntervalMs - (now - lastRenderWallMs));
        if (currentScroll != null) currentScroll.postDelayed(() -> {
            if (!activityDestroyed && renderQueued) render();
        }, delay);
        else render();
    }

    private void setActiveMonitor(String kind) {
        captureMonitorCoordinator.setActive(kind);
    }

    private void activateMonitor(String kind) {
        setActiveMonitor(kind);
        clearActionaauxError();
    }

    private void clearActiveMonitor(String kind) {
        captureMonitorCoordinator.clearIfActive(kind);
    }

    String activeMonitorKind() {
        return captureMonitorCoordinator.activeKind();
    }

    String androidPacketStatusLine() {
        return androidPacketStatus + "\nPackets: " + androidPacketCount + " · bytes: " + androidPacketBytes
                + (lastAndroidPacketCapturePath == null || lastAndroidPacketCapturePath.isEmpty() ? "" : "\nPCAP: " + lastAndroidPacketCapturePath);
    }

    void clearActionaauxError() {
        lastErrorTitle = "";
        lastErrorOperator = "";
        lastErrorTechnical = "";
        showTechnicalErrors = false;
    }

    void setActionaauxError(String title, String operator, String technical) {
        lastErrorTitle = title == null ? "Action needed" : title;
        lastErrorOperator = operator == null ? "DeadScout needs setup or a retry before this source can continue." : operator;
        lastErrorTechnical = technical == null ? "" : technical;
    }

    private TextView pill(String label, int color) {
        TextView t = text(label, 12, color, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(rounded(Color.argb(42, Color.red(color), Color.green(color), Color.blue(color)), 18, color, 1));
        return t;
    }

    String importCaptureButtonLabel() {
        return !hasImportCapture() ? "Start import" : "Stop import";
    }

    String usbCaptureButtonLabel() {
        if (rtlLiveCapture) return "Stop RTL capture";
        if (usbAdapterMonitor) return "Stop USB";
        if (usbAdapterScanner.hasRtlSdr()) return "Start RTL capture";
        return "Start USB";
    }

    String androidPacketButtonLabel() {
        return androidPacketCapture ? "Stop Packet" : "Start Packet";
    }

    String primaryActionLabel() {
        if (androidPacketCapture) return "Stop Local packet capture";
        if (rtlLiveCapture) return "Stop RTL capture";
        if (rtlTcpCapture) return "Stop external rtl_tcp capture";
        if (usbAdapterMonitor) return "Stop USB monitor";
        if (hasImportCapture()) return "Stop import capture";
        if (primaryRtlCaptureAvailaaux()) return "Start RTL capture";
        if (primaryUsbMonitorAvailaaux()) return "Start USB monitor";
        return "Start import capture";
    }

    void runPrimaryAction() {
        if (stopPrimaryActionIfRunning()) return;
        if (primaryRtlCaptureAvailaaux()) { rtlControlsVisible = true; toggleRtlLiveMonitor(); return; }
        if (primaryUsbMonitorAvailaaux()) { startUsbAdapterMonitor("USB monitor"); return; }
        toggleImportCapture();
    }

    private boolean stopPrimaryActionIfRunning() {
        if (androidPacketCapture) { stopBuiltInAndroidPacketCapture(true); return true; }
        if (rtlLiveCapture) { stopRtlCaptureMonitor(true); return true; }
        if (rtlTcpCapture) { stopRtlTcpMonitor(true); return true; }
        if (usbAdapterMonitor) { stopUsbAdapterMonitor(true); return true; }
        if (hasImportCapture()) { stopImportCapture(true); return true; }
        return false;
    }

    private boolean primaryRtlCaptureAvailaaux() {
        return usbAdapterScanner.hasRtlSdr() || rtlControlsVisible || rtlSession != null;
    }

    private boolean primaryUsbMonitorAvailaaux() {
        return usbAdapterScanner.deviceCount() > 0;
    }

    void toggleImportCapture() {
        if (hasImportCapture()) { stopImportCapture(true); return; }
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.IMPORT);
        activateMonitor(CaptureMonitorCoordinator.IMPORT);
        importStatus = "Import capture starting. Pick a capture file, or cancel to leave import stopped.";
        render();
        openImportPicker();
    }

    private void stopImportCapture(boolean shouldRender) {
        clearImportCaptureState();
        importStatus = "Import capture stopped. Start import to load a capture file.";
        clearActiveMonitor(CaptureMonitorCoordinator.IMPORT);
        if (shouldRender) render();
    }

    private void clearImportCaptureState() {
        lastImport = null;
        lastImportBytes = null;
    }

    private boolean hasImportCapture() {
        return lastImport != null;
    }

    void toggleUsbSdrCapture() {
        if (rtlLiveCapture) { stopRtlCaptureMonitor(true); return; }
        if (usbAdapterScanner.hasRtlSdr()) {
            rtlControlsVisible = true;
            toggleRtlLiveMonitor();
            return;
        }
        if (usbAdapterMonitor) stopUsbAdapterMonitor(true);
        else startUsbAdapterMonitor("USB monitor");
    }

    void toggleBuiltInAndroidPacketCapture() {
        if (androidPacketCapture) stopBuiltInAndroidPacketCapture(true);
        else startBuiltInAndroidPacketCapture();
    }

    void stopRunningCaptureMonitors(String keep) {
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.RTL)) stopRtlCaptureMonitor(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.RTL_TCP)) stopRtlTcpMonitor(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.AUDIO)) stopRtlAudioMonitor(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.ANDROID_PACKET)) stopBuiltInAndroidPacketCapture(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.USB)) stopUsbAdapterMonitor(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.SWEEP)) stopSweepMonitor(false);
        if (captureMonitorCoordinator.shouldStopForKeep(keep, CaptureMonitorCoordinator.IMPORT) && hasImportCapture()) stopImportCapture(false);
        if (captureMonitorCoordinator.shouldResetAfterStop(keep)) captureMonitorCoordinator.reset();
    }

    private void startMonitorService(String monitorMode, String status) {
        CaptureMonitorService.start(this, monitorMode, status);
    }

    private void updateMonitorService(String monitorMode, String status) {
        if (isMonitorServiceUpdateActive()) {
            CaptureMonitorService.update(this, monitorMode, compactStatus(status));
        }
    }

    private void stopMonitorServiceIfIdle() {
        if (!isMonitorServiceUpdateActive() && !sweepRunning) {
            CaptureMonitorService.stop(this);
        }
    }

    private boolean isMonitorServiceUpdateActive() {
        return rtlLiveCapture || rtlTcpCapture || rtlAudioMonitor || androidPacketCapture || usbAdapterMonitor;
    }

    private void finishStoppedMonitor(String kind, boolean shouldRender) {
        clearActiveMonitor(kind);
        stopMonitorServiceIfIdle();
        if (shouldRender) render();
    }

    private void finishFailedMonitor(String kind, String title, String operator, String technical) {
        clearActiveMonitor(kind);
        setActionaauxError(title, operator, technical);
        stopMonitorServiceIfIdle();
    }

    private void stopRtlCaptureMonitor(boolean shouldRender) {
        boolean wasRunning = rtlLiveCapture;
        rtlLiveCapture = false;
        if (rtlLiveThread != null) rtlLiveThread.interrupt();
        if (rtlContinuousSession != null) rtlContinuousSession.finish(System.currentTimeMillis());
        if (wasRunning) rtlStatus = "RTL capture stopped. Raw IQ remains available for waterfall/audio review; decoded packets appear only when a decoder finds real frames.";
        finishStoppedMonitor(CaptureMonitorCoordinator.RTL, shouldRender);
    }

    private void stopRtlTcpMonitor(boolean shouldRender) {
        boolean wasRunning = rtlTcpCapture;
        rtlTcpCapture = false;
        if (rtlTcpThread != null) rtlTcpThread.interrupt();
        if (rtlTcpContinuousSession != null) rtlTcpContinuousSession.finish(System.currentTimeMillis());
        if (wasRunning) rtlStatus = "External rtl_tcp capture stopped. Raw IQ remains available for waterfall/audio review; decoded packets appear only when a decoder finds real frames.";
        finishStoppedMonitor(CaptureMonitorCoordinator.RTL_TCP, shouldRender);
    }

    private void startBuiltInAndroidPacketCapture() {
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.ANDROID_PACKET);
        activateMonitor(CaptureMonitorCoordinator.ANDROID_PACKET);
        androidPacketStatus = "Starting built-in packet capture. The OS will show a VPN consent dialog for local TUN capture.";
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            render();
            startActivityForResult(consent, REQ_ANDROID_PACKET_VPN);
            return;
        }
        startBuiltInAndroidPacketCaptureAfterConsent();
    }

    private void startBuiltInAndroidPacketCaptureAfterConsent() {
        androidPacketCapture = true;
        androidPacketCount = 0;
        androidPacketBytes = 0L;
        lastAndroidPacketCapturePath = "";
        clearImportCaptureState();
        clearActionaauxError();
        androidPacketStatus = "Built-in packet capture starting. OS VPN icon means local packet capture, not a remote VPN.";
        startMonitorService("Local packet", androidPacketStatus);
        AndroidPacketCaptureService.startCapture(this);
        render();
    }

    private void stopBuiltInAndroidPacketCapture(boolean shouldRender) {
        boolean wasRunning = androidPacketCapture;
        androidPacketCapture = false;
        AndroidPacketCaptureService.stopCapture(this);
        if (wasRunning) androidPacketStatus = "Stopping built-in packet capture and importing the app-private PCAP.";
        finishStoppedMonitor(CaptureMonitorCoordinator.ANDROID_PACKET, shouldRender);
    }

    private void importBuiltInPacketCapture(File file) {
        if (file == null || !file.exists() || file.length() == 0L || androidPacketCount == 0) {
            if (androidPacketCount == 0) androidPacketStatus = "Built-in packet capture stopped with no raw IP packets captured.";
            lastAndroidPacketCapturePath = "";
            return;
        }
        try {
            byte[] raw = readAll(new FileInputStream(file));
            CaptureImportResult result = CaptureImporter.importCapture(file.getName(), raw);
            lastImport = result;
            lastImportBytes = raw;
            lastAndroidPacketCapturePath = "";
            androidPacketStatus = "Built-in packet capture imported: " + result.summary().replace('\n', ' ');
            clearActionaauxError();
        } catch (Exception ex) {
            setActionaauxError("Packet capture import failed",
                    "DeadScout captured a local PCAP but could not import it into packet review. Try importing the saved app-private PCAP manually or retry capture.",
                    ex.getClass().getName() + ": " + ex.getMessage());
            androidPacketStatus = "Built-in packet capture import failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private void stopUsbAdapterMonitor(boolean shouldRender) {
        boolean wasRunning = usbAdapterMonitor;
        usbAdapterMonitor = false;
        if (usbAdapterThread != null) usbAdapterThread.interrupt();
        if (wasRunning) usbStatus = "USB monitor stopped. Start USB when you want to watch attached adapters again.";
        finishStoppedMonitor(CaptureMonitorCoordinator.USB, shouldRender);
    }

    void liveSourceStatusPanel() {
        rtlControlsPanel.renderLiveSourceStatus();
    }

    boolean isAnyMonitorRunning() {
        return isMonitorServiceUpdateActive() || sweepRunning || hasImportCapture();
    }

    void signalObservationsPanel(CaptureSession session) {
        List<SignalObservation> observations = activeSignalObservations(session);
        section("Signal observations");
        if (observations.isEmpty()) {
            card("No signal observations", "Raw IQ/waterfall markers and scan hits appear here. They stay separate from decoded Packet Records so Packets remains packet-focused.", QUIET);
            return;
        }
        card("Observations separate from Packets", observations.size() + " RF/IQ/energy observation(s). These are not decoded packet records.", ACCENT);
        int limit = Math.min(8, observations.size());
        for (int i = 0; i < limit; i++) {
            SignalObservation obs = observations.get(i);
            card(obs.sourceId, obs.brief() + "\n" + obs.note, ACCENT_2);
        }
        if (observations.size() > limit) card("More observations hidden", (observations.size() - limit) + " more observation(s) kept in the capture session.", QUIET);
    }

    private List<SignalObservation> activeSignalObservations(CaptureSession session) {
        ArrayList<SignalObservation> out = new ArrayList<>();
        if (session != null) out.addAll(session.observations());
        long now = System.currentTimeMillis();
        for (SweepHit hit : sweepHits) {
            out.add(new SignalObservation(now, "sweep", hit.frequencyHz, rtlConfig.sampleRateHz, hit.bandwidthHz,
                    hit.occupancyPercent, hit.occupancyPercent, "sweep hit", "Sweep hit: " + hit.card()));
        }
        return out;
    }

    void spectrumControls(SpectrumSnapshot snapshot) {
        spectrumReviewPanel.renderControls(snapshot);
    }

    SpectrumSnapshot rawCurrentSpectrumSnapshot() {
        if (hasImportCapture() && lastImportBytes != null && lastImport.detectedType.toLowerCase(Locale.US).contains("iq")) {
            try {
                RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(RtlSdrIqPipeline.Config.default915());
                return pipeline.ingestUnsignedIq(lastImportBytes);
            } catch (RuntimeException ignored) { }
        }
        return rtlSnapshot;
    }

    void sweepControls() {
        sweepControlsPanel.render();
    }

    EditText numericBox(String value, String hint) {
        EditText box = new EditText(this);
        box.setSingleLine(true);
        box.setText(value);
        box.setHint(hint);
        box.setHintTextColor(MUTED);
        box.setTextColor(TEXT);
        box.setTextSize(12);
        box.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.setBackground(rounded(SURFACE, 16, STROKE, 1));
        box.setPadding(dp(12), 0, dp(12), 0);
        return box;
    }

    void startSweepFromInputs(EditText start, EditText stop, EditText step, EditText dwell, EditText threshold) {
        try {
            double startMhz = Double.parseDouble(start.getText().toString().trim());
            double stopMhz = Double.parseDouble(stop.getText().toString().trim());
            double stepMhz = Double.parseDouble(step.getText().toString().trim());
            int dwellMs = Integer.parseInt(dwell.getText().toString().trim());
            double thresholdPct = Double.parseDouble(threshold.getText().toString().trim());
            startSweep(startMhz, stopMhz, stepMhz, dwellMs, thresholdPct);
        } catch (RuntimeException ex) {
            failSweepSetup("Invalid sweep setup",
                    "Check start/stop/step/dwell/threshold. Example: 902 to 928 MHz, 1 MHz step, 350 ms dwell, 8% occupancy threshold.",
                    ex.toString(),
                    "Invalid sweep values. Use MHz numbers plus integer dwell milliseconds.");
        }
    }

    private void failSweepSetup(String title, String operator, String technical, String status) {
        sweepStatus = status;
        setActionaauxError(title, operator, technical);
        render();
    }

    private void startSweep(double startMhz, double stopMhz, double stepMhz, int dwellMs, double thresholdPct) {
        long startHz = Math.round(startMhz * 1_000_000.0);
        long stopHz = Math.round(stopMhz * 1_000_000.0);
        long stepHz = Math.round(stepMhz * 1_000_000.0);
        if (!rtlFrequencyValid(startHz) || !rtlFrequencyValid(stopHz) || stopHz < startHz || stepHz <= 0 || dwellMs < 100 || dwellMs > 5000) {
            failSweepSetup("Invalid sweep range",
                    "Invalid sweep range. Keep RTL sweeps inside 24–1766 MHz, stop ≥ start, positive step, dwell 100–5000 ms.",
                    "startHz=" + startHz + " stopHz=" + stopHz + " stepHz=" + stepHz + " dwellMs=" + dwellMs,
                    "Invalid sweep range. Keep RTL sweeps inside 24–1766 MHz, stop ≥ start, positive step, dwell 100–5000 ms.");
            return;
        }
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.SWEEP);
        activateMonitor(CaptureMonitorCoordinator.SWEEP);
        sweepStartMhz = startMhz;
        sweepStopMhz = stopMhz;
        sweepStepMhz = stepMhz;
        sweepDwellMs = dwellMs;
        sweepThresholdPercent = thresholdPct;
        sweepHits.clear();
        sweepRunning = true;
        sweepSession = new CaptureSession("rtl-sweep-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("rtl-sdr-sweep", startHz, rtlConfig.sampleRateHz);
        sweepSession.addTag("sweep");
        sweepSession.addNote(String.format(Locale.US, "Sweep started %.3f–%.3f MHz step %.3f MHz dwell %d ms threshold %.1f%%", startMhz, stopMhz, stepMhz, dwellMs, thresholdPct));
        rtlSession = sweepSession;
        sweepStatus = "Sweep starting. Tap Stop sweep to end it.";
        startMonitorService("RTL sweep", sweepStatus);
        render();
        sweepThread = new Thread(() -> {
            int update = 0;
            try {
                for (long freq = startHz; sweepRunning && !activityDestroyed && freq <= stopHz; freq += stepHz) {
                    RtlSdrIqPipeline.Config config = new RtlSdrIqPipeline.Config(freq, rtlConfig.sampleRateHz, rtlConfig.gainTenthsDb, rtlConfig.ppmCorrection, rtlConfig.agc, rtlConfig.fftSize, rtlConfig.squelchDbAboveNoise);
                    long dwellBytes = (long) config.sampleRateHz * 2L * Math.max(100L, (long) dwellMs) / 1000L;
                    int bytes = (int) Math.min(768L * 1024L, Math.max(64L * 1024L, dwellBytes));
                    AndroidRtlSdrUsbProbe.Result result = rtlUsbProbe.capture(config, bytes, dwellMs + 450);
                    final int updateNumber = ++update;
                    final long f = freq;
                    final AndroidRtlSdrUsbProbe.Result r = result;
                    runOnUiThread(() -> applySweepResult(f, updateNumber, r));
                    if (!result.ok && updateNumber == 1) break;
                }
            } finally {
                runOnUiThread(this::finishSweepMonitorRun);
            }
        }, "deadscout-rtl-sweep");
        sweepThread.start();
    }

    private void finishSweepMonitorRun() {
        if (sweepRunning) sweepStatus = "Sweep finished. Hits retained for review.";
        sweepRunning = false;
        clearActiveMonitor(CaptureMonitorCoordinator.SWEEP);
        if (sweepSession != null) sweepSession.finish(System.currentTimeMillis());
        stopMonitorServiceIfIdle();
        render();
    }

    private void applySweepResult(long frequencyHz, int updateNumber, AndroidRtlSdrUsbProbe.Result result) {
        if (activityDestroyed || sweepSession == null) return;
        String freq = formatMhz(frequencyHz);
        if (result.ok && result.snapshot != null) {
            clearActionaauxError();
            rtlSnapshot = result.snapshot;
            lastRtlIqBytes = capIq(result.iqBytes);
            for (SignalObservation obs : result.session.observations()) sweepSession.addObservation(obs);
            for (String snap : result.session.snapshots) sweepSession.addSnapshot("sweep " + freq + " MHz: " + snap);
            boolean hit = result.snapshot.occupancyPercent >= sweepThresholdPercent || !result.snapshot.markers.isEmpty();
            if (hit) {
                SweepHit sweepHit = new SweepHit(frequencyHz, System.currentTimeMillis(), result.snapshot.occupancyPercent,
                        rtlConfig.sampleRateHz, result.snapshot.noiseFloorDb, result.snapshot.summary(), result.snapshot.waterfallText(6));
                sweepHits.add(0, sweepHit);
                while (sweepHits.size() > 60) sweepHits.remove(sweepHits.size() - 1);
            }
            sweepStatus = String.format(Locale.US, "Sweep update #%d %.3f MHz: %.1f%% occupied, %d marker(s).", updateNumber, frequencyHz / 1_000_000.0, result.snapshot.occupancyPercent, result.snapshot.markers.size());
        } else {
            finishFailedSweepUpdate(freq, result.status);
        }
        trimCaptureMonitorSession(sweepSession);
        updateMonitorService("RTL sweep", sweepStatus);
        renderThrottled(500L);
    }

    private void finishFailedSweepUpdate(String freq, String status) {
        sweepStatus = "Sweep stopped at " + freq + " MHz: " + status;
        setActionaauxError("Sweep source unavailable", "DeadScout could not read IQ for this sweep. Check RTL-SDR USB permission/caaux or open setup for rtl_tcp/import amobilernatives.", status);
        sweepRunning = false;
    }

    void stopSweepMonitor(boolean shouldRender) {
        boolean wasRunning = sweepRunning;
        sweepRunning = false;
        if (sweepThread != null) sweepThread.interrupt();
        if (sweepSession != null) sweepSession.finish(System.currentTimeMillis());
        if (wasRunning) sweepStatus = "Sweep stopped. Hits and observations remain in the active session.";
        finishStoppedMonitor(CaptureMonitorCoordinator.SWEEP, shouldRender);
    }

    void captureDetails() {
        section("Current capture details");
        card("Import", importStatus, !hasImportCapture() ? QUIET : ACCENT);
        card("USB", compactStatus(usbStatus), usbAdapterScanner.deviceCount() > 0 ? ACCENT : QUIET);
        for (String body : usbAdapterScanner.scanCards()) card("Attached adapter", body, ACCENT);
    }

    void capturePathChooser() {
        section("Input paths");
        card("Actions, not a catalog", "Pick what you are actually doing now. Reference-only source descriptions stay behind Show reference catalog.", ACCENT);
        sourceAction("import", !hasImportCapture() ? "Start import capture" : "Stop import capture", v -> toggleImportCapture());
        sourceAction("rtl-sdr-usb", rtlLiveCapture ? "Stop RTL capture" : "Start RTL capture", v -> toggleUsbSdrCapture());
        sourceAction("rtl-tcp", rtlTcpCapture ? "Stop external rtl_tcp capture" : "Start external rtl_tcp capture", v -> chooseRtlTcpPath());
        sourceAction("own-device-packet-capture", "Open local packet capture guide", v -> openAndroidPacketCaptureGuide());
        sourceAction("ieee802154", usbAdapterMonitor ? "Stop USB/sniffer monitor" : "Start USB/sniffer monitor", v -> chooseUsbSnifferPath("802.15.4"));
        actionButton(showAdvancedSources ? "Hide reference catalog" : "Show reference catalog", PANEL_2, TEXT, v -> { showAdvancedSources = !showAdvancedSources; render(); });
        if (showAdvancedSources) {
            for (CaptureSourceDescriptor s : SourceRegistry.sources()) card("Reference: " + s.label, s.summary + "\n" + s.capabilityLine(), s.usesNetwork ? WARN : QUIET);
        }
    }

    void captureRecipes() {
        section("Capture recipes");
        card("Self-contained recipes", "A recipe sets center frequency, sample rate, gain/AGC, and decoder route. It does not claim hardware runtime until a monitor actually receives data.", ACCENT);
        for (CaptureRecipe recipe : CaptureRecipe.builtIns()) {
            actionCard(recipe.label, recipe.card(), "Use recipe", ACCENT, v -> {
                rtlConfig = recipe.rtlConfig();
                rtlControlsVisible = true;
                rtlStatus = "Recipe loaded: " + recipe.label + ". Start RTL capture or AM/FM monitor when hardware is attached.";
                render();
            });
        }
    }

    private void chooseRtlSdrUsbPath() {
        rtlControlsVisible = true;
        if (rtlLiveCapture) { stopRtlCaptureMonitor(true); return; }
        if (usbAdapterScanner.hasRtlSdr() && usbAdapterScanner.permissionGrantedCount() >= usbAdapterScanner.deviceCount()) {
            toggleRtlLiveMonitor();
            return;
        }
        startUsbAdapterMonitor("RTL-SDR USB");
    }

    void chooseRtlTcpPath() {
        rtlControlsVisible = true;
        showExternalRtlFallback = true;
        if (rtlTcpCapture) stopRtlTcpMonitor(true);
        else startRtlTcpMonitor();
    }

    private void chooseUsbSnifferPath(String label) {
        if (usbAdapterMonitor) stopUsbAdapterMonitor(true);
        else startUsbAdapterMonitor(label + " sniffer");
    }

    void rtlSdrControls() {
        rtlControlsPanel.renderControls();
    }


    void tuneFromEdit(EditText mhz) {
        try {
            double value = Double.parseDouble(mhz.getText().toString().trim());
            setRtlFrequency(Math.round(value * 1_000_000.0), "Tuned manually");
        } catch (RuntimeException ex) {
            rtlStatus = "Enter frequency as MHz, for example 433.920 or 915.";
            render();
        }
    }

    void stepRtlFrequency(long deltaHz) {
        setRtlFrequency(rtlConfig.frequencyHz + deltaHz, "Stepped tuner");
    }

    void setRtlFrequency(long frequencyHz, String label) {
        if (!rtlFrequencyValid(frequencyHz)) {
            rtlStatus = "Invalid RTL-SDR tuner range: " + formatMhz(frequencyHz) + " MHz. Enter 24.000–1766.000 MHz for this RTL path, or open setup for another receiver/sniffer route.";
            setActionaauxError("Invalid tuner range", "That frequency is outside the RTL-SDR tuner path. Choose a valid RTL preset, or use the setup panels for 2.4 GHz sniffers / other SDR hardware.", rtlStatus);
            render();
            return;
        }
        applyRtlConfig(new RtlSdrIqPipeline.Config(frequencyHz, rtlConfig.sampleRateHz, rtlConfig.gainTenthsDb, rtlConfig.ppmCorrection, rtlConfig.agc, rtlConfig.fftSize, rtlConfig.squelchDbAboveNoise), label);
    }

    void setRtlSampleRate(int sampleRateHz) {
        applyRtlConfig(new RtlSdrIqPipeline.Config(rtlConfig.frequencyHz, sampleRateHz, rtlConfig.gainTenthsDb, rtlConfig.ppmCorrection, rtlConfig.agc, rtlConfig.fftSize, rtlConfig.squelchDbAboveNoise), "Sample rate changed");
    }

    void applyRtlConfig(RtlSdrIqPipeline.Config config, String label) {
        try { config.validate(); } catch (RuntimeException ex) {
            setActionaauxError("Invalid RTL setup", "The selected frequency/sample-rate combination is outside the current RTL-SDR limits. Adjust tuner setup and retry.", ex.toString());
            rtlStatus = "Invalid RTL setup: " + ex.getMessage();
            render();
            return;
        }
        rtlConfig = config;
        rememberRtlFrequency(config.frequencyHz);
        clearActionaauxError();
        rtlStatus = label + ": " + rtlTuningLine() + (rtlLiveCapture || rtlAudioMonitor ? ". Stop and restart the continuous RTL stream to apply this tuning change." : ".");
        render();
    }






    void openUnavailablePreset(FrequencyPlan plan) {
        setActionaauxError("Preset needs another source", plan.label + " is not directly tunaaux by the current RTL-SDR path. Open setup for Android metadata, USB sniffer, HackRF/other SDR, or import routes.", plan.display() + "\n" + plan.notes);
        showAllCapturePaths = true;
        showAdvancedTools = true;
        advancedTool = "hardware";
        render();
    }

    boolean rtlFrequencyValid(long frequencyHz) {
        return frequencyHz >= 24_000_000L && frequencyHz <= 1_766_000_000L;
    }

    private void rememberRtlFrequency(long frequencyHz) {
        rtlTunerMemory.remember(frequencyHz);
    }

    void toggleFavoriteFrequency(long frequencyHz) {
        rtlTunerMemory.toggleFavorite(frequencyHz);
    }

    String formatMhz(long frequencyHz) {
        return String.format(Locale.US, "%.3f", frequencyHz / 1_000_000.0);
    }

    Button smallButton(String label) {
        Button b = styledButton(label, PANEL_2, TEXT, 16);
        b.setTextSize(12);
        return b;
    }

    void setRtlPreset(long frequencyHz, int sampleRateHz, int gainTenthsDb, boolean agc) {
        applyRtlConfig(new RtlSdrIqPipeline.Config(frequencyHz, sampleRateHz, gainTenthsDb, rtlConfig.ppmCorrection, agc, rtlConfig.fftSize, rtlConfig.squelchDbAboveNoise), "Preset selected");
    }

    String rtlTuningLine() {
        return String.format(Locale.US, "%.3f MHz · %.1f MS/s · %s · ppm %d",
                rtlConfig.frequencyHz / 1_000_000.0,
                rtlConfig.sampleRateHz / 1_000_000.0,
                rtlConfig.agc ? "AGC" : String.format(Locale.US, "gain %.1f dB", rtlConfig.gainTenthsDb / 10.0),
                rtlConfig.ppmCorrection);
    }


    private String rtlTcpArgs() {
        String gain = rtlConfig.agc ? "0" : Integer.toString(rtlConfig.gainTenthsDb / 10);
        return "-a 127.0.0.1 -p 1234 -f " + rtlConfig.frequencyHz + " -s " + rtlConfig.sampleRateHz + " -g " + gain + " -P " + rtlConfig.ppmCorrection;
    }

    void launchLocalRtlDriver() {
        try {
            String uri = "iqsrc://" + rtlTcpArgs();
            Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(uri));
            rtlStatus = "Launching local RTL-SDR driver with " + rtlTuningLine() + ". If the driver screen opens, choose/open the radio there; DeadScout will connect when the driver returns ready.";
            startActivityForResult(intent, REQ_RTL_DRIVER);
        } catch (ActivityNotFoundException ex) {
            rtlStatus = "No RTL-SDR driver app handled iqsrc://. Install the open-source SDR driver / rtl_tcp Android driver, then tap Launch again.";
            setActionaauxError("Install RTL-SDR driver", "No app handled the iqsrc:// rtl_tcp launch. Install the Android SDR driver, or use built-in USB capture with USB permission.", ex.toString());
            render();
        } catch (RuntimeException ex) {
            rtlStatus = "Could not launch local RTL-SDR driver: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
            setActionaauxError("External driver launch failed", "DeadScout could not open the rtl_tcp driver handoff. Use built-in USB capture or open setup for driver install notes.", ex.toString());
            render();
        }
    }

    private void startRtlTcpMonitor() {
        if (rtlTcpCapture) return;
        beginRtlTcpMonitor();
        rtlTcpThread = new Thread(() -> {
            try {
                while (rtlTcpCapture && !activityDestroyed) {
                    RtlSdrIqPipeline.Config config = rtlConfig;
                    AndroidRtlTcpClient.Result result = AndroidRtlTcpClient.readIq("127.0.0.1", 1234, config, rtlLiveChunkBytes(config), 1800);
                    final AndroidRtlTcpClient.Result update = result;
                    final int refreshNumber = ++rtlTcpRefreshCount;
                    final boolean keepRunning = update.ok && update.iqBytes.length >= 64;
                    runOnUiThread(() -> {
                        if (activityDestroyed) return;
                        if (!rtlTcpCapture && keepRunning) return;
                        rtlStatus = update.status + " " + update.dongleInfo + (keepRunning
                                ? " External rtl_tcp update #" + refreshNumber + "; next refresh in about 1.5 seconds."
                                : " External rtl_tcp capture stopped instead of retrying in a fast loop.");
                        appendRtlTcpMonitorResult(update, refreshNumber);
                        rtlSnapshot = update.snapshot;
                        lastRtlIqBytes = capIq(update.iqBytes);
                        clearImportCaptureState();
                        updateMonitorService("rtl_tcp", rtlStatus);
                        if (!keepRunning) {
                            rtlTcpCapture = false;
                            finishFailedMonitor(CaptureMonitorCoordinator.RTL_TCP,
                                    "rtl_tcp monitor stopped",
                                    "DeadScout could not keep receiving IQ from 127.0.0.1:1234. Launch or restart the SDR driver, then retry.",
                                    rtlStatus);
                        }
                        renderThrottled(350L);
                    });
                    if (!keepRunning) break;
                    try { Thread.sleep(RTL_TCP_REFRESH_INTERVAL_MS); } catch (InterruptedException ignored) { break; }
                }
            } finally {
                if (Thread.currentThread() == rtlTcpThread) rtlTcpThread = null;
            }
        }, "deadscout-rtl-tcp-monitor");
        rtlTcpThread.start();
    }

    private void beginRtlTcpMonitor() {
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.RTL_TCP);
        rtlControlsVisible = true;
        showExternalRtlFallback = true;
        rtlTcpRefreshCount = 0;
        rtlTcpContinuousSession = new CaptureSession("rtl-tcp-monitor-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("rtl_tcp 127.0.0.1:1234", rtlConfig.frequencyHz, rtlConfig.sampleRateHz);
        rtlTcpContinuousSession.addTag("rtl_tcp");
        rtlTcpContinuousSession.addNote("External rtl_tcp capture started. It refreshes waterfall/audio buffers while raw IQ stays out of decoded packet review.");
        rtlSession = rtlTcpContinuousSession;
        rtlTcpCapture = true;
        activateMonitor(CaptureMonitorCoordinator.RTL_TCP);
        rtlStatus = "External rtl_tcp capture starting on 127.0.0.1:1234. Tap Stop external rtl_tcp capture to end it.";
        startMonitorService("rtl_tcp", rtlStatus);
        render();
    }

    private void appendRtlTcpMonitorResult(AndroidRtlTcpClient.Result update, int refreshNumber) {
        if (rtlTcpContinuousSession == null) {
            rtlTcpContinuousSession = new CaptureSession("rtl-tcp-monitor-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("rtl_tcp 127.0.0.1:1234", rtlConfig.frequencyHz, rtlConfig.sampleRateHz);
            rtlTcpContinuousSession.addNote("External rtl_tcp capture resumed. It refreshes waterfall/audio buffers while raw IQ stays out of decoded packet review.");
        }
        if (update != null && update.session != null) {
            for (PacketRecord packet : update.session.packets()) rtlTcpContinuousSession.addPacket(packet);
            for (org.deadscout.core.SignalObservation obs : update.session.observations()) rtlTcpContinuousSession.addObservation(obs);
            for (String snap : update.session.snapshots) rtlTcpContinuousSession.addSnapshot("update #" + refreshNumber + ": " + snap);
            rtlTcpContinuousSession.addNote(String.format(Locale.US, "rtl_tcp update #%d: %s", refreshNumber, compactStatus(update.status)));
            trimCaptureMonitorSession(rtlTcpContinuousSession);
        }
        rtlSession = rtlTcpContinuousSession;
    }

    void toggleRtlLiveMonitor() {
        if (rtlLiveCapture) {
            stopRtlCaptureMonitor(true);
            return;
        }
        beginRtlCaptureMonitor();
        rtlLiveThread = new Thread(() -> {
            RtlSdrIqPipeline.Config config = rtlConfig;
            int chunkBytes = rtlLiveChunkBytes(config);
            rtlUsbProbe.stream(config, chunkBytes, RTL_LIVE_TRANSFER_TIMEOUT_MS,
                    () -> rtlLiveCapture && !activityDestroyed,
                    (result, chunkIndex) -> {
                        final AndroidRtlSdrUsbProbe.Result update = result;
                        final int refreshNumber = chunkIndex <= 0 ? ++rtlLiveRefreshCount : chunkIndex;
                        final boolean keepRunning = update.ok && update.iqBytes.length >= 64;
                        runOnUiThread(() -> {
                            if (activityDestroyed) return;
                            if (!rtlLiveCapture && keepRunning) return;
                            rtlLiveRefreshCount = Math.max(rtlLiveRefreshCount, refreshNumber);
                            rtlStatus = update.status + (keepRunning
                                    ? " Continuous capture is holding the USB stream open; decoded packet records are added only when a decoder emits real frames."
                                    : " RTL capture stopped instead of retrying in a fast loop.");
                            appendRtlMonitorResult(update, refreshNumber);
                            rtlSnapshot = update.snapshot;
                            lastRtlIqBytes = capIq(update.iqBytes);
                            clearImportCaptureState();
                            updateMonitorService("RTL capture", rtlStatus);
                            if (!keepRunning) {
                                rtlLiveCapture = false;
                                finishFailedMonitor(CaptureMonitorCoordinator.RTL,
                                        "RTL capture stopped",
                                        "DeadScout could not keep receiving IQ from the USB radio. Check USB permission, caaux/OTG power, and that no other SDR app is claiming the device.",
                                        rtlStatus);
                            }
                            renderThrottled(350L);
                        });
                    });
            if (Thread.currentThread() == rtlLiveThread) rtlLiveThread = null;
        }, "deadscout-rtl-live-monitor");
        rtlLiveThread.start();
    }

    private void beginRtlCaptureMonitor() {
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.RTL);
        rtlLiveRefreshCount = 0;
        rtlContinuousSession = new CaptureSession("rtl-capture-monitor-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("rtl-sdr-usb", rtlConfig.frequencyHz, rtlConfig.sampleRateHz);
        rtlContinuousSession.addTag("rtl-sdr-usb");
        rtlContinuousSession.addNote("RTL capture started. DeadScout holds the USB stream open for live waterfall/audio monitoring; decoded packets appear only when a decoder finds real frames.");
        rtlSession = rtlContinuousSession;
        rtlLiveCapture = true;
        activateMonitor(CaptureMonitorCoordinator.RTL);
        rtlStatus = "RTL capture starting. DeadScout will hold the USB stream open until you tap Stop RTL capture.";
        startMonitorService("RTL capture", rtlStatus);
        render();
    }

    private int rtlLiveChunkBytes(RtlSdrIqPipeline.Config config) {
        int oneSecond = Math.max(1, config.sampleRateHz) * 2;
        return Math.min(RTL_LIVE_MAX_CHUNK_BYTES, Math.max(RTL_LIVE_MIN_CHUNK_BYTES, oneSecond));
    }

    private void appendRtlMonitorResult(AndroidRtlSdrUsbProbe.Result update, int refreshNumber) {
        if (rtlContinuousSession == null) {
            rtlContinuousSession = new CaptureSession("rtl-capture-monitor-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("rtl-sdr-usb", rtlConfig.frequencyHz, rtlConfig.sampleRateHz);
            rtlContinuousSession.addNote("RTL capture resumed. DeadScout holds the USB stream open for live waterfall/audio monitoring; decoded packets appear only when a decoder finds real frames.");
        }
        if (update != null && update.session != null) {
            for (PacketRecord packet : update.session.packets()) rtlContinuousSession.addPacket(packet);
            for (org.deadscout.core.SignalObservation obs : update.session.observations()) rtlContinuousSession.addObservation(obs);
            for (String snap : update.session.snapshots) rtlContinuousSession.addSnapshot("update #" + refreshNumber + ": " + snap);
            rtlContinuousSession.addNote(String.format(Locale.US, "Update #%d: %s", refreshNumber, compactStatus(update.status)));
            trimCaptureMonitorSession(rtlContinuousSession);
        }
        rtlSession = rtlContinuousSession;
    }

    void trimCaptureMonitorSession(CaptureSession session) {
        if (session == null) return;
        while (session.notes.size() > MAX_SESSION_NOTES) session.notes.remove(1);
        while (session.packets.size() > MAX_SESSION_PACKETS) session.packets.remove(0);
        while (session.observations.size() > MAX_SESSION_OBSERVATIONS) session.observations.remove(0);
        while (session.snapshots.size() > MAX_SESSION_SNAPSHOTS) session.snapshots.remove(0);
    }

    private byte[] capIq(byte[] iq) {
        if (iq == null) return new byte[0];
        if (iq.length <= MAX_SAVED_IQ_BYTES) return iq;
        return Arrays.copyOfRange(iq, iq.length - MAX_SAVED_IQ_BYTES, iq.length);
    }

    void toggleRtlAudioMonitor(boolean fm) {
        String requestedMode = fm ? "FM" : "AM";
        if (rtlAudioMonitor && requestedMode.equals(rtlAudioMode)) {
            stopRtlAudioMonitor(true);
            return;
        }
        beginRtlAudioMonitor(requestedMode);
        rtlAudioMonitorThread = new Thread(() -> {
            long[] lastUiMs = new long[]{0L};
            try {
                int audioRate = RtlSdrAudioDemodulator.DEFAULT_AUDIO_RATE_HZ;
                AudioTrack stream = createRtlAudioStream(audioRate);
                rtlAudioTrack = stream;
                stream.play();
                RtlSdrIqPipeline.Config config = rtlConfig;
                int chunkBytes = rtlLiveChunkBytes(config);
                rtlUsbProbe.stream(config, chunkBytes, RTL_LIVE_TRANSFER_TIMEOUT_MS,
                        () -> rtlAudioMonitor && !activityDestroyed && requestedMode.equals(rtlAudioMode),
                        (result, chunkIndex) -> {
                            if (!result.ok || result.iqBytes.length < 128) {
                                String status = requestedMode + " monitor stopped: " + result.status;
                                runOnUiThread(() -> {
                                    if (activityDestroyed) return;
                                    rtlAudioMonitor = false;
                                    stopRtlAudio();
                                    rtlAudioStatus = status;
                                    finishFailedMonitor(CaptureMonitorCoordinator.AUDIO,
                                            requestedMode + " monitor stopped",
                                            "DeadScout could not keep receiving audio IQ from the USB radio. Check USB permission/caaux or switch to external rtl_tcp fallback.",
                                            status);
                                    render();
                                });
                                return;
                            }
                            short[] pcm = fm
                                    ? RtlSdrAudioDemodulator.fmToPcm16(result.iqBytes, config.sampleRateHz, audioRate, audioRate)
                                    : RtlSdrAudioDemodulator.amEnvelopeToPcm16(result.iqBytes, config.sampleRateHz, audioRate, audioRate);
                            if (pcm.length > 0 && rtlAudioMonitor && requestedMode.equals(rtlAudioMode)) {
                                try { stream.write(pcm, 0, pcm.length); } catch (RuntimeException ignored) { }
                            }
                            final AndroidRtlSdrUsbProbe.Result update = result;
                            final int refreshNumber = chunkIndex;
                            final double occupied = update.snapshot == null ? 0.0 : update.snapshot.occupancyPercent;
                            long now = System.currentTimeMillis();
                            if (refreshNumber <= 1 || now - lastUiMs[0] >= 1500L) {
                                lastUiMs[0] = now;
                                runOnUiThread(() -> {
                                    if (activityDestroyed || !rtlAudioMonitor || !requestedMode.equals(rtlAudioMode)) return;
                                    rtlSnapshot = update.snapshot;
                                    lastRtlIqBytes = capIq(update.iqBytes);
                                    rtlStatus = update.status + " " + requestedMode + " monitor is holding the USB stream open; decoded packets are captured only when a decoder emits real frames.";
                                    rtlAudioStatus = String.format(Locale.US, "%s continuous monitor running · chunk %d · %.1f%% occupied", requestedMode, refreshNumber, occupied);
                                    updateMonitorService(requestedMode + " audio", rtlAudioStatus);
                                    renderThrottled(350L);
                                });
                            }
                        });
            } finally {
                if (Thread.currentThread() == rtlAudioMonitorThread) rtlAudioMonitorThread = null;
            }
        }, "deadscout-rtl-audio-monitor");
        rtlAudioMonitorThread.start();
    }

    private void beginRtlAudioMonitor(String requestedMode) {
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.AUDIO);
        rtlControlsVisible = true;
        rtlAudioMode = requestedMode;
        rtlAudioMonitor = true;
        activateMonitor(CaptureMonitorCoordinator.AUDIO);
        rtlAudioStatus = requestedMode + " monitor starting from " + rtlTuningLine() + ". It listens live without adding raw IQ to packet review.";
        rtlStatus = "Live " + requestedMode + " monitor active. Use Stop " + requestedMode + " monitor or Stop audio to end it.";
        startMonitorService(requestedMode + " audio", rtlAudioStatus);
        render();
    }

    void stopRtlAudioMonitor(boolean shouldRender) {
        boolean wasRunning = rtlAudioMonitor;
        rtlAudioMonitor = false;
        if (rtlAudioMonitorThread != null) rtlAudioMonitorThread.interrupt();
        stopRtlAudio();
        if (wasRunning) rtlAudioStatus = "Live radio audio monitor stopped. Last IQ buffer remains available for clip playback and Lab inspection.";
        finishStoppedMonitor(CaptureMonitorCoordinator.AUDIO, shouldRender);
    }

    void recordRtlAudioClip() {
        rtlStatus = "Recording a 2 second RTL-SDR IQ clip for audio playback…";
        rtlAudioStatus = "Recording clip from " + rtlTuningLine() + "…";
        render();
        new Thread(() -> {
            int targetBytes = Math.min(10 * 1024 * 1024, Math.max(512 * 1024, rtlConfig.sampleRateHz * 2 * 2));
            AndroidRtlSdrUsbProbe.Result result = rtlUsbProbe.capture(rtlConfig, targetBytes, 2600);
            runOnUiThread(() -> {
                if (activityDestroyed) return;
                rtlStatus = result.status;
                rtlSession = result.session;
                rtlSnapshot = result.snapshot;
                lastRtlIqBytes = capIq(result.iqBytes);
                clearImportCaptureState();
                double seconds = result.iqBytes.length / Math.max(1.0, rtlConfig.sampleRateHz * 2.0);
                rtlAudioStatus = result.ok
                        ? String.format(Locale.US, "Recorded %.2f s / %d IQ bytes. Use Start AM/FM monitor for live listening, or play the last clip from radio setup.", seconds, result.iqBytes.length)
                        : "No playaaux IQ clip recorded yet. " + result.status;
                if (result.ok) clearActionaauxError();
                else setActionaauxError("RTL audio clip failed", "DeadScout could not read enough IQ for a playaaux clip. Check USB permission/caaux or try external rtl_tcp fallback.", result.status);
                render();
            });
        }, "deadscout-rtl-audio-record").start();
    }

    void playRtlAudio(boolean fm) {
        if (lastRtlIqBytes == null || lastRtlIqBytes.length < 128) {
            rtlAudioStatus = "No radio IQ is available yet. Tap Start RTL capture, Start external rtl_tcp capture, or Record audio first.";
            render();
            return;
        }
        try {
            playRtlAudioBuffer(lastRtlIqBytes, fm, (fm ? "FM" : "AM/envelope") + " playback from last RTL-SDR IQ clip");
        } catch (RuntimeException ex) {
            rtlAudioStatus = "Audio playback failed: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
        }
        render();
    }

    private AudioTrack createRtlAudioStream(int audioRate) {
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(audioRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        int minBytes = AudioTrack.getMinBufferSize(audioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBytes > 0 ? minBytes * 4 : 0, audioRate * 2);
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    private void playRtlAudioBuffer(byte[] iqBytes, boolean fm, String label) {
        if (iqBytes == null || iqBytes.length < 128) throw new IllegalArgumentException("not enough IQ bytes for audio");
        stopRtlAudio();
        int audioRate = RtlSdrAudioDemodulator.DEFAULT_AUDIO_RATE_HZ;
        short[] pcm = fm
                ? RtlSdrAudioDemodulator.fmToPcm16(iqBytes, rtlConfig.sampleRateHz, audioRate, audioRate * 2)
                : RtlSdrAudioDemodulator.amEnvelopeToPcm16(iqBytes, rtlConfig.sampleRateHz, audioRate, audioRate * 2);
        if (pcm.length == 0) throw new IllegalStateException("audio demodulator produced no samples");
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(audioRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        rtlAudioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        rtlAudioTrack.write(pcm, 0, pcm.length);
        rtlAudioTrack.play();
        double seconds = pcm.length / (double) audioRate;
        rtlAudioStatus = String.format(Locale.US, "%s: playing %.2f s from %d IQ bytes.", label, seconds, iqBytes.length);
    }

    void stopRtlAudio() {
        try { if (rtlAudioTrack != null) rtlAudioTrack.stop(); } catch (RuntimeException ignored) { }
        try { if (rtlAudioTrack != null) rtlAudioTrack.release(); } catch (RuntimeException ignored) { }
        rtlAudioTrack = null;
    }

    void runDirectUsbProbe() {
        rtlStatus = "Running quick built-in RTL-SDR USB test…";
        render();
        new Thread(() -> {
            AndroidRtlSdrUsbProbe.Result result = rtlUsbProbe.probe(rtlConfig, 150);
            runOnUiThread(() -> {
                if (activityDestroyed) return;
                rtlStatus = result.status;
                rtlSession = result.session;
                rtlSnapshot = result.snapshot;
                lastRtlIqBytes = capIq(result.iqBytes);
                clearImportCaptureState();
                if (result.ok) clearActionaauxError();
                else setActionaauxError("RTL USB test failed", "DeadScout could not read IQ from the built-in USB path. Check USB permission/caaux or open setup for fallback options.", result.status);
                render();
            });
        }, "deadscout-rtl-usb-probe").start();
    }

    private void packetsMode() {
        packetReviewPanel.render();
    }

    private void labMode() {
        labPanel.render();
    }

    private void moreMode() {
        section("Advanced workbench");
        card("Quiet by default", "DeadScout now puts capture first. Deep packet tables, reports, plugins, and node catalogs live here so the main screen stays usaaux.", ACCENT);
        actionButton(showAdvancedTools ? "Hide advanced tools" : "Show advanced tools", PANEL_2, TEXT, v -> { showAdvancedTools = !showAdvancedTools; render(); });
        if (!showAdvancedTools) return;

        CaptureSession session = activeSession();
        advancedToolPicker();
        if (!advancedTool.isEmpty()) advancedToolDetail(advancedTool, session);
        if (session == null) {
            card("No active session", "Import a capture or run a capture before running packet workbench reports.", QUIET);
        } else {
            PacketWorkbenchReport report = PacketWorkbench.analyze(session.packets());
            section("Packet workbench");
            card("Capture summary", report.summary(), ACCENT);
            card("Display filters", PacketWorkbench.displayFilterExamples(), WARN);
            card("Protocol hierarchy", report.protocolHierarchy(), ACCENT);
            card("Endpoints", report.endpointTable(10), ACCENT);
            card("Findings", report.findingTable(12), WARN);
            card("Correlations", CrossProtocolCorrelationEngine.table(session, 8), WARN);
            if (!session.packets().isEmpty()) {
                String raw = session.packets().get(0).rawHex;
                if (raw == null || raw.isEmpty()) raw = "";
                card("Hex / ASCII inspector", raw.isEmpty() ? "No raw packet hex is attached to the first packet. Select a packet with raw bytes or import a PCAP/PCAPNG capture." : HexInspector.hexdump(raw, 96) + "\nEntropy: " + String.format(Locale.US, "%.2f", HexInspector.entropyBitsPerByte(raw)) + " bits/byte", raw.isEmpty() ? QUIET : ACCENT);
            }
            AlertEngine engine = AlertEngine.defaultWatchRules();
            engine.evaluate(session);
            card("Watch rules", engine.eventLog(16), WARN);
        }

        section("Reports");
        if (session != null) card("Report preview", shorten(ReportGenerator.markdownReport(session, null), 900), WARN);
        else card("Report preview", "Import or capture a session before generating a report.", QUIET);
        section("Plugins / nodes");
        card("Remote nodes", RemoteNodeManager.configuredController().summary(), ACCENT);
        card("Decoder plugins", DecoderPluginRegistry.summary(), WARN);
        for (DecoderPlugin p : DecoderPluginRegistry.forInput("802.15.4")) card(p.name, p.card(), ACCENT);
        card("rtl_433 integration", Rtl433Module.integrationStatus(), ACCENT);
        card("rtl_433 license", Rtl433Module.licenseNotice(), WARN);
        card("802.15.4 readers", Ieee802154SnifferReader.supportedReaders(), ACCENT);

        section("Band planner");
        card("Coverage", SurroundingCapturePlanner.coverageSummary(), WARN);
        for (FrequencyPlan p : FrequencyPlan.presets()) card(p.display(), p.notes, ACCENT);
        card("Surrounding matrix", SurroundingCapturePlanner.matrix(14), WARN);
        sessionLibraryPanel.render(session);
    }

    private void advancedToolPicker() {
        section("Tool picker");
        actionCard("Local packet capture", "Built-in no-root path: DeadScout starts a local VpnService/TUN capture, writes raw IPv4/IPv6 packets to app-private PCAP, then imports them into packet review.", "Open capture", ACCENT, v -> { advancedTool = "android-packet-capture"; render(); });
        actionCard("Packet workbench", "Filters, protocol hierarchy, endpoints, findings, and hex/ASCII review for decoded packets.", "Open workbench", ACCENT, v -> { advancedTool = "packet-workbench"; render(); });
                actionCard("Hardware compatibility", "Phone/RTL-SDR/802.15.4/HackRF priority matrix and support state.", "Open hardware", ACCENT, v -> { advancedTool = "hardware"; render(); });
        actionCard("Plugins and nodes", "Decoder module status, rtl_433 license note, 802.15.4 readers, and remote node notes.", "Open plugins", ACCENT_2, v -> { advancedTool = "plugins"; render(); });
        actionCard("Band planner", "Frequency coverage matrix for SDR, ISM, ADS-B, 802.15.4, and utility paths.", "Open planner", WARN, v -> { advancedTool = "band-planner"; render(); });
    }

    private void advancedToolDetail(String tool, CaptureSession session) {
        section("Selected tool");
        if ("android-packet-capture".equals(tool)) {
            card("Built-in local packet capture", "DeadScout uses the local VpnService/TUN consent flow to capture own-device IPv4/IPv6 packets into an app-private PCAP. The OS VPN icon means local packet capture, not a remote VPN or telemetry.", ACCENT);
            buttonRow(new String[]{androidPacketButtonLabel(), "Import PCAP", "Root/tcpdump notes"}, new View.OnClickListener[]{
                    v -> toggleBuiltInAndroidPacketCapture(),
                    v -> toggleImportCapture(),
                    v -> { advancedTool = "root-packet-capture"; render(); }
            });
        } else if ("root-packet-capture".equals(tool)) {
            card("Advanced root capture", "Rooted-device path: run tcpdump from adb shell/Termux, save .pcap, then import it. Example shape: tcpdump -i any -s 0 -w /sdcard/deadscout.pcap. This is outside normal no-root device security and DeadScout does not require it.", WARN);
            actionButton("Import tcpdump PCAP", ACCENT, BG, v -> toggleImportCapture());
        } else if ("packet-workbench".equals(tool)) {
            card("Packet workbench action", session == null ? "No decoded packet session yet. Import PCAP/PCAPNG or capture a supported protocol first." : "Active decoded packets: " + session.packets().size() + "\nUse the detailed sections below for hierarchy, endpoints, findings, correlations, and hex review.", session == null ? QUIET : ACCENT);
        } else if ("hardware".equals(tool)) {
            card("Priority hardware", HardwareCompatibilityDatabase.summary(), ACCENT);
            for (HardwareCompatibilityDatabase.Entry e : HardwareCompatibilityDatabase.priorityMatrix()) card(e.label, e.card(), e.priority.equals("primary") ? ACCENT : QUIET);
        } else if ("plugins".equals(tool)) {
            card("Plugin action", "Scroll to Plugins / nodes below. Every tile now responds; unavailable tools show clear scope notes instead of silent taps.", ACCENT_2);
        } else if ("band-planner".equals(tool)) {
            card("Band planner action", "Scroll to Band planner below for coverage and frequency matrix. Planner is reference guidance; it does not imply hardware runtime was tested.", WARN);
        }
    }

    private void openAndroidPacketCaptureGuide() {
        showAdvancedTools = true;
        advancedTool = "android-packet-capture";
        mode = "More";
        render();
    }

    void emptyState() {
        card("No decoded packets yet", "Use Start import, Start USB, Start RTL capture, or the local packet-capture guide. Raw SDR IQ stays in the waterfall/audio path until a decoder finds real frames.", QUIET);
        actionButton("Go to capture", ACCENT, BG, v -> { mode = "Capture"; render(); });
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Import DeadScout capture"), REQ_IMPORT_CAPTURE);
    }

    private void startUsbAdapterMonitor(String label) {
        if (usbAdapterMonitor) return;
        beginUsbAdapterMonitor(label);
        usbAdapterThread = new Thread(() -> {
            try {
                while (usbAdapterMonitor && !activityDestroyed) {
                    final int refresh = ++usbAdapterRefreshCount;
                    final String summary = usbAdapterScanner.scanSummary();
                    final int deviceCount = usbAdapterScanner.deviceCount();
                    final int granted = usbAdapterScanner.permissionGrantedCount();
                    final boolean hasRtl = usbAdapterScanner.hasRtlSdr();
                    final boolean needsPermission = deviceCount > 0 && granted < deviceCount;
                    final long now = System.currentTimeMillis();
                    final boolean requestPermissionNow = needsPermission && now - lastUsbPermissionRequestMs > 4000L;
                    runOnUiThread(() -> {
                        if (activityDestroyed || !usbAdapterMonitor) return;
                        if (requestPermissionNow) {
                            lastUsbPermissionRequestMs = System.currentTimeMillis();
                            usbAdapterScanner.requestUsbPermissions();
                        }
                        usbStatus = usbMonitorUpdateStatus(refresh, summary, needsPermission);
                        if (hasRtl) noteRtlDetectedByUsbMonitor();
                        updateMonitorService("USB monitor", usbStatus);
                        renderThrottled(500L);
                    });
                    try { Thread.sleep(USB_MONITOR_REFRESH_INTERVAL_MS); } catch (InterruptedException ignored) { break; }
                }
            } finally {
                if (Thread.currentThread() == usbAdapterThread) usbAdapterThread = null;
            }
        }, "deadscout-usb-monitor");
        usbAdapterThread.start();
    }

    private String usbMonitorUpdateStatus(int refresh, String summary, boolean needsPermission) {
        return "USB monitor update #" + refresh + ": " + summary
                + (needsPermission ? " Permission required; accept Android prompt, then keep monitoring or start capture." : "")
                + " Tap Stop USB to end monitoring.";
    }

    private void noteRtlDetectedByUsbMonitor() {
        rtlControlsVisible = true;
        rtlStatus = "RTL-SDR detected by USB monitor. Tap Start RTL capture to begin packet capture; tap Stop USB to end adapter monitoring.";
    }

    private void beginUsbAdapterMonitor(String label) {
        stopRunningCaptureMonitors(CaptureMonitorCoordinator.USB);
        usbAdapterMonitor = true;
        activateMonitor(CaptureMonitorCoordinator.USB);
        usbAdapterRefreshCount = 0;
        usbStatus = "USB monitor starting for " + label + ". Connect hardware or grant Android USB permission; tap Stop USB to end it.";
        rtlStatus = "USB monitor active. If an RTL-SDR appears with permission, Start RTL will begin waterfall/audio capture.";
        startMonitorService("USB monitor", usbStatus);
        render();
    }

    CaptureSession activeSession() {
        if (hasImportCapture()) return lastImport.session;
        if (rtlSession != null) return rtlSession;
        return null;
    }

    String sourceStatus() {
        String activeStatus = activeSourceStatus();
        if (activeStatus != null) return activeStatus;
        if (hasImportCapture()) return lastImport.detectedType;
        if (trainingSession != null && rtlSession == trainingSession) return "Training sample";
        if (rtlSession != null) return "RTL-SDR";
        if (rtlControlsVisible) return "RTL selected";
        if (usbAdapterScanner != null && usbAdapterScanner.hasRtlSdr()) return "RTL attached";
        return "Choose input";
    }

    private String activeSourceStatus() {
        return activeMonitorStatus("RTL capture", "rtl_tcp capture", " audio", "RTL sweep", "Local capture", "USB monitor");
    }

    String packetCountText() {
        CaptureSession session = activeSession();
        int packets = session == null ? 0 : session.packets().size();
        return packets + " pkt";
    }

    private String captureStatus() {
        String activeStatus = activeCaptureStatus();
        if (activeStatus != null) return activeStatus;
        if (hasImportCapture()) return "Import active";
        if (rtlSession != null) return "Radio ready";
        return "Idle";
    }

    private String activeCaptureStatus() {
        return activeMonitorStatus("RTL running", "rtl_tcp running", " live", "Sweep running", "Local packet", "USB monitor");
    }

    private String activeMonitorStatus(String rtlStatus, String rtlTcpStatus, String audioSuffix,
                                       String sweepStatus, String androidStatus, String usbStatus) {
        if (rtlLiveCapture) return rtlStatus;
        if (rtlTcpCapture) return rtlTcpStatus;
        if (rtlAudioMonitor) return rtlAudioMode + audioSuffix;
        if (sweepRunning) return sweepStatus;
        if (androidPacketCapture) return "Local packet capture";
        if (usbAdapterMonitor) return usbStatus;
        return null;
    }

    String signalStatus() {
        SpectrumSnapshot snapshot = currentSpectrumSnapshot();
        if (snapshot == null) return "No IQ";
        return String.format(Locale.US, "%.1f%% occ", snapshot.occupancyPercent);
    }

    String sessionSummary(CaptureSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append(session.packets().size()).append(" decoded packets");
        sb.append(" · ").append(session.observations.size()).append(" signal observations");
        if (session.startedMillis > 0) sb.append("\nStarted: ").append(session.startedMillis);
        if (session.stoppedMillis > 0) sb.append("\nStopped: ").append(session.stoppedMillis);
        if (!session.sourceId.isEmpty()) sb.append("\nSource: ").append(session.sourceId);
        if (session.centerFrequencyHz > 0) sb.append("\nFrequency: ").append(formatMhz(session.centerFrequencyHz)).append(" MHz");
        if (session.sampleRateHz > 0) sb.append("\nSample rate: ").append(session.sampleRateHz).append(" sps");
        if (!session.tags.isEmpty()) sb.append("\nTags: ").append(session.tags);
        if (!session.snapshots.isEmpty()) sb.append("\nSnapshots: ").append(session.snapshots.size());
        boolean rtl = session.id.startsWith("rtl-") || session.sourceId.toLowerCase(Locale.US).contains("rtl") || (!session.packets().isEmpty() && session.packets().get(0).sourceId.startsWith("rtl-"));
        if (rtl) {
            sb.append("\nTuning: ").append(rtlTuningLine());
            if (rtlLiveCapture) sb.append("\nStatus: RTL capture running");
            else if (rtlTcpCapture) sb.append("\nStatus: external rtl_tcp capture running");
            else if (sweepRunning) sb.append("\nStatus: RTL sweep running");
            else sb.append("\nStatus: capture stopped/ready");
            if (rtlSnapshot != null) sb.append(String.format(Locale.US, "\nSignal: %.1f%% occupied", rtlSnapshot.occupancyPercent));
            if (hasRtlSetupWarnings(session)) sb.append("\nRadio setup: IQ received; technical tuning warnings hidden");
            return sb.toString();
        }
        if (!session.notes.isEmpty()) {
            sb.append("\nNotes:");
            for (int i = 0; i < Math.min(2, session.notes.size()); i++) sb.append("\n- ").append(compactStatus(session.notes.get(i)));
        }
        return sb.toString();
    }

    private boolean hasRtlSetupWarnings(CaptureSession session) {
        for (PacketRecord p : session.packets()) {
            String failures = p.decode.fields.get("control_failures");
            if (failures != null) {
                try { if (Integer.parseInt(failures) > 0) return true; } catch (RuntimeException ignored) { }
            }
        }
        for (String note : session.notes) {
            String lower = note == null ? "" : note.toLowerCase(Locale.US);
            if (lower.contains("non-fatal") || lower.contains("tuning warning") || lower.contains("control_fail")) return true;
        }
        return false;
    }

    String packetBrief(PacketRecord p) {
        if (p.sourceId.startsWith("rtl-")) return rtlPacketBrief(p);
        StringBuilder sb = new StringBuilder();
        sb.append(p.decode.status).append(" · ").append(p.decode.summary);
        if (p.frequencyHz > 0) sb.append(String.format(Locale.US, "\n%.3f MHz", p.frequencyHz / 1_000_000.0));
        if (!p.sourceId.isEmpty()) sb.append(" · ").append(p.sourceId);
        if (p.rssiDbm != 0) sb.append(String.format(Locale.US, "\nRSSI %.1f dBm", p.rssiDbm));
        if (!p.channel.isEmpty()) sb.append(" · ").append(p.channel);
        int count = 0;
        for (Map.Entry<String, String> e : p.decode.fields.entrySet()) {
            if (count++ >= 4) break;
            sb.append("\n").append(e.getKey()).append(": ").append(shorten(e.getValue(), 80));
        }
        if (!p.rawHex.isEmpty() || !p.rawBits.isEmpty()) sb.append("\nTechnical payload hidden");
        return sb.toString();
    }

    private String rtlPacketBrief(PacketRecord p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.sourceId.equals("rtl-tcp-local") ? "Captured external rtl_tcp IQ for spectrum/waterfall review" : "Captured RTL-SDR IQ for spectrum/waterfall review");
        if (p.frequencyHz > 0) sb.append(String.format(Locale.US, "\nFrequency: %.3f MHz", p.frequencyHz / 1_000_000.0));
        appendFriendlyField(sb, p, "iq_bytes", "IQ bytes");
        appendFriendlyField(sb, p, "sample_rate_hz", "Sample rate");
        appendFriendlyField(sb, p, "gain", "Gain");
        appendFriendlyField(sb, p, "tuner", "Tuner");
        String failures = p.decode.fields.get("control_failures");
        if (failures != null && !"0".equals(failures)) sb.append("\nRadio setup: data received; technical warnings hidden");
        return sb.toString();
    }

    private void appendFriendlyField(StringBuilder sb, PacketRecord p, String key, String label) {
        String value = p.decode.fields.get(key);
        if (value != null && !value.isEmpty()) sb.append("\n").append(label).append(": ").append(shorten(value, 80));
    }

    String technicalPacketDetails(PacketRecord p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.decode.summary);
        if (!p.channel.isEmpty()) sb.append("\nChannel: ").append(p.channel);
        if (!p.modulationGuess.isEmpty()) sb.append("\nModulation: ").append(p.modulationGuess);
        if (p.frequencyHz > 0) sb.append(String.format(Locale.US, "\nFrequency: %.3f MHz", p.frequencyHz / 1_000_000.0));
        for (Map.Entry<String, String> e : p.decode.fields.entrySet()) {
            if ("status".equals(e.getKey())) continue;
            sb.append("\n").append(e.getKey()).append(": ").append(shorten(e.getValue(), 140));
        }
        if (!p.rawHex.isEmpty()) sb.append("\nHex preview: ").append(shorten(p.rawHex, 64));
        if (!p.rawBits.isEmpty()) sb.append("\nBits preview: ").append(shorten(p.rawBits, 96));
        return sb.toString();
    }

    private String compactStatus(String status) {
        return shorten(status == null ? "" : status.replace("\n", " · "), 220);
    }

    private void sourceAction(String id, String tapLabel, View.OnClickListener listener) {
        CaptureSourceDescriptor s = findSource(id);
        if (s == null) return;
        int color = s.usesNetwork ? WARN : ACCENT;
        actionCard("▶ " + s.label, s.summary + "\n" + s.capabilityLine(), "Tap: " + tapLabel, color, listener);
    }

    private void compactSource(String id) {
        CaptureSourceDescriptor s = findSource(id);
        if (s == null) return;
        card(s.label, s.summary + "\n" + s.capabilityLine(), s.usesNetwork ? WARN : ACCENT);
    }

    private CaptureSourceDescriptor findSource(String id) {
        for (CaptureSourceDescriptor s : SourceRegistry.sources()) if (s.id.equals(id)) return s;
        return null;
    }

    SpectrumSnapshot currentSpectrumSnapshot() {
        if (spectrumFrozen && frozenSpectrumSnapshot != null) return frozenSpectrumSnapshot;
        return rawCurrentSpectrumSnapshot();
    }

    String compactSpectrum(SpectrumSnapshot snapshot) {
        return spectrumReviewPanel.compactSpectrum(snapshot);
    }

    void addSpectrumView(SpectrumSnapshot snapshot) {
        spectrumReviewPanel.addSpectrumView(snapshot);
    }

    void section(String label) {
        TextView t = text(label.toUpperCase(Locale.US), 12, MUTED, Typeface.BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(2), dp(18), 0, dp(8));
        root.addView(t);
    }

    void actionButton(String label, int bg, int fg, View.OnClickListener listener) {
        Button b = styledButton(label, bg, fg, bg == ACCENT ? 22 : 18);
        b.setTextSize(bg == ACCENT ? 15 : 13);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(bg == ACCENT ? 54 : 48));
        lp.setMargins(0, dp(6), 0, dp(6));
        root.addView(b, lp);
    }

    void buttonRow(String[] labels, View.OnClickListener[] listeners) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < labels.length; i++) {
            Button b = styledButton(labels[i], PANEL_2, TEXT, 16);
            b.setTextSize(12);
            b.setOnClickListener(listeners[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(b, lp);
        }
        root.addView(row);
    }

    private Button styledButton(String label, int bg, int fg, int radiusDp) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        int stroke = bg == ACCENT ? Color.argb(70, 255, 255, 255) : STROKE;
        b.setBackground(rounded(bg, radiusDp, stroke, 1));
        return b;
    }

    void card(String title, String body, int stripe) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(PANEL, 20, Color.argb(125, Color.red(stripe), Color.green(stripe), Color.blue(stripe)), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        box.setLayoutParams(lp);
        if (title != null && !title.isEmpty()) {
            TextView titleView = text(title, 16, stripe, Typeface.BOLD);
            titleView.setPadding(0, 0, 0, dp(5));
            box.addView(titleView);
        }
        box.addView(text(body, 13, TEXT, Typeface.NORMAL));
        root.addView(box);
    }

    void actionCard(String title, String body, String tapLabel, int stripe, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(PANEL_2, 20, Color.argb(150, Color.red(stripe), Color.green(stripe), Color.blue(stripe)), 1));
        box.setClickable(true);
        box.setFocusable(true);
        box.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        box.setLayoutParams(lp);
        if (title != null && !title.isEmpty()) box.addView(text(title, 16, stripe, Typeface.BOLD));
        TextView bodyView = text(body, 13, TEXT, Typeface.NORMAL);
        bodyView.setPadding(0, dp(5), 0, 0);
        box.addView(bodyView);
        TextView tap = pill(tapLabel, WARN);
        LinearLayout.LayoutParams tapLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tapLp.setMargins(0, dp(10), 0, 0);
        box.addView(tap, tapLp);
        root.addView(box);
    }

    private TextView text(String body, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(body == null ? "" : body);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(3f, 1.08f);
        return t;
    }

    GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private byte[] readAll(InputStream in) throws IOException {
        if (in == null) return new byte[0];
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }

    String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private int statusBarInset() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int navigationBarInset() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }


}
