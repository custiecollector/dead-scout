package org.deadscout.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PacketRecord {
    public final long timestampMillis;
    public final String sourceId;
    public final long frequencyHz;
    public final String channel;
    public final double rssiDbm;
    public final int lqi;
    public final String modulationGuess;
    public final String rawHex;
    public final String rawBits;
    public final ProtocolDecode decode;
    public final LinkedHashMap<String, String> tags;

    public PacketRecord(long timestampMillis, String sourceId, long frequencyHz, String channel, double rssiDbm, int lqi,
                        String modulationGuess, String rawHex, String rawBits, ProtocolDecode decode, Map<String, String> tags) {
        this.timestampMillis = timestampMillis;
        this.sourceId = sourceId;
        this.frequencyHz = frequencyHz;
        this.channel = channel == null ? "" : channel;
        this.rssiDbm = rssiDbm;
        this.lqi = lqi;
        this.modulationGuess = modulationGuess == null ? "" : modulationGuess;
        this.rawHex = rawHex == null ? "" : rawHex;
        this.rawBits = rawBits == null ? "" : rawBits;
        this.decode = decode == null ? ProtocolDecode.unknown("none", "not decoded") : decode;
        this.tags = new LinkedHashMap<>();
        if (tags != null) this.tags.putAll(tags);
    }

    public String title() {
        String freq = frequencyHz > 0 ? String.format(Locale.US, "%.3f MHz", frequencyHz / 1_000_000.0) : "metadata";
        return decode.protocol + " · " + freq;
    }

    public String displayBody() {
        StringBuilder sb = new StringBuilder();
        sb.append(decode.status).append(" · ").append(decode.module).append(" · ").append(decode.summary);
        if (!channel.isEmpty()) sb.append("\nChannel: ").append(channel);
        if (rssiDbm != 0) sb.append(String.format(Locale.US, "\nRSSI: %.1f dBm", rssiDbm));
        if (lqi >= 0) sb.append(" · LQI: ").append(lqi);
        if (!modulationGuess.isEmpty()) sb.append("\nModulation: ").append(modulationGuess);
        if (!rawBits.isEmpty()) sb.append("\nBits: ").append(rawBits.length() > 96 ? rawBits.substring(0, 96) + "…" : rawBits);
        if (!rawHex.isEmpty()) sb.append("\nHex: ").append(rawHex.length() > 96 ? rawHex.substring(0, 96) + "…" : rawHex);
        for (Map.Entry<String, String> e : decode.fields.entrySet()) sb.append("\n").append(e.getKey()).append(": ").append(e.getValue());
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendJson(sb, "sourceId", sourceId).append(',');
        sb.append("\"timestampMillis\":").append(timestampMillis).append(',');
        sb.append("\"frequencyHz\":").append(frequencyHz).append(',');
        appendJson(sb, "channel", channel).append(',');
        sb.append("\"rssiDbm\":").append(String.format(Locale.US, "%.1f", rssiDbm)).append(',');
        sb.append("\"lqi\":").append(lqi).append(',');
        appendJson(sb, "modulationGuess", modulationGuess).append(',');
        appendJson(sb, "rawHex", rawHex).append(',');
        appendJson(sb, "rawBits", rawBits).append(',');
        appendJson(sb, "module", decode.module).append(',');
        appendJson(sb, "protocol", decode.protocol).append(',');
        appendJson(sb, "status", decode.status.name()).append(',');
        appendJson(sb, "summary", decode.summary).append(',');
        sb.append("\"fields\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : decode.fields.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            appendJson(sb, e.getKey(), e.getValue());
        }
        sb.append("}}");
        return sb.toString();
    }

    private static StringBuilder appendJson(StringBuilder sb, String key, String value) {
        sb.append('"').append(HexUtils.jsonEscape(key)).append("\":\"").append(HexUtils.jsonEscape(value)).append('"');
        return sb;
    }
}
