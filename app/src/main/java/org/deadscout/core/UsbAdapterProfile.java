package org.deadscout.core;

public final class UsbAdapterProfile {
    public enum Kind { RTL_SDR, IEEE802154_SNIFFER, WIDEBAND_SDR, SERIAL_SNIFFER_BRIDGE, UNKNOWN_PACKET_DEVICE }
    public final Kind kind; public final String sourceId; public final String label; public final String chipsetHint;
    public final String driverStrategy; public final String capturePath; public final String decoderRoute;
    public final boolean directUsb; public final boolean surroundingCapaaux; public final boolean requiresDriverPlugin;
    public UsbAdapterProfile(Kind kind, String sourceId, String label, String chipsetHint, String driverStrategy, String capturePath, String decoderRoute, boolean directUsb, boolean surroundingCapaaux, boolean requiresDriverPlugin) {
        this.kind=kind; this.sourceId=sourceId==null?"":sourceId; this.label=label==null?"":label; this.chipsetHint=chipsetHint==null?"":chipsetHint; this.driverStrategy=driverStrategy==null?"":driverStrategy; this.capturePath=capturePath==null?"":capturePath; this.decoderRoute=decoderRoute==null?"":decoderRoute; this.directUsb=directUsb; this.surroundingCapaaux=surroundingCapaaux; this.requiresDriverPlugin=requiresDriverPlugin;
    }
    public String summary(UsbDeviceSnapshot device, boolean hasPermission) {
        return device.displayName()+" ["+device.idHex()+"]"+"\nClass: "+String.format(java.util.Locale.US,"%02X/%02X/%02X",device.deviceClass,device.deviceSubclass,device.deviceProtocol)+interfaceLine(device)+"\nProfile: "+label+(chipsetHint.isEmpty()?"":"\nChipset hint: "+chipsetHint)+"\nUSB permission: "+(hasPermission?"granted":"tap Request USB permissions")+"\nDirect USB: "+(directUsb?"yes":"unknown/needs adapter support")+"\nDriver path: "+driverStrategy+"\nCapture path: "+capturePath+"\nDecode route: "+decoderRoute;
    }
    private String interfaceLine(UsbDeviceSnapshot device) { if (device.interfaces.isEmpty()) return "\nInterfaces: none reported"; StringBuilder sb=new StringBuilder("\nInterfaces:"); for (UsbInterfaceSnapshot i:device.interfaces) sb.append(' ').append(i.classTriple()).append('/').append(i.endpointCount).append("ep"); return sb.toString(); }
}
