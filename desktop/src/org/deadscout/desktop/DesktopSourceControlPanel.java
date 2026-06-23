package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;

final class DesktopSourceControlPanel {
    private final DeadScoutDesktopGui gui;
    private JScrollPane sourceScroll;

    DesktopSourceControlPanel(DeadScoutDesktopGui gui) {
        this.gui = gui;
    }

    JPanel build() {
        JPanel outer = darkPanel(new BorderLayout(8, 8));
        outer.setPreferredSize(new Dimension(380, 720));
        JPanel stack = darkPanel(new BorderLayout(0, 8));
        JPanel controls = darkPanel(new GridLayout(0, 1, 0, 6));
        JButton[] buttons = {
                gui.startImportButton,
                gui.startNetworkButton,
                gui.startNetAButton,
                gui.startAuxiliaryButton,
                gui.startSnifferButton,
                gui.startUsbButton,
                gui.startRtlButton,
                gui.startRtlTcpButton
        };
        for (JButton button : buttons) {
            styleButton(button);
            controls.add(button);
        }
        gui.startImportButton.addActionListener(e -> gui.toggleSource(ActiveSource.IMPORT));
        gui.startNetworkButton.addActionListener(e -> gui.toggleSource(ActiveSource.NETWORK));
        gui.startNetAButton.addActionListener(e -> gui.toggleSource(ActiveSource.NET_A));
        gui.startAuxiliaryButton.addActionListener(e -> gui.toggleSource(ActiveSource.AUXILIARY));
        gui.startSnifferButton.addActionListener(e -> gui.toggleSource(ActiveSource.SNIFFER));
        gui.startUsbButton.addActionListener(e -> gui.toggleSource(ActiveSource.USB));
        gui.startRtlButton.addActionListener(e -> gui.toggleSource(ActiveSource.RTL));
        gui.startRtlTcpButton.addActionListener(e -> gui.toggleSource(ActiveSource.RTL_TCP));
        stack.add(section("Sources", controls), BorderLayout.NORTH);

        JPanel fields = darkPanel(new GridLayout(0, 1, 0, 6));
        fields.add(fieldRow("rtl_tcp host", gui.rtlTcpHost));
        fields.add(fieldRow("port", gui.rtlTcpPort));
        fields.add(fieldRow("center frequency (MHz)", gui.rtlFreqMhz));
        fields.add(fieldRow("sample rate", gui.sampleRate));
        fields.add(fieldRow("gain (tenths dB)", gui.gainTenths));
        stack.add(section("Tuner / network SDR", fields), BorderLayout.CENTER);

        JTextArea notes = textArea();
        notes.setText("Source rules:\n"
                + "• Each source button toggles Start/Stop.\n"
                + "• Starting one source stops any other live source.\n"
                + "• Import is local and reviewable.\n"
                + "• Network, NetworkA, Auxiliary, SDR, sniffer, and PCAP sources share the same packet review.\n"
                + "• Raw IQ and source checks stay in observations until a decoder produces packets.\n"
                + "• No hardware path is marked live until DeadScout receives capture data.");
        notes.setRows(7);
        stack.add(section("Notes", notes), BorderLayout.SOUTH);
        sourceScroll = scroll(stack);
        sourceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sourceScroll.getVerticalScrollBar().setUnitIncrement(24);
        SwingUtilities.invokeLater(this::scrollToTop);
        outer.add(sourceScroll, BorderLayout.CENTER);
        return outer;
    }

    void scrollToTop() {
        if (sourceScroll == null) return;
        sourceScroll.getViewport().setViewPosition(new Point(0, 0));
        sourceScroll.getVerticalScrollBar().setValue(0);
    }
}
