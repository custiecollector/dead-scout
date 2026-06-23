package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.SpectrumSnapshot;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

final class DesktopWaterfallPanel {
    private final DeadScoutDesktopGui gui;
    private final JButton zoomInButton = new JButton("Freq +");
    private final JButton zoomOutButton = new JButton("Freq -");
    private final JButton timeInButton = new JButton("Time +");
    private final JButton timeOutButton = new JButton("Time -");
    private final JButton resetButton = new JButton("Reset view");
    private final JLabel statusLabel = smallLabel("No IQ waterfall yet");
    private final WaterfallCanvas canvas = new WaterfallCanvas();
    private final JScrollPane scrollPane = scroll(canvas);
    private final JTextArea details = textArea();

    DesktopWaterfallPanel(DeadScoutDesktopGui gui) {
        this.gui = gui;
    }

    JPanel build() {
        JPanel panel = darkPanel(new BorderLayout(8, 8));
        JPanel toolbar = darkPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(smallLabel("Waterfall view"));
        JButton[] buttons = { zoomInButton, zoomOutButton, timeInButton, timeOutButton, resetButton };
        for (JButton button : buttons) {
            styleButton(button);
            toolbar.add(button);
        }
        toolbar.add(smallLabel("Ctrl+wheel = frequency zoom · Shift+wheel = time zoom · scrollbars pan like a WebSDR waterfall"));
        panel.add(toolbar, BorderLayout.NORTH);

        scrollPane.getHorizontalScrollBar().setUnitIncrement(36);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = darkPanel(new BorderLayout(6, 4));
        statusLabel.setForeground(MUTED);
        footer.add(statusLabel, BorderLayout.NORTH);
        details.setRows(4);
        details.setLineWrap(false);
        footer.add(scroll(details), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        zoomInButton.addActionListener(e -> zoomFrequency(1.6));
        zoomOutButton.addActionListener(e -> zoomFrequency(1.0 / 1.6));
        timeInButton.addActionListener(e -> zoomTime(1.4));
        timeOutButton.addActionListener(e -> zoomTime(1.0 / 1.4));
        resetButton.addActionListener(e -> resetView());
        return panel;
    }

    void update(SpectrumSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        canvas.setSnapshot(snapshot);
        if (snapshot == null) {
            sb.append("No IQ waterfall in this desktop session yet. Start import with .cu8/.iq/.raw data, or connect external rtl_tcp to append real IQ bytes.\n");
            sb.append("The Waterfall tab is a scrollable/zoomable viewport: Ctrl+mouse-wheel or Freq +/- zooms the frequency axis; Shift+mouse-wheel or Time +/- zooms time/history.\n");
            sb.append("USB radio hardware is marked live only after DeadScout receives IQ bytes.\n");
        } else {
            sb.append(snapshot.summary()).append("\n\n");
            sb.append("Scrollable/zoomable waterfall controls\n");
            sb.append("- Frequency: Freq +/-, Ctrl+mouse-wheel, horizontal scrollbar pan\n");
            sb.append("- Time/history: Time +/-, Shift+mouse-wheel, vertical scrollbar review\n\n");
            sb.append(snapshot.waterfallText(12)).append("\n");
        }
        statusLabel.setText(canvas.statusText());
        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    private void zoomFrequency(double factor) {
        canvas.zoomFrequency(factor);
        statusLabel.setText(canvas.statusText());
        gui.setStatus("Waterfall frequency zoom: " + canvas.zoomLabel() + "; use the horizontal scrollbar to pan.");
    }

    private void zoomTime(double factor) {
        canvas.zoomTime(factor);
        statusLabel.setText(canvas.statusText());
        gui.setStatus("Waterfall time zoom: " + canvas.zoomLabel() + "; use the vertical scrollbar to review history.");
    }

    private void resetView() {
        canvas.resetView();
        statusLabel.setText(canvas.statusText());
        gui.setStatus("Waterfall zoom reset.");
    }
}
