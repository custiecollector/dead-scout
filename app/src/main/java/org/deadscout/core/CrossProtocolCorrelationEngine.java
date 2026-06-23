package org.deadscout.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class CrossProtocolCorrelationEngine {
    private CrossProtocolCorrelationEngine() {}

    public static List<AnalysisFinding> correlate(CaptureSession session) {
        ArrayList<AnalysisFinding> findings = new ArrayList<>();
        if (session == null) return findings;
        HashMap<String, Integer> identifiers = new HashMap<>();
        for (int i = 0; i < session.packets.size(); i++) {
            PacketRecord p = session.packets.get(i);
            for (String key : new String[]{"src", "dst", "source", "dest", "addr2_transmitter", "addr1_receiver", "addr3_bssid", "device_address", "address", "bssid", "id", "cell_id", "nci", "pci", "dest_pan"}) {
                String v = p.decode.fields.get(key);
                if (v == null || v.trim().isEmpty()) continue;
                String normalized = key + "=" + v.trim().toUpperCase(Locale.US);
                Integer first = identifiers.get(normalized);
                if (first == null) identifiers.put(normalized, i);
                else if (first != i) findings.add(new AnalysisFinding(AnalysisFinding.Severity.NOTICE, "correlation", "Identifier repeated across packets",
                        normalized + " first seen at #" + first + ", seen again in " + p.decode.protocol, i));
            }
            correlateByTimeFrequency(session, i, p, findings);
        }
        correlateObservations(session, findings);
        return findings;
    }

    private static void correlateByTimeFrequency(CaptureSession session, int index, PacketRecord p, ArrayList<AnalysisFinding> findings) {
        if (p.frequencyHz <= 0) return;
        for (int j = Math.max(0, index - 6); j < index; j++) {
            PacketRecord other = session.packets.get(j);
            long dt = Math.abs(p.timestampMillis - other.timestampMillis);
            long df = Math.abs(p.frequencyHz - other.frequencyHz);
            if (dt <= 10_000 && df <= 2_000_000 && !p.decode.protocol.equals(other.decode.protocol)) {
                findings.add(new AnalysisFinding(AnalysisFinding.Severity.INFO, "correlation", "Possiaux related activity",
                        String.format(Locale.US, "#%d %s and #%d %s are %.1fs apart and %.3f MHz apart", j, other.decode.protocol, index, p.decode.protocol, dt / 1000.0, df / 1_000_000.0), index));
            }
        }
    }

    private static void correlateObservations(CaptureSession session, ArrayList<AnalysisFinding> findings) {
        for (SignalObservation obs : session.observations) {
            for (int i = 0; i < session.packets.size(); i++) {
                PacketRecord p = session.packets.get(i);
                if (obs.frequencyHz > 0 && p.frequencyHz > 0 && Math.abs(obs.frequencyHz - p.frequencyHz) <= Math.max(250_000, obs.bandwidthHz)) {
                    findings.add(new AnalysisFinding(AnalysisFinding.Severity.INFO, "correlation", "Packet falls inside observed RF energy",
                            obs.sourceId + " " + obs.brief() + " overlaps " + p.decode.protocol, i));
                    break;
                }
            }
        }
    }

    public static String table(CaptureSession session, int limit) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (AnalysisFinding f : correlate(session)) {
            if (i++ >= limit) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(f.displayLine());
        }
        return sb.length() == 0 ? "No cross-protocol correlations yet" : sb.toString();
    }
}
