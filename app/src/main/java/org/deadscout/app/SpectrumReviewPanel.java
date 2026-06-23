package org.deadscout.app;

import android.widget.LinearLayout;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.SpectrumSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class SpectrumReviewPanel {
    private final MainActivity activity;

    SpectrumReviewPanel(MainActivity activity) {
        this.activity = activity;
    }

    void renderControls(SpectrumSnapshot snapshot) {
        activity.section("Waterfall controls");
        activity.buttonRow(new String[]{activity.spectrumFrozen ? "Resume live" : "Freeze", "Clear", activity.spectrumShowPeakHold ? "Hide max hold" : "Peak hold"}, new android.view.View.OnClickListener[]{
                v -> toggleFreeze(),
                v -> clearReview(),
                v -> { activity.spectrumShowPeakHold = !activity.spectrumShowPeakHold; activity.render(); }
        });
        activity.buttonRow(new String[]{activity.spectrumHighContrast ? "Soft color" : "High contrast", "Zoom -", "Zoom +"}, new android.view.View.OnClickListener[]{
                v -> { activity.spectrumHighContrast = !activity.spectrumHighContrast; activity.render(); },
                v -> { activity.spectrumZoomLevel = Math.max(0, activity.spectrumZoomLevel - 1); activity.spectrumPanStep = 0; activity.render(); },
                v -> { activity.spectrumZoomLevel = Math.min(4, activity.spectrumZoomLevel + 1); activity.render(); }
        });
        activity.buttonRow(new String[]{"Pan left", "Pan right", "Save snapshot"}, new android.view.View.OnClickListener[]{
                v -> { activity.spectrumPanStep = Math.max(-8, activity.spectrumPanStep - 1); activity.render(); },
                v -> { activity.spectrumPanStep = Math.min(8, activity.spectrumPanStep + 1); activity.render(); },
                v -> saveSnapshot(snapshot)
        });
        if (!snapshot.markers.isEmpty()) {
            activity.selectedMarkerIndex = Math.max(0, Math.min(activity.selectedMarkerIndex, snapshot.markers.size() - 1));
            SpectrumSnapshot.SignalMarker marker = snapshot.markers.get(activity.selectedMarkerIndex);
            activity.card("Marker readout", "Marker " + (activity.selectedMarkerIndex + 1) + "/" + snapshot.markers.size() + "\n" + marker.display(), MainActivity.WARN);
            activity.buttonRow(new String[]{"Prev marker", "Center marker", "Next marker"}, new android.view.View.OnClickListener[]{
                    v -> { activity.selectedMarkerIndex = Math.max(0, activity.selectedMarkerIndex - 1); activity.render(); },
                    v -> { centerOnMarker(marker, snapshot); activity.render(); },
                    v -> { activity.selectedMarkerIndex = Math.min(snapshot.markers.size() - 1, activity.selectedMarkerIndex + 1); activity.render(); }
            });
        } else {
            activity.card("Marker readout", "No peak markers above threshold in the current snapshot.", MainActivity.QUIET);
        }
        activity.card("Snapshot status", (activity.spectrumFrozen ? "Frozen review is active. Capture can continue without changing this view." : "Live review follows the current capture buffer.")
                + "\nZoom: " + (1 << activity.spectrumZoomLevel) + "x · pan " + activity.spectrumPanStep
                + "\n" + activity.snapshotSaveStatus, activity.spectrumFrozen ? MainActivity.WARN : MainActivity.ACCENT);
    }

    void addSpectrumView(SpectrumSnapshot snapshot) {
        SignalView view = new SignalView(activity, snapshot, activity.spectrumShowPeakHold, activity.spectrumHighContrast, activity.spectrumZoomLevel, activity.spectrumPanStep);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(170));
        lp.setMargins(0, activity.dp(6), 0, activity.dp(6));
        activity.root.addView(view, lp);
    }

    String compactSpectrum(SpectrumSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%.3f MHz center · %.1f dB floor · %.1f%% occupied · %d marker(s)",
                snapshot.centerFrequencyHz / 1_000_000.0, snapshot.noiseFloorDb, snapshot.occupancyPercent, snapshot.markers.size()));
        for (int i = 0; i < Math.min(3, snapshot.markers.size()); i++) sb.append('\n').append(snapshot.markers.get(i).display());
        return sb.toString();
    }

    private void toggleFreeze() {
        if (activity.spectrumFrozen) {
            activity.spectrumFrozen = false;
            activity.frozenSpectrumSnapshot = null;
            activity.snapshotSaveStatus = "Live waterfall resumed.";
        } else {
            activity.frozenSpectrumSnapshot = activity.rawCurrentSpectrumSnapshot();
            activity.spectrumFrozen = activity.frozenSpectrumSnapshot != null;
            activity.snapshotSaveStatus = activity.spectrumFrozen ? "Waterfall frozen for review; live capture keeps running in the background." : "No spectrum available to freeze.";
        }
        activity.render();
    }

    private void clearReview() {
        activity.rtlSnapshot = null;
        activity.frozenSpectrumSnapshot = null;
        activity.spectrumFrozen = false;
        activity.lastRtlIqBytes = new byte[0];
        if (activity.lastImport != null && activity.lastImport.detectedType.toLowerCase(Locale.US).contains("iq")) activity.lastImportBytes = null;
        activity.snapshotSaveStatus = "Spectrum review cleared. Start/import IQ to build a new waterfall.";
        activity.render();
    }

    private void saveSnapshot(SpectrumSnapshot snapshot) {
        if (snapshot == null) {
            activity.snapshotSaveStatus = "No spectrum snapshot available to save.";
            activity.render();
            return;
        }
        try {
            File dir = new File(activity.getFilesDir(), "spectrum-snapshots");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "spectrum-" + System.currentTimeMillis() + ".txt");
            String body = snapshot.summary() + "\n\nwaterfall\n" + snapshot.waterfallText(24);
            FileOutputStream fos = new FileOutputStream(out);
            try { fos.write(body.getBytes(StandardCharsets.UTF_8)); } finally { fos.close(); }
            activity.snapshotSaveStatus = "Saved snapshot: " + out.getAbsolutePath();
            CaptureSession session = activity.activeSession();
            if (session != null) session.addSnapshot(body);
        } catch (IOException ex) {
            activity.snapshotSaveStatus = "Snapshot save failed: " + ex.getMessage();
            activity.setActionaauxError("Snapshot save failed", "DeadScout could not write the spectrum snapshot to app-private storage. Retry or clear storage if the device is full.", ex.toString());
        }
        activity.render();
    }

    private void centerOnMarker(SpectrumSnapshot.SignalMarker marker, SpectrumSnapshot snapshot) {
        if (marker == null || snapshot == null || snapshot.frequenciesHz.length < 2) return;
        double start = snapshot.frequenciesHz[0];
        double end = snapshot.frequenciesHz[snapshot.frequenciesHz.length - 1];
        double norm = (marker.frequencyHz - start) / Math.max(1.0, end - start);
        int zoom = 1 << activity.spectrumZoomLevel;
        activity.spectrumPanStep = (int) Math.round((norm - 0.5) * zoom * 4.0);
        activity.spectrumPanStep = Math.max(-8, Math.min(8, activity.spectrumPanStep));
    }
}
