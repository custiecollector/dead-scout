package org.deadscout.core;

import java.util.Arrays;
import java.util.Locale;

public final class BurstAnalyzer {
    private BurstAnalyzer() {}

    public static BurstAnalysis analyzeOok(int[] pulseGapMicros) {
        if (pulseGapMicros == null || pulseGapMicros.length < 4) throw new IllegalArgumentException("need at least two pulse/gap pairs");
        int[] copy = pulseGapMicros.clone();
        Arrays.sort(copy);
        int shortUs = copy[0];
        int longUs = copy[copy.length - 1];
        int threshold = Math.max(shortUs + 1, (shortUs + longUs) / 2);
        StringBuilder bits = new StringBuilder();
        for (int i = 1; i < pulseGapMicros.length; i += 2) bits.append(pulseGapMicros[i] >= threshold ? '1' : '0');
        double ratio = longUs / (double) Math.max(1, shortUs);
        double confidence = Math.min(0.99, Math.max(0.25, (ratio - 1.0) / 2.5));
        String summary = String.format(Locale.US, "OOK pulse train: %d symbols, short≈%dus long≈%dus ratio %.2f", bits.length(), shortUs, longUs, ratio);
        return new BurstAnalysis("OOK/ASK", shortUs, longUs, bits.toString(), confidence, summary);
    }

    public static BurstAnalysis analyzeFsk(double[] symbolFrequenciesHz, int symbolRate) {
        if (symbolFrequenciesHz == null || symbolFrequenciesHz.length < 4) throw new IllegalArgumentException("need at least four FSK symbol estimates");
        double[] copy = symbolFrequenciesHz.clone();
        Arrays.sort(copy);
        double low = copy[copy.length / 4];
        double high = copy[(copy.length * 3) / 4];
        double threshold = (low + high) / 2.0;
        StringBuilder bits = new StringBuilder();
        for (double f : symbolFrequenciesHz) bits.append(f >= threshold ? '1' : '0');
        String summary = String.format(Locale.US, "FSK/GFSK estimate: %d sym/s, mark≈%.0fHz space≈%.0fHz", symbolRate, high, low);
        return new BurstAnalysis("FSK/GFSK", (int) Math.round(low), (int) Math.round(high), bits.toString(), 0.70, summary);
    }
}
