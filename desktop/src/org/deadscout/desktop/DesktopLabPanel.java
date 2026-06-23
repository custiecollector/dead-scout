package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.HexInspector;
import org.deadscout.core.PacketRecord;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.util.Locale;

final class DesktopLabPanel {
    private final JTextArea labDetails = textArea();

    JComponent build() {
        return scroll(labDetails);
    }

    void update(CaptureSession session, PacketRecord selectedPacket) {
        StringBuilder sb = new StringBuilder();
        sb.append("DeadScout Lab inspector\n");
        sb.append("Purpose: inspect selected packet bytes, raw hex, IQ/session notes, and unknown-signal clues without confusing raw IQ with decoded traffic packets.\n\n");
        if (selectedPacket != null) {
            sb.append("Selected: ").append(selectedPacket.title()).append("\n");
            sb.append("Status: ").append(selectedPacket.decode.status).append(" · ").append(selectedPacket.decode.summary).append("\n");
            if (!selectedPacket.rawHex.isEmpty()) {
                sb.append("Entropy: ").append(String.format(Locale.US, "%.2f", HexInspector.entropyBitsPerByte(selectedPacket.rawHex))).append(" bits/byte\n");
                sb.append("Hex / ASCII preview\n").append(HexInspector.hexdump(selectedPacket.rawHex, 256)).append("\n");
            } else {
                sb.append("No raw hex is attached to this selected record. Use imports, PCAP/PCAPNG, raw hex, HCI logs, 802.15.4 logs, or decoder output to populate byte-level inspection.\n");
            }
        } else {
            sb.append("No selected packet. Load a capture/session or clear the display filter.\n");
        }
        sb.append("\nSource observations in this session: ").append(session.observations.size()).append("\n");
        sb.append("Decoded packet rows in this session: ").append(session.packets.size()).append("\n");
        sb.append("\nCapture rule: keep one live source active, then add packets only when imports or decoders return bytes or metadata.\n");
        labDetails.setText(sb.toString());
        labDetails.setCaretPosition(0);
    }
}
