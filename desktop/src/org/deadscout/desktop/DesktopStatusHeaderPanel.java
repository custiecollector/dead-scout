package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureSession;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridLayout;

final class DesktopStatusHeaderPanel {
    private final JLabel sourceValue = cardValue("idle");
    private final JLabel packetsValue = cardValue("0");
    private final JLabel signalValue = cardValue("no capture");
    private final JLabel modeValue = cardValue("review ready");

    JPanel build() {
        JPanel cards = darkPanel(new GridLayout(1, 4, 8, 8));
        cards.add(card("Source", sourceValue));
        cards.add(card("Packets", packetsValue));
        cards.add(card("Signal", signalValue));
        cards.add(card("Mode", modeValue));
        return cards;
    }

    void update(ActiveSource activeSource, CaptureSession session) {
        sourceValue.setText(activeSource.label());
        packetsValue.setText(Integer.toString(session.packets.size()));
        signalValue.setText(session.observations.size() + " observations");
        modeValue.setText(activeSource == ActiveSource.NONE ? "review ready" : "active");
    }
}
