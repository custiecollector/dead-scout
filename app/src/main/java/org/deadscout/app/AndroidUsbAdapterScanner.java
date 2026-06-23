package org.deadscout.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import org.deadscout.core.UsbAdapterClassifier;
import org.deadscout.core.UsbAdapterProfile;
import org.deadscout.core.UsbDeviceSnapshot;
import org.deadscout.core.UsbInterfaceSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class AndroidUsbAdapterScanner {
    private static final String ACTION_USB_PERMISSION = "org.deadscout.app.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;

    public AndroidUsbAdapterScanner(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    }

    public List<String> scanCards() {
        ArrayList<String> cards = new ArrayList<>();
        if (usbManager == null) {
            cards.add("USB manager unavailable on this Android build.");
            return cards;
        }
        if (usbManager.getDeviceList().isEmpty()) {
            cards.add("No USB packet/radio adapters currently attached. Connect a USB OTG hub/device, then rescan.");
            return cards;
        }
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            UsbDeviceSnapshot snapshot = snapshot(device);
            UsbAdapterProfile profile = UsbAdapterClassifier.classify(snapshot);
            cards.add(profile.summary(snapshot, usbManager.hasPermission(device))
                    + "\nUtilization: " + utilizationNote(profile));
        }
        return cards;
    }

    public int requestUsbPermissions() {
        if (usbManager == null) return 0;
        int requested = 0;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()),
                pendingIntentFlags());
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (!usbManager.hasPermission(device)) {
                usbManager.requestPermission(device, permissionIntent);
                requested++;
            }
        }
        return requested;
    }

    public String scanSummary() {
        if (usbManager == null) return "USB manager unavailable on this Android build.";
        int total = usbManager.getDeviceList().size();
        if (total == 0) return "No USB radio/packet adapters attached.";
        int granted = 0;
        int rtl = 0;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (usbManager.hasPermission(device)) granted++;
            if (isRtlSdr(device)) rtl++;
        }
        return total + " USB adapter(s) attached · " + granted + " permission granted · " + rtl + " RTL-SDR radio(s) detected.";
    }

    public int deviceCount() {
        return usbManager == null ? 0 : usbManager.getDeviceList().size();
    }

    public int permissionGrantedCount() {
        if (usbManager == null) return 0;
        int granted = 0;
        for (UsbDevice device : usbManager.getDeviceList().values()) if (usbManager.hasPermission(device)) granted++;
        return granted;
    }

    public boolean hasRtlSdr() {
        if (usbManager == null) return false;
        for (UsbDevice device : usbManager.getDeviceList().values()) if (isRtlSdr(device)) return true;
        return false;
    }

    public boolean hasUsbDevices() {
        return usbManager != null && !usbManager.getDeviceList().isEmpty();
    }

    private boolean isRtlSdr(UsbDevice device) {
        return device != null && device.getVendorId() == 0x0BDA && (device.getProductId() == 0x2832 || device.getProductId() == 0x2838);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private UsbDeviceSnapshot snapshot(UsbDevice device) {
        ArrayList<UsbInterfaceSnapshot> ifaces = new ArrayList<>();
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            ifaces.add(new UsbInterfaceSnapshot(intf.getInterfaceClass(), intf.getInterfaceSubclass(), intf.getInterfaceProtocol(), intf.getEndpointCount()));
        }
        return new UsbDeviceSnapshot(
                device.getDeviceName(),
                device.getVendorId(),
                device.getProductId(),
                device.getDeviceClass(),
                device.getDeviceSubclass(),
                device.getDeviceProtocol(),
                safeManufacturer(device),
                safeProduct(device),
                safeSerial(device),
                ifaces);
    }

    private String safeManufacturer(UsbDevice device) {
        if (Build.VERSION.SDK_INT >= 21) {
            try { return device.getManufacturerName(); } catch (RuntimeException ignored) { return ""; }
        }
        return "";
    }

    private String safeProduct(UsbDevice device) {
        if (Build.VERSION.SDK_INT >= 21) {
            try { return device.getProductName(); } catch (RuntimeException ignored) { return ""; }
        }
        return "";
    }

    private String safeSerial(UsbDevice device) {
        if (Build.VERSION.SDK_INT >= 21) {
            try { return device.getSerialNumber(); } catch (RuntimeException ignored) { return ""; }
        }
        return "";
    }

    private String utilizationNote(UsbAdapterProfile profile) {
        switch (profile.kind) {
            case RTL_SDR:
                return "open USB device -> librtlsdr-compatible IQ stream -> SDR scout/decoder modules.";
            case IEEE802154_SNIFFER:
                return "open USB/serial sniffer -> 802.15.4 frames -> MAC decoder + PCAP export.";
            case WIDEBAND_SDR:
                return "open vendor SDR driver -> IQ stream -> surrounding RF capture matrix.";
            case SERIAL_SNIFFER_BRIDGE:
                return "open USB serial bridge -> select attached sniffer protocol -> packet decoder.";
            default:
                return "descriptor visible; map to a DeadScout USB source plugin before packet capture.";
        }
    }
}
