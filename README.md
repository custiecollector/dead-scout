# DeadScout

DeadScout is a standalone RF/SDR capture and packet-analysis application. Version **0.2.25** is the current public Android and desktop release.

## Public feature scope

This public build focuses on:

- RTL-SDR USB and external `rtl_tcp` capture paths for IQ/waterfall/audio review.
- Local capture import for PCAP, PCAPNG, IQ/raw samples, rtl_433 JSON, raw hex, 802.15.4 logs, and DeadScout session data.
- Packet review with display filters, protocol hierarchy, endpoint summaries, findings, hex/ASCII inspection, reports, and exports.
- 802.15.4 sniffer import/reader support, rtl_433 JSON decoding, unknown burst analysis, and SDR-oriented planning.
- Desktop packages for Linux and Windows with app launch scripts only; no third-party packet-driver bootstrap is bundled in the public Windows package.

The public release intentionally omits wireless device discovery, short-range adapter capture, mobile survey, private scan surfaces, and external Windows packet-driver setup. Those lines remain private development work.

## Privacy and permissions

DeadScout does not include telemetry, advertising, or account sign-in. Internet permission is present for explicit operator actions such as `rtl_tcp` or user-selected network imports. Foreground service permission supports user-started local capture monitors.

## Use

1. Download the APK or desktop package from GitHub Releases.
2. Start with Import, RTL-SDR USB, external `rtl_tcp`, or 802.15.4/sniffer input.
3. Review decoded packets, signal observations, reports, and exports locally.
