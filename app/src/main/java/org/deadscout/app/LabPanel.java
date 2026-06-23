package org.deadscout.app;

import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.deadscout.core.BurstAnalysis;
import org.deadscout.core.CaptureSession;
import org.deadscout.core.HexDecodeLab;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.SpectrumSnapshot;
import org.deadscout.core.TrainingFixtures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

final class LabPanel {
    private final MainActivity activity;

    LabPanel(MainActivity activity) {
        this.activity = activity;
    }

    void render() {
        activity.section("Lab");
        CaptureSession session = activity.activeSession();
        SpectrumSnapshot snapshot = activity.currentSpectrumSnapshot();
        activity.card("Lab uses your active capture", "It analyzes the imported file or SDR/IQ session you already have. If there is no capture, it stays quiet and gives you actions to start one.", MainActivity.ACCENT);
        activity.actionButton(activity.lastImport == null ? "Start import for lab" : "Stop import for lab", MainActivity.ACCENT, MainActivity.BG, v -> activity.toggleImportCapture());
        activity.actionButton("Go to capture controls", MainActivity.PANEL_3, MainActivity.TEXT, v -> { activity.mode = "Capture"; activity.render(); });
        activity.actionButton(activity.trainingSession == null ? "Load training sample session" : "Clear training sample session", MainActivity.PANEL_3, MainActivity.TEXT, v -> toggleTrainingFixtures());
        renderHexInspector();
        renderUnknownBurstWorkbench();
        activity.signalObservationsPanel(session);
        if (session != null) {
            activity.actionButton("Open decoded packets from active capture", MainActivity.PANEL_3, MainActivity.TEXT, v -> { activity.mode = "Packets"; activity.render(); });
            activity.actionButton("Run advanced report", MainActivity.PANEL_3, MainActivity.TEXT, v -> { activity.showAdvancedTools = true; activity.mode = "More"; activity.render(); });
        }

        if (session == null && snapshot == null) {
            activity.card("No active capture", "Start import, Start Android, or Start RTL/USB from Capture first. Lab stays blank until there is real data to analyze.", MainActivity.QUIET);
        } else {
            activity.section("Active analysis");
            if (snapshot != null) {
                activity.addSpectrumView(snapshot);
                activity.card("Signal summary", activity.compactSpectrum(snapshot), MainActivity.ACCENT);
            } else {
                activity.card("Signal summary", "No IQ/waterfall in this capture. Packet-level lab tools can still analyze imported frames.", MainActivity.QUIET);
            }
            if (session != null) {
                activity.card("Session", activity.sessionSummary(session), MainActivity.ACCENT);
                PacketWorkbenchReport report = PacketWorkbench.analyze(session.packets());
                activity.card("Packet workbench", report.summary() + "\n" + report.protocolHierarchy(), session.packets().isEmpty() ? MainActivity.QUIET : MainActivity.ACCENT);
                if (!session.packets().isEmpty()) activity.card("First packet", activity.packetBrief(session.packets().get(0)), MainActivity.WARN);
            }
        }
    }

    private void toggleTrainingFixtures() {
        if (activity.trainingSession != null) {
            activity.trainingSession = null;
            if (activity.rtlSession != null && activity.rtlSession.id.startsWith("training-samples-")) activity.rtlSession = null;
            activity.labHexResult = "Training sample session cleared. Lab is ready for imported or captured data.";
            activity.render();
            return;
        }
        activity.stopRunningCaptureMonitors(CaptureMonitorCoordinator.TRAINING);
        activity.lastImport = null;
        activity.lastImportBytes = null;
        activity.rtlSnapshot = null;
        activity.lastRtlIqBytes = new byte[0];
        activity.trainingSession = TrainingFixtures.packetTrainingSession();
        activity.rtlSession = activity.trainingSession;
        activity.selectedPacketIndex = 0;
        activity.packetFilter = "";
        activity.labHexResult = "Training sample session loaded. This mode is explicitly operator-selected.";
        activity.mode = "Packets";
        activity.render();
    }

    private void renderUnknownBurstWorkbench() {
        activity.section("Unknown burst workbench");
        activity.card("Pulse/gap + symbol estimates", "Analyze unknown OOK/ASK or FSK/GFSK bursts from timing measurements or sliced symbols. Save a candidate when you want it in the active session as a packet-like workbench record.", MainActivity.ACCENT);
        EditText pulses = new EditText(activity);
        pulses.setMinLines(2);
        pulses.setMaxLines(4);
        pulses.setText(activity.burstPulseText);
        pulses.setHint("Pulse/gap microseconds: 480 510 500 1510…");
        pulses.setHintTextColor(MainActivity.MUTED);
        pulses.setTextColor(MainActivity.TEXT);
        pulses.setTextSize(12);
        pulses.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pulses.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        pulses.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        activity.root.addView(pulses, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(92)));
        EditText fsk = new EditText(activity);
        fsk.setMinLines(2);
        fsk.setMaxLines(4);
        fsk.setText(activity.burstFskText);
        fsk.setHint("FSK symbol frequency estimates Hz: -2400 2400…");
        fsk.setHintTextColor(MainActivity.MUTED);
        fsk.setTextColor(MainActivity.TEXT);
        fsk.setTextSize(12);
        fsk.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fsk.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        fsk.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        activity.root.addView(fsk, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(92)));
        EditText symbol = activity.numericBox(Integer.toString(activity.burstSymbolRate), "symbol rate");
        activity.root.addView(symbol, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(46)));
        activity.buttonRow(new String[]{"Analyze OOK", "Analyze FSK", "Save candidate"}, new android.view.View.OnClickListener[]{
                v -> analyzeOokBurst(pulses),
                v -> analyzeFskBurst(fsk, symbol),
                v -> saveBurstCandidate()
        });
        activity.card("Burst result", activity.burstWorkbenchResult, activity.burstCandidate == null ? MainActivity.QUIET : MainActivity.WARN);
    }

    private void analyzeOokBurst(EditText input) {
        try {
            activity.burstPulseText = input.getText().toString();
            int[] values = parseInts(activity.burstPulseText);
            BurstAnalysis burst = org.deadscout.core.BurstAnalyzer.analyzeOok(values);
            int repetitionUs = 0;
            for (int v : values) repetitionUs += Math.max(0, v);
            int symbolRate = burst.shortMicros <= 0 ? 0 : (int) Math.round(1_000_000.0 / burst.shortMicros);
            String hex = bitsToHex(burst.bitString);
            activity.burstCandidate = new PacketRecord(System.currentTimeMillis(), "unknown-burst-workbench", activity.rtlConfig.frequencyHz, "lab", 0, -1,
                    burst.modulation, hex, burst.bitString, burst.asDecode(), Collections.singletonMap("workbench", "ook"));
            activity.burstWorkbenchResult = burst.summary
                    + "\nPulse/gap count: " + values.length
                    + "\nSymbol-rate estimate: ~" + symbolRate + " sym/s"
                    + "\nRepetition interval: ~" + repetitionUs + " µs"
                    + "\nGuess: " + burst.modulation + " confidence " + String.format(Locale.US, "%.2f", burst.confidence)
                    + "\nRaw bits: " + burst.bitString
                    + "\nHex candidate: " + hex;
            activity.labHexText = hex;
            activity.labHexResult = HexDecodeLab.analyze(hex);
            activity.clearActionaauxError();
        } catch (RuntimeException ex) {
            activity.burstCandidate = null;
            activity.burstWorkbenchResult = "OOK analysis failed. Enter at least two pulse/gap pairs as microsecond numbers.";
            activity.setActionaauxError("Burst timing invalid", activity.burstWorkbenchResult, ex.toString());
        }
        activity.render();
    }

    private void analyzeFskBurst(EditText symbols, EditText rate) {
        try {
            activity.burstFskText = symbols.getText().toString();
            activity.burstSymbolRate = Integer.parseInt(rate.getText().toString().trim());
            double[] values = parseDoubles(activity.burstFskText);
            BurstAnalysis burst = org.deadscout.core.BurstAnalyzer.analyzeFsk(values, activity.burstSymbolRate);
            String hex = bitsToHex(burst.bitString);
            double repetitionMs = values.length * 1000.0 / Math.max(1, activity.burstSymbolRate);
            activity.burstCandidate = new PacketRecord(System.currentTimeMillis(), "unknown-burst-workbench", activity.rtlConfig.frequencyHz, "lab", 0, -1,
                    burst.modulation, hex, burst.bitString, burst.asDecode(), Collections.singletonMap("workbench", "fsk"));
            activity.burstWorkbenchResult = burst.summary
                    + "\nSymbol estimates: " + values.length
                    + "\nSymbol-rate estimate/input: " + activity.burstSymbolRate + " sym/s"
                    + "\nRepetition interval: ~" + String.format(Locale.US, "%.2f", repetitionMs) + " ms"
                    + "\nGuess: " + burst.modulation + " confidence " + String.format(Locale.US, "%.2f", burst.confidence)
                    + "\nRaw bits: " + burst.bitString
                    + "\nHex candidate: " + hex;
            activity.labHexText = hex;
            activity.labHexResult = HexDecodeLab.analyze(hex);
            activity.clearActionaauxError();
        } catch (RuntimeException ex) {
            activity.burstCandidate = null;
            activity.burstWorkbenchResult = "FSK analysis failed. Enter at least four frequency estimates and an integer symbol rate.";
            activity.setActionaauxError("FSK symbols invalid", activity.burstWorkbenchResult, ex.toString());
        }
        activity.render();
    }

    private void saveBurstCandidate() {
        if (activity.burstCandidate == null) {
            activity.burstWorkbenchResult = "Analyze an OOK or FSK burst first; no candidate is ready to save.";
            activity.render();
            return;
        }
        CaptureSession session = activity.activeSession();
        if (session == null) {
            session = new CaptureSession("lab-burst-" + System.currentTimeMillis(), System.currentTimeMillis()).withSource("unknown-burst-workbench", activity.rtlConfig.frequencyHz, activity.rtlConfig.sampleRateHz);
            activity.rtlSession = session;
        }
        session.addPacket(activity.burstCandidate);
        session.addTag("unknown-burst");
        session.addNote("Unknown burst candidate saved from Lab workbench.");
        activity.selectedPacketIndex = Math.max(0, session.packets().size() - 1);
        activity.burstWorkbenchResult += "\nSaved candidate to active session packet records.";
        activity.mode = "Packets";
        activity.render();
    }

    private int[] parseInts(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("[^0-9+-]+");
        ArrayList<Integer> vals = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty() && !"+".equals(p) && !"-".equals(p)) vals.add(Integer.parseInt(p));
        int[] out = new int[vals.size()];
        for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
        return out;
    }

    private double[] parseDoubles(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("[^0-9+-.]+");
        ArrayList<Double> vals = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty() && !"+".equals(p) && !"-".equals(p) && !".".equals(p)) vals.add(Double.parseDouble(p));
        double[] out = new double[vals.size()];
        for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
        return out;
    }

    private String bitsToHex(String bits) {
        if (bits == null || bits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(bits);
        while ((sb.length() % 8) != 0) sb.append('0');
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < sb.length(); i += 8) {
            int v = Integer.parseInt(sb.substring(i, i + 8), 2);
            hex.append(String.format(Locale.US, "%02X", v));
        }
        return hex.toString();
    }

    private void renderHexInspector() {
        activity.section("Hex / packet decoder");
        activity.card("Decode packet hex", "Select a captured packet from Packets, use the selected packet button here, or paste/import hex when needed. DeadScout identifies IP, 802.15.4, unknown framed bytes, and raw RTL IQ previews only when bytes are not already recognized as packets.", MainActivity.ACCENT);
        EditText input = new EditText(activity);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setText(activity.labHexText);
        input.setHint("Packet hex bytes, e.g. 4500... or RTL IQ preview");
        input.setHintTextColor(MainActivity.MUTED);
        input.setTextColor(MainActivity.TEXT);
        input.setTextSize(12);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        input.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(120));
        lp.setMargins(0, activity.dp(6), 0, activity.dp(6));
        activity.root.addView(input, lp);
        activity.buttonRow(new String[]{"Decode hex", "Use selected pkt", "Use last IQ"}, new android.view.View.OnClickListener[]{
                v -> { activity.labHexText = input.getText().toString(); activity.labHexResult = HexDecodeLab.analyze(activity.labHexText); activity.render(); },
                v -> { activity.packetReviewPanel.useSelectedPacketHex(activity.activeSession()); },
                v -> { activity.labHexText = org.deadscout.core.HexUtils.toHex(Arrays.copyOf(activity.lastRtlIqBytes == null ? new byte[0] : activity.lastRtlIqBytes, Math.min(activity.lastRtlIqBytes == null ? 0 : activity.lastRtlIqBytes.length, 192))); activity.labHexResult = HexDecodeLab.analyze(activity.labHexText); activity.render(); }
        });
        activity.actionButton("Clear hex", MainActivity.PANEL_3, MainActivity.TEXT, v -> { activity.labHexText = ""; activity.labHexResult = "Select a packet or enter hex, then tap Decode hex."; activity.render(); });
        activity.card("Decode result", activity.labHexResult, activity.labHexResult.contains("No confident") || activity.labHexResult.contains("IQ") ? MainActivity.WARN : MainActivity.ACCENT);
    }
}
