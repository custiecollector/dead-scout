package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.SignalObservation;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.util.Locale;

final class DesktopTopologyPanel {
    private final JTextArea topologyDetails = textArea();

    JComponent build() {
        topologyDetails.setLineWrap(false);
        return scroll(topologyDetails);
    }

    void update(CaptureSession session, ActiveSource activeSource) {
        PacketWorkbenchReport report = PacketWorkbench.analyze(session.packets());
        StringBuilder sb = new StringBuilder();
        sb.append("DeadScout topology / conversations\n");
        sb.append("Active source: ").append(activeSource.label()).append("\n");
        sb.append("Packets: ").append(session.packets.size())
                .append(" · Observations: ").append(session.observations.size()).append("\n");
        sb.append("Active monitors: ").append(DesktopSourceLaneModel.activeLaneCount(session, activeSource))
                .append(" (one at a time)\n\n");

        sb.append("Source status\n");
        for (DesktopSourceLaneModel.LaneStatus lane : DesktopSourceLaneModel.statuses(session, activeSource)) {
            sb.append(lane.summaryLine()).append("\n");
        }
        sb.append("\n");

        sb.append("Source guide\n");
        sb.append("- Network: Start network runs Windows pktmon, an existing dumpcap helper, or Linux tcpdump and decodes captured Ethernet/IP packets.\n");
        sb.append("- NetworkA: Start NetworkA runs Windows pktmon or a monitor-mode helper; non-monitor link types are reported clearly instead of faked as 802.11.\n");
        sb.append("- Auxiliary: Start Auxiliary runs btmon/hcidump/custom HCI helpers and decodes BTSnoop/HCI/AUX records.\n");
        sb.append("- SDR/radio: USB/RTL/rtl_tcp run rtl_433, rtl_sdr, or TCP IQ capture and route real bytes into waterfall/packet review.\n");
        sb.append("- Sniffers: Start sniffer runs a configured serial/extcap helper or sniffer PCAP interface and decodes 802.15.4 frames.\n");
        sb.append("- Sessions: DeadScout bundles reopen packets, observations, notes, and tags for review.\n\n");

        sb.append("Protocol hierarchy\n").append(report.protocolHierarchy()).append("\n\n");
        sb.append("Endpoints\n").append(report.endpointTable(30)).append("\n\n");
        sb.append("Conversations\n").append(report.conversationTable(30)).append("\n\n");
        sb.append("Topology edges\n");
        if (report.conversations.isEmpty()) {
            sb.append("No source/destination edges yet. Load decoded network, NetworkA, Auxiliary, or sniffer packets to populate the graph.\n");
        } else {
            for (PacketWorkbenchReport.ConversationStats c : report.conversations.values()) {
                sb.append(String.format(Locale.US, "%s  <->  %s   packets=%d bytes=%d protocol=%s\n",
                        c.a, c.b, c.packets, c.bytes, c.protocol));
            }
        }

        sb.append("\nSource observations\n");
        if (session.observations.isEmpty()) {
            sb.append("No source observations yet. Raw IQ, source checks, sweep hits, and topology hints appear here until decoded packets are available.\n");
        } else {
            int count = 0;
            for (SignalObservation obs : session.observations) {
                if (count++ >= 25) {
                    sb.append("… ").append(session.observations.size() - 25).append(" more observations\n");
                    break;
                }
                sb.append(String.format(Locale.US, "- %s · %.3f MHz · sr=%d · bw=%d · %.1f dBm · %.1f dB · %s · %s\n",
                        obs.sourceId, obs.frequencyHz / 1_000_000.0, obs.sampleRateHz, obs.bandwidthHz,
                        obs.rssiDbm, obs.snrDb, obs.modulation, obs.note));
            }
        }

        topologyDetails.setText(sb.toString());
        topologyDetails.setCaretPosition(0);
    }
}
