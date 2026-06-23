package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.CrossProtocolCorrelationEngine;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.ReportGenerator;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

final class DesktopReportPanel {
    private final DeadScoutDesktopGui gui;
    private final JButton exportReportButton = new JButton("Export report");
    private final JTextArea reportDetails = textArea();

    DesktopReportPanel(DeadScoutDesktopGui gui) {
        this.gui = gui;
    }

    JPanel build() {
        JPanel reportPanel = darkPanel(new BorderLayout(8, 8));
        JPanel reportActions = darkPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        styleButton(exportReportButton);
        exportReportButton.addActionListener(e -> gui.exportReport());
        reportActions.add(exportReportButton);
        reportActions.add(smallLabel("exports the current session report as Markdown"));
        reportPanel.add(reportActions, BorderLayout.NORTH);
        reportPanel.add(scroll(reportDetails), BorderLayout.CENTER);
        return reportPanel;
    }

    void update(CaptureSession session) {
        if (session.packets.isEmpty()) {
            reportDetails.setText("No report yet. Load decoded packets with Start import or a live source that returns capture data. Raw IQ and source checks remain in observations.");
            return;
        }
        PacketWorkbenchReport workbench = PacketWorkbench.analyze(session.packets());
        StringBuilder sb = new StringBuilder();
        sb.append("Summary: ").append(workbench.summary()).append("\n\n");
        sb.append("Protocol hierarchy\n").append(workbench.protocolHierarchy()).append("\n\n");
        sb.append("Endpoints\n").append(workbench.endpointTable(20)).append("\n\n");
        sb.append("Conversations\n").append(workbench.conversationTable(20)).append("\n\n");
        sb.append("Findings\n").append(workbench.findingTable(20)).append("\n\n");
        sb.append("Correlations\n");
        List<?> correlations = CrossProtocolCorrelationEngine.correlate(session);
        if (correlations.isEmpty()) sb.append("No correlations.\n\n");
        else for (Object finding : correlations) sb.append("- ").append(finding).append('\n');
        sb.append("\nMarkdown export preview\n").append(ReportGenerator.markdownReport(session, null));
        reportDetails.setText(sb.toString());
        reportDetails.setCaretPosition(0);
    }
}
