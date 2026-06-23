package org.deadscout.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PacketWorkbenchReport {
    public int packetCount;
    public int decodedCount;
    public int rawByteCount;
    public final LinkedHashMap<String, Integer> protocolCounts = new LinkedHashMap<>();
    public final LinkedHashMap<String, Integer> sourceCounts = new LinkedHashMap<>();
    public final LinkedHashMap<String, EndpointStats> endpoints = new LinkedHashMap<>();
    public final LinkedHashMap<String, ConversationStats> conversations = new LinkedHashMap<>();
    public final List<AnalysisFinding> findings = new ArrayList<>();

    public String summary() {
        return packetCount + " packets · " + decodedCount + " decoded · " + rawByteCount + " raw bytes · "
                + endpoints.size() + " endpoints · " + conversations.size() + " conversations · " + findings.size() + " findings";
    }

    public String protocolHierarchy() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : protocolCounts.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            double pct = packetCount == 0 ? 0.0 : (100.0 * e.getValue() / packetCount);
            sb.append(e.getKey()).append(": ").append(e.getValue()).append(String.format(Locale.US, " (%.1f%%)", pct));
        }
        return sb.length() == 0 ? "No packets" : sb.toString();
    }

    public String endpointTable(int limit) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (EndpointStats s : endpoints.values()) {
            if (i++ >= limit) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.endpoint).append(" · packets ").append(s.packets).append(" · bytes ").append(s.bytes)
                    .append(s.lastProtocol.isEmpty() ? "" : " · ").append(s.lastProtocol);
        }
        return sb.length() == 0 ? "No endpoint fields found" : sb.toString();
    }

    public String conversationTable(int limit) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (ConversationStats s : conversations.values()) {
            if (i++ >= limit) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.a).append(" ↔ ").append(s.b).append(" · packets ").append(s.packets).append(" · bytes ").append(s.bytes)
                    .append(s.protocol.isEmpty() ? "" : " · ").append(s.protocol);
        }
        return sb.length() == 0 ? "No src/dst conversations found" : sb.toString();
    }

    public String findingTable(int limit) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (AnalysisFinding f : findings) {
            if (i++ >= limit) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(f.displayLine());
        }
        return sb.length() == 0 ? "No notable findings" : sb.toString();
    }

    public static final class EndpointStats {
        public final String endpoint;
        public int packets;
        public int bytes;
        public String lastProtocol = "";
        EndpointStats(String endpoint) { this.endpoint = endpoint; }
    }

    public static final class ConversationStats {
        public final String a;
        public final String b;
        public final String protocol;
        public int packets;
        public int bytes;
        ConversationStats(String a, String b, String protocol) { this.a = a; this.b = b; this.protocol = protocol == null ? "" : protocol; }
    }
}
