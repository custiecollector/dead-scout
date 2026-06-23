package org.deadscout.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SpectrumAnalyzer {
    private static final char[] WATERFALL = " .:-=+*#%@".toCharArray();

    private SpectrumAnalyzer() {}

    public static SpectrumSnapshot analyzeUnsignedIq(byte[] interleavedIq, long centerFrequencyHz, int sampleRateHz, int fftSize) {
        if (interleavedIq == null || interleavedIq.length < 8) throw new IllegalArgumentException("need interleaved unsigned IQ bytes");
        int complexSamples = interleavedIq.length / 2;
        int n = Math.min(Math.max(8, fftSize), complexSamples);
        n = Integer.highestOneBit(n);
        double[] real = new double[n];
        double[] imag = new double[n];
        for (int i = 0; i < n; i++) {
            double window = 0.54 - 0.46 * Math.cos((2.0 * Math.PI * i) / Math.max(1, n - 1));
            real[i] = (((interleavedIq[i * 2] & 0xFF) - 127.5) / 128.0) * window;
            imag[i] = (((interleavedIq[i * 2 + 1] & 0xFF) - 127.5) / 128.0) * window;
        }
        double[] powers = new double[n];
        double[] freqs = new double[n];
        for (int k = 0; k < n; k++) {
            double sumR = 0.0;
            double sumI = 0.0;
            for (int t = 0; t < n; t++) {
                double angle = -2.0 * Math.PI * k * t / n;
                double c = Math.cos(angle);
                double s = Math.sin(angle);
                sumR += real[t] * c - imag[t] * s;
                sumI += real[t] * s + imag[t] * c;
            }
            double mag2 = (sumR * sumR + sumI * sumI) / Math.max(1, n);
            int shifted = (k + n / 2) % n;
            powers[shifted] = 10.0 * Math.log10(mag2 + 1.0e-12);
            freqs[shifted] = centerFrequencyHz + ((shifted - (n / 2.0)) * sampleRateHz / n);
        }
        double[] sorted = powers.clone();
        Arrays.sort(sorted);
        double noise = sorted[Math.max(0, (int) (sorted.length * 0.45))];
        ArrayList<SpectrumSnapshot.SignalMarker> markers = new ArrayList<>();
        int occupied = 0;
        for (int i = 1; i < powers.length - 1; i++) {
            if (powers[i] > noise + 6.0) occupied++;
            if (powers[i] > noise + 12.0 && powers[i] >= powers[i - 1] && powers[i] >= powers[i + 1]) {
                markers.add(new SpectrumSnapshot.SignalMarker((long) freqs[i], powers[i], powers[i] - noise, "peak"));
            }
        }
        double occupancy = 100.0 * occupied / Math.max(1, powers.length);
        ArrayList<String> rows = new ArrayList<>();
        rows.add(waterfallRow(powers, noise));
        return new SpectrumSnapshot(centerFrequencyHz, sampleRateHz, noise, occupancy, freqs, powers, powers.clone(), markers, rows);
    }

    public static SpectrumSnapshot mergePeakHold(SpectrumSnapshot previous, SpectrumSnapshot current) {
        if (previous == null || previous.peakHoldDb.length != current.powerDb.length) return current;
        double[] peak = current.powerDb.clone();
        for (int i = 0; i < peak.length; i++) peak[i] = Math.max(peak[i], previous.peakHoldDb[i]);
        ArrayList<String> rows = new ArrayList<>(previous.waterfallRows);
        rows.addAll(current.waterfallRows);
        while (rows.size() > 32) rows.remove(0);
        return new SpectrumSnapshot(current.centerFrequencyHz, current.sampleRateHz, current.noiseFloorDb, current.occupancyPercent,
                current.frequenciesHz, current.powerDb, peak, current.markers, rows);
    }

    public static String bandScanHeatmap(List<SignalObservation> observations) {
        if (observations == null || observations.isEmpty()) return "no observations yet";
        StringBuilder sb = new StringBuilder();
        for (SignalObservation o : observations) {
            int bars = Math.max(1, Math.min(18, (int) Math.round((o.rssiDbm + 120.0) / 5.0)));
            if (sb.length() > 0) sb.append('\n');
            sb.append(String.format(Locale.US, "%8.3f MHz ", o.frequencyHz / 1_000_000.0));
            for (int i = 0; i < bars; i++) sb.append('█');
            sb.append(String.format(Locale.US, " %.1f dBm · %s", o.rssiDbm, o.modulation));
        }
        return sb.toString();
    }

    private static String waterfallRow(double[] powers, double noise) {
        double max = noise + 30.0;
        StringBuilder sb = new StringBuilder();
        int bins = Math.min(64, powers.length);
        for (int i = 0; i < bins; i++) {
            int idx = (int) Math.floor(i * (powers.length / (double) bins));
            double norm = (powers[idx] - noise) / Math.max(1.0, max - noise);
            int c = Math.max(0, Math.min(WATERFALL.length - 1, (int) Math.round(norm * (WATERFALL.length - 1))));
            sb.append(WATERFALL[c]);
        }
        return sb.toString();
    }
}
