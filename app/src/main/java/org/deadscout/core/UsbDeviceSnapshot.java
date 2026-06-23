package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsbDeviceSnapshot {
    public final String deviceName;
    public final int vendorId;
    public final int productId;
    public final int deviceClass;
    public final int deviceSubclass;
    public final int deviceProtocol;
    public final String manufacturerName;
    public final String productName;
    public final String serialNumber;
    public final List<UsbInterfaceSnapshot> interfaces;

    public UsbDeviceSnapshot(String deviceName, int vendorId, int productId, int deviceClass, int deviceSubclass,
                             int deviceProtocol, String manufacturerName, String productName, String serialNumber,
                             List<UsbInterfaceSnapshot> interfaces) {
        this.deviceName = safe(deviceName);
        this.vendorId = vendorId;
        this.productId = productId;
        this.deviceClass = deviceClass;
        this.deviceSubclass = deviceSubclass;
        this.deviceProtocol = deviceProtocol;
        this.manufacturerName = safe(manufacturerName);
        this.productName = safe(productName);
        this.serialNumber = safe(serialNumber);
        this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces == null ? Collections.emptyList() : interfaces));
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public String idHex() {
        return String.format(java.util.Locale.US, "%04X:%04X", vendorId & 0xFFFF, productId & 0xFFFF);
    }

    public String displayName() {
        if (!productName.isEmpty() && !manufacturerName.isEmpty()) return manufacturerName + " " + productName;
        if (!productName.isEmpty()) return productName;
        if (!manufacturerName.isEmpty()) return manufacturerName + " USB device";
        return "USB " + idHex();
    }

    public boolean hasInterface(int cls, int sub, int proto) {
        for (UsbInterfaceSnapshot i : interfaces) {
            boolean classMatch = cls < 0 || i.interfaceClass == cls;
            boolean subMatch = sub < 0 || i.interfaceSubclass == sub;
            boolean protoMatch = proto < 0 || i.interfaceProtocol == proto;
            if (classMatch && subMatch && protoMatch) return true;
        }
        return false;
    }

    public boolean textContains(String needle) {
        String n = needle == null ? "" : needle.toLowerCase(java.util.Locale.US);
        String hay = (deviceName + " " + manufacturerName + " " + productName + " " + serialNumber).toLowerCase(java.util.Locale.US);
        return hay.contains(n);
    }
}
