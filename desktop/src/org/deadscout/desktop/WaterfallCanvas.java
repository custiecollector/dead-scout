package org.deadscout.desktop;

import org.deadscout.core.SpectrumSnapshot;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.deadscout.desktop.DesktopTheme.ACCENT;
import static org.deadscout.desktop.DesktopTheme.BORDER;
import static org.deadscout.desktop.DesktopTheme.CARD;
import static org.deadscout.desktop.DesktopTheme.MUTED;
import static org.deadscout.desktop.DesktopTheme.TEXT;
import static org.deadscout.desktop.DesktopTheme.WARN;

final class WaterfallCanvas extends JComponent {
    private static final int LEFT_GUTTER = 82;
    private static final int RIGHT_GUTTER = 36;
    private static final int TOP_GUTTER = 76;
    private static final int BOTTOM_GUTTER = 58;

    private SpectrumSnapshot snapshot;
    private double frequencyZoom = 1.0;
    private double timeZoom = 1.0;

    WaterfallCanvas() {
        setOpaque(true);
        setBackground(CARD);
        setForeground(TEXT);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        addMouseWheelListener(e -> {
            if (!e.isControlDown() && !e.isShiftDown()) return;
            double factor = e.getPreciseWheelRotation() < 0 ? 1.25 : 1.0 / 1.25;
            if (e.isControlDown()) zoomFrequency(factor);
            else zoomTime(factor);
            e.consume();
        });
    }

    void setSnapshot(SpectrumSnapshot snapshot) {
        this.snapshot = snapshot;
        revalidate();
        repaint();
    }

    void zoomFrequency(double factor) {
        frequencyZoom = clamp(frequencyZoom * factor, 0.55, 12.0);
        revalidate();
        repaint();
    }

    void zoomTime(double factor) {
        timeZoom = clamp(timeZoom * factor, 0.65, 6.0);
        revalidate();
        repaint();
    }

    void resetView() {
        frequencyZoom = 1.0;
        timeZoom = 1.0;
        revalidate();
        repaint();
    }

    String zoomLabel() {
        return String.format(Locale.US, "freq %.1fx · time %.1fx", frequencyZoom, timeZoom);
    }

    String statusText() {
        if (snapshot == null) {
            return "No IQ waterfall yet · import IQ or connect rtl_tcp";
        }
        return String.format(Locale.US, "%d rows · %.3f MHz center · %.1f%% occupied · %s",
                snapshot.waterfallRows.size(), snapshot.centerFrequencyHz / 1_000_000.0,
                snapshot.occupancyPercent, zoomLabel());
    }

    @Override public Dimension getPreferredSize() {
        int bins = binCount();
        int rows = rowCount();
        int width = LEFT_GUTTER + RIGHT_GUTTER + bins * binWidth();
        int height = TOP_GUTTER + BOTTOM_GUTTER + rows * rowHeight();
        return new Dimension(Math.max(760, width), Math.max(430, height));
    }

    @Override protected void paintComponent(Graphics raw) {
        Graphics2D g = (Graphics2D) raw.create();
        try {
            int width = Math.max(getWidth(), getPreferredSize().width);
            int height = Math.max(getHeight(), getPreferredSize().height);
            g.setColor(CARD);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString("Scrollable waterfall", 16, 28);
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("Utwente/WebSDR-style view: zoom frequency, zoom time/history, then pan with scrollbars", 16, 48);
            if (snapshot == null) {
                g.setColor(WARN);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                g.drawString("No IQ bytes loaded yet. Start import with .cu8/.iq/.raw, or use rtl_tcp.", 16, 90);
                g.setColor(MUTED);
                g.drawString("USB radio hardware is marked live only after DeadScout receives IQ bytes.", 16, 116);
                return;
            }

            int bins = binCount();
            int rows = rowCount();
            int cellW = binWidth();
            int cellH = rowHeight();
            int plotW = bins * cellW;
            int plotH = rows * cellH;
            int plotX = LEFT_GUTTER;
            int plotY = TOP_GUTTER;

            drawWaterfallAxis(g, plotX, plotY, plotW, plotH, bins, rows);
            List<String> waterfallRows = snapshot.waterfallRows.isEmpty()
                    ? Collections.singletonList("") : snapshot.waterfallRows;
            for (int r = 0; r < rows; r++) {
                String row = waterfallRows.get(Math.min(r, waterfallRows.size() - 1));
                for (int b = 0; b < bins; b++) {
                    char ch = b < row.length() ? row.charAt(b) : ' ';
                    g.setColor(waterfallColor(ch));
                    g.fillRect(plotX + b * cellW, plotY + r * cellH, cellW + 1, cellH + 1);
                }
            }

            g.setColor(new Color(36, 58, 70, 160));
            for (int b = 0; b <= bins; b += Math.max(1, bins / 8)) {
                int gx = plotX + b * cellW;
                g.drawLine(gx, plotY, gx, plotY + plotH);
            }
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(plotX, plotY, plotW, plotH);
            g.setStroke(new BasicStroke(1f));

            drawWaterfallMarkers(g, plotX, plotY, plotW, plotH);
            drawWaterfallLabels(g, plotX, plotY, plotW, plotH, rows);
        } finally {
            g.dispose();
        }
    }

    private void drawWaterfallAxis(Graphics2D g, int plotX, int plotY, int plotW, int plotH, int bins, int rows) {
        g.setColor(new Color(5, 11, 16));
        g.fillRect(plotX, plotY, plotW, plotH);
        g.setColor(MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(String.format(Locale.US, "%.3f MHz", minFrequencyHz() / 1_000_000.0), plotX, plotY - 12);
        String center = String.format(Locale.US, "%.3f MHz", snapshot.centerFrequencyHz / 1_000_000.0);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(center, plotX + (plotW - fm.stringWidth(center)) / 2, plotY - 12);
        String max = String.format(Locale.US, "%.3f MHz", maxFrequencyHz() / 1_000_000.0);
        g.drawString(max, plotX + plotW - fm.stringWidth(max), plotY - 12);
        g.drawString("oldest", 18, plotY + 14);
        g.drawString("newest", 18, plotY + Math.max(28, plotH - 6));
    }

    private void drawWaterfallMarkers(Graphics2D g, int plotX, int plotY, int plotW, int plotH) {
        if (snapshot.markers.isEmpty()) return;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (SpectrumSnapshot.SignalMarker marker : snapshot.markers) {
            int mx = plotX + (int) Math.round(((marker.frequencyHz - minFrequencyHz()) / (double) Math.max(1, snapshot.sampleRateHz)) * plotW);
            if (mx < plotX || mx > plotX + plotW) continue;
            g.setColor(ACCENT);
            g.drawLine(mx, plotY, mx, plotY + plotH);
            g.setColor(WARN);
            g.drawString(String.format(Locale.US, "%s %.3f", marker.label, marker.frequencyHz / 1_000_000.0), mx + 6, plotY + 18);
        }
    }

    private void drawWaterfallLabels(Graphics2D g, int plotX, int plotY, int plotW, int plotH, int rows) {
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(snapshot.summary().split("\n", 2)[0], plotX, plotY + plotH + 26);
        g.setColor(MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Rows: " + rows + " · " + zoomLabel() + " · horizontal scrollbar pans RF, vertical scrollbar pans time/history", plotX, plotY + plotH + 46);
    }

    private int binCount() {
        if (snapshot == null || snapshot.waterfallRows.isEmpty()) return 64;
        int max = 64;
        for (String row : snapshot.waterfallRows) max = Math.max(max, row.length());
        return max;
    }

    private int rowCount() {
        if (snapshot == null || snapshot.waterfallRows.isEmpty()) return 24;
        return Math.max(12, snapshot.waterfallRows.size());
    }

    private int binWidth() {
        return Math.max(5, Math.min(80, (int) Math.round(10.0 * frequencyZoom)));
    }

    private int rowHeight() {
        return Math.max(8, Math.min(48, (int) Math.round(14.0 * timeZoom)));
    }

    private long minFrequencyHz() {
        return snapshot.centerFrequencyHz - snapshot.sampleRateHz / 2L;
    }

    private long maxFrequencyHz() {
        return snapshot.centerFrequencyHz + snapshot.sampleRateHz / 2L;
    }

    private static Color waterfallColor(char ch) {
        switch (ch) {
            case '@': return new Color(255, 236, 120);
            case '%': return new Color(255, 178, 78);
            case '#': return new Color(239, 93, 93);
            case '*': return new Color(214, 81, 134);
            case '+': return new Color(164, 88, 197);
            case '=': return new Color(88, 128, 218);
            case '-': return new Color(61, 158, 191);
            case ':': return new Color(56, 177, 147);
            case '.': return new Color(41, 87, 94);
            default: return new Color(9, 20, 27);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
