package org.deadscout.app;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.DeadScoutExport;
import org.deadscout.core.HexDecodeLab;
import org.deadscout.core.HexUtils;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PcapNgWriter;
import org.deadscout.core.PcapReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PacketReviewPanel {
    private final MainActivity activity;

    PacketReviewPanel(MainActivity activity) {
        this.activity = activity;
    }

    void render() {
        CaptureSession session = activity.activeSession();
        activity.section("Review decoded packets");
        if (session == null) {
            activity.emptyState();
            return;
        }
        activity.card("Session", activity.sessionSummary(session), MainActivity.ACCENT);
        if (!session.observations.isEmpty()) activity.card("Signal observations kept separate", session.observations.size() + " raw IQ/energy observation(s) are available in Capture/Lab and are intentionally not mixed into Packet Records.", MainActivity.ACCENT_2);
        if (session.packets().isEmpty()) {
            activity.card("No decoded traffic packets yet", "DeadScout has no decoded frames in this capture yet. Raw RTL IQ stays in the waterfall/audio path and is intentionally kept out of this packet list.", activity.currentSpectrumSnapshot() == null ? MainActivity.QUIET : MainActivity.WARN);
        } else {
            renderFilterBox(session);
            activity.actionButton(activity.showRawPacketDetails ? "Hide technical packet details" : "Show technical packet details", MainActivity.PANEL_2, MainActivity.TEXT, v -> { activity.showRawPacketDetails = !activity.showRawPacketDetails; activity.render(); });
            List<PacketRecord> visiblePackets = activity.packetFilter.trim().isEmpty() ? session.packets() : PacketWorkbench.displayFilter(session.packets(), activity.packetFilter);
            if (visiblePackets.isEmpty()) activity.card("No packets match filter", "Filter: " + activity.packetFilter + "\nTry protocol:IPv4, source:rtl_433, subtype=Beacon, src=192.168, or clear the filter.", MainActivity.WARN);
            int limit = activity.showRawPacketDetails ? Math.min(40, visiblePackets.size()) : Math.min(8, visiblePackets.size());
            for (int i = 0; i < limit; i++) {
                PacketRecord p = visiblePackets.get(i);
                final int packetIndex = Math.max(0, session.packets().indexOf(p));
                activity.actionCard((packetIndex == activity.selectedPacketIndex ? "● " : "") + p.title(), activity.showRawPacketDetails ? activity.technicalPacketDetails(p) : activity.packetBrief(p), "Select row", p.decode.status.name().equals("DECODED") ? MainActivity.ACCENT : MainActivity.WARN, v -> { activity.selectedPacketIndex = packetIndex; activity.render(); });
            }
            if (visiblePackets.size() > limit) activity.card("More decoded packets hidden", (visiblePackets.size() - limit) + " more decoded packet(s). Turn on technical details for a longer review list.", MainActivity.QUIET);
            renderHexWorkbench(session);
        }
        if (activity.showRawPacketDetails && !session.packets().isEmpty()) {
            byte[] pcap = DeadScoutExport.ieee802154PcapFromPackets(session.packets());
            activity.card("Export readiness", "Session JSON: " + session.toJson().length() + " bytes\n802.15.4 PCAP export: " + pcap.length + " bytes\nPCAPNG route: " + pcapngForSession(session).length + " bytes", MainActivity.WARN);
        }
    }

    private void renderFilterBox(CaptureSession session) {
        activity.section("Display filter");
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(activity.packetFilter);
        input.setHint("protocol:IPv4, source:rtl_433, subtype=Beacon, src=192.168, text");
        input.setHintTextColor(MainActivity.MUTED);
        input.setTextColor(MainActivity.TEXT);
        input.setTextSize(12);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        input.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(48));
        lp.setMargins(0, activity.dp(4), 0, activity.dp(4));
        activity.root.addView(input, lp);
        int matchCount = activity.packetFilter.trim().isEmpty() ? session.packets().size() : PacketWorkbench.displayFilter(session.packets(), activity.packetFilter).size();
        activity.buttonRow(new String[]{"Apply filter", "Clear filter"}, new android.view.View.OnClickListener[]{
                v -> { activity.packetFilter = input.getText().toString().trim(); activity.selectedPacketIndex = 0; activity.render(); },
                v -> { activity.packetFilter = ""; activity.selectedPacketIndex = 0; activity.render(); }
        });
        activity.card("Filter status", (activity.packetFilter.trim().isEmpty() ? "No filter" : "Filter: " + activity.packetFilter) + "\nMatching decoded packets: " + matchCount + " / " + session.packets().size() + "\nExamples: " + PacketWorkbench.displayFilterExamples(), matchCount == 0 ? MainActivity.WARN : MainActivity.ACCENT);
    }

    private byte[] pcapngForSession(CaptureSession session) {
        ArrayList<byte[]> frames = new ArrayList<>();
        if (session != null) {
            for (PacketRecord p : session.packets()) {
                String raw = packetRawHex(p);
                String protocol = p.decode == null ? "" : p.decode.protocol;
                if (raw.isEmpty() || (!protocol.contains("802.15.4") && !p.sourceId.contains("ieee802154"))) continue;
                try { frames.add(HexUtils.fromHex(raw)); } catch (RuntimeException ignored) { }
            }
        }
        return PcapNgWriter.writeSingleInterface(PcapReader.LINKTYPE_IEEE802_15_4_NOFCS, "ieee802154", frames);
    }

    private void renderHexWorkbench(CaptureSession session) {
        if (session == null || session.packets().isEmpty()) return;
        activity.selectedPacketIndex = Math.max(0, Math.min(activity.selectedPacketIndex, session.packets().size() - 1));
        PacketRecord selected = session.packets().get(activity.selectedPacketIndex);
        String raw = packetRawHex(selected);
        String title = String.format(Locale.US, "Packet hex decode #%d/%d", activity.selectedPacketIndex + 1, session.packets().size());
        activity.card("Selected packet metadata", packetMetadata(selected), MainActivity.ACCENT);
        if (raw.isEmpty()) {
            activity.card(title, selected.title() + "\nNo raw hex is attached to this decoded packet yet. Capture/import a frame source that preserves raw bytes to decode its hex here.", MainActivity.QUIET);
        } else {
            String result = HexDecodeLab.analyze(raw);
            activity.card(title, selected.title() + "\n" + activity.shorten(result, 900), result.contains("No confident") || result.contains("raw IQ") ? MainActivity.WARN : MainActivity.ACCENT);
            activity.card("Raw hex", groupHex(raw, 16, 256), MainActivity.ACCENT);
            activity.card("ASCII pane", asciiPane(raw, 256), MainActivity.ACCENT_2);
        }
        activity.card("Decoded field tree", packetFieldTree(selected), MainActivity.ACCENT);
        activity.buttonRow(new String[]{"Prev packet", "Decode in Lab", "Next packet"}, new android.view.View.OnClickListener[]{
                v -> { activity.selectedPacketIndex = Math.max(0, activity.selectedPacketIndex - 1); activity.render(); },
                v -> useSelectedPacketHex(session),
                v -> { activity.selectedPacketIndex = Math.min(session.packets().size() - 1, activity.selectedPacketIndex + 1); activity.render(); }
        });
    }

    private String packetMetadata(PacketRecord p) {
        if (p == null) return "No packet selected";
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp: ").append(p.timestampMillis);
        sb.append("\nSource: ").append(p.sourceId);
        sb.append("\nProtocol: ").append(p.decode.protocol).append(" / ").append(p.decode.status);
        if (p.frequencyHz > 0) sb.append("\nFrequency: ").append(activity.formatMhz(p.frequencyHz)).append(" MHz");
        if (!p.channel.isEmpty()) sb.append("\nChannel: ").append(p.channel);
        if (p.rssiDbm != 0) sb.append(String.format(Locale.US, "\nRSSI: %.1f dBm", p.rssiDbm));
        if (!p.modulationGuess.isEmpty()) sb.append("\nModulation guess: ").append(p.modulationGuess);
        sb.append("\nRaw bytes: ").append(packetRawHex(p).isEmpty() ? 0 : HexUtils.fromHex(packetRawHex(p)).length);
        return sb.toString();
    }

    private String groupHex(String raw, int groupBytes, int maxBytes) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        int maxChars = Math.min(cleaned.length(), Math.max(0, maxBytes) * 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxChars; i += 2) {
            if (i > 0) {
                if ((i / 2) % Math.max(1, groupBytes) == 0) sb.append('\n');
                else sb.append(' ');
            }
            sb.append(cleaned, i, Math.min(i + 2, maxChars));
        }
        if (cleaned.length() > maxChars) sb.append("\n… ").append((cleaned.length() - maxChars) / 2).append(" more bytes");
        return sb.length() == 0 ? "No raw hex" : sb.toString();
    }

    private String asciiPane(String raw, int maxBytes) {
        try {
            byte[] bytes = HexUtils.fromHex(raw == null ? "" : raw);
            return HexUtils.ascii(Arrays.copyOf(bytes, Math.min(bytes.length, maxBytes))) + (bytes.length > maxBytes ? "\n… " + (bytes.length - maxBytes) + " more bytes" : "");
        } catch (RuntimeException ex) {
            return "ASCII unavailable: " + ex.getMessage();
        }
    }

    private String packetFieldTree(PacketRecord p) {
        if (p == null) return "No packet selected";
        StringBuilder sb = new StringBuilder();
        sb.append("Frame\n");
        sb.append("  source: ").append(p.sourceId).append('\n');
        if (p.frequencyHz > 0) sb.append("  frequency: ").append(String.format(Locale.US, "%.3f MHz", p.frequencyHz / 1_000_000.0)).append('\n');
        if (!p.channel.isEmpty()) sb.append("  channel: ").append(p.channel).append('\n');
        if (p.rssiDbm != 0) sb.append("  RSSI: ").append(String.format(Locale.US, "%.1f dBm", p.rssiDbm)).append('\n');
        sb.append("Protocol\n");
        sb.append("  name: ").append(p.decode.protocol).append('\n');
        sb.append("  status: ").append(p.decode.status).append('\n');
        sb.append("  summary: ").append(p.decode.summary).append('\n');
        sb.append("Fields\n");
        int count = 0;
        for (Map.Entry<String, String> e : p.decode.fields.entrySet()) {
            if (count++ >= 24) { sb.append("  …").append(p.decode.fields.size() - 24).append(" more fields\n"); break; }
            sb.append("  ").append(e.getKey()).append(": ").append(activity.shorten(e.getValue(), 96)).append('\n');
        }
        if (count == 0) sb.append("  none\n");
        String raw = packetRawHex(p);
        sb.append("Bytes\n  raw hex bytes: ").append(raw.isEmpty() ? 0 : HexUtils.fromHex(raw).length).append('\n');
        return sb.toString();
    }

    void useSelectedPacketHex(CaptureSession session) {
        if (session == null || session.packets().isEmpty()) return;
        activity.selectedPacketIndex = Math.max(0, Math.min(activity.selectedPacketIndex, session.packets().size() - 1));
        String raw = packetRawHex(session.packets().get(activity.selectedPacketIndex));
        if (raw.isEmpty()) {
            activity.labHexText = "";
            activity.labHexResult = "Selected packet has no raw hex attached yet.";
        } else {
            activity.labHexText = raw;
            activity.labHexResult = HexDecodeLab.analyze(raw);
        }
        activity.mode = "Lab";
        activity.render();
    }

    String packetRawHex(PacketRecord packet) {
        if (packet == null) return "";
        return packet.rawHex == null ? "" : packet.rawHex.trim();
    }
}
