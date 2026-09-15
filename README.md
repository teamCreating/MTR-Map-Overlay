# [CRTools] MTR:Xaero Mapper

> **AI Agent / 多人协作必读**：[AGENTS.md](AGENTS.md)（协作协议与项目状态索引）、[walkthrough.md](walkthrough.md)（开发全导览）、[docs/agents/STATE.md](docs/agents/STATE.md)（当前权威状态）。

A unified Minecraft NeoForge 1.21.1 mod that displays [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) networks on your map - Create-train-map style. One jar supports every major map mod: **Xaero's World Map / Minimap** and **JourneyMap** are auto-detected, and whichever you have installed is what gets enabled (both can be used side by side, just like Create itself).

## Features

- **Automatic Waypoint Sync** — MTR stations and depots appear as Xaero waypoints automatically
- **Station Mode** — One waypoint per station, showing full station name
- **Platform Mode** — One waypoint per platform, showing platform number with route and destination info on hover
- **Correct Altitude** — Waypoints are placed at actual platform level, not underground
- **Map Path Layer** — MTR route lines are drawn directly on Xaero's World Map, Create-train-map style:
  - Colored polylines following each route's stop order (circular routes are closed)
  - Track layer: actual rail geometry (arcs & slopes) sampled along each rail, drawn as a dark underlay
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
| Xaero's Minimap | ⚠️ Optional (enables waypoint sync) |
| Xaero's World Map | ⚠️ Optional (recommended, enables the path layer; 1.40.11+) |
| This mod on the server | ⚠️ Optional (enables full-network view) |

## Commands

All commands are client-side and work on any server:

| Command | Description |
|---------|-------------|
| `/mtrsurveyor syncWaypoints` | Force a waypoint sync |
| `/mtrsurveyor syncRoutes` | Request a full-network snapshot from the server |
| `/mtrsurveyor syncLandmarks` | Force a JourneyMap landmark refresh |
| `/mtrsurveyor testMarker` | Place a diagnostic marker at your position (JourneyMap) |
| `/mtrsurveyor mode` | Show current display mode |
| `/mtrsurveyor mode station` | Switch to station mode (one waypoint per station) |
| `/mtrsurveyor mode platform` | Switch to platform mode (one waypoint per platform) |
| `/mtrsurveyor config enabled <true/false>` | Enable/disable auto-sync |
| `/mtrsurveyor config showStations <true/false>` | Show/hide station waypoints |
| `/mtrsurveyor config showDepots <true/false>` | Show/hide depot waypoints |
| `/mtrsurveyor config routeLines <true/false>` | Show/hide route lines on the world map |
| `/mtrsurveyor config trackLines <true/false>` | Show/hide the track layer on the world map |

## Configuration

The config file is located at `.minecraft/config/mtrsurveyor.toml`.

Key options:
- `enabled` — Master switch (default: `true`)
- `waypointMode` — Display mode: `"station"` or `"platform"` (default: `"station"`)
- `routeLinesEnabled` — Draw route lines on the world map (default: `true`)
- `trackLinesEnabled` — Draw the track layer on the world map (default: `true`)
- `networkSync.enabled` — Request full-network snapshots from modded servers (default: `true`)
- `networkSync.refreshIntervalSeconds` — Snapshot refresh interval (default: `300`)
- `showStationLandmarks` — Show station waypoints (default: `true`)
- `showDepotLandmarks` — Show depot waypoints (default: `false`)
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

**BenLi06** — Based on [mtrsurveyor](https://github.com/AmberIsFrozen/mtrsurveyor) by AmberFrost
