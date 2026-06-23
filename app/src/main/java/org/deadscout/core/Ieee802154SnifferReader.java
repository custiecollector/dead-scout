package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ieee802154SnifferReader {
    private static final Pattern CHANNEL = Pattern.compile("(?i)(?:ch|channel)\\s*[:= ]\\s*(\\d{1,2})");
    private static final Pattern RSSI = Pattern.compile("(?i)rssi\\s*[:= ]\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LQI = Pattern.compile("(?i)lqi\\s*[:= ]\\s*(\\d{1,3})");
    private static final Pattern HEX_BYTES = Pattern.compile("(?i)([0-9a-f]{2}(?:[ :,-]*[0-9a-f]{2}){4,})");

    private Ieee802154SnifferReader() {}

    public static List<PacketRecord> parseLines(List<String> lines, String sourceId, int defaultChannel) {
        ArrayList<PacketRecord> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            PacketRecord record = parseLine(line, sourceId, defaultChannel);
            if (record != null) out.add(record);
        }
        return out;
    }

    public static PacketRecord parseLine(String line, String sourceId, int defaultChannel) {
        if (line == null) return null;
        Matcher hm = HEX_BYTES.matcher(line);
        if (!hm.find()) return null;
        byte[] raw = HexUtils.fromHex(hm.group(1));
        int channel = parseInt(CHANNEL.matcher(line), defaultChannel);
        int lqi = parseInt(LQI.matcher(line), -1);
        double rssi = parseDouble(RSSI.matcher(line), 0.0);
        return Ieee802154FrameDecoder.decodeRecord(raw, sourceId == null ? "ieee802154-sniffer" : sourceId, channel, lqi, rssi);
    }

    public static String supportedReaders() {
        return "nRF52840 extcap/serial lines, TI CC2531 hex dumps, ESP32-C6/Sonoff/Silabs serial firmware lines, and PCAP/PCAPNG linktype 230/195 imports.";
    }

    private static int parseInt(Matcher matcher, int fallback) {
        if (!matcher.find()) return fallback;
        try { return Integer.parseInt(matcher.group(1)); } catch (RuntimeException ex) { return fallback; }
    }

    private static double parseDouble(Matcher matcher, double fallback) {
        if (!matcher.find()) return fallback;
        try { return Double.parseDouble(matcher.group(1)); } catch (RuntimeException ex) { return fallback; }
    }
}
