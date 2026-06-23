package org.deadscout.core;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReportGenerator {
    private ReportGenerator() {}

    public static String markdownReport(CaptureSession session, FieldSurveyMap survey) {
        PacketWorkbenchReport workbench = PacketWorkbench.analyze(session.packets());
        List<AnalysisFinding> correlations = CrossProtocolCorrelationEngine.correlate(session);
        StringBuilder sb = new StringBuilder();
        sb.append("# DeadScout session report\n\n");
        sb.append("Session: ").append(session.id).append("\n");
        sb.append("Decoded packets: ").append(session.packets.size()).append("\n");
        sb.append("Workbench: ").append(workbench.summary()).append("\n\n");
        sb.append("## Protocol hierarchy\n").append(workbench.protocolHierarchy()).append("\n\n");
        sb.append("## Findings\n").append(workbench.findingTable(20)).append("\n\n");
        sb.append("## Correlations\n");
        if (correlations.isEmpty()) sb.append("No correlations.\n\n");
        else for (AnalysisFinding f : correlations) sb.append("- ").append(f.displayLine().replace('\n', ' ')).append('\n');
        sb.append("\n## Survey\n").append(survey == null ? "No survey map attached" : survey.summary()).append("\n\n");
        sb.append("## Export bundle\nSession JSON, selected packet JSON, PCAP/PCAPNG export, CSV/GeoJSON/KML survey exports, and SHA-256 hashes are generated locally.\n");
        return sb.toString();
    }

    public static String bundleManifestJson(CaptureSession session, FieldSurveyMap survey) {
        String sessionJson = session.toJson();
        String report = markdownReport(session, survey);
        String csv = survey == null ? "" : survey.toCsv();
        String geo = survey == null ? "" : survey.toGeoJson();
        List<byte[]> ieee802154Frames = ieee802154Frames(session);
        byte[] pcap = DeadScoutExport.ieee802154PcapFromPackets(session.packets());
        byte[] pcapng = PcapNgWriter.writeSingleInterface(PcapReader.LINKTYPE_IEEE802_15_4_NOFCS, "ieee802154", ieee802154Frames);
        return "{\"format\":\"deadscout-report-bundle-v1\","
                + "\"session\":\"" + HexUtils.jsonEscape(session.id) + "\","
                + entry("session.json", sessionJson.getBytes().length, sha256(sessionJson.getBytes())) + ','
                + entry("report.md", report.getBytes().length, sha256(report.getBytes())) + ','
                + entry("survey.csv", csv.getBytes().length, sha256(csv.getBytes())) + ','
                + entry("survey.geojson", geo.getBytes().length, sha256(geo.getBytes())) + ','
                + entry("ieee802154.pcap", pcap.length, sha256(pcap)) + ','
                + entry("ieee802154.pcapng", pcapng.length, sha256(pcapng)) + "}";
    }

    private static String entry(String name, int bytes, String sha) {
        return "\"" + HexUtils.jsonEscape(name) + "\":{\"bytes\":" + bytes + ",\"sha256\":\"" + sha + "\"}";
    }

    private static List<byte[]> ieee802154Frames(CaptureSession session) {
        ArrayList<byte[]> out = new ArrayList<>();
        if (session == null) return out;
        for (PacketRecord p : session.packets()) {
            if (p == null || p.rawHex == null || p.rawHex.trim().isEmpty()) continue;
            String protocol = p.decode == null ? "" : p.decode.protocol;
            if (!protocol.contains("802.15.4") && !p.sourceId.contains("ieee802154")) continue;
            try { out.add(HexUtils.fromHex(p.rawHex)); } catch (RuntimeException ignored) { }
        }
        return out;
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexUtils.toHex(digest.digest(bytes)).toLowerCase(Locale.US);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
