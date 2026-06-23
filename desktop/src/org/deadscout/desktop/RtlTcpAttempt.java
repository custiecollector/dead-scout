package org.deadscout.desktop;

import java.util.Arrays;
import java.util.LinkedHashMap;

final class RtlTcpAttempt {
    final String summary;
    final byte[] iqBytes;
    final long frequencyHz;
    final LinkedHashMap<String, String> fields;

    RtlTcpAttempt(String summary, byte[] iqBytes, long frequencyHz, LinkedHashMap<String, String> fields) {
        this.summary = summary;
        this.iqBytes = iqBytes == null ? new byte[0] : Arrays.copyOf(iqBytes, iqBytes.length);
        this.frequencyHz = frequencyHz;
        this.fields = fields;
    }
}
