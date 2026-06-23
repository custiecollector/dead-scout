package org.deadscout.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProtocolDecode {
    public enum Status { DECODED, PARTIAL, UNKNOWN, ENCRYPTED, ERROR }

    public final String module;
    public final String protocol;
    public final Status status;
    public final String summary;
    public final LinkedHashMap<String, String> fields;

    public ProtocolDecode(String module, String protocol, Status status, String summary, Map<String, String> fields) {
        this.module = module;
        this.protocol = protocol;
        this.status = status;
        this.summary = summary;
        this.fields = new LinkedHashMap<>();
        if (fields != null) this.fields.putAll(fields);
    }

    public static ProtocolDecode decoded(String module, String protocol, String summary, Map<String, String> fields) {
        return new ProtocolDecode(module, protocol, Status.DECODED, summary, fields);
    }

    public static ProtocolDecode partial(String module, String protocol, String summary, Map<String, String> fields) {
        return new ProtocolDecode(module, protocol, Status.PARTIAL, summary, fields);
    }

    public static ProtocolDecode unknown(String module, String summary) {
        return new ProtocolDecode(module, "unknown", Status.UNKNOWN, summary, Collections.emptyMap());
    }
}
