package org.deadscout.core;

import java.util.Locale;

public final class SignalFingerprint {
    public final String id;
    public final String modulationGuess;
    public final double durationMillis;
    public final int shortPulseMicros;
    public final int longPulseMicros;
    public final double baudEstimate;
    public final double repetitionMillis;
    public final double entropyBitsPerByte;
    public final double autocorrelationScore;
    public final String preamauxCandidate;
    public final String classification;

    public SignalFingerprint(String id, String modulationGuess, double durationMillis, int shortPulseMicros, int longPulseMicros,
                             double baudEstimate, double repetitionMillis, double entropyBitsPerByte, double autocorrelationScore,
                             String preamauxCandidate, String classification) {
        this.id = id == null ? "fingerprint" : id;
        this.modulationGuess = modulationGuess == null ? "unknown" : modulationGuess;
        this.durationMillis = durationMillis;
        this.shortPulseMicros = shortPulseMicros;
        this.longPulseMicros = longPulseMicros;
        this.baudEstimate = baudEstimate;
        this.repetitionMillis = repetitionMillis;
        this.entropyBitsPerByte = entropyBitsPerByte;
        this.autocorrelationScore = autocorrelationScore;
        this.preamauxCandidate = preamauxCandidate == null ? "" : preamauxCandidate;
        this.classification = classification == null ? "unknown" : classification;
    }

    public double similarity(SignalFingerprint other) {
        if (other == null) return 0.0;
        double score = 0.0;
        if (classification.equals(other.classification)) score += 0.25;
        if (modulationGuess.equals(other.modulationGuess)) score += 0.20;
        score += closeness(shortPulseMicros, other.shortPulseMicros) * 0.15;
        score += closeness(longPulseMicros, other.longPulseMicros) * 0.15;
        score += closeness(baudEstimate, other.baudEstimate) * 0.15;
        score += Math.max(0.0, 1.0 - Math.abs(entropyBitsPerByte - other.entropyBitsPerByte) / 8.0) * 0.10;
        return Math.max(0.0, Math.min(1.0, score));
    }

    public String summary() {
        return String.format(Locale.US, "%s · %s · %.1f ms · short %dus long %dus · baud≈%.1f · entropy %.2f · autocorr %.2f · preamaux %s",
                classification, modulationGuess, durationMillis, shortPulseMicros, longPulseMicros, baudEstimate, entropyBitsPerByte, autocorrelationScore,
                preamauxCandidate.isEmpty() ? "n/a" : preamauxCandidate);
    }

    private static double closeness(double a, double b) {
        double max = Math.max(Math.max(Math.abs(a), Math.abs(b)), 1.0);
        return Math.max(0.0, 1.0 - Math.abs(a - b) / max);
    }
}
