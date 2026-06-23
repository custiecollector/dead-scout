package org.deadscout.core;

import java.util.Locale;

public final class SignalFingerprinter {
    private SignalFingerprinter() {}

    public static SignalFingerprint fromBurst(String id, BurstAnalysis burst, double repetitionMillis) {
        if (burst == null) throw new IllegalArgumentException("burst required");
        double baud = burst.shortMicros > 0 ? 1_000_000.0 / burst.shortMicros : 0.0;
        String cls = classify(burst.modulation, burst.shortMicros, burst.longMicros, baud, 0.0);
        String preamaux = longestRunPrefix(burst.bitString);
        double duration = burst.bitString.length() * Math.max(1, burst.shortMicros) / 1000.0;
        double entropy = bitEntropy(burst.bitString);
        double autocorr = autocorrelation(burst.bitString);
        return new SignalFingerprint(id, burst.modulation, duration, burst.shortMicros, burst.longMicros, baud, repetitionMillis, entropy, autocorr, preamaux, cls);
    }

    public static SignalFingerprint fromIq(String id, byte[] unsignedIq, int sampleRateHz) {
        SpectrumSnapshot s = SpectrumAnalyzer.analyzeUnsignedIq(unsignedIq, 0L, sampleRateHz, Math.min(128, Math.max(8, unsignedIq.length / 2)));
        double entropy = HexInspector.entropyBitsPerByte(HexUtils.toHex(unsignedIq));
        String cls = s.occupancyPercent > 45.0 ? "wideband/OFDM-like" : (s.markers.size() > 4 ? "FSK/GFSK-like" : "narrowband/OOK-ASK-like");
        double duration = (unsignedIq.length / 2.0) * 1000.0 / Math.max(1, sampleRateHz);
        return new SignalFingerprint(id, cls, duration, 0, 0, 0.0, 0.0, entropy, s.occupancyPercent / 100.0,
                s.markers.isEmpty() ? "" : s.markers.get(0).display(), cls);
    }

    public static String compare(SignalFingerprint a, SignalFingerprint b) {
        return String.format(Locale.US, "similarity %.2f · A %s · B %s", a.similarity(b), a.summary(), b.summary());
    }

    private static String classify(String modulation, int shortUs, int longUs, double baud, double entropy) {
        String m = modulation == null ? "" : modulation.toUpperCase(Locale.US);
        if (m.contains("OOK") || m.contains("ASK")) return longUs > shortUs * 2 ? "OOK/ASK pulse-distance remote/sensor" : "OOK/ASK fixed-pulse telemetry";
        if (m.contains("FSK")) return baud > 20000 ? "fast FSK/GFSK telemetry" : "FSK/GFSK narrowband telemetry";
        if (entropy > 6.5) return "encrypted/compressed packet-like";
        return "unknown narrowband burst";
    }

    private static String longestRunPrefix(String bits) {
        if (bits == null || bits.isEmpty()) return "";
        int len = Math.min(bits.length(), 16);
        return bits.substring(0, len);
    }

    private static double bitEntropy(String bits) {
        if (bits == null || bits.isEmpty()) return 0.0;
        int ones = 0;
        for (int i = 0; i < bits.length(); i++) if (bits.charAt(i) == '1') ones++;
        double p = ones / (double) bits.length();
        if (p <= 0.0 || p >= 1.0) return 0.0;
        return -(p * log2(p) + (1 - p) * log2(1 - p));
    }

    private static double autocorrelation(String bits) {
        if (bits == null || bits.length() < 4) return 0.0;
        int best = 0;
        for (int lag = 1; lag <= bits.length() / 2; lag++) {
            int match = 0;
            for (int i = 0; i + lag < bits.length(); i++) if (bits.charAt(i) == bits.charAt(i + lag)) match++;
            best = Math.max(best, match);
        }
        return best / (double) bits.length();
    }

    private static double log2(double v) { return Math.log(v) / Math.log(2.0); }
}
