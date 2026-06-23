package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;

public final class AlertEngine {
    private final List<AlertRule> rules = new ArrayList<>();
    private final List<AlertEvent> events = new ArrayList<>();

    public AlertEngine addRule(AlertRule rule) {
        rules.add(rule);
        return this;
    }

    public List<AlertEvent> evaluate(CaptureSession session) {
        events.clear();
        if (session == null) return events;
        int idx = 0;
        for (PacketRecord packet : session.packets()) {
            for (AlertRule rule : rules) {
                if (rule.matches(packet)) {
                    events.add(new AlertEvent(packet.timestampMillis, rule.id, rule.display(), packet.decode.summary, idx));
                }
            }
            idx++;
        }
        for (SignalObservation obs : session.observations()) {
            for (AlertRule rule : rules) {
                if (rule.matches(obs)) {
                    events.add(new AlertEvent(obs.timestampMillis, rule.id, rule.display(), obs.note, -1));
                }
            }
        }
        return new ArrayList<>(events);
    }

    public String eventLog(int limit) {
        if (events.isEmpty()) return "No watch-rule events yet.";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AlertEvent event : events) {
            if (count++ >= limit) break;
            sb.append(event.displayLine()).append('\n');
        }
        return sb.toString().trim();
    }

    public static AlertEngine defaultWatchRules() {
        return new AlertEngine()
                .addRule(AlertRule.frequencyActive("rtl-active", 915_000_000L, 20_000_000L, -70))
                .addRule(AlertRule.fieldMatch("sensor-model", "model", ""))
                .addRule(AlertRule.protocolSeen("802154", "802.15.4"));
    }
}
