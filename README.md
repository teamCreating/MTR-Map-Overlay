<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay logo" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>Render Minecraft Transit Railway routes, physical tracks, live trains, and station arrival boards directly on your fullscreen map.</p>

  <p>
    <a href="README.md">English</a> · 
    <a href="README.zh-CN.md">简体中文</a> · 
    <a href="https://mtrmapoverlay.benli06.site/en/">Website &amp; Docs</a> · 
    <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3">v1.5.3 Pre-release</a> · 
    <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/snapshot">Snapshots</a>
  </p>

  <p>
    <a href="https://modrinth.com/project/ZU7SzyH7"><img src="docs/assets/badges/modrinth-cozy.svg" alt="Available on Modrinth" height="56"></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/mtr-map-overlay"><img src="docs/assets/badges/curseforge-cozy.svg" alt="Available on CurseForge" height="56"></a>
  </p>
</div>

**MTR Map Overlay** brings your [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) network into **Xaero's World Map** and **JourneyMap**. It reads MTR data directly without relying on MTR Surveyor, drawing physical tracks, color-coded transit lines, real-time train positions, and interactive station departure boards.

**Supported versions (`v1.5.3`):** Minecraft **1.20.1 / 1.20.4** (Forge & Fabric) and **1.21.1** (NeoForge & Fabric).

## Core Features

| Map Mod | Supported Overlay Features |
| :--- | :--- |
| **Xaero's World Map** | Physical rails, route ribbons, live train cars, station/platform/depot markers, and pinned station cards with live PIDS arrivals. |
| **JourneyMap** | Fullscreen rails, shared-route bands, live train cars, and station boards. Includes dedicated `TRACKS` and `ROUTES` toolbar toggles (supports JourneyMap 5 & 6). |

- **Clean Map-Only Markers:** Stations, platforms, and depots are rendered strictly on the fullscreen map. They never clutter your Xaero waypoint list, compass, minimap, or in-world HUD.
- **Readable Interchanges & Line Styles:** Multiple routes sharing a single track sit side-by-side in parallel color bands instead of overlapping. Light rail renders at 70% width, while high-speed lines feature alternating route-color and white blocks with a continuous border.
- **Whole-Network Live Trains:** When installed on both client and server, the map streams real-time train positions across the entire dimension (~4 Hz). Hovering over a train displays its vehicle model, route, destination, and consist length.
- **Interactive Station PIDS Boards:** Click any station to pin its info card, then click **Expand** to view live upcoming departures by destination, line, and platform. Supports both **British** (black/amber) and **European** (airport-style) themes via `/mtrmap config pidsStyle british|european`.
- **Client-Only Fallback:** Works out of the box on vanilla/unmodded MTR servers using nearby chunk data sent by MTR. Installing the mod on the server unlocks full-dimension snapshots, global live trains, and live station arrivals.

## What's New in v1.5.3

> **Release Status:** [v1.5.3](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3) is currently marked as a **pre-release** ([v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2) remains the latest stable build). Full-map live trains and station arrival boards require matching `1.5.3` builds on both client and server.

- **Pinned Stations & Live Arrivals:** Expand station cards to subscribe to live PIDS departure countdowns directly from MTR/TSC. Missing predictions are explicitly shown as `-` rather than `0 min`.Closing the map or collapsing the card automatically releases the network subscription.
- **Switchable PIDS Themes:** Toggle between `british` and `european` board styles; preferences persist across restarts.
- **Navigation Extension API (Java 17):** Exposes network catalogs, station-board subscriptions, and isolated map view sessions for third-party route planners. When a compatible planner mod is installed, clicking **Navigate** highlights upcoming route segments and dims completed or unused tracks. See the [Public API Guide](docs/api/overlay-api-v1.md) for `MtrMapApi` integration details.
- **UI & Decode Optimizations:** Station cards capture scroll and click events cleanly without dragging the underlying map, backed by bounded queues and background packet decoding.

## Requirements & Installation

| Minecraft | Loader | Java | MTR Version | Supported Map Mods (Pick one or both) |
| :--- | :--- | :--- | :--- | :--- |
| **1.21.1** | NeoForge 21.1.x / Fabric (+ Fabric API) | 21 | `4.1.0-beta.2` | Xaero's World Map `1.45.0+` / JourneyMap `6.0.8+` |
| **1.20.1** | Forge 47.x / Fabric (+ Fabric API) | 17+ | `>=4.0.5, <4.1` | Xaero's World Map `1.45.0+` / JourneyMap `6.0.6+` |
| **1.20.4** | Forge 49.x / Fabric (+ Fabric API) | 17+ | `>=4.0.5, <4.1` | Xaero's World Map `1.45.0+` / JourneyMap `5.10.0` |

*Note: Xaero's Minimap is not required; if present, it is only used to clean up legacy `[MTR]` waypoints from older releases.*

1. Download the JAR matching your exact Minecraft version and mod loader from [Releases](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3) and drop it into your client's `mods` folder.
2. Install the matching **MTR** build and at least one supported map mod (**Xaero's World Map** or **JourneyMap**). Fabric users must also install **Fabric API**.
3. *(Optional, Recommended)* Install the matching **MTR Map Overlay** JAR on the server to enable full-dimension route snapshots, whole-map live trains, and live station boards. *(Note: Both sides now use the `mtrmap` mod ID; remove any legacy `mtrsurveyor` JARs before upgrading.)*
4. Open your fullscreen map. Use the `ROUTES` and `TRACKS` buttons on the map toolbar (green indicator = enabled, red = disabled) or client commands to toggle layers.

## Commands & Configuration

All `/mtrmap` commands register on the **client** and work on any server:

| Command | Description |
| :--- | :--- |
| `/mtrmap syncRoutes` | Request a full-dimension network snapshot from the server. |
| `/mtrmap syncLandmarks` | Refresh JourneyMap landmark overlays. |
| `/mtrmap testMarker` | Place a diagnostic marker at your current position in JourneyMap. |
| `/mtrmap mode station\|platform\|both` | Set landmark display mode when running in client-only fallback mode. |
| `/mtrmap config enabled <true\|false>` | Master toggle for the entire map overlay. |
| `/mtrmap config routeLines <true\|false>` | Show or hide colored route ribbons. |
| `/mtrmap config trackLines <true\|false>` | Show or hide physical rail tracks. |
| `/mtrmap config trainCars <true\|false>` | Show or hide live trains (default: `true`). |
| `/mtrmap config showStations <true\|false>` | Show or hide station icons. |
| `/mtrmap config showPlatforms <true\|false>` | Show or hide platform icons. |
| `/mtrmap config showDepots <true\|false>` | Show or hide depot icons. |
| `/mtrmap config pidsStyle [british\|european]` | Switch or query the station departure board theme (default: `british`). |

**Configuration Files:**
- **NeoForge / Forge:** Stored in `config/mtrmap.toml` (automatically migrates settings from legacy `mtrsurveyor.toml` on first launch).
- **Fabric:** Stored in `config/mtrmap.properties`.
- Key settings include `networkSync.enabled` (default `true`), `networkSync.refreshIntervalSeconds` (default `300`), and layer visibility defaults.

## Technical Notes & Validation

- **Rendering & Caching:** Both map integrations render fixed 2D layers from bottom to top: physical rail backing → colored routes → live train cars → station/platform icons. Static rail/route geometry is cached separately from dynamic train poses with viewport prefetching (see [planar cache validation](docs/validation/planar-cache.md)).
- **Network Protocol:** Full-map train sync uses compact 24-byte per-car coordinate frames relative to a shared train origin, avoiding repeated string metadata (see [global train validation](docs/validation/global-trains.md)). In singleplayer, Xaero keeps the integrated server unpaused while the live train layer is active so train movement continues updating.
- **Known Limitations:** In `1.5.3` validation, Forge 1.20.4 singleplayer quick-play experienced a disconnect during Xaero server-data initialization (dedicated server tests passed). See [1.5.3 validation notes](docs/validation/pids-and-dimmed-1.5.3.md) and [v1.5.3 release notes](docs/releases/v1.5.3.md) for test coverage details.

## Building from Source

Requires a **Java 21** toolchain. The `main` branch targets **Minecraft 1.21.1**; for `1.20.1` and `1.20.4` builds, switch to the [`compat/mtr-4.0-mc-1.20`](https://github.com/teamCreating/MTR-Map-Overlay/tree/compat/mtr-4.0-mc-1.20) branch.

| Loader | Windows | macOS / Linux | Output Artifact |
| :--- | :--- | :--- | :--- |
| **NeoForge** | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.5.3.jar` |
| **Fabric** | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.5.3.jar` |

### Architecture Overview

Shared and NeoForge sources live under [`src/main/java/io/github/teamcreating/mtrmap`](src/main/java/io/github/teamcreating/mtrmap), while [`fabric/`](fabric) provides Fabric-specific entry points and hooks that compile against the shared codebase.

| Package / Directory | Responsibility |
| :--- | :--- |
| [`mapdata/`](src/main/java/io/github/teamcreating/mtrmap/mapdata) | `MapDataCache` manages server snapshots and nearby client fallback data. `TrackSampler` samples rail geometry, `TrackRoutePalette` assigns non-overlapping color bands, and `MapTrack` handles viewport culling bounds. |
| [`network/`](src/main/java/io/github/teamcreating/mtrmap/network) | Handles chunked snapshot transfer (`NetworkSnapshotCodec`, `NetworkChunkAssembler`), background simulator polling (`ServerNetworkCollector`), live train frames, and station PIDS subscriptions. |
| [`integration/xaero/`](src/main/java/io/github/teamcreating/mtrmap/integration/xaero) | `XaeroRouteRenderer` draws tracks, route ribbons, trains, and interactive station cards in Xaero world-map coordinates. |
| [`integration/journeymap/`](src/main/java/io/github/teamcreating/mtrmap/integration/journeymap) | JourneyMap v2 plugin (`JourneyMapToolbar`, `JourneyMapScreenProjection`, `JourneyMapPathManager`, `JourneyMapForegroundRenderer`). |
| [`mixin/`](src/main/java/io/github/teamcreating/mtrmap/mixin) | Accessors for MTR internal data and Xaero world-map render hooks. |
| [`config/`](src/main/java/io/github/teamcreating/mtrmap/config) | Loader-specific config serialization, client command registration, and networking bootstrap. |

## Troubleshooting

- **Only nearby stations or tracks appear:** The server does not have MTR Map Overlay installed (or versions are mismatched), so the client is falling back to MTR's local sync radius.
- **Lines do not appear on Xaero's World Map:** Verify Xaero's **World Map** (not just Minimap) is installed and check `latest.log` for `Path layer render hook into Xaero's World Map is active`.
- **No waypoints on the minimap:** By design. All icons are rendered exclusively on the fullscreen map to prevent HUD clutter.
- **Upgrading from `mtrsurveyor`:** Delete the old `mtrsurveyor` JAR from `mods/`. The mod ID and command prefix are now `mtrmap` and `/mtrmap`.

## Support Development

If MTR Map Overlay makes managing your transit network easier, consider supporting development on Ko-fi or Afdian:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Z8C424REOO)
<a href="https://afdian.com/a/benli06"><img src="docs/assets/badges/afdian.svg" alt="Support me on Afdian · 爱发电" height="30"></a>

## License & Attribution

Licensed under the [MIT License](LICENSE). Original copyright and attribution for AmberFrost's contributions are preserved in `LICENSE` and the Git commit history; subsequent development is maintained by BenLi06.
