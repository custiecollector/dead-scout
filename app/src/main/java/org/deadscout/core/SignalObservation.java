package org.deadscout.core;

import java.util.Locale;

public final class SignalObservation {
    public final long timestampMillis;
    public final String sourceId;
    public final long frequencyHz;
    public final int sampleRateHz;
    public final int bandwidthHz;
    public final double rssiDbm;
    public final double snrDb;
    public final String modulation;
    public final String note;

    public SignalObservation(long timestampMillis, String sourceId, long frequencyHz, int sampleRateHz,
                             int bandwidthHz, double rssiDbm, double snrDb, String modulation, String note) {
        this.timestampMillis = timestampMillis;
        this.sourceId = sourceId;
        this.frequencyHz = frequencyHz;
        this.sampleRateHz = sampleRateHz;
        this.bandwidthHz = bandwidthHz;
        this.rssiDbm = rssiDbm;
        this.snrDb = snrDb;
        this.modulation = modulation;
        this.note = note;
    }

    public String brief() {
        return String.format(Locale.US, "%.3f MHz · %s · RSSI %.1f dBm · SNR %.1f dB", frequencyHz / 1_000_000.0, modulation, rssiDbm, snrDb);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendJson(sb, "sourceId", sourceId).append(',');
        sb.append("\"timestampMillis\":").append(timestampMillis).append(',');
        sb.append("\"frequencyHz\":").append(frequencyHz).append(',');
        sb.append("\"sampleRateHz\":").append(sampleRateHz).append(',');
        sb.append("\"bandwidthHz\":").append(bandwidthHz).append(',');
        sb.append("\"rssiDbm\":").append(String.format(Locale.US, "%.1f", rssiDbm)).append(',');
        sb.append("\"snrDb\":").append(String.format(Locale.US, "%.1f", snrDb)).append(',');
        appendJson(sb, "modulation", modulation).append(',');
        appendJson(sb, "note", note);
        sb.append("}");
        return sb.toString();
    }

    private static StringBuilder appendJson(StringBuilder sb, String key, String value) {
        sb.append('\"').append(HexUtils.jsonEscape(key)).append("\":\"").append(HexUtils.jsonEscape(value)).append('\"');
        return sb;
    }
}
