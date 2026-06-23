package org.deadscout.core;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Rtl433JsonDecoder {
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    private Rtl433JsonDecoder() {}

    public static PacketRecord decodeLine(String json, String sourceId) {
        LinkedHashMap<String, String> values = parseFlatJson(json);
        String model = first(values, "model", "protocol", "device", "unknown rtl_433 device");
        String id = first(values, "id", "device_id", "sid", "");
        String channel = first(values, "channel", "subtype", "");
        long freq = parseFrequency(values);
        double rssi = parseDouble(first(values, "rssi", "rssi_db", "rssi_dB", "0"), 0);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (String key : values.keySet()) {
            if (!"time".equals(key) && !"model".equals(key) && !"frequency".equals(key)) fields.put(key, values.get(key));
        }
        String summary = model + (id.isEmpty() ? "" : " id " + id);
        ProtocolDecode decode = ProtocolDecode.decoded("rtl_433", "rtl_433/" + model, summary, fields);
        return new PacketRecord(System.currentTimeMillis(), sourceId, freq, channel, rssi, -1,
                "OOK/FSK module reported", "", "", decode, null);
    }

    public static LinkedHashMap<String, String> parseFlatJson(String json) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        Matcher sm = STRING_FIELD.matcher(json);
        while (sm.find()) out.put(sm.group(1), sm.group(2));
        Matcher nm = NUMBER_FIELD.matcher(json);
        while (nm.find()) if (!out.containsKey(nm.group(1))) out.put(nm.group(1), nm.group(2));
        return out;
    }

    private static long parseFrequency(LinkedHashMap<String, String> values) {
        String raw = first(values, "freq", "frequency", "freq_mhz", "");
        if (raw.isEmpty()) raw = first(values, "frequency_MHz", "frequency_hz", "hz", "0");
        double v = parseDouble(raw, 0);
        if (v <= 0) return 0;
        if (v < 10_000) return (long) (v * 1_000_000L);
        return (long) v;
    }

    private static String first(LinkedHashMap<String, String> values, String a, String b, String fallback) {
        String v = values.get(a); if (v != null && !v.isEmpty()) return v;
        v = values.get(b); if (v != null && !v.isEmpty()) return v;
        return fallback;
    }

    private static String first(LinkedHashMap<String, String> values, String a, String b, String c, String fallback) {
        String v = values.get(a); if (v != null && !v.isEmpty()) return v;
        v = values.get(b); if (v != null && !v.isEmpty()) return v;
        v = values.get(c); if (v != null && !v.isEmpty()) return v;
        return fallback;
    }

    private static double parseDouble(String raw, double fallback) {
        try { return Double.parseDouble(raw); } catch (RuntimeException ex) { return fallback; }
    }
}
