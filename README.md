# Hotspot Control Pro — Advanced Android Tethering Manager

**Hotspot Control Pro** is a native Kotlin & Jetpack Compose Android application designed for smartphone owners who share internet connectivity (via SIM 4G/5G/LTE Mobile Data or Wi-Fi Internet Sharing) and want granular visibility, bandwidth shaping, data package allocation, and access control over connected hotspot clients.

---

## 1. Architecture & Operating Modes

### Unrooted Standard Mode (Default)
- **Upstream Source Detection**: Uses `ConnectivityManager` and `NetworkCapabilities` to automatically detect whether the phone is receiving internet via **SIM Mobile Data (`rmnet_data*`)** or **Wi-Fi Sharing (`wlan0`)**.
- **Downstream Hotspot Interface Discovery**: Enumerates `NetworkInterface` entries (`ap0`, `wlan1`, `swlan0`, `softap0`, `rndis0`) and identifies the active hotspot subnet (`/24` IPv4 and IPv6 link addresses).
- **Multi-Strategy Client Discovery**: Combines `/proc/net/arp` & `ip neigh show` inspection (where permitted by the OS), multi-coroutine ICMP + TCP handshake subnet sweeps across the hotspot DHCP range, and active socket tracking.
- **Unrooted Local Hotspot Gateway Server (`UnrootedGatewayServer`)**:
  - Runs a multi-threaded HTTP/HTTPS CONNECT & TCP relay gateway on the phone's hotspot IP (default port `8888`).
  - Enforces **per-client internet blocking** (Permanent, Temporary with auto-expiry, Paused, Scheduled, and Quota-Exhausted) without disconnecting other clients or affecting the owner's own internet.
  - Enforces **independent download & upload speed limits** using a real per-client **Token-Bucket Stream Shaper** (`100 KB/s` up to `10 MB/s` or custom KB/s) with High/Normal/Low priority multipliers and global fair sharing.
  - Tracks **real per-client downloaded and uploaded bytes** to power the Data Allocation & Quota Exhaustion engine.

### Root Mode (`su` + `iptables` / `ip6tables` / `tc`)
- When executed on a rooted Android device and switched to **Root Mode** in the Capabilities screen, `EnforcementEngine` applies validated `iptables` / `ip6tables` `FORWARD` chain rules on the downstream tethering interface and attaches Linux `tc` `htb` classes per client IP, automatically restoring rules when stopped.

---

## 2. Feature Compatibility Report

| Feature | Unrooted Android 8–16 (Standard + Local Gateway) | Shizuku Mode | Root Mode (`su`) |
| :--- | :--- | :--- | :--- |
| Upstream Source Detection (SIM vs Wi-Fi) | Supported natively | Supported | Supported |
| Downstream Interface & Subnet Detection | Supported natively | Supported | Supported |
| Active Subnet Client Discovery | Supported (ICMP/TCP Sweep + Gateway) | Supported + Neighbor Table | Supported + Full ARP |
| Per-Client Internet Blocking | Enforced via Local Hotspot Gateway `:8888` | Gateway `:8888` | Transparent Kernel `iptables`/`ip6tables` |
| Independent Download/Upload Speed Limits | Enforced via Gateway Token-Bucket Shaper | Gateway Token-Bucket | Kernel `tc qdisc htb` + Gateway |
| Per-Client Data Quotas & Auto-Cutoff | Supported (Gateway + Socket Byte Counters) | Supported | Supported |
| Direct Programmatic System Hotspot Toggle | Opens System Tethering Settings (`TETHER_PRIVILEGED` restriction) | System Settings Shortcut | Supported via root service call |

---

## 3. Build & Installation Instructions

### Build Debug & Release APKs
```bash
gradle assembleDebug
gradle assembleRelease
```

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
