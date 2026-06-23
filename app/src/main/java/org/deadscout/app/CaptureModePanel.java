package org.deadscout.app;

import android.view.View;

import org.deadscout.core.SpectrumSnapshot;

final class CaptureModePanel {
    private final MainActivity activity;

    CaptureModePanel(MainActivity activity) {
        this.activity = activity;
    }

    void render() {
        activity.section("Capture");
        activity.actionButton(activity.primaryActionLabel(), MainActivity.ACCENT, MainActivity.BG, v -> activity.runPrimaryAction());
        activity.buttonRow(new String[]{activity.importCaptureButtonLabel(), activity.usbCaptureButtonLabel()}, new View.OnClickListener[]{
                v -> activity.toggleImportCapture(),
                v -> activity.toggleUsbSdrCapture()
        });
        activity.buttonRow(new String[]{activity.androidPacketButtonLabel(), "Review decoded packets"}, new View.OnClickListener[]{
                v -> activity.toggleBuiltInAndroidPacketCapture(),
                v -> { activity.mode = "Packets"; activity.render(); }
        });

        activity.rtlSdrControls();
        activity.liveSourceStatusPanel();
        activity.sweepControls();

        activity.actionButton(activity.showCaptureRecipes ? "Hide capture recipes" : "Show capture recipes", MainActivity.PANEL_2, MainActivity.TEXT, v -> { activity.showCaptureRecipes = !activity.showCaptureRecipes; activity.render(); });
        if (activity.showCaptureRecipes) activity.captureRecipes();

        renderSignalSection();

        activity.actionButton(activity.showCaptureDetails ? "Hide capture details" : "Show capture details", MainActivity.PANEL_2, MainActivity.TEXT, v -> { activity.showCaptureDetails = !activity.showCaptureDetails; activity.render(); });
        if (activity.showCaptureDetails) activity.captureDetails();

        activity.actionButton(activity.showAllCapturePaths ? "Hide input paths" : "Show input paths", MainActivity.PANEL_2, MainActivity.TEXT, v -> { activity.showAllCapturePaths = !activity.showAllCapturePaths; activity.render(); });
        if (activity.showAllCapturePaths) activity.capturePathChooser();
    }

    private void renderSignalSection() {
        activity.section("Signal");
        SpectrumSnapshot snapshot = activity.currentSpectrumSnapshot();
        if (snapshot == null) {
            activity.card("No waterfall yet", "No fake rows. Import IQ/SigMF or start RTL-SDR capture/sweep; the spectrum/waterfall appears only when DeadScout has real IQ bytes.", MainActivity.QUIET);
        } else {
            activity.spectrumControls(snapshot);
            activity.addSpectrumView(snapshot);
            activity.card("Signal summary", activity.compactSpectrum(snapshot), MainActivity.ACCENT);
        }
        activity.signalObservationsPanel(activity.activeSession());
    }
}
