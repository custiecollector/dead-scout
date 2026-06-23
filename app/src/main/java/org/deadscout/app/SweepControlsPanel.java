package org.deadscout.app;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.Locale;

final class SweepControlsPanel {
    private final MainActivity activity;

    SweepControlsPanel(MainActivity activity) {
        this.activity = activity;
    }

    void render() {
        activity.section("Scan / sweep");
        activity.card("Real frequency sweep", "DeadScout steps the RTL-SDR tuner and captures IQ at each frequency. Hits are RF observations, not decoded packet records, until a decoder emits frames.", activity.sweepRunning ? MainActivity.ACCENT : MainActivity.QUIET);
        EditText start = activity.numericBox(String.format(Locale.US, "%.3f", activity.sweepStartMhz), "start MHz");
        EditText stop = activity.numericBox(String.format(Locale.US, "%.3f", activity.sweepStopMhz), "stop MHz");
        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(start, new LinearLayout.LayoutParams(0, activity.dp(46), 1));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, activity.dp(46), 1);
        stopLp.setMargins(activity.dp(6), 0, 0, 0);
        row1.addView(stop, stopLp);
        activity.root.addView(row1);

        EditText step = activity.numericBox(String.format(Locale.US, "%.3f", activity.sweepStepMhz), "step MHz");
        EditText dwell = activity.numericBox(Integer.toString(activity.sweepDwellMs), "dwell ms");
        EditText threshold = activity.numericBox(String.format(Locale.US, "%.1f", activity.sweepThresholdPercent), "threshold %");
        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(step, new LinearLayout.LayoutParams(0, activity.dp(46), 1));
        LinearLayout.LayoutParams dwellLp = new LinearLayout.LayoutParams(0, activity.dp(46), 1);
        dwellLp.setMargins(activity.dp(6), 0, 0, 0);
        row2.addView(dwell, dwellLp);
        LinearLayout.LayoutParams thresholdLp = new LinearLayout.LayoutParams(0, activity.dp(46), 1);
        thresholdLp.setMargins(activity.dp(6), 0, 0, 0);
        row2.addView(threshold, thresholdLp);
        activity.root.addView(row2);

        activity.buttonRow(new String[]{activity.sweepRunning ? "Stop sweep" : "Start sweep", "Clear hits", "Use 902-928"}, new View.OnClickListener[]{
                v -> { if (activity.sweepRunning) activity.stopSweepMonitor(true); else activity.startSweepFromInputs(start, stop, step, dwell, threshold); },
                v -> clearHits(),
                v -> useUtilityBand()
        });
        activity.card("Sweep status", activity.sweepStatus + "\nHits: " + activity.sweepHits.size(), activity.sweepRunning ? MainActivity.ACCENT : MainActivity.QUIET);
        int limit = Math.min(8, activity.sweepHits.size());
        for (int i = 0; i < limit; i++) activity.card("Sweep hit " + (i + 1), activity.sweepHits.get(i).card(), MainActivity.WARN);
        if (activity.sweepHits.size() > limit) activity.card("More sweep hits", (activity.sweepHits.size() - limit) + " more hit(s) retained in the session.", MainActivity.QUIET);
    }

    private void clearHits() {
        activity.sweepHits.clear();
        if (activity.sweepSession != null) activity.sweepSession.observations.clear();
        activity.sweepStatus = "Sweep hits cleared.";
        activity.render();
    }

    private void useUtilityBand() {
        activity.sweepStartMhz = 902.0;
        activity.sweepStopMhz = 928.0;
        activity.sweepStepMhz = 1.0;
        activity.sweepDwellMs = 350;
        activity.sweepThresholdPercent = 8.0;
        activity.render();
    }
}
