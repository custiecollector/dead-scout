package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.HexInspector;
import org.deadscout.core.HexUtils;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PacketWorkbench;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DesktopPacketReviewPanel {
    private final DeadScoutDesktopGui gui;
    private final JTextField filter = new JTextField("", 22);
    private final JButton applyFilterButton = new JButton("Apply filter");
    private final JButton clearFilterButton = new JButton("Clear filter");
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final List<Integer> indexMap = new ArrayList<>();
    private final JTextArea details = textArea();
    private final JTextArea bytes = textArea();
    private CaptureSession currentSession;

    DesktopPacketReviewPanel(DeadScoutDesktopGui gui) {
        this.gui = gui;
    }

    JPanel build() {
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? SELECTED : CARD);
                c.setForeground(TEXT);
                c.setFont(MONO_FONT);
                if (c instanceof JComponent) ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails(currentSession);
                gui.updateLabText();
            }
        });

        styleField(filter);
        styleButton(applyFilterButton);
        styleButton(clearFilterButton);
        filter.addActionListener(e -> gui.refreshUi());
        applyFilterButton.addActionListener(e -> gui.refreshUi());
        clearFilterButton.addActionListener(e -> {
            filter.setText("");
            gui.refreshUi();
        });

        details.setLineWrap(true);
        bytes.setLineWrap(false);
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll(details), scroll(bytes));
        bottomSplit.setDividerLocation(390);
        bottomSplit.setResizeWeight(0.58);
        bottomSplit.setBorder(BorderFactory.createLineBorder(BORDER));
        bottomSplit.setBackground(PANEL);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(list), bottomSplit);
        split.setDividerLocation(330);
        split.setResizeWeight(0.52);
        split.setBorder(BorderFactory.createLineBorder(BORDER));
        split.setBackground(PANEL);

        JPanel panel = darkPanel(new BorderLayout(8, 8));
        JPanel filterRow = darkPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(smallLabel("Display filter"));
        filterRow.add(filter);
        filterRow.add(applyFilterButton);
        filterRow.add(clearFilterButton);
        filterRow.add(smallLabel(PacketWorkbench.displayFilterExamples()));
        panel.add(filterRow, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    void update(CaptureSession session) {
        currentSession = session;
        int selectedActual = -1;
        int selected = list.getSelectedIndex();
        if (selected >= 0 && selected < indexMap.size()) selectedActual = indexMap.get(selected);
        model.clear();
        indexMap.clear();
        String activeFilter = filter.getText().trim().toLowerCase(Locale.US);
        if (session == null || session.packets.isEmpty()) {
            model.addElement("No decoded packets or metadata records loaded yet");
        } else {
            for (int i = 0; i < session.packets.size(); i++) {
                PacketRecord p = session.packets.get(i);
                if (!matchesPacketFilter(p, activeFilter)) continue;
                indexMap.add(i);
                model.addElement(String.format(Locale.US, "%05d  %-18s  %-14s  %s",
                        i + 1, truncate(p.decode.protocol, 18), truncate(p.sourceId, 14), p.title()));
            }
            if (indexMap.isEmpty()) {
                model.addElement("No packets match current filter");
            } else {
                int displayIndex = indexMap.indexOf(selectedActual);
                list.setSelectedIndex(displayIndex >= 0 ? displayIndex : 0);
            }
        }
        updateDetails(session);
    }

    PacketRecord selectedPacket(CaptureSession session) {
        int index = list.getSelectedIndex();
        if (session == null || session.packets.isEmpty() || index < 0 || index >= indexMap.size()) return null;
        return session.packets.get(indexMap.get(index));
    }

    private void updateDetails(CaptureSession session) {
        PacketRecord p = selectedPacket(session);
        if (p == null) {
            details.setText("No packet selected. Use Start import for PCAP/PCAPNG/rtl_433 JSON/raw hex/session files, or start a source that returns decoded packet data. Raw IQ and source checks stay in observations, not the packet list.");
            bytes.setText("Packet bytes pane\nNo raw packet bytes selected.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Packet details\n");
        sb.append("Title: ").append(p.title()).append("\n");
        sb.append("Source: ").append(p.sourceId).append("\n");
        sb.append("Time: ").append(p.timestampMillis).append("\n");
        if (p.frequencyHz > 0) sb.append(String.format(Locale.US, "Frequency: %.3f MHz\n", p.frequencyHz / 1_000_000.0));
        if (!p.channel.isEmpty()) sb.append("Channel: ").append(p.channel).append("\n");
        if (!p.modulationGuess.isEmpty()) sb.append("Modulation: ").append(p.modulationGuess).append("\n");
        sb.append("Decode: ").append(p.decode.status).append(" · ").append(p.decode.module).append(" · ").append(p.decode.protocol).append("\n");
        sb.append("Summary: ").append(p.decode.summary).append("\n\n");
        sb.append("Decoded fields\n");
        if (p.decode.fields.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (Map.Entry<String, String> field : p.decode.fields.entrySet()) {
                sb.append("- ").append(field.getKey()).append(": ").append(field.getValue()).append("\n");
            }
        }
        if (!p.tags.isEmpty()) sb.append("\nTags: ").append(p.tags).append("\n");
        details.setText(sb.toString());
        details.setCaretPosition(0);

        StringBuilder bb = new StringBuilder();
        bb.append("Packet bytes / ASCII\n");
        if (!p.rawHex.isEmpty()) {
            bb.append("Bytes: ").append(HexUtils.fromHex(p.rawHex).length).append("\n");
            bb.append("Entropy: ").append(String.format(Locale.US, "%.2f", HexInspector.entropyBitsPerByte(p.rawHex))).append(" bits/byte\n\n");
            bb.append(HexInspector.hexdump(p.rawHex, 512));
        } else if (!p.rawBits.isEmpty()) {
            bb.append("Raw bits\n").append(p.rawBits.length() > 512 ? p.rawBits.substring(0, 512) + "…" : p.rawBits);
        } else {
            bb.append("No raw bytes are attached to this decoded/metadata record. That is acceptable for parser output that exposes fields but no payload.");
        }
        bytes.setText(bb.toString());
        bytes.setCaretPosition(0);
    }

    private boolean matchesPacketFilter(PacketRecord p, String activeFilter) {
        if (activeFilter == null || activeFilter.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        sb.append(p.title()).append(' ').append(p.sourceId).append(' ').append(p.channel).append(' ')
                .append(p.modulationGuess).append(' ').append(p.rawHex).append(' ').append(p.rawBits).append(' ')
                .append(p.decode.module).append(' ').append(p.decode.protocol).append(' ').append(p.decode.status).append(' ')
                .append(p.decode.summary).append(' ').append(p.decode.fields).append(' ').append(p.tags);
        return sb.toString().toLowerCase(Locale.US).contains(activeFilter);
    }

    private static String truncate(String text, int width) {
        if (text == null) text = "";
        if (text.length() <= width) return text;
        return text.substring(0, Math.max(0, width - 1)) + "…";
    }
}
