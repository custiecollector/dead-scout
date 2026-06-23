package org.deadscout.app;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.HexUtils;
import org.deadscout.core.RtlSdrIqPipeline;
import org.deadscout.core.SpectrumSnapshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class AndroidRtlSdrUsbProbe {
    private final UsbManager usbManager;

    public AndroidRtlSdrUsbProbe(Context context) {
        this.usbManager = (UsbManager) context.getApplicationContext().getSystemService(Context.USB_SERVICE);
    }

    public Result probe(RtlSdrIqPipeline.Config config, int timeoutMs) {
        return capture(config, 16 * 1024, timeoutMs, "probe");
    }

    public Result capture(RtlSdrIqPipeline.Config config, int targetBytes, int timeoutMs) {
        return capture(config, Math.max(16 * 1024, targetBytes), timeoutMs, "capture");
    }

    public void stream(RtlSdrIqPipeline.Config config, int chunkBytes, int transferTimeoutMs, ContinueFlag keepRunning, StreamListener listener) {
        int targetBytes = Math.max(16 * 1024, chunkBytes);
        long now = System.currentTimeMillis();
        UsbDevice device = findRtlSdr();
        String status;
        boolean opened = false;
        boolean claimed = false;
        String endpoint = "none";
        if (listener == null) return;
        if (usbManager == null) {
            listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "USB manager unavailable on this Android build.", false, false, false, endpoint, new byte[0], null), 0);
            return;
        }
        if (device == null) {
            listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "No RTL-SDR USB radio is attached.", false, false, false, endpoint, new byte[0], null), 0);
            return;
        }
        if (!usbManager.hasPermission(device)) {
            listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "RTL-SDR is attached but USB permission is not granted yet. Tap Request USB adapter permission first.", false, false, false, endpoint, new byte[0], null), 0);
            return;
        }

        UsbDeviceConnection connection = null;
        UsbInterface intf = null;
        try {
            connection = usbManager.openDevice(device);
            opened = connection != null;
            if (!opened) {
                listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "Android refused to open the RTL-SDR USB device.", false, false, false, endpoint, new byte[0], null), 0);
                return;
            }
            intf = firstInterface(device);
            if (intf == null) {
                listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "RTL-SDR opened, but no USB interface was exposed.", false, true, false, endpoint, new byte[0], null), 0);
                return;
            }
            claimed = connection.claimInterface(intf, true);
            UsbEndpoint bulkIn = firstBulkIn(intf);
            endpoint = bulkIn == null ? "no bulk-in endpoint" : "bulk-in ep " + bulkIn.getEndpointNumber() + " maxPacket=" + bulkIn.getMaxPacketSize();
            if (!claimed) {
                listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "RTL-SDR opened, but Android could not claim the USB interface.", false, true, false, endpoint, new byte[0], null), 0);
                return;
            }
            if (bulkIn == null) {
                listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + now, now), "RTL-SDR opened and interface claimed, but no bulk IQ endpoint was found.", false, true, true, endpoint, new byte[0], null), 0);
                return;
            }

            AndroidRtlSdrController.ControlReport control = AndroidRtlSdrController.configure(connection, config);
            byte[] buf = new byte[Math.min(32 * 1024, targetBytes)];
            int chunkIndex = 0;
            while (keepRunning == null || keepRunning.keepGoing()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(targetBytes);
                while (out.size() < targetBytes && (keepRunning == null || keepRunning.keepGoing())) {
                    int n = connection.bulkTransfer(bulkIn, buf, Math.min(buf.length, targetBytes - out.size()), Math.max(50, transferTimeoutMs));
                    if (n > 0) out.write(buf, 0, n);
                    else break;
                }
                byte[] iq = out.toByteArray();
                CaptureSession session = new CaptureSession("rtl-usb-stream-" + System.currentTimeMillis(), System.currentTimeMillis());
                if (iq.length < 64) {
                    status = "RTL-SDR continuous stream opened " + endpoint + ", but no IQ bytes arrived. Close any external SDR driver app, unplug/replug the radio, then try again.";
                    if (!control.ok) status += " Setup also reported tuning warnings.";
                    listener.onChunk(finish(config, session, status, false, opened, claimed, endpoint, iq, control), chunkIndex);
                    return;
                }
                chunkIndex++;
                status = String.format(Locale.US, "Continuous RTL-SDR stream chunk %d: %d IQ bytes at %.3f MHz.", chunkIndex, iq.length, config.frequencyHz / 1_000_000.0);
                if (!control.ok) status += " Radio setup reported non-fatal tuning warnings; IQ data was still received.";
                listener.onChunk(finish(config, session, status, control.ok, true, true, endpoint, iq, control), chunkIndex);
            }
        } catch (RuntimeException ex) {
            status = "Built-in RTL-SDR USB stream failed: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
            listener.onChunk(finish(config, new CaptureSession("rtl-usb-stream-" + System.currentTimeMillis(), System.currentTimeMillis()), status, false, opened, claimed, endpoint, new byte[0], null), 0);
        } finally {
            try { if (connection != null && intf != null && claimed) connection.releaseInterface(intf); } catch (RuntimeException ignored) { }
            try { if (connection != null) connection.close(); } catch (RuntimeException ignored) { }
        }
    }

    private Result capture(RtlSdrIqPipeline.Config config, int targetBytes, int timeoutMs, String mode) {
        long now = System.currentTimeMillis();
        CaptureSession session = new CaptureSession("rtl-usb-" + mode + "-" + now, now);
        UsbDevice device = findRtlSdr();
        String status;
        byte[] iq = new byte[0];
        boolean opened = false;
        boolean claimed = false;
        String endpoint = "none";
        if (usbManager == null) {
            status = "USB manager unavailable on this Android build.";
            return finish(config, session, status, false, false, false, endpoint, iq, null);
        }
        if (device == null) {
            status = "No RTL-SDR USB radio is attached.";
            return finish(config, session, status, false, false, false, endpoint, iq, null);
        }
        if (!usbManager.hasPermission(device)) {
            status = "RTL-SDR is attached but USB permission is not granted yet. Tap Request USB adapter permission first.";
            return finish(config, session, status, false, false, false, endpoint, iq, null);
        }

        UsbDeviceConnection connection = null;
        UsbInterface intf = null;
        try {
            connection = usbManager.openDevice(device);
            opened = connection != null;
            if (!opened) {
                status = "Android refused to open the RTL-SDR USB device.";
                return finish(config, session, status, false, false, false, endpoint, iq, null);
            }
            intf = firstInterface(device);
            if (intf == null) {
                status = "RTL-SDR opened, but no USB interface was exposed.";
                return finish(config, session, status, false, true, false, endpoint, iq, null);
            }
            claimed = connection.claimInterface(intf, true);
            UsbEndpoint bulkIn = firstBulkIn(intf);
            endpoint = bulkIn == null ? "no bulk-in endpoint" : "bulk-in ep " + bulkIn.getEndpointNumber() + " maxPacket=" + bulkIn.getMaxPacketSize();
            if (!claimed) {
                status = "RTL-SDR opened, but Android could not claim the USB interface.";
                return finish(config, session, status, false, true, false, endpoint, iq, null);
            }
            if (bulkIn == null) {
                status = "RTL-SDR opened and interface claimed, but no bulk IQ endpoint was found.";
                return finish(config, session, status, false, true, true, endpoint, iq, null);
            }

            AndroidRtlSdrController.ControlReport control = AndroidRtlSdrController.configure(connection, config);
            ByteArrayOutputStream out = new ByteArrayOutputStream(targetBytes);
            byte[] buf = new byte[Math.min(32 * 1024, Math.max(16 * 1024, targetBytes))];
            long deadline = System.currentTimeMillis() + Math.max(100, timeoutMs);
            while (out.size() < targetBytes && System.currentTimeMillis() < deadline) {
                int n = connection.bulkTransfer(bulkIn, buf, Math.min(buf.length, targetBytes - out.size()), Math.max(50, timeoutMs));
                if (n > 0) out.write(buf, 0, n);
                else break;
            }
            iq = out.toByteArray();
            if (iq.length > 0) {
                status = String.format(Locale.US, "Captured %d IQ bytes from RTL-SDR at %.3f MHz.", iq.length, config.frequencyHz / 1_000_000.0);
                if (!control.ok) status += " Radio setup reported non-fatal tuning warnings; IQ data was still received.";
            } else {
                status = "RTL-SDR opened " + endpoint + ", but no IQ bytes arrived. Close any external SDR driver app, unplug/replug the radio, then try capture monitor again.";
                if (!control.ok) status += " Setup also reported tuning warnings.";
            }
            return finish(config, session, status, control.ok, true, true, endpoint, iq, control);
        } catch (RuntimeException ex) {
            status = "Built-in RTL-SDR USB " + mode + " failed: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
            return finish(config, session, status, false, opened, claimed, endpoint, iq, null);
        } finally {
            try { if (connection != null && intf != null && claimed) connection.releaseInterface(intf); } catch (RuntimeException ignored) { }
            try { if (connection != null) connection.close(); } catch (RuntimeException ignored) { }
        }
    }

    private Result finish(RtlSdrIqPipeline.Config config, CaptureSession session, String status, boolean controlOk, boolean opened, boolean claimed, String endpoint, byte[] iq, AndroidRtlSdrController.ControlReport control) {
        RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(config);
        session.withSource("rtl-sdr-usb", config.frequencyHz, config.sampleRateHz);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("frequency_hz", Long.toString(config.frequencyHz));
        fields.put("sample_rate_hz", Integer.toString(config.sampleRateHz));
        fields.put("gain", config.agc ? "agc" : String.format(Locale.US, "%.1f dB", config.gainTenthsDb / 10.0));
        fields.put("opened", Boolean.toString(opened));
        fields.put("claimed", Boolean.toString(claimed));
        fields.put("control_ok", Boolean.toString(controlOk));
        if (control != null) {
            fields.put("tuner", control.tuner);
            fields.put("control_failures", Integer.toString(control.failures));
        }
        fields.put("endpoint", endpoint);
        fields.put("iq_bytes", Integer.toString(iq == null ? 0 : iq.length));
        fields.put("status", status);
        session.addNote(status);
        if (control != null && control.detail != null && !control.detail.isEmpty()) session.addNote(control.detail);
        session.addNote(pipeline.controlPlan());
        SpectrumSnapshot snapshot = null;
        if (iq != null && iq.length >= 64) {
            snapshot = pipeline.ingestUnsignedIq(iq);
            for (org.deadscout.core.SignalObservation obs : pipeline.observationsFrom(snapshot)) session.addObservation(obs);
            session.addSnapshot(snapshot.summary());
        } else session.addNote("RTL-SDR USB control/probe status only; no decoded packet was created because no IQ bytes arrived.");
        return new Result(iq != null && iq.length >= 64, status, iq == null ? new byte[0] : iq, snapshot, session);
    }

    private UsbDevice findRtlSdr() {
        if (usbManager == null) return null;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x0BDA && (device.getProductId() == 0x2832 || device.getProductId() == 0x2838)) return device;
        }
        return null;
    }

    private UsbInterface firstInterface(UsbDevice device) {
        if (device == null || device.getInterfaceCount() <= 0) return null;
        return device.getInterface(0);
    }

    private UsbEndpoint firstBulkIn(UsbInterface intf) {
        if (intf == null) return null;
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint endpoint = intf.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.getDirection() == UsbConstants.USB_DIR_IN) return endpoint;
        }
        return null;
    }

    public interface ContinueFlag {
        boolean keepGoing();
    }

    public interface StreamListener {
        void onChunk(Result result, int chunkIndex);
    }

    public static final class Result {
        public final boolean ok;
        public final String status;
        public final byte[] iqBytes;
        public final SpectrumSnapshot snapshot;
        public final CaptureSession session;

        public Result(boolean ok, String status, byte[] iqBytes, SpectrumSnapshot snapshot, CaptureSession session) {
            this.ok = ok;
            this.status = status;
            this.iqBytes = iqBytes == null ? new byte[0] : iqBytes;
            this.snapshot = snapshot;
            this.session = session;
        }
    }
}
