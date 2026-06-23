package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PacketWorkbench {
    private PacketWorkbench() {}

    public static PacketWorkbenchReport analyze(List<PacketRecord> packets) {
        PacketWorkbenchReport report = new PacketWorkbenchReport();
        if (packets == null) packets = Collections.emptyList();
        report.packetCount = packets.size();
        for (int i = 0; i < packets.size(); i++) {
            PacketRecord p = packets.get(i);
            if (p.decode.status == ProtocolDecode.Status.DECODED) report.decodedCount++;
            int bytes = p.rawHex == null ? 0 : HexUtils.fromHex(p.rawHex).length;
            report.rawByteCount += bytes;
            bump(report.protocolCounts, p.decode.protocol);
            bump(report.sourceCounts, p.sourceId);
            addEndpoint(report, p.decode.fields.get("src"), bytes, p.decode.protocol);
            addEndpoint(report, p.decode.fields.get("dst"), bytes, p.decode.protocol);
            addEndpoint(report, p.decode.fields.get("addr2_transmitter"), bytes, p.decode.protocol);
            addEndpoint(report, p.decode.fields.get("addr1_receiver"), bytes, p.decode.protocol);
            addEndpoint(report, p.decode.fields.get("device_address"), bytes, p.decode.protocol);
            addConversation(report, firstNonEmpty(p.decode.fields.get("src"), p.decode.fields.get("addr2_transmitter")),
                    firstNonEmpty(p.decode.fields.get("dst"), p.decode.fields.get("addr1_receiver")), p.decode.protocol, bytes);
            if (p.rawHex != null && !p.rawHex.isEmpty()) {
                double entropy = HexInspector.entropyBitsPerByte(p.rawHex);
                if (entropy > 7.2 && bytes >= 32) report.findings.add(new AnalysisFinding(AnalysisFinding.Severity.NOTICE,
                        "payload", "High-entropy packet bytes", String.format(Locale.US, "%.2f bits/byte over %d bytes", entropy, bytes), i));
            }
        }
        return report;
    }

    public static String displayFilterExamples() {
        return "protocol:IPv4 · source:rtl_433 · channel:802.15.4 ch 15 · src=192.168 · model=sensor · text search";
    }

    public static List<PacketRecord> displayFilter(List<PacketRecord> packets, String query) {
        return new ArrayList<>(PacketFilter.filter(packets, query));
    }

    private static void bump(java.util.LinkedHashMap<String, Integer> map, String key) {
        key = key == null || key.isEmpty() ? "unknown" : key;
        Integer v = map.get(key);
        map.put(key, v == null ? 1 : v + 1);
    }

    private static void addEndpoint(PacketWorkbenchReport report, String endpoint, int bytes, String protocol) {
        if (endpoint == null || endpoint.trim().isEmpty()) return;
        PacketWorkbenchReport.EndpointStats s = report.endpoints.get(endpoint);
        if (s == null) {
            s = new PacketWorkbenchReport.EndpointStats(endpoint);
            report.endpoints.put(endpoint, s);
        }
        s.packets++;
        s.bytes += bytes;
        s.lastProtocol = protocol == null ? "" : protocol;
    }

    private static void addConversation(PacketWorkbenchReport report, String a, String b, String protocol, int bytes) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return;
        String aa = a.compareTo(b) <= 0 ? a : b;
        String bb = a.compareTo(b) <= 0 ? b : a;
        String key = aa + "|" + bb + "|" + protocol;
        PacketWorkbenchReport.ConversationStats s = report.conversations.get(key);
        if (s == null) {
            s = new PacketWorkbenchReport.ConversationStats(aa, bb, protocol);
            report.conversations.put(key, s);
        }
        s.packets++;
        s.bytes += bytes;
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }
}
