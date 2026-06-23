package org.deadscout.app;

import java.util.Locale;

final class SweepHit {
    final long frequencyHz;
    final long timestampMillis;
    final double occupancyPercent;
    final int bandwidthHz;
    final double noiseFloorDb;
    final String snapshotSummary;
    final String waterfallPreview;

    SweepHit(long frequencyHz, long timestampMillis, double occupancyPercent, int bandwidthHz, double noiseFloorDb, String snapshotSummary, String waterfallPreview) {
        this.frequencyHz = frequencyHz;
        this.timestampMillis = timestampMillis;
        this.occupancyPercent = occupancyPercent;
        this.bandwidthHz = bandwidthHz;
        this.noiseFloorDb = noiseFloorDb;
        this.snapshotSummary = snapshotSummary == null ? "" : snapshotSummary;
        this.waterfallPreview = waterfallPreview == null ? "" : waterfallPreview;
    }

    String card() {
        return String.format(Locale.US, "Frequency: %.3f MHz\nTime: %d\nOccupancy: %.1f%%\nBandwidth/sample-rate: %d Hz\nFloor: %.1f dB\nSnapshot: %s\n%s",
                frequencyHz / 1_000_000.0, timestampMillis, occupancyPercent, bandwidthHz, noiseFloorDb, snapshotSummary, waterfallPreview);
    }
}
