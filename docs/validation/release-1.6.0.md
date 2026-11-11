# 1.6.0 release acceptance — held

> Startup blocker subsequently repaired and verified on a new binary: [Forge 1.20.4 fix](forge1204-startup-fix.md). This remains the historical acceptance of Candidate42879095868, which does not contain that repair.

Tester: Codex. Recorded 2026-11-11T03:28:00+08:00 (UTC+08:00). **No formal release or v1.6.0 tags were created.**

## Candidate identity

- [Candidate 42879095868, attempt 1](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42879095868) passed all six builds, tests and package checks.
- Manifest SHA-256: `7ae5c58b18c481d644a686dba92d254ec8fd5d47ef7755f4d90a175b8812a9f0`.
- Main source: `307cfcd75b46eaa0ccb4bbe0bef962267595cbb0`; compat source: `9869d0d3009db1b95a9ad84f997cea52c7d380ae`.
- [Sealed manifest](evidence/release-1.6.0/release-manifest.json) and [six JAR checksums](evidence/release-1.6.0/SHA256SUMS).
- [Publish dry-run 42883569831](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42883569831) passed integrity selection only. The real publication workflow was not dispatched.

## Production singleplayer checks

| Minecraft | Loader | MTR | Java tests passed | Native UI cases |
|---|---|---|---|---|
| 1.21.1 | neoforge 21.1.249 | 4.1.0-beta.2 | 107 | 8/8 |
| 1.21.1 | fabric 0.16.14 | 4.1.0-beta.2 | shared with NeoForge | 8/8 |
| 1.20.1 | forge 47.4.0 | 4.0.5 | 110 | 8/8 |
| 1.20.1 | fabric 0.16.14 | 4.0.5 | 110 | 8/8 |
| 1.20.4 | forge 49.2.0 | 4.0.5 | 110 | 8/8 |
| 1.20.4 | fabric 0.16.14 | 4.0.5 | 110 | 8/8 |

Each target loaded its downloaded sealed production JAR, not Gradle development classes. Acceptance checked both the candidate SHA-256 and actual protection-domain code-source archive, including Forge/NeoForge SecureJar URI normalization. The separate QA JAR is never part of release assets. Mod/QA dependency hashes and eight-case result files are retained per target.

All 48 normal-flow cases used actual singleplayer MTR fixtures, native map screens, KeyboardHandler/MouseHandler input, resource reloads and raw framebuffer captures: Xaero/JourneyMap × en_us/zh_cn/zh_tw/ja_jp. Checks cover rendered station selection, collapsed/expanded boards, translated labels, unknown-station rejection, Navigate readiness, native arrivals and advancing server timestamps while maps stay unpaused. Player-defined names are preserved. A native LIMITED board is expected for fixture routes without complete predictions; the UI displays that status instead of inventing arrivals.

The five profiles without a navigation provider show a disabled Navigate action and ignore its click. NeoForge includes actual HowToGo 0.2.1 and MTR Navigation: both providers are available; the chooser invokes HowToGo and its public target contains the native station name with MTR display separators (`|`) converted to spaces, floored block coordinates and `mtrmap` source, with the map closed. This tests destination handoff, not HowToGo road-route search or guidance; no road graph was built.

## Release blocker: Forge 1.20.4 quick-play

Normal title-screen → Singleplayer → copied save passes. Starting the same profile via `--quickPlaySingleplayer Overlay160Test` disconnects with `Error loading server minimap properties. Please retry.` Both Xaero mods dereference null server data during player join. Reproduced on Forge 49.2.0 and the current recommended 49.2.13, with the exact candidate. The failure also occurs in a plain control containing only MTR, JourneyMap and the two Xaero mods, without Overlay or the QA add-on. Updating Xaero to World Map 1.47.0 / Minimap 26.6.0 also did not fix it; release dependencies were not changed.

- [Forge 49.2.0 failed state](evidence/release-1.6.0/quickplay-forge1204/final.json) and [runtime log](evidence/release-1.6.0/quickplay-forge1204/launcher.log).
- [Forge 49.2.13 failed state](evidence/release-1.6.0/quickplay-forge49213/final.json) and [runtime log](evidence/release-1.6.0/quickplay-forge49213/launcher.log).
- [Control log without Overlay/QA](evidence/release-1.6.0/control-without-overlay.log) and [four-mod inventory](evidence/release-1.6.0/control-mod-inventory.json).

Observed lifecycle evidence: normal startup records Forge server events and both Xaero data objects initialized by ServerStarted; quick-play records no server lifecycle callbacks. The official Forge patch invokes onResourceLoadFinished before completeModLoading, whose implementation starts the event bus. This ordering is consistent with quick-play entering the world before event dispatch is enabled; it is an inference from source plus runtime evidence, not a product fix. See pinned [Minecraft startup patch](https://github.com/MinecraftForge/MinecraftForge/blob/e00bddd6d8311b4e9e70432fe572e3f40c8b3b52/patches/minecraft/net/minecraft/client/Minecraft.java.patch) and [ClientModLoader](https://github.com/MinecraftForge/MinecraftForge/blob/e00bddd6d8311b4e9e70432fe572e3f40c8b3b52/src/main/java/net/minecraftforge/client/loading/ClientModLoader.java). [Forge download page](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.4.html) identifies 49.2.13 as recommended at test time.

No forced event-bus start, synthetic lifecycle event, private Xaero data injection, swallowed exception, or patched dependency JAR was added. A read-only class-load probe and focused JDK/Vineflower inspection confirmed native Forge hooks are present. REA tooling was unavailable; those observations are not represented as REA output.

The user's publication condition remains unmet. Resolve and rerun this failing startup path, or obtain an explicit revised acceptance scope, before dispatching real Publish. Candidate checklist items for new dedicated-server/client-only-fallback and large-network runs were not revalidated by this singleplayer matrix and are not marked complete. Prior-version checks are not substituted for exact-candidate checks.

## Selected raw captures

- [1.21.1-neoforge: en_us-xaero-handoff](evidence/release-1.6.0/1.21.1-neoforge/en_us-xaero-handoff.png)
- [1.21.1-neoforge: zh_cn-journeymap-board](evidence/release-1.6.0/1.21.1-neoforge/zh_cn-journeymap-board.png)
- [1.21.1-neoforge: en_us-journeymap-chooser](evidence/release-1.6.0/1.21.1-neoforge/en_us-journeymap-chooser.png)
- [1.21.1-neoforge: en_us-journeymap-handoff](evidence/release-1.6.0/1.21.1-neoforge/en_us-journeymap-handoff.png)
- [1.21.1-fabric: zh_tw-xaero-board](evidence/release-1.6.0/1.21.1-fabric/zh_tw-xaero-board.png)
- [1.21.1-fabric: en_us-journeymap-collapsed](evidence/release-1.6.0/1.21.1-fabric/en_us-journeymap-collapsed.png)
- [1.20.1-forge: ja_jp-xaero-board](evidence/release-1.6.0/1.20.1-forge/ja_jp-xaero-board.png)
- [1.20.1-forge: en_us-journeymap-collapsed](evidence/release-1.6.0/1.20.1-forge/en_us-journeymap-collapsed.png)
- [1.20.1-fabric: zh_cn-xaero-board](evidence/release-1.6.0/1.20.1-fabric/zh_cn-xaero-board.png)
- [1.20.1-fabric: ja_jp-journeymap-collapsed](evidence/release-1.6.0/1.20.1-fabric/ja_jp-journeymap-collapsed.png)
- [1.20.4-forge: zh_tw-xaero-collapsed](evidence/release-1.6.0/1.20.4-forge/zh_tw-xaero-collapsed.png)
- [1.20.4-forge: ja_jp-journeymap-board](evidence/release-1.6.0/1.20.4-forge/ja_jp-journeymap-board.png)
- [1.20.4-fabric: zh_cn-xaero-collapsed](evidence/release-1.6.0/1.20.4-fabric/zh_cn-xaero-collapsed.png)
- [1.20.4-fabric: ja_jp-journeymap-board](evidence/release-1.6.0/1.20.4-fabric/ja_jp-journeymap-board.png)

Full local raw archive: `build/MTR-Map-Overlay-1.6.0-runtime-evidence.zip`, 7492661 bytes, SHA-256 `32e151e642f6af8dc3af8bd8d1874aa27930d7e051ef921f5baf705b5dc26da7`. Includes raw CLI traces and earlier harness attempts; only the six final normal-flow result files are counted as passes.
