package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;

import javax.swing.JComponent;
import javax.swing.JTextArea;

final class DesktopSourceHelpPanel {
    private final JTextArea sourceHelpDetails = textArea();

    JComponent build() {
        sourceHelpDetails.setLineWrap(false);
        return scroll(sourceHelpDetails);
    }

    void update(CaptureSession session, ActiveSource activeSource) {
        StringBuilder sb = new StringBuilder();
        sb.append(DesktopSourceLaneModel.render(session, activeSource)).append('\n');
        sb.append("DeadScout Desktop sources\n\n");
        sb.append("Start/Stop import\n");
        sb.append("  Load PCAP, PCAPNG, rtl_433 JSON logs, IQ/.cu8/.raw files, raw hex, Auxiliary HCI text logs, 802.15.4 logs, cell survey logs, or DeadScout session exports. Decoded imports appear in Packets; IQ and source checks appear in observations.\n\n");
        sb.append("Start/Stop network\n");
        sb.append("  Starts Windows pktmon, an existing dumpcap helper, or Linux tcpdump on DEADSCOUT_DESKTOP_NETWORK_IFACE / DEADSCOUT_DESKTOP_CAPTURE_IFACE. Decoded Ethernet/IP packets appear in Packets.\n\n");
        sb.append("Start/Stop NetworkA\n");
        sb.append("  Starts Windows pktmon, an existing dumpcap/tcpdump helper, or a configured monitor-mode helper on DEADSCOUT_DESKTOP_NET_A_IFACE. Radiotap/802.11 frames decode into NetworkA packet rows; non-monitor link types are reported as setup observations.\n\n");
        sb.append("Start/Stop Auxiliary\n");
        sb.append("  Starts btmon, hcidump, or DEADSCOUT_DESKTOP_AUXILIARY_CMD. BTSnoop, HCI, and AUX advertising packets decode into packet rows.\n\n");
        sb.append("Start/Stop sniffer\n");
        sb.append("  Starts DEADSCOUT_DESKTOP_SNIFFER_CMD or an existing sniffer PCAP interface. Helpers that print frame hex with optional channel/RSSI/LQI feed the 802.15.4 decoder live.\n\n");
        sb.append("Start/Stop USB / RTL / rtl_tcp\n");
        sb.append("  USB first tries rtl_433 JSON and then rtl_sdr IQ; RTL runs rtl_sdr directly; rtl_tcp reads the configured TCP source. IQ updates waterfall/observations and decoded JSON appends packet rows.\n\n");
        sb.append("Review tools\n");
        sb.append("  Packets: display filter, field details, and raw hex/ASCII.\n");
        sb.append("  Topology: endpoints, conversations, source status, and observations.\n");
        sb.append("  Waterfall: frequency/time zoom plus modern scroll panning.\n");
        sb.append("  Lab: selected packet bytes, unknown-signal clues, and capture notes.\n\n");
        sb.append("Honest capture boundary\n");
        sb.append("  DeadScout now starts real helpers for each source. Missing helper, permission, wrong adapter mode, or zero frames is reported as an actionable observation instead of a fake packet.\n");
        sb.append("  Packaged helper setup: Windows ZIP/install includes packet driver-only setup-capture-helpers-windows.ps1/.cmd plus DeadScout's own deadscout-windows-capture-helper.ps1/.cmd for pktmon/NETLAN/Auxiliary status and bounded native PCAPNG capture; it does not install packet workbench. Linux ZIP includes scripts/setup_capture_helpers_linux.sh.\n");
        sourceHelpDetails.setText(sb.toString());
        sourceHelpDetails.setCaretPosition(0);
    }
}
