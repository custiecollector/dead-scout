package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;

public final class Rtl433Module {
    private Rtl433Module() {}

    public static DecoderPlugin plugin() {
        return new DecoderPlugin("rtl_433", "rtl_433 ISM sensor decoder", "rtl_433 JSON", "JSON/bytes",
                "315/345/433/868/915 MHz", "model,id,channel,rssi,device fields", "JSON/session", "GPL-2.0-or-later",
                "Embedded path normalizes rtl_433 JSON lines into DeadScout packet records; bundled native/helper execution must ship with GPL notices/source offer.");
    }

    public static List<PacketRecord> decodeJsonLog(String text, String sourceId) {
        ArrayList<PacketRecord> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{") && (trimmed.contains("\"model\"") || trimmed.contains("\"protocol\"") || trimmed.contains("\"rssi\""))) {
                out.add(Rtl433JsonDecoder.decodeLine(trimmed, sourceId));
            }
        }
        return out;
    }

    public static String licenseNotice() {
        return "rtl_433 module path: preserve upstream GPL notices/source offer when bundling native/helper rtl_433 code; DeadScout core only normalizes JSON in this build.";
    }

    public static String integrationStatus() {
        return "Active: rtl_433 JSON/log import and packet normalization. Native/helper execution is disaauxd unless a GPL-compliant rtl_433 binary is bundled or operator-provided.";
    }
}
