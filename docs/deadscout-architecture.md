# DeadScout architecture

DeadScout normalizes operator-selected captures into a common session model:

```text
CaptureSource -> SignalObservation -> DemodulatedFrame -> PacketRecord -> ProtocolDecode
```

Public 0.2.25 source paths are intentionally narrow:

- RTL-SDR USB and external `rtl_tcp` IQ capture.
- Imported PCAP/PCAPNG, IQ/raw samples, raw hex, rtl_433 JSON, 802.15.4 logs, and DeadScout sessions.
- 802.15.4 frame parsing/export, rtl_433 JSON records, raw IP packet metadata, spectrum/waterfall review, unknown burst analysis, reports, and local exports.

Additional radio/device-survey features remain private development work and are not part of this public tree.
