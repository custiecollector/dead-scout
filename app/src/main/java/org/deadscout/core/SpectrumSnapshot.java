package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SpectrumSnapshot {
    public final long centerFrequencyHz;
    public final int sampleRateHz;
    public final double noiseFloorDb;
    public final double occupancyPercent;
    public final double[] frequenciesHz;
    public final double[] powerDb;
    public final double[] peakHoldDb;
    public final ArrayList<SignalMarker> markers;
    public final ArrayList<String> waterfallRows;

    public SpectrumSnapshot(long centerFrequencyHz, int sampleRateHz, double noiseFloorDb, double occupancyPercent,
                            double[] frequenciesHz, double[] powerDb, double[] peakHoldDb,
                            List<SignalMarker> markers, List<String> waterfallRows) {
        this.centerFrequencyHz = centerFrequencyHz;
        this.sampleRateHz = sampleRateHz;
        this.noiseFloorDb = noiseFloorDb;
        this.occupancyPercent = occupancyPercent;
        this.frequenciesHz = frequenciesHz;
        this.powerDb = powerDb;
        this.peakHoldDb = peakHoldDb;
        this.markers = new ArrayList<>(markers);
        this.waterfallRows = new ArrayList<>(waterfallRows);
    }

    public List<SignalMarker> markers() { return Collections.unmodifiableList(markers); }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%.3f MHz center · %.1f dB noise floor · %.1f%% occupied · %d markers",
                centerFrequencyHz / 1_000_000.0, noiseFloorDb, occupancyPercent, markers.size()));
        for (SignalMarker m : markers) sb.append('\n').append(m.display());
        return sb.toString();
    }

    public String waterfallText(int maxRows) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, waterfallRows.size() - maxRows);
        for (int i = start; i < waterfallRows.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(waterfallRows.get(i));
        }
        return sb.length() == 0 ? "no waterfall rows" : sb.toString();
    }

    public static final class SignalMarker {
        public final long frequencyHz;
        public final double powerDb;
        public final double aboveNoiseDb;
        public final String label;

        public SignalMarker(long frequencyHz, double powerDb, double aboveNoiseDb, String label) {
            this.frequencyHz = frequencyHz;
            this.powerDb = powerDb;
            this.aboveNoiseDb = aboveNoiseDb;
            this.label = label == null ? "signal" : label;
        }

        public String display() {
            return String.format(Locale.US, "%s @ %.3f MHz · %.1f dB · +%.1f dB over floor", label, frequencyHz / 1_000_000.0, powerDb, aboveNoiseDb);
        }
    }
}
