package org.deadscout.core;

import java.util.Locale;

public final class BurstAnalysis {
    public final String modulation;
    public final int shortMicros;
    public final int longMicros;
    public final String bitString;
    public final double confidence;
    public final String summary;

    public BurstAnalysis(String modulation, int shortMicros, int longMicros, String bitString, double confidence, String summary) {
        this.modulation = modulation;
        this.shortMicros = shortMicros;
        this.longMicros = longMicros;
        this.bitString = bitString;
        this.confidence = confidence;
        this.summary = summary;
    }

    public ProtocolDecode asDecode() {
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("short_us", Integer.toString(shortMicros));
        fields.put("long_us", Integer.toString(longMicros));
        fields.put("bits", bitString);
        fields.put("confidence", String.format(Locale.US, "%.2f", confidence));
        return ProtocolDecode.partial("unknown-burst", "unknown/" + modulation, summary, fields);
    }
}
