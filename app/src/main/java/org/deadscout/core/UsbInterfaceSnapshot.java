package org.deadscout.core;

public final class UsbInterfaceSnapshot {
    public final int interfaceClass;
    public final int interfaceSubclass;
    public final int interfaceProtocol;
    public final int endpointCount;

    public UsbInterfaceSnapshot(int interfaceClass, int interfaceSubclass, int interfaceProtocol, int endpointCount) {
        this.interfaceClass = interfaceClass;
        this.interfaceSubclass = interfaceSubclass;
        this.interfaceProtocol = interfaceProtocol;
        this.endpointCount = endpointCount;
    }

    public String classTriple() {
        return String.format(java.util.Locale.US, "%02X/%02X/%02X", interfaceClass, interfaceSubclass, interfaceProtocol);
    }
}
