package org.deadscout.core;

public final class UsbAdapterClassifier {
    private UsbAdapterClassifier() {}
    public static UsbAdapterProfile classify(UsbDeviceSnapshot d) {
        if (isRtlSdr(d)) return new UsbAdapterProfile(UsbAdapterProfile.Kind.RTL_SDR, "rtl-sdr-usb", "RTL-SDR compatible SDR", "Reamobilek RTL2832U/R820T family", "libusb/librtlsdr direct USB source", "IQ stream -> frequency scout/burst/rtl_433/ADS-B paths", "SDR demodulators, rtl_433, OOK/FSK, ADS-B module path", true, true, true);
        if (is802154Sniffer(d)) return new UsbAdapterProfile(UsbAdapterProfile.Kind.IEEE802154_SNIFFER, "ieee802154", "802.15.4 USB sniffer", "nRF52840/CC2531/Silabs/serial sniffer family", "USB CDC/HID/vendor/serial reader depending on firmware", "802.15.4 frames -> PCAP/linktype 230", "802.15.4 MAC, PAN/src/dst/sequence/LQI", true, true, true);
        if (isSerialBridge(d)) return new UsbAdapterProfile(UsbAdapterProfile.Kind.SERIAL_SNIFFER_BRIDGE, "usb-serial-sniffer", "USB serial sniffer bridge", "CP210x/FTDI/CH340/CDC bridge", "USB serial driver plugin; route attached sniffer firmware output by selected protocol", "serial frame stream -> selected decoder", "802.15.4, GPS/timebase, external sniffer protocols", true, true, true);
        if (isWidebandSdr(d)) return new UsbAdapterProfile(UsbAdapterProfile.Kind.WIDEBAND_SDR, "wideband-sdr", "Wideband SDR adapter", "HackRF/Lime/Pluto/Airspy/SDRplay family", "libusb/vendor SDR driver plugin", "IQ stream -> spectrum/burst/demod routing", "wideband scout, OOK/FSK, 802.15.4/HackRF path, protocol modules", true, true, true);
        return new UsbAdapterProfile(UsbAdapterProfile.Kind.UNKNOWN_PACKET_DEVICE, "usb-unknown", "Unknown USB packet/radio device", "unclassified", "enumerate descriptors and map to a source plugin", "raw USB endpoint/plugin-defined stream", "manual plugin route", false, false, true);
    }
    public static boolean isDirectPublicAdapter(UsbDeviceSnapshot d) { return false; }
    private static boolean isRtlSdr(UsbDeviceSnapshot d) { return d.vendorId == 0x0BDA && (d.productId == 0x2832 || d.productId == 0x2838); }
    private static boolean is802154Sniffer(UsbDeviceSnapshot d) { return d.textContains("nrf52840") || d.textContains("cc2531") || d.textContains("zigbee") || d.textContains("802.15.4") || d.textContains("sonoff") || d.vendorId == 0x1915 || d.vendorId == 0x0451 || d.vendorId == 0x10C4; }
    private static boolean isSerialBridge(UsbDeviceSnapshot d) { return d.vendorId == 0x10C4 || d.vendorId == 0x0403 || d.vendorId == 0x1A86 || d.hasInterface(0x02, -1, -1); }
    private static boolean isWidebandSdr(UsbDeviceSnapshot d) { return d.textContains("hackrf") || d.textContains("limesdr") || d.textContains("plutosdr") || d.textContains("airspy") || d.textContains("sdrplay") || d.vendorId == 0x1D50 || d.vendorId == 0x2CF0 || d.vendorId == 0x03EB; }
}
