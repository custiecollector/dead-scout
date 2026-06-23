package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaptureImportResult {
    public final String fileName;
    public final String detectedType;
    public final int importedBytes;
    public final CaptureSession session;
    public final ArrayList<String> warnings = new ArrayList<>();

    public CaptureImportResult(String fileName, String detectedType, int importedBytes, CaptureSession session) {
        this.fileName = fileName == null ? "" : fileName;
        this.detectedType = detectedType == null ? "unknown" : detectedType;
        this.importedBytes = importedBytes;
        this.session = session;
    }

    public CaptureImportResult warn(String warning) {
        if (warning != null && !warning.isEmpty()) warnings.add(warning);
        return this;
    }

    public List<String> warnings() { return Collections.unmodifiableList(warnings); }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(detectedType).append(" import · ").append(importedBytes).append(" bytes · ")
                .append(session.packets.size()).append(" decoded packets");
        if (!session.observations.isEmpty()) sb.append(" · ").append(session.observations.size()).append(" waterfall markers");
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:");
            for (String w : warnings) sb.append("\n- ").append(w);
        }
        return sb.toString();
    }
}
