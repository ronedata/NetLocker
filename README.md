# NetLocker

A per-app Wi-Fi / Mobile Data firewall for Android — **no root, no Shizuku, no remote
server.** You choose, per installed app, whether it can use Wi-Fi, Mobile Data, both,
or neither, and NetLocker enforces it live.

This README leads with the honest technical story (what's actually possible on stock
Android/Samsung, and why), because that's what the rest of the code is built around.

---

## 1. What NetLocker is

NetLocker lets you individually allow/block **Wi-Fi** and **Mobile Data** for every
installed app, without rooting the phone or installing Shizuku/ADB tooling. You get:

- **Apps tab** — every installed app with quick Wi-Fi/Mobile-Data switches, category
  chips (All / Games / Social / System), search, and a loading state while the first scan
  of installed apps finishes
- **Rules tab** — only the apps you have a rule for, with status filters (Blocked /
  Wi-Fi Only / Mobile Only / Allowed), search, and per-rule Edit / Disable / Delete. Add a
  rule from a picker of apps that don't have one yet
- A details screen per app showing its exact current access state
- Rules that persist across app restarts and device reboots
- Honest status reporting — a rule is never shown as "applied" unless it actually is: the
  firewall banner and each rule's "Enforced" / "Not enforced" label come from the real VPN
  state, not from what is saved

**Apps and Rules share one Room table**, so a switch flipped on either tab appears on the
other immediately. A rule exists for an app if it has a row; deleting the row returns the
app to default (fully allowed). **Disabling** a rule keeps its saved Wi-Fi/Mobile values
but stops enforcing it — the app behaves exactly as if it had no rule (verified on a real
device: a disabled "Blocked" rule produced zero firewall decisions and the app went
online; re-enabling it resumed dropping its traffic).

The database is at **schema v5** (v2 added `isEnabled` and `createdAt`; v3 adds the
`blocked_stats` table; v4 adds the per-rule schedule window; v5 adds which days of the
week it applies to). Upgrading keeps every saved rule via explicit migrations — there is
deliberately no destructive fallback, since silently wiping a firewall's rules would be a
security regression.

**Schedule.** Settings has an off-by-default "Schedule" master switch; only while it's on
does each rule show a "Schedule" section (Edit Rule, Add Rule, App Details) to block that
app during a time window, optionally repeating only on chosen days (default: every day).
Turning the master off again doesn't delete any app's schedule, it just stops enforcing it
— the same "paused, not lost" pattern as a disabled rule. An app with a live schedule stays
inside the VPN tunnel even outside its block window (it can't use the zero-overhead
excluded-app path), so the small relay cost is paid only by apps a user opts into
scheduling.

**Blocked attempts.** While the firewall runs, each connection a rule blocks is counted per
app per day and shown on the Rules cards and App Details ("N blocked attempts today"). A
"blocked attempt" is one new flow (TCP SYN / UDP flow, including DNS lookups): retransmits
of the same flow are counted once per 30 seconds, so a retrying app doesn't inflate the
number. Only flows attributed to an app with a restrictive rule are counted; counts are kept
for 7 days.

**Keeping the firewall running.** A Quick Settings tile toggles the firewall (it reflects the
real status, and opens the app if the VPN permission hasn't been granted yet). The optional
"Start when phone turns on" setting restarts it after a reboot or an app update, only if
the VPN permission is still granted. Android's own "Always-on VPN" is linked from Settings
but has not been verified with NetLocker on a device.

## 2. Why this needs a local VPN (read this before anything else)

Android sandboxes every app into its own UID. The only APIs that can change **another
app's** network permissions (`NetworkPolicyManager.setUidPolicy`, iptables/netd rules)
require a **signature-level permission** (`MANAGE_NETWORK_POLICY`) or **root** — neither
of which a normal, sideloaded/Play app can obtain. This is a deliberate Android security
boundary, not an oversight, and there is no way around it without root or an MDM
Device-Owner provisioning flow (which requires a factory reset to set up — impractical
on a personal phone).

The one public, documented API that lets a third-party app see and gate *other* apps'
traffic without root is **`VpnService`**. NetLocker uses it as a **local firewall**: it
builds a virtual network interface on the device, and for apps it needs to restrict, it
inspects their packets and decides per-packet whether to forward them — all on-device,
with **no remote server, no data ever leaving the phone**.

Consequence you cannot avoid on stock Android: the OS will show the standard 🔑 "VPN
connected" status-bar icon and system notification whenever NetLocker's firewall is
active. This is not a bug and cannot be hidden — it's how Android tells the user *any*
app is intercepting traffic, and that's the correct, honest signal for what NetLocker
is doing.

**Apps you leave fully allowed (Wi-Fi + Mobile Data both on) never enter the tunnel at
all** — they're excluded via `VpnService.Builder.addDisallowedApplication()`, so their
traffic is completely unaffected, at native speed, with zero NetLocker overhead.

## 3. Architecture actually used

```
NetLocker
 ├── ui/            Jetpack Compose (Material 3) screens + ViewModels
 ├── domain/        Models, repository interfaces, use cases (pure Kotlin)
 ├── data/          Room (rules) + PackageManager (installed apps)
 └── network/       The firewall itself
      ├── NetLockerVpnService   the VpnService, foreground notification, tunnel lifecycle
      ├── FirewallEngine        reads the tun device, makes the allow/drop decision
      ├── TransportMonitor      tracks live Wi-Fi/Cellular Network handles independently
      ├── ConnectionOwnerResolver  packet -> owning app, via getConnectionOwnerUid()
      ├── RuleIndex             fast uid -> rule lookup for the packet hot path
      ├── packet/               IPv4/TCP/UDP header parsing + checksums
      └── relay/                UDP + TCP session relay for partially-restricted apps
```

### How a packet is actually handled

| App's rule | What happens | Reliability |
|---|---|---|
| **Wi-Fi ✓ + Mobile ✓** (fully allowed) | Excluded from the tunnel entirely — normal OS routing, NetLocker never sees its traffic | Guaranteed, zero risk |
| **Wi-Fi ✕ + Mobile ✕** (fully blocked) | Packets enter the tunnel and are simply never forwarded | Guaranteed |
| **Only one of Wi-Fi/Mobile allowed** | The flow is relayed through a real socket bound to *that specific* transport's `Network` object (via `TransportMonitor` + `Network.bindSocket()`); if that transport isn't currently up, it's blocked | **Best-effort** — see §7 |

`TransportMonitor` holds independent `Network` handles for Wi-Fi and Cellular (via two
parallel `registerNetworkCallback` requests), not just "whatever the phone currently
defaults to". That means a "Wi-Fi-only" app really is routed over Wi-Fi specifically —
even if, say, cellular happened to be the system default at that moment — as long as
Wi-Fi is actually connected.

## 4. Supported Android versions / Samsung compatibility

| | Status |
|---|---|
| **minSdk** | 29 (Android 10 / One UI 2.0+) |
| **targetSdk / compileSdk** | 36 (Android 16) |
| Android 13, 14, 15, 16 | Supported |
| Samsung One UI (2.0 and newer) | Supported — standard AOSP APIs, not modified by One UI |
| Samsung battery management ("Put unused apps to sleep", Sleeping/Deep sleeping apps) | Can kill the background firewall service. **You must exclude NetLocker from battery optimization** (Settings → Apps → NetLocker → Battery → Unrestricted) for reliable always-on enforcement. This is a Samsung OS behavior NetLocker cannot override from inside the app (documented widely at dontkillmyapp.com). |
| Knox-managed / enterprise-restricted devices | If your organization's MDM sets `DISALLOW_CONFIG_VPN`, NetLocker detects this (`VpnSupportChecker`) and shows an honest "not supported on this device" screen instead of silently failing |

**Why minSdk 29:** per-app UID attribution relies on
`ConnectivityManager.getConnectionOwnerUid()` (API 29+). Below that, direct `/proc/net`
inspection would be the only fallback, and that's been access-restricted for
non-privileged apps since Android 11 anyway — there's no reliable non-root path below
API 29.

## 5. Permissions required

| Permission | Why |
|---|---|
| `BIND_VPN_SERVICE` (system-granted via consent dialog, not requested in-app) | The firewall tunnel itself |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Relaying traffic, watching Wi-Fi/Cellular availability |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the firewall alive in the background |
| `POST_NOTIFICATIONS` (Android 13+) | Shows the "firewall active" status notification — requested explicitly, right before you enable the firewall, never silently |
| `RECEIVE_BOOT_COMPLETED` | Only used by the optional "Start when phone turns on" setting |
| `PACKAGE_USAGE_STATS` ("Usage access", granted by you in system settings) | Shows an app's data use today on its details page |
| `QUERY_ALL_PACKAGES` | Lists *all* installed apps, not just ones NetLocker declares an intent filter for. This is a Google Play "sensitive permission" requiring a declaration form — see §9 |

NetLocker never requests a permission without a visible reason shown first (spec
requirement: no silent permission requests, no fake success if one is denied).

## 6. VPN required?

**Yes — see §2.** There is no non-VPN, non-root, non-Shizuku way to do this on stock
Android. NetLocker uses `VpnService` purely as a local packet gate; no VPN server,
no remote endpoint, no traffic leaves your device for any reason.

## 7. Root required?

**No.** NetLocker never requests root and contains no root-only code path.

## 8. Shizuku required?

**No.** Shizuku/ADB access was evaluated (see the original feasibility discussion) and
rejected as the primary path: it cannot express a "Wi-Fi off, Mobile Data on" rule (the
relevant shell-level network-policy commands are metered-data-oriented, with no Wi-Fi
equivalent), its exact command surface varies by Android version/OEM, and it requires
non-trivial one-time setup (wireless debugging or a PC) that most users won't do. It
remains a possible *future* optional enhancement, not something this build depends on.

## 9. Known limitations (read before relying on this in production)

- **The partial-restriction relay is best-effort, not a full TCP/IP stack.** See
  `network/relay/TcpNatSession.kt`'s doc comment for the exact scope: correct 3-way
  handshake/teardown and cumulative ACKs, but single-outstanding-segment
  ("stop-and-wait") flow control, no SACK, no window scaling, no reassembly buffer.
  Expect **reduced throughput on large transfers** for apps in "Wi-Fi only" or "Mobile
  Data only" mode. Fully-allowed and fully-blocked apps have none of these caveats.
- **IPv4 only.** An IPv6-only mobile carrier path (some operators' 464XLAT setups) will
  not be attributed or relayed by the current engine. This is a real gap, not
  theoretical — worth verifying on your specific carrier.
- **ICMP (ping) is not attributed** — `getConnectionOwnerUid()` only supports TCP/UDP,
  so ICMP from an app inside the tunnel (partial/blocked apps) is dropped rather than
  guessed at.
- **The 🔑 VPN icon is unavoidable** (see §2), and NetLocker cannot run alongside
  another VPN app — Android only allows one active `VpnService` at a time.
- **Samsung background-kill behavior** (§4) can silently stop enforcement unless you
  disable battery optimization for NetLocker.
- **Google Play distribution is uncertain.** Play's Developer Policy requires a
  "Prominent Disclosure" declaration for VPN-permission apps, and historically similar
  local-firewall apps (e.g. NetGuard) have not been distributed via Play, only via
  GitHub/F-Droid-style sideloading. Budget for this if Play distribution matters to you.
  Because of this, NetLocker ships its own **Settings → Check for updates**: it checks
  this repo's [latest GitHub Release](https://github.com/ronedata/NetLocker/releases/latest)
  and, if newer, downloads the APK (`DownloadManager`) and hands it to the system
  Package Installer (`ACTION_VIEW`) — the same "Install unknown apps" consent and
  install confirmation screens you'd see installing any sideloaded APK by hand. No
  silent installation; Android does not allow that for a non-privileged app, and
  NetLocker doesn't try to.
- **Rule changes take effect immediately for the exact case that matters most**
  (blocking/unblocking), including tearing down already-open connections for a
  just-restricted app (`FirewallEngine.invalidateSessionsForUid`) — but toggling an app
  *into or out of* "fully allowed" briefly rebuilds the whole tunnel
  (`NetLockerVpnService.reconfigureAndEstablish`), which very briefly interrupts other
  *partially-restricted* apps' in-flight relayed connections (fully-allowed apps are
  never affected, since their traffic never touches the tunnel).

## Build verification (what has actually been run, not just written)

This project was built and tested end-to-end outside Android Studio, using a manually
installed Android SDK (cmdline-tools, platform 36, build-tools 36.0.0) and Gradle 8.9:

- `gradle test` — **all unit tests pass** (checksum/packet round-trips, rule logic,
  use-case combining, `RuleIndex` uid→rule snapshot building)
- `gradle assembleDebug` — **succeeds**, producing a real, installable `app-debug.apk`
  (manifest merge, resource compilation, Room/KSP annotation processing, the Compose
  compiler, and D8 dexing all completed without error)

Two real bugs were found and fixed this way (not hypothetical — both reproduced with
a real compiler/test run before being fixed):
1. Kotlin backtick test names containing `->` are illegal JVM method names (`>` isn't
   allowed) — renamed to plain English.
2. Two test files each declared a top-level `private class FakeNetworkRuleRepository`
   in the same package — Kotlin/JVM still requires unique class names per package
   regardless of the `private` modifier, causing a redeclaration clash. Renamed one.
3. A `backgroundScope`-launched flow collector was not being flushed by
   `advanceUntilIdle()` in this AGP/kotlinx-coroutines-test combination (confirmed with
   a minimal, isolated repro before assuming it was environment-specific rather than a
   real logic bug); `runCurrent()` reliably flushes it and was used instead.

**What this does *not* verify:** actual runtime behavior on a device or emulator — the
VPN permission dialog, the tun interface actually intercepting traffic, per-app
enforcement working against a real Wi-Fi/cellular radio, Samsung One UI's background
behavior. None of that can be exercised without a real device/emulator, which this
environment doesn't have. That's still on you — see the manual checklist below.

## 10. Build instructions

1. Android Studio (a recent stable release with AGP 8.7+/Kotlin 2.1 support).
2. Open the project root (`E:\AI Project\NetLocker`) — it's a standard Gradle project,
   no special setup.
3. `gradle/wrapper/gradle-wrapper.properties` (pointing at Gradle 8.9) is included, but
   the wrapper's binary jar/`gradlew`/`gradlew.bat` launcher scripts are not — this
   repo was assembled outside Android Studio, which can't emit that binary. On first
   open, Android Studio will offer to generate/repair the wrapper automatically; if it
   doesn't, run `gradle wrapper` once from a system-installed Gradle to create it.
4. Let Gradle sync (it will fetch the AndroidX/Compose/Room dependencies declared in
   `gradle/libs.versions.toml`).
5. Run on a device or emulator running **Android 10 (API 29) or newer**. A real device
   is strongly recommended for anything network-related — see §11.

## 11. Testing instructions

### Automated (run in Android Studio or `./gradlew test`)

Pure-JVM unit tests cover the parts that don't need a real device/network stack:

- `NetworkRuleTest` — the four Wi-Fi/Mobile-Data combinations map to the correct
  `NetworkAccessState` (spec Tests 1–4)
- `ChecksumTest`, `ParsedPacketTest` — IPv4/TCP/UDP header build+parse round-trips and
  the RFC 1071 checksum self-consistency property
- `RuleIndexTest` — uid→rule snapshot building and live updates
- `ObserveAppsWithRulesUseCaseTest`, `UpdateNetworkRuleUseCaseTest` — app-list
  sorting/defaulting and the persist-then-notify-firewall flow

**These were written and reasoned through carefully, but this conversation has no
Android device/emulator to actually execute `./gradlew test` against — please run them
yourself and treat that as the real first checkpoint before trusting anything below.**

### Manual, on-device (spec Tests 5–8 — these need a real phone, not a unit test)

1. **Reboot test:** set a rule, reboot the phone, confirm the rule is still shown
   correctly (Room persists it; note the firewall itself does **not** auto-start after
   reboot unless you open the app and re-grant/re-confirm — NetLocker does not use a
   `BOOT_COMPLETED` receiver to silently restart a VPN without you present, matching the
   "no silent permission use" rule).
2. **App restart test:** kill NetLocker from Recents, reopen, confirm rules and
   firewall status are exactly as left.
3. **Network-switch test:** with an app set to "Wi-Fi only", start a download, then
   turn off Wi-Fi mid-transfer — confirm it stops (falls back to blocked, not silently
   switching to mobile). Then reverse it for a "Mobile Data only" app.
4. **Samsung One UI test:** verify the battery-optimization exemption prompt/flow
   works as described in §4, and that the firewall survives at least a few hours in
   the background with the screen off.

## Project status

This is a from-scratch implementation, not a fork of an existing firewall — the
enforcement engine (packet parsing, checksum, relay sessions) is original code written
for this project, scoped and documented as described above.
