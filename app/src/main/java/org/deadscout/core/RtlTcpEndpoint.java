package org.deadscout.core;

public final class RtlTcpEndpoint {
    public final String host;
    public final int port;
    public final long frequencyHz;
    public final int sampleRateHz;
    public final int gainTenthsDb;

    public RtlTcpEndpoint(String host, int port, long frequencyHz, int sampleRateHz, int gainTenthsDb) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host required");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("invalid port");
        this.host = host.trim();
        this.port = port;
        this.frequencyHz = frequencyHz;
        this.sampleRateHz = sampleRateHz;
        this.gainTenthsDb = gainTenthsDb;
    }

    public String summary() {
        return host + ":" + port + " @ " + frequencyHz + " Hz / " + sampleRateHz + " sps";
    }
}
