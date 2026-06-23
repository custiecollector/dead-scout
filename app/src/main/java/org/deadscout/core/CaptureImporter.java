package org.deadscout.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CaptureImporter {
    private static final Pattern NUMBER_FIELD = Pattern.compile("(?i)([a-z0-9_:-]+)\\s*[:=,]\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    private CaptureImporter() {}

    public static CaptureImportResult importCapture(String fileName, byte[] data) {
        return importCapture(fileName, data, null);
    }

    public static CaptureImportResult importCapture(String fileName, byte[] data, String sourceId) {
        if (data == null) data = new byte[0];
        String liveSource = normalizeSourceId(sourceId);
        String name = fileName == null ? "capture" : fileName;
        String lower = name.toLowerCase(Locale.US);
        long now = System.currentTimeMillis();
        CaptureSession session = new CaptureSession("import-" + sanitize(name) + "-" + now, now);
        session.addNote("Imported capture: " + name + " (" + data.length + " bytes)");
        String text = looksText(data) ? new String(data, StandardCharsets.UTF_8) : "";
        CaptureImportResult result;
        try {
            if (text.contains("deadscout-session-v1") || lower.endsWith(".deadscout.json")) {
                CaptureSession loaded = CaptureSession.fromJson(text);
                loaded.addNote("Loaded from DeadScout session bundle: " + name);
                return new CaptureImportResult(name, "DeadScout session", data.length, loaded);
            }
            if (PcapNgReader.looksLike(data) || lower.endsWith(".pcapng")) {
                PcapNgReader reader = PcapNgReader.parse(data);
                for (PacketRecord p : reader.decodeRecords(sourceIdOr(liveSource, "pcapng-import"))) session.addPacket(p);
                session.addNote(reader.summary());
                result = new CaptureImportResult(name, "PCAPNG", data.length, session);
                if (session.packets.isEmpty()) result.warn("PCAPNG parsed but no supported packets were decoded yet.");
                return result;
            }
            if (looksPcap(data) || lower.endsWith(".pcap")) {
                PcapReader reader = PcapReader.parse(data);
                for (PacketRecord p : reader.decodeRecords(sourceIdOr(liveSource, "pcap-import"))) session.addPacket(p);
                session.addNote("PCAP linktype " + reader.linkType + " packets " + reader.packets.size());
                result = new CaptureImportResult(name, "PCAP", data.length, session);
                if (session.packets.isEmpty()) result.warn("PCAP parsed but linktype " + reader.linkType + " has no DeadScout decoder yet.");
                return result;
            }
            if (lower.contains("rtl_433") || lower.contains("rtl-433") || text.contains("\"model\"") && text.contains("\"rssi")) {
                for (PacketRecord p : Rtl433Module.decodeJsonLog(text, sourceIdOr(liveSource, "rtl_433-import"))) session.addPacket(p);
                session.addNote(Rtl433Module.licenseNotice());
                return new CaptureImportResult(name, "rtl_433 JSON", data.length, session);
            }
            if (lower.contains("802154") || lower.contains("15.4") || text.toLowerCase(Locale.US).contains("lqi")) {
                List<String> lines = Arrays.asList(text.split("\\r?\\n"));
                for (PacketRecord p : Ieee802154SnifferReader.parseLines(lines, sourceIdOr(liveSource, "ieee802154-import"), 11)) session.addPacket(p);
                session.addNote(Ieee802154SnifferReader.supportedReaders());
                result = new CaptureImportResult(name, "802.15.4 sniffer log", data.length, session);
                if (session.packets.isEmpty()) result.warn("No 802.15.4 frame hex was found in the text log.");
                return result;
            }
            if (lower.endsWith(".sigmf-meta") || text.contains("core:sample_rate") || text.contains("core:frequency")) {
                importSigMfMeta(text, session, sourceIdOr(liveSource, "sigmf-import"));
                return new CaptureImportResult(name, "SigMF metadata", data.length, session);
            }
            if (looksRawHex(text)) {
                String hex = text.replaceAll("[^0-9A-Fa-f]", "");
                PacketRecord p = rawHexRecord(hex, sourceIdOr(liveSource, "raw-hex-import"), now);
                session.addPacket(p);
                return new CaptureImportResult(name, "raw hex", data.length, session);
            }
            if (lower.endsWith(".iq") || lower.endsWith(".cu8") || lower.endsWith(".sigmf-data") || lower.endsWith(".raw") || data.length >= 64) {
                importIq(data, session, name, sourceIdOr(liveSource, "iq-import"));
                return new CaptureImportResult(name, "raw IQ", data.length, session);
            }
        } catch (RuntimeException ex) {
            result = new CaptureImportResult(name, "import error", data.length, session).warn(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return result;
        }
        result = new CaptureImportResult(name, "unknown", data.length, session).warn("DeadScout could not identify this capture format yet.");
        return result;
    }

    private static void importIq(byte[] data, CaptureSession session, String name, String sourceId) {
        RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(RtlSdrIqPipeline.Config.default915());
        SpectrumSnapshot spectrum = pipeline.ingestUnsignedIq(data);
        for (SignalObservation obs : pipeline.observationsFrom(spectrum, sourceId)) session.addObservation(obs);
        SignalFingerprint fp = SignalFingerprinter.fromIq("iq-" + sanitize(name), data, pipeline.config.sampleRateHz);
        session.addNote("Raw IQ import: " + fp.summary());
        session.addNote(String.format(Locale.US, "IQ noise floor %.1f dB · occupancy %.1f%%", spectrum.noiseFloorDb, spectrum.occupancyPercent));
        session.addNote(pipeline.sigMfMetaJson(data.length / 2, name));
    }

    private static void importSigMfMeta(String text, CaptureSession session, String sourceId) {
        long freq = (long) extractDouble(text, "core:frequency", 0.0);
        int sampleRate = (int) extractDouble(text, "core:sample_rate", 0.0);
        LinkedHashMap<String, String> fields = Rtl433JsonDecoder.parseFlatJson(text);
        fields.put("sample_rate_hz", Integer.toString(sampleRate));
        fields.put("frequency_hz", Long.toString(freq));
        session.addObservation(new SignalObservation(System.currentTimeMillis(), sourceId, freq, sampleRate, sampleRate,
                0.0, 0.0, fields.getOrDefault("core:datatype", "SigMF capture"), "SigMF metadata imported; pair with .sigmf-data/.cu8 for IQ waterfall."));
        session.addNote("SigMF metadata imported for IQ/waterfall context: " + fields.toString());
    }

    private static PacketRecord rawHexRecord(String hex, String sourceId, long timestamp) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("bytes", Integer.toString(hex.length() / 2));
        fields.put("ascii", HexUtils.ascii(HexUtils.fromHex(hex.substring(0, Math.min(hex.length(), 128)))));
        fields.put("entropy_bits_per_byte", String.format(Locale.US, "%.2f", HexInspector.entropyBitsPerByte(hex)));
        return new PacketRecord(timestamp, sourceId, 0L, "raw hex", 0, -1, "unknown bytes", hex, "",
                ProtocolDecode.partial("raw-hex", "raw bytes", "hex dump imported for manual decoder/plugin routing", fields), null);
    }

    private static boolean looksPcap(byte[] data) {
        if (data.length < 4) return false;
        int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF, b2 = data[2] & 0xFF, b3 = data[3] & 0xFF;
        return (b0 == 0xD4 && b1 == 0xC3 && b2 == 0xB2 && b3 == 0xA1) || (b0 == 0xA1 && b1 == 0xB2 && b2 == 0xC3 && b3 == 0xD4);
    }

    private static boolean looksText(byte[] data) {
        int n = Math.min(data.length, 512);
        int printable = 0;
        for (int i = 0; i < n; i++) {
            int c = data[i] & 0xFF;
            if (c == 9 || c == 10 || c == 13 || (c >= 32 && c < 127)) printable++;
        }
        return n == 0 || printable >= n * 0.85;
    }

    private static boolean looksRawHex(String text) {
        if (text == null || text.length() < 8) return false;
        String hex = text.replaceAll("[^0-9A-Fa-f]", "");
        return hex.length() >= 8 && (hex.length() % 2 == 0) && hex.length() >= text.trim().length() * 0.55;
    }

    private static double extractDouble(String text, String key, double fallback) {
        Pattern p = Pattern.compile("(?i)\\\"?" + Pattern.quote(key) + "\\\"?\\s*[:=,]\\s*\\\"?(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(text == null ? "" : text);
        if (m.find()) try { return Double.parseDouble(m.group(1)); } catch (RuntimeException ignored) { }
        Matcher generic = NUMBER_FIELD.matcher(text == null ? "" : text);
        while (generic.find()) if (generic.group(1).toLowerCase(Locale.US).contains(key.toLowerCase(Locale.US))) {
            try { return Double.parseDouble(generic.group(2)); } catch (RuntimeException ignored) { }
        }
        return fallback;
    }

    private static String sourceIdOr(String requested, String fallback) {
        return requested == null || requested.trim().isEmpty() ? fallback : requested.trim();
    }

    private static String normalizeSourceId(String sourceId) {
        return sourceId == null ? "" : sourceId.trim();
    }

    private static String sanitize(String name) { return (name == null ? "capture" : name).replaceAll("[^A-Za-z0-9._-]", "_"); }
}
