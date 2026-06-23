package org.deadscout.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.deadscout.core.SpectrumSnapshot;

import java.util.Locale;

final class SignalView extends View {
    private static final int PANEL = Color.rgb(13, 18, 29);
    private static final int PANEL_2 = Color.rgb(20, 29, 45);
    private static final int STROKE = Color.rgb(42, 61, 82);
    private static final int TEXT = Color.rgb(232, 240, 247);
    private static final int MUTED = Color.rgb(143, 160, 177);
    private static final int ACCENT = Color.rgb(82, 232, 195);
    private static final int WARN = Color.rgb(255, 195, 92);
    private static final String RAMP = " .:-=+*#%@";

    private final SpectrumSnapshot snapshot;
    private final boolean showPeakHold;
    private final boolean highContrast;
    private final int zoomLevel;
    private final int panStep;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    SignalView(Activity context, SpectrumSnapshot snapshot, boolean showPeakHold, boolean highContrast, int zoomLevel, int panStep) {
        super(context);
        this.snapshot = snapshot;
        this.showPeakHold = showPeakHold;
        this.highContrast = highContrast;
        this.zoomLevel = Math.max(0, zoomLevel);
        this.panStep = panStep;
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(20f * density);
        bg.setStroke(Math.max(1, (int) density), STROKE);
        setBackground(bg);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 22f, 22f, paint);
        if (snapshot == null || snapshot.powerDb.length == 0) {
            drawLabel(canvas, "No IQ/spectrum loaded", w / 2f, h / 2f, MUTED, 14, Paint.Align.CENTER);
            return;
        }

        int pad = 12;
        int spectrumTop = pad;
        int spectrumBottom = Math.max(spectrumTop + 40, (int) (h * 0.52f));
        paint.setColor(PANEL_2);
        rect.set(pad, spectrumTop, w - pad, spectrumBottom);
        canvas.drawRoundRect(rect, 14f, 14f, paint);

        double[] series = (showPeakHold && snapshot.peakHoldDb != null && snapshot.peakHoldDb.length == snapshot.powerDb.length) ? snapshot.peakHoldDb : snapshot.powerDb;
        int[] window = visibleWindow(series.length);
        int first = window[0];
        int last = window[1];
        double min = snapshot.noiseFloorDb - (highContrast ? 2.0 : 6.0);
        double max = snapshot.noiseFloorDb + (highContrast ? 22.0 : 38.0);
        int bins = Math.max(1, Math.min(w - 2 * pad, last - first + 1));
        paint.setStrokeWidth(showPeakHold ? 3f : 2f);
        paint.setColor(showPeakHold ? WARN : ACCENT);
        for (int x = 0; x < bins; x++) {
            int idx = first + (int) Math.floor(x * ((last - first + 1) / (double) bins));
            idx = Math.max(0, Math.min(series.length - 1, idx));
            double norm = (series[idx] - min) / Math.max(1.0, max - min);
            norm = Math.max(0.0, Math.min(1.0, norm));
            float sx = pad + x * ((w - 2f * pad) / Math.max(1, bins - 1));
            float sy = (float) (spectrumBottom - norm * (spectrumBottom - spectrumTop - 6));
            canvas.drawLine(sx, spectrumBottom, sx, sy, paint);
        }

        paint.setColor(WARN);
        paint.setStrokeWidth(1f);
        for (SpectrumSnapshot.SignalMarker marker : snapshot.markers) {
            float x = frequencyToX(marker.frequencyHz, w, pad);
            canvas.drawLine(x, spectrumTop, x, spectrumBottom, paint);
        }

        drawLabel(canvas, String.format(Locale.US, "%.3f MHz · %.1f dB floor · %.1f%% occupied", snapshot.centerFrequencyHz / 1_000_000.0, snapshot.noiseFloorDb, snapshot.occupancyPercent), pad + 4, spectrumTop + 16, TEXT, 11, Paint.Align.LEFT);

        int waterfallTop = spectrumBottom + pad;
        int rows = Math.max(1, snapshot.waterfallRows.size());
        int rowH = Math.max(6, (h - waterfallTop - pad) / rows);
        int maxRows = Math.min(rows, 12);
        int start = Math.max(0, rows - maxRows);
        for (int r = start; r < rows; r++) {
            String row = croppedWaterfallRow(snapshot.waterfallRows.get(r));
            int cols = Math.max(1, row.length());
            float cellW = (w - 2f * pad) / cols;
            int drawRow = r - start;
            for (int c = 0; c < cols; c++) {
                int level = Math.max(0, RAMP.indexOf(row.charAt(c)));
                paint.setColor(heatColor(level / (float) (RAMP.length() - 1)));
                rect.set(pad + c * cellW, waterfallTop + drawRow * rowH, pad + (c + 1) * cellW + 1, waterfallTop + (drawRow + 1) * rowH + 1);
                canvas.drawRect(rect, paint);
            }
        }
        drawLabel(canvas, (showPeakHold ? "peak hold" : "live") + " waterfall · zoom " + (1 << zoomLevel) + "x", pad + 4, h - 8, MUTED, 10, Paint.Align.LEFT);
    }

    private float frequencyToX(long frequencyHz, int width, int pad) {
        if (snapshot.frequenciesHz.length < 2) return width / 2f;
        int[] window = visibleWindow(snapshot.frequenciesHz.length);
        double start = snapshot.frequenciesHz[window[0]];
        double end = snapshot.frequenciesHz[window[1]];
        double norm = (frequencyHz - start) / Math.max(1.0, end - start);
        return (float) (pad + Math.max(0.0, Math.min(1.0, norm)) * (width - 2.0 * pad));
    }

    private int[] visibleWindow(int length) {
        if (length <= 1) return new int[]{0, Math.max(0, length - 1)};
        int zoom = Math.max(1, 1 << Math.min(4, zoomLevel));
        int visible = Math.max(8, length / zoom);
        visible = Math.min(length, visible);
        int maxStart = Math.max(0, length - visible);
        int centerStart = maxStart / 2;
        int start = centerStart + panStep * Math.max(1, visible / 3);
        start = Math.max(0, Math.min(maxStart, start));
        return new int[]{start, Math.min(length - 1, start + visible - 1)};
    }

    private String croppedWaterfallRow(String row) {
        if (row == null || row.isEmpty()) return " ";
        int[] window = visibleWindow(row.length());
        int start = Math.max(0, Math.min(row.length() - 1, window[0]));
        int end = Math.max(start + 1, Math.min(row.length(), window[1] + 1));
        return row.substring(start, end);
    }

    private int heatColor(float level) {
        level = Math.max(0f, Math.min(1f, level));
        if (highContrast) {
            int r = (int) (255 * Math.max(0f, level - 0.35f) / 0.65f);
            int g = (int) (255 * level);
            int b = (int) (80 + 120 * (1f - level));
            return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
        }
        int r = (int) (20 + 235 * Math.max(0f, level - 0.45f) / 0.55f);
        int g = (int) (28 + 190 * level);
        int b = (int) (42 + 120 * (1f - Math.abs(level - 0.45f)));
        return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
    }

    private void drawLabel(Canvas canvas, String label, float x, float y, int color, int sp, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(label, x, y, paint);
    }
}
