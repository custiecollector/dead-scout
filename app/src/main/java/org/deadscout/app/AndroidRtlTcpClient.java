package org.deadscout.app;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.HexUtils;
import org.deadscout.core.RtlSdrIqPipeline;
import org.deadscout.core.SpectrumSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class AndroidRtlTcpClient {
    private AndroidRtlTcpClient() {}

    public static Result readIq(String host, int port, RtlSdrIqPipeline.Config config, int bytesToRead, int timeoutMs) {
        long now = System.currentTimeMillis();
        CaptureSession session = new CaptureSession("rtl-tcp-" + now, now).withSource("rtl-tcp", config.frequencyHz, config.sampleRateHz);
        RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(config);
        byte[] iq = new byte[0];
        String dongle = "not connected";
        String status;
        boolean ok = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            InputStream in = socket.getInputStream();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            byte[] header = readUpTo(in, 12, timeoutMs);
            dongle = describeHeader(header);
            sendCommand(out, 0x01, (int) config.frequencyHz);
            sendCommand(out, 0x02, config.sampleRateHz);
            sendCommand(out, 0x03, config.agc ? 0 : 1);
            if (!config.agc) sendCommand(out, 0x04, config.gainTenthsDb);
            sendCommand(out, 0x05, config.ppmCorrection);
            iq = readUpTo(in, Math.max(512, bytesToRead), timeoutMs);
            ok = iq.length >= 64;
            status = ok
                    ? "Connected to local rtl_tcp driver and read " + iq.length + " IQ bytes."
                    : "Connected to local rtl_tcp driver, but no IQ bytes arrived before timeout.";
        } catch (SocketTimeoutException ex) {
            status = "Timed out connecting/reading local rtl_tcp driver on " + host + ":" + port + ". Launch the SDR driver first, then tap Start external rtl_tcp capture.";
        } catch (IOException ex) {
            status = "Could not connect to local rtl_tcp driver on " + host + ":" + port + ": " + ex.getClass().getSimpleName() + " " + ex.getMessage();
        } catch (RuntimeException ex) {
            status = "RTL-TCP read failed: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("host", host);
        fields.put("port", Integer.toString(port));
        fields.put("frequency_hz", Long.toString(config.frequencyHz));
        fields.put("sample_rate_hz", Integer.toString(config.sampleRateHz));
        fields.put("gain", config.agc ? "agc" : String.format(Locale.US, "%.1f dB", config.gainTenthsDb / 10.0));
        fields.put("ppm", Integer.toString(config.ppmCorrection));
        fields.put("dongle", dongle);
        fields.put("iq_bytes", Integer.toString(iq.length));
        fields.put("status", status);
        SpectrumSnapshot snapshot = null;
        if (ok) {
            snapshot = pipeline.ingestUnsignedIq(iq);
            for (org.deadscout.core.SignalObservation obs : pipeline.observationsFrom(snapshot, "rtl_tcp")) session.addObservation(obs);
            session.addSnapshot(snapshot.summary());
        } else session.addNote("rtl_tcp control/read status only; no decoded packet was created because no IQ bytes arrived.");
        session.addNote(status);
        session.addNote(pipeline.controlPlan());
        return new Result(ok, status, dongle, iq, snapshot, session);
    }

    private static void sendCommand(DataOutputStream out, int command, int value) throws IOException {
        out.writeByte(command & 0xFF);
        out.writeInt(value);
        out.flush();
    }

    private static byte[] readUpTo(InputStream in, int maxBytes, int timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(100, timeoutMs);
        ByteArrayOutputStream out = new ByteArrayOutputStream(maxBytes);
        byte[] buf = new byte[Math.min(8192, Math.max(512, maxBytes))];
        while (out.size() < maxBytes && System.currentTimeMillis() < deadline) {
            try {
                int n = in.read(buf, 0, Math.min(buf.length, maxBytes - out.size()));
                if (n < 0) break;
                if (n > 0) out.write(buf, 0, n);
            } catch (SocketTimeoutException ex) {
                break;
            }
        }
        return out.toByteArray();
    }

    private static String describeHeader(byte[] header) {
        if (header == null || header.length < 12) return "no rtl_tcp dongle header";
        String magic = new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int tuner = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16) | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        int gainCount = ((header[8] & 0xFF) << 24) | ((header[9] & 0xFF) << 16) | ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
        return magic + " tuner=" + tuner + " gain_count=" + gainCount;
    }

    public static final class Result {
        public final boolean ok;
        public final String status;
        public final String dongleInfo;
        public final byte[] iqBytes;
        public final SpectrumSnapshot snapshot;
        public final CaptureSession session;

        public Result(boolean ok, String status, String dongleInfo, byte[] iqBytes, SpectrumSnapshot snapshot, CaptureSession session) {
            this.ok = ok;
            this.status = status;
            this.dongleInfo = dongleInfo;
            this.iqBytes = iqBytes == null ? new byte[0] : iqBytes;
            this.snapshot = snapshot;
            this.session = session;
        }
    }
}
