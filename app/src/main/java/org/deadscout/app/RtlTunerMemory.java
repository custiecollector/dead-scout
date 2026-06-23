package org.deadscout.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RtlTunerMemory {
    private static final int MAX_RECENT = 8;
    private static final int MAX_FAVORITES = 12;

    private final ArrayList<Long> recentFrequencies = new ArrayList<>();
    private final ArrayList<Long> favoriteFrequencies = new ArrayList<>();

    void remember(long frequencyHz) {
        recentFrequencies.remove(frequencyHz);
        recentFrequencies.add(0, frequencyHz);
        trim(recentFrequencies, MAX_RECENT);
    }

    void toggleFavorite(long frequencyHz) {
        if (favoriteFrequencies.contains(frequencyHz)) favoriteFrequencies.remove(frequencyHz);
        else favoriteFrequencies.add(0, frequencyHz);
        trim(favoriteFrequencies, MAX_FAVORITES);
    }

    boolean hasRecent() {
        return !recentFrequencies.isEmpty();
    }

    boolean hasFavorites() {
        return !favoriteFrequencies.isEmpty();
    }

    long firstRecent() {
        return recentFrequencies.get(0);
    }

    long firstFavorite() {
        return favoriteFrequencies.get(0);
    }

    String recentList(int max) {
        return frequencyList(recentFrequencies, max);
    }

    String favoriteList(int max) {
        return frequencyList(favoriteFrequencies, max);
    }

    private static void trim(List<Long> frequencies, int max) {
        while (frequencies.size() > max) frequencies.remove(frequencies.size() - 1);
    }

    private static String frequencyList(List<Long> frequencies, int max) {
        if (frequencies == null || frequencies.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, frequencies.size()); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(formatMhz(frequencies.get(i))).append(" MHz");
        }
        return sb.toString();
    }

    private static String formatMhz(long frequencyHz) {
        return String.format(Locale.US, "%.3f", frequencyHz / 1_000_000.0);
    }
}
