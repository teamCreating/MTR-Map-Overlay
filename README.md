# MTR Map Overlay

> **AI Agent / 多人协作必读**：[AGENTS.md](AGENTS.md)（协作协议与项目状态索引）、[walkthrough.md](walkthrough.md)（开发全导览）、[docs/agents/STATE.md](docs/agents/STATE.md)（当前权威状态）。

A unified Minecraft NeoForge 1.21.1 mod that displays [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) networks on your map - Create-train-map style. One jar supports every major map mod: **Xaero's World Map / Minimap** and **JourneyMap** are auto-detected, and whichever you have installed is what gets enabled (both can be used side by side, just like Create itself).

This is a standalone MTR-to-map overlay maintained by BenLi06. It reads MTR's own network data and draws directly on Xaero's World Map or JourneyMap; it has no integration with MTR Surveyor's map.

## Features

- **Map-Only Station Icons** — stations and platforms are drawn directly on fullscreen maps; they never enter waypoint lists, minimaps, compasses, or the in-world HUD
- **Full-Network Markers** — on modded servers, every station and platform in the dimension is available, not only nearby client data
- **Station + Platform Mode** — station icons stay compact; platform dots appear only when zoomed in, with route/destination information on hover
- **Map Path Layer** — MTR route lines are drawn directly on Xaero's World Map, Create-train-map style:
  - Colored polylines following each route's stop order (circular routes are closed)
  - Track layer: actual rail geometry (arcs & slopes) sampled along each rail, drawn as a dark underlay
  - Shared rails are rendered once as a stable, transverse multi-route rainbow ribbon; route colors no longer overwrite each other
  - Hover tooltips for stops (station name → destination) and route names
  - ROUTES / TRACKS toggle widgets in the top-left corner of the map (persisted in config)
  - Dimension-aware: only draws when the map view matches the data's dimension
- **Full-Network Sync** (install this mod on the server to unlock):
  - The server streams a snapshot of the whole MTR network (routes + sampled track geometry) per dimension
  - Path layer covers the entire network at any zoom, like Create's train map - not just the area around you
  - Snapshot refreshes automatically and can be forced with a command
  - Snapshot collection runs on MTR's simulator threads and is chunked in transit, safe for big networks
- **Client-Only Fallback** — without server installation, the path layer still renders whatever MTR synced to
  the client (within render distance of the player)
- **JourneyMap Landmarks** — station/depot markers (with fare zone + route tooltips, or per-platform markers
  in platform mode) rendered through the JourneyMap v2 API with per-transport-mode colored icons;
  auto-enabled when JourneyMap is installed, silent when it is not

## Requirements

| Mod | Required |
|-----|----------|
| Minecraft 1.21.1 | ✅ |
| NeoForge 21.1.x | ✅ |
| Minecraft Transit Railway 4.x | ✅ |
| Xaero's Minimap | ⚠️ Optional (legacy `[MTR]` waypoint cleanup only) |
| Xaero's World Map | ⚠️ Optional (recommended, enables the path layer; 1.40.11+) |
| MTR Map Overlay on the server | ⚠️ Optional (enables full-network view; client and server must both use the `mtrmap` mod ID) |

## Commands

All commands are client-side and work on any server:

| Command | Description |
|---------|-------------|
| `/mtrmap syncRoutes` | Request a full-network snapshot from the server |
| `/mtrmap syncLandmarks` | Force a JourneyMap landmark refresh |
| `/mtrmap testMarker` | Place a diagnostic marker at your position (JourneyMap) |
| `/mtrmap mode` | Show current display mode |
| `/mtrmap mode station` | Show station map icons only |
| `/mtrmap mode platform` | Show platform map icons only |
| `/mtrmap mode both` | Show station and platform map icons together |
| `/mtrmap config enabled <true/false>` | Enable/disable auto-sync |
| `/mtrmap config showStations <true/false>` | Show/hide station map icons |
| `/mtrmap config showPlatforms <true/false>` | Show/hide platform map icons |
| `/mtrmap config showDepots <true/false>` | Show/hide depot map icons |
| `/mtrmap config routeLines <true/false>` | Show/hide route lines on the world map |
| `/mtrmap config trackLines <true/false>` | Show/hide the track layer on the world map |

## Configuration

The config file is located at `.minecraft/config/mtrmap.toml`. On first launch, settings are copied from the legacy `mtrsurveyor.toml` file when it exists and the new file does not.

Key options:
- `enabled` — Master switch (default: `true`)
- `waypointMode` — Client-only fallback marker mode: `"station"`, `"platform"`, or `"both"` (default: `"both"`)
- `routeLinesEnabled` — Draw route lines on the world map (default: `true`)
- `trackLinesEnabled` — Draw the track layer on the world map (default: `true`)
- `networkSync.enabled` — Request full-network snapshots from modded servers (default: `true`)
- `networkSync.refreshIntervalSeconds` — Snapshot refresh interval (default: `300`)
- `showStationLandmarks` — Show station map icons (default: `true`)
- `showPlatformLandmarks` — Show platform map icons when zoomed in (default: `true`)
- `showDepotLandmarks` — Show depot map icons (default: `false`)
- `showEmptyStation` — Show stations with no routes (default: `false`)
- `debugLog` — Enable detailed sync logging (default: `false`)

## How the path layer works

Rendering hooks into `xaero.map.gui.GuiMap` (Xaero's World Map is closed-source with no overlay API,
the same approach Create itself uses). World coordinates are transformed with the map camera/scale so
geometry follows panning and zooming. Dimension ids use MTR's `namespace/path` world-id format.

Data resolution per dimension, in order of preference:
1. **Server snapshot** (mod installed on the server) - the whole network, collected from MTR's
   authoritative simulators and streamed to the client in chunks.
2. **MTR client data** (client-only) - MTR only syncs stations/routes/rails within render distance of
   the player, so this covers the explored area only.

Check the log line `Path layer render hook into Xaero's World Map is active` to confirm the mixin
applied; a `Xaero's World Map` update that moves internals will silently disable the layer (logged).

## License

This project is licensed under the MIT License.

## Author

**BenLi06**
