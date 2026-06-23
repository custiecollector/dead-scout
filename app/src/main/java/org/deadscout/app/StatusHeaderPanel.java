package org.deadscout.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class StatusHeaderPanel {
    private static final String[] MODES = {"Capture", "Packets", "Lab", "More"};

    private final MainActivity activity;

    StatusHeaderPanel(MainActivity activity) {
        this.activity = activity;
    }

    void render() {
        renderStatusCards();
        renderModeTabs();
        renderActionaauxErrorPanel();
    }

    private void renderActionaauxErrorPanel() {
        if (activity.lastErrorTitle == null || activity.lastErrorTitle.isEmpty()) return;
        activity.section("Action needed");
        activity.card(activity.lastErrorTitle, activity.lastErrorOperator, MainActivity.WARN);
        activity.buttonRow(new String[]{"Retry", "Stop active", activity.showTechnicalErrors ? "Hide technical" : "Show technical"}, new View.OnClickListener[]{
                v -> { activity.clearActionaauxError(); activity.runPrimaryAction(); },
                v -> { activity.stopRunningCaptureMonitors(CaptureMonitorCoordinator.NONE); activity.clearActionaauxError(); activity.render(); },
                v -> { activity.showTechnicalErrors = !activity.showTechnicalErrors; activity.render(); }
        });
        activity.actionButton("Open setup panels", MainActivity.PANEL_3, MainActivity.TEXT, v -> {
            activity.showCaptureDetails = true;
            activity.showAllCapturePaths = true;
            activity.showRtlRadioSetup = true;
            activity.showExternalRtlFallback = true;
            activity.showAdvancedTools = true;
            activity.advancedTool = "hardware";
            activity.mode = "More";
            activity.render();
        });
        if (activity.showTechnicalErrors && activity.lastErrorTechnical != null && !activity.lastErrorTechnical.isEmpty()) {
            activity.card("Technical details", activity.lastErrorTechnical, MainActivity.QUIET);
        }
    }

    private void renderStatusCards() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
        panel.setBackground(activity.rounded(MainActivity.PANEL, 24, MainActivity.STROKE, 1));
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelLp.setMargins(0, 0, 0, activity.dp(10));
        activity.root.addView(panel, panelLp);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row1);
        row1.addView(miniCard("Source", activity.sourceStatus(), MainActivity.ACCENT), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(miniCard("Decoded", activity.packetCountText(), MainActivity.WARN), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row2);
        row2.addView(miniCard("Signal", activity.signalStatus(), MainActivity.ACCENT_2), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(miniCard("Mode", activity.mode, MainActivity.ACCENT), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }

    private View miniCard(String label, String value, int color) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        box.setBackground(activity.rounded(MainActivity.SURFACE, 18, Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(activity.dp(3), activity.dp(3), activity.dp(3), activity.dp(3));
        box.setLayoutParams(lp);
        TextView labelView = text(label.toUpperCase(Locale.US), 10, MainActivity.MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.08f);
        box.addView(labelView);
        box.addView(text(value, 15, color, Typeface.BOLD));
        return box;
    }

    private TextView text(String body, int sp, int color, int style) {
        TextView t = new TextView(activity);
        t.setText(body == null ? "" : body);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(3f, 1.08f);
        return t;
    }

    private void renderModeTabs() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
        row.setBackground(activity.rounded(MainActivity.SURFACE, 24, MainActivity.STROKE, 1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, activity.dp(2), 0, activity.dp(12));
        for (String m : MODES) {
            boolean selected = m.equals(activity.mode);
            Button b = new Button(activity);
            b.setText(selected ? "● " + m : m);
            b.setTextColor(selected ? MainActivity.BG : MainActivity.MUTED);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setMinHeight(0);
            b.setMinWidth(0);
            b.setPadding(activity.dp(2), 0, activity.dp(2), 0);
            b.setBackground(activity.rounded(selected ? MainActivity.ACCENT : Color.TRANSPARENT, 18, selected ? MainActivity.ACCENT : Color.TRANSPARENT, 0));
            final String targetMode = m;
            b.setOnClickListener(v -> { if (!targetMode.equals(activity.mode)) { activity.mode = targetMode; activity.render(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, activity.dp(40), 1);
            lp.setMargins(activity.dp(2), 0, activity.dp(2), 0);
            row.addView(b, lp);
        }
        activity.root.addView(row, rowLp);
    }
}
