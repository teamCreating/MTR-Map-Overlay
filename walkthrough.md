# walkthrough.md — MTR Map Overlay 开发全导览

> 面向接手本项目的开发者或 AI agent。读完这一篇，你应当能独立完成构建、测试、排错和二次开发。
> **开始任何工作前，请先阅读 [AGENTS.md](AGENTS.md)（多 agent 协作协议）。**

## 本分支说明（2026-09-30，Codex）

本分支为 MTR 4.0.5 / Forge + Fabric MC 1.20.1 / 1.20.4，mod/pack/metadata 均保持 v1.5.1。下面保留的 1.21.1 主线导览用于理解共享架构；**依赖、构建命令和入口以本分支 README / gradle.properties / build.gradle 为准**。MTR 4.0 mod 包为 `org.mtr.mod.*`，服务端入口为 `org.mtr.mod.Init`；Forge 使用可选 SimpleChannel，Fabric 使用 1.20 原始包通道，均保留快照协议 v5。默认构建 1.20.1，加 `-Pminecraft_version=1.20.4` 构建 1.20.4；后者的 Forge 49 / JourneyMap 5 适配在 `compat/mc1204/`。

## 1. 项目是什么

MTR Map Overlay 是一个 **Minecraft 1.21.1 NeoForge / Fabric 双平台 mod**，把
[Minecraft Transit Railway (MTR) 4.x](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway)
的交通网络呈现在 [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) 上（对标 Create 6.0 的列车地图体验），并在地图图层直接绘制站点/站台图标。

核心能力（v1.2.0）：

| 能力 | 说明 | 生效条件 |
|---|---|---|
| 地图地标 | 服务端全量站点/站台/车辆段 → 全屏地图小图标；不创建正常 waypoint | Xaero World Map 或 JourneyMap |
| 路线层 | 每条 MTR 路线一条着色折线（环线自动闭合），悬浮显示站名→终点站 | 客户端装 Xaero's World Map |
| 轨道层 | 沿真实轨道几何（含弧线/坡度）采样的暗色底层线 | 同上 |
| 全网同步 | 服务端把整张轨道网+路线快照分块推给客户端，任意维度全景显示 | **服务端也装本 mod** |
| 纯客户端回退 | 无服务端组件时，自动退回 MTR 客户端半径数据（渲染距离内） | 无 |

## 2. 平台与依赖（不要凭记忆改版本，改前查 Modrinth API）

| 依赖 | 版本 | Gradle 坐标（Modrinth maven） |
|---|---|---|
| Minecraft | 1.21.1 | — |
| NeoForge | 21.1.249 | ModDevGradle `neoForge { version }` |
| MTR | 4.1.0-beta.2 | `maven.modrinth:minecraft-transit-railway:NEOFORGE-4.1.0-beta.2+1.21.1`（compileOnly） |
| Xaero's World Map | 1.45.0+ | `maven.modrinth:xaeros-world-map:neoforge-1.21.1-1.45.0`（compileOnly） |
| Xaero's Minimap | 26.4.2+ | `maven.modrinth:xaeros-minimap:neoforge-1.21.1-26.4.2`（compileOnly） |

- 构建：Gradle 8.8 + **JDK 21**（`gradle.properties` 的 `org.gradle.java.home` 指向本机路径，换机器要改）。
- 历史：项目 2026-09-05 从 Forge 1.20.1 整体移植到 NeoForge 1.21.1；2026-09-23 新增 Fabric 并行构建。旧 Forge 1.20.1 代码只在 git 历史里（`77121f5` 之前），不能移除现有 NeoForge 支持。
- **MTR 4.1 包名注意**：mod 侧类在 `org.mtr.client.*`（不是 4.0 的 `org.mtr.mod.client.*`），入口类 `org.mtr.MTR`（持有 `private static Main main`）。mtr-core 层（`org.mtr.core.data.*`、`org.mtr.core.simulation.Simulator`）未变。

## 3. 目录结构

```
src/main/java/io/github/teamcreating/mtrmap/
├── MTRMap.java            # @Mod 入口：mod 总线/游戏总线注册、配置注册
├── CommandRegistration.java    # /mtrmap 客户端命令树
├── MTRDataSummary.java         # 车站→路线 摘要（地图图标 hover 信息用）
├── config/MTRMapConfig.java   # ModConfigSpec（TOML）
├── integration/
│   ├── xaero/XaeroRouteRenderer.java  # 路线、轨道与地图内地标图标
│   └── journeymap/             # JourneyMap 全屏地图 MarkerOverlay
├── mapdata/
│   ├── MapRoute.java           # 路线渲染 DTO（站点折线+名称）
│   ├── MapTrack.java           # 轨道折线 DTO
│   ├── MapDataCache.java       # 维度→渲染数据缓存（服务端快照优先，客户端回退）
│   └── TrackSampler.java       # RailMath 采样（客户端/服务端共用）
├── mixin/
│   ├── XaeroMixinPlugin.java   # 条件加载：Xaero 不在场时跳过 xaero mixins
│   ├── MainAccessorMixin.java  # @Accessor Main.simulators（服务端全网数据入口）
│   ├── MTRAccessorMixin.java   # @Accessor org.mtr.MTR.main（静态字段）
│   ├── MTRSimulatorMixin.java  # Simulator.sync 钩子（当前为空操作，留作扩展点）
│   └── client/
│       ├── MinecraftClientDataMixin.java  # MTR 客户端数据同步 → 刷新地图 overlay 缓存
│       ├── ClientCommonListenerAccessor.java # 读 NeoForge ConnectionType
│       └── xaero/
│           ├── XaeroWorldMapAccessor.java # GuiMap.cameraX/cameraZ/scale/mapProcessor
│           └── XaeroWorldMapMixin.java    # 注入 GuiMap.render 尾部画路径层
└── network/
    ├── MTRNetwork.java             # RegisterPayloadHandlersEvent 注册
    ├── RequestNetworkSync.java     # C2S 请求快照
    ├── NetworkSyncChunk.java       # S2C 分块传输 + 二进制编解码（含 PendingDimension）
    ├── ServerNetworkCollector.java # 服务端采集（Simulator 线程上读取，分块发送）
    └── ClientNetworkSync.java      # 客户端请求调度/重组/缓存写入
src/main/resources/
├── META-INF/neoforge.mods.toml # mod 元数据 + 依赖 + mixin 注册
└── mtrmap.mixins.json     # mixin 清单（plugin: XaeroMixinPlugin）
```

## 4. 运行时架构（数据流）

### 4.1 路径层渲染（每帧）

```
Xaero GuiMap.render
  └─(mixin@INVOKE blit)→ XaeroRouteRenderer.onRender
      ├─ 读 cameraX/Z/scale/mapProcessor（Accessor）→ 变换 PoseStack（屏幕中心→地图比例→相机偏移）
      ├─ 解析当前查看维度 → key = "namespace/path"（⚠️ MTR 世界 id 用斜杠不是冒号）
      ├─ MapDataCache.get(dim)：
      │    ① SERVER_DATA（全网快照，服务端装了本 mod 才有）
      │    ② 回退：MinecraftClientData（只有玩家周围 renderDistance×16 格的数据！）
      ├─ 画轨道层（MapTrack 折线，1.25px 暗灰）→ 画路线层（MapRoute，3px 线+站点方帽）
      ├─ RenderSystem.disableCull + endBatch(RenderType.gui())  # 立即落盘，防后续批处理剔除
      ├─ ROUTES/TRACKS 开关按钮（写配置，持久化）
      └─ 鼠标反投影 → 世界坐标 → 最近站点/线段 → 悬停 tooltip
```

### 4.2 全网同步（方案 C，对标 Create TrainMapSync）

```
客户端 LoggingIn → +3s / 每 refreshIntervalSeconds / /mtrmap syncRoutes
  → 检查 ConnectionType == NEOFORGE（否则退避 10 分钟，走纯客户端回退）
  → PacketDistributor.sendToServer(RequestNetworkSync)
服务端：ctx.enqueueWork(服务器线程) → 对每个 Simulator：
  simulator.run(() -> collect(...))   # 在 MTR 模拟器线程上读数据，避免竞态
    routes: Route.getRoutePlatforms() → 站点坐标/名称/自定义终点 + color/circular
    rails:  TrackSampler.sample → RailMath.getPosition 每 8 格采样（≤256 点/轨）
  → server.execute(发送) → 200KB/块 → NetworkSyncChunk{transferId,index,total,data}
客户端：按 transferId 重组 → NetworkSyncChunk.readDimensionList
  → MapDataCache.putServerData(dim, DimensionData)
渲染器发现 SERVER_DATA 命中即用全网数据（含玩家没去过的区域）。
```

### 4.3 地图内地标

协议 v5 的 `MapLandmark` 随全网快照进入 `MapDataCache`。Xaero 在 `GuiMap` overlay 中直接绘制固定像素大小的站点/站台图标；JourneyMap 使用仅限 `Fullscreen` 的 `MarkerOverlay`。两者都不创建正常 waypoint。纯客户端模式从 MTR 半径数据临时生成附近地标。

## 5. 关键设计决策（为什么这样做）

1. **mixin 而非官方 API**：Xaero 双图闭源且无 overlay API，社区（含 Create 本体）都是 mixin 进 `xaero.map.gui.GuiMap`。注入点是 render 尾部的 `GuiGraphics.blit(RL,IIIIII)` INVOKE——与 Create 的 `XaeroFullscreenMapMixin` 相同，1.45.0 实测命中。
2. **`require = 0`**：Xaero 升级若移动内部结构，注入静默跳过而不是崩溃。配套诊断：MixinPlugin 打日志 + 首帧渲染成功打 `Path layer render hook ... is active`（看不到这行 = mixin 没生效）。**不要改回 require=1**，会让不兼容版本直接崩。
3. **为什么必须做服务端同步**：MTR 客户端数据同步带半径过滤（`InitClient` 用 `renderDistance*16` 作 `DataRequest` 半径；`DataRequest.getData` 里 `station.inArea`/`rail.closeTo` 过滤）。纯客户端永远拿不到全网。
4. **服务端读数据必须在 Simulator 线程**：`simulator.run(Runnable)` 入队，采集完再 `server.execute` 发包。直接在服务器线程读会与模拟竞态。
5. **维度 key 格式是 `namespace/path`**：MTR `Init.getWorldId` 用斜杠（`minecraft/overworld`），而 `ResourceLocation.toString()` 是冒号——曾经因此 key 对不上（已在渲染端统一）。新增维度相关代码时警惕。
6. **payload 双侧 `optional()`**：即使目标服务器是 NeoForge 但没装本 mod，发送也不会踢人；配合客户端经验性退避（30s 重试直到首包，失败退避 10 分钟）。
7. **Xaero 界面上的图层顺序**：注入点在地图瓦片之后；路线/轨道先绘制，地图内地标图标再绘制，保证图标位于线路上方。

## 6. 开发与构建

```bash
# 环境：JDK 21（gradle.properties 指定 java.home），Gradle wrapper 8.8
./gradlew build            # 出包 build/libs/CRTools-MTR-Map-Overlay-<ver>.jar
./gradlew compileJava      # 只编译（快）
./gradlew runClient        # 开发客户端
./gradlew runClient -Pquickplay=TestWorld   # 直接进入 run/saves/TestWorld 世界
./gradlew runServer        # 开发服务器（游戏目录 run-server/）

# Fabric 使用独立的 Loom / Gradle wrapper，版本号读取仓库根目录 gradle.properties
./fabric/gradlew -p fabric build   # 出包 fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-<ver>.jar
```

测试环境准备（本仓库 `run/mods/` 已就绪，换机器需重新下载）：

1. 下载 MTR（neoforge 4.1.0-beta.2+1.21.1）、Xaero 双图 jar 放入 `run/mods/`（和 `run-server/mods/`）。
2. `run/options.txt` 首行加 `onboardAccessibility:false`——否则首次启动卡在无障碍引导页，quickplay 不会执行。
3. 测试世界 `run/saves/TestWorld` 由 `run-server` 生成后拷贝而来（超平坦、和平难度、allowCommands=1；level.dat 是 NBT，改配置用 python gzip+NBT 补丁）。

## 7. 测试清单（每次大改后过一遍）

启动 `runClient -Pquickplay=TestWorld`，对照 `run/logs/latest.log`：

| # | 检查点 | 期望日志（Grep 关键字） |
|---|---|---|
| 1 | mod 加载 | `MTR Map Overlay 1.4.5 (mtrmap)` |
| 2 | Xaero 检测 | `Xaero's World Map detected - map path layer mixins will be applied` |
| 3 | mixin 应用 | `Applied XaeroWorldMapMixin to GuiMap` |
| 4 | payload 注册 | `Full-network sync payloads registered (protocol 5)` |
| 5 | 进世界 | `Dev joined the game` |
| 6 | 服务端发送 | `Sent full-network snapshot for minecraft/overworld to Dev` |
| 7 | 客户端入库 | `Full-network snapshot applied for minecraft/overworld` |
| 8 | **渲染钩子**（按 M 打开地图） | `Path layer render hook into Xaero's World Map is active` |
| 9 | 视觉 | 地图左上角 ROUTES/TRACKS 开关可见；有数据时路线线/轨道线出现 |

自动化提示：如果测试环境的键盘注入不可靠（无头/远程桌面场景），可临时在
`ClientNetworkSync.onClientTick` 里程序化触发 Xaero 的地图键：
`xaero.map.controls.ControlsRegister.keyOpenMap`（public static KeyMapping，`setDown(true)` + `KeyMapping.click(key)`）。
debugLog 开启时每 3 秒会输出 `screen-trace: <当前 Screen>`，可用于确认地图是否真的打开了。

## 8. 排错手册（实战踩过的坑）

| 症状 | 原因 | 处理 |
|---|---|---|
| 地图上什么都不画 | mixin 未命中（Xaero 升级移动了内部结构） | grep 日志确认无 `Path layer render hook`；javap 检查新版 GuiMap 的 render/blit 签名；改 mixin 描述符 |
| 路线只在玩家附近出现 | 没装服务端组件，走了客户端回退（MTR 数据半径限制） | 预期行为；装服务端后看 `Sent full-network snapshot` |
| 全网同步无响应 | 服务器不是 NeoForge / 未装本 mod | 客户端已自动退避 10 分钟；`debugLog=true` 看 `Snapshot request failed` |
| quickplay 停在标题屏 | `onboardAccessibility:true`（全新 run 目录） | options.txt 设 false |
| 维度对不上、数据不显示 | 维度 key 冒号/斜杠混淆 | 统一用 MTR 的 `namespace/path` 格式 |
| 客户端被服务器踢出 payload 错误 | payload 未注册 optional() | 检查 MTRNetwork 注册两侧都在 |
| 编译报 `org.mtr.mod.*` 不存在 | 用了 MTR 4.0 的旧包名 | 改 `org.mtr.client.*` / `org.mtr.MTR` |
| gradle 报 JDK 版本错 | java.home 指向不存在路径 | 改 gradle.properties |

## 9. 发布流程

1. 改 `gradle.properties` 的 `mod_version`（默认只增加 patch 位；minor 位仅在用户明确要求时增加）。
2. 更新 `RELEASE_NOTES.md`（新版本小节置顶）与 `README.md`（功能/依赖表）。
3. `./gradlew build` 与 `./fabric/gradlew -p fabric build` → 分别验证 `build/libs/`、`fabric/build/libs/` 的 JAR。
4. **每完成一个逻辑步骤就 commit & push**（本项目的既定约定），commit message 说明动机与验证方式。
5. 打 tag（历史上有 `V1.0.0`/`v1.0.1` 先例）。

## 10. 参考实现与资料

- **Create 6.0 列车地图**（本项目的对标与模板）：`CreateMod/src/main/java/com/simibubi/create/compat/trainmap/`（XaeroTrainMap / TrainMapManager / TrainMapRenderer / TrainMapSync）。
- 反编译参考：`trainmap_decomp/`（Create trainmap + Xaero GuiMap 1.40.x）、`xaero_extracted/`（旧版 Xaero jar 展开）——仅本地参考，已加入 .gitignore。
- MTR 4.1 jar：`run/mods/MTR-neoforge-4.1.0-beta.2+1.21.1.jar`（110MB fat jar，javap 分析 API 用）。
- 上游issue：Create #9590（Xaero 上图层顺序问题，本项目同样存在、同样接受）。

## 11. 已知限制 / 未来方向

- 只渲染 TRAIN 模式轨道（船/缆车轨道未画）。
- 列车实时位置未上地图（客户端 `MinecraftClientData.vehicles` 已有数据，低成本可加）。
- 洞穴图层（cave layers）下路径线悬浮显示，与 Create 行为一致，暂无解。
- 路线层画的是站台顺序折线，不沿轨道走线（轨道层负责真实几何；路线沿轨需要图上寻路，工程量大）。


## [2026-11-11T01:53:55+08:00] Codex — User-authorized 1.6.0 release preparation

- User explicitly selects 1.6.0 and authorizes publication after the supported six-target matrix passes. Claim covers ports, compatibility fixes, builds, native UI tests, sealed-candidate verification and the existing release pipeline.
- Preserve platform split: NeoForge/Fabric 1.21.1 + MTR4.1 on main; Forge/Fabric 1.20.1/1.20.4 + MTR4.0 on the compatibility branch. No unsupported cross-version loader claim.
- Merge current remote maintainer documentation before changing release data; resolve README conflicts by preserving the maintainer rewrite and adding current integration references. Record and fix known Forge49 singleplayer initialization failure before acceptance rather than substituting dedicated-server success.


## [2026-11-11T01:55:56+08:00] Codex — Port Navigate availability and localization to Java17 targets

- Ported the optional public HowToGo bridge, per-target provider registry, translated controls/commands/diagnostic outcomes, and four matching language catalogs from the validated main implementation.
- Retained Java17 list access and ResourceLocation construction, MTR4.0 client package locations, existing Forge/Fabric entry points, Forge47/49 network/input adapters and generated JourneyMap5 bridges. Build/test failures are release blockers and will be fixed before sealing candidates.


## [2026-11-11T02:13:10+08:00] Codex — Six-target release builds and candidate-jar QA support

- All four Java17 production targets build with 110 passing tests and one explicit generator skip each. Main builds retain 107 passing tests and one generator skip. Actual HowToGo signature data is now fetched and hash-checked by CI before Gradle; missing data fails instead of silently skipping an acceptance check.
- Added separately packaged acceptance controllers for NeoForge, Forge and Fabric. They will be installed beside exact candidate JARs in isolated production-launcher profiles, not bundled in released mods. The controller records the loaded Overlay code source.
- Native mouse bindings use verified Mojang/SRG/intermediary mappings and the platform's input dispatch. Shared keyboard actions use the serialized binding rather than a NeoForge-only helper. Existing Minecraft/loader splits remain independent. Runtime acceptance is still pending.


### 2026-11-11T02:49:51+08:00 — Codex: production runtime acceptance tooling

Added raw native widget/arrival-board/server lifecycle observations and a CLI acceptance runner that checks the loaded production JAR hash, both maps, four locales, disabled Navigate and live native arrivals. The separate Forge/NeoForge QA JAR now reuses the target resource-pack metadata; it is never shipped in release assets. Forge 49 normal title-screen singleplayer startup succeeds and initializes native Xaero server data; quick-play while a loading-warning screen is pending can skip lifecycle completion and is not treated as a pass. Production source remains unchanged. Final six-candidate acceptance is still pending.


### 2026-11-11T02:55:43+08:00 — Codex: native keyboard acceptance input

JourneyMap 5 listens to platform keyboard events, so merely setting KeyMapping state did not open its screen. QA now sends actual KeyboardHandler press/release events, preserving loader hooks. The runner also waits for title resource loading to finish before clicking. This changes only test tooling; the initial missing-screen attempt is not counted as a product pass.


### 2026-11-11T03:07:25+08:00 — Codex: loader-independent QA screen roles

Production Fabric uses intermediary class names. QA now exposes typed native title/world-selection/disconnection roles via instanceof, avoiding name-string assumptions in the CLI startup check. This is confined to the opt-in QA source set. Forge49 quick-play still fails independently of Overlay, while both Forge normal singleplayer matrices pass; release remains gated.


### 2026-11-11T03:14:26+08:00 — Codex: sync QA loopback endpoint

Synced the main acceptance CLI endpoint fix: default to the observed IPv4 loopback listener with an optional MTRMAP_QA_URL override, avoiding Windows localhost IPv6 fallback delays. No production source or candidate changed.


### 2026-11-11T03:28:46+08:00 — Codex: seal six-target baseline and hold 1.6.0 publication

All six exact downloaded candidate JARs passed 48 normal singleplayer cases (Xaero/JourneyMap × four languages), native station boards/timestamps and Navigate availability; NeoForge also passed the two-provider chooser and actual HowToGo destination handoff. Selected native screenshots, JSON, dependency hashes, sealed manifest and failing/control logs are committed in `docs/validation/evidence/release-1.6.0/`. Full raw archive SHA-256 is `32e151e642f6af8dc3af8bd8d1874aa27930d7e051ef921f5baf705b5dc26da7`. Candidate 42879095868 and dry-run 42883569831 pass integrity checks.

Publication remains blocked: Forge 1.20.4 quick-play disconnects with null Xaero server data on both 49.2.0 and recommended 49.2.13, also without Overlay/QA and with newer Xaero. Native normal startup works; source/runtime evidence indicates Forge starts its event bus after quick-play can enter the world. No event-bus/lifecycle injection or dependency patch was added. Dedicated/client-only fallback and performance checks are not claimed complete. No real Publish or v1.6.0 tags; user's publication gate is preserved. See `docs/validation/release-1.6.0.md`.


### 2026-11-11T03:30:06+08:00 — Codex: preserve raw release evidence bytes

Added narrow Git attributes for sealed validation manifests/checksums and raw logs so Windows newline conversion cannot change their byte identity. Explicitly tracked the three referenced QA/control logs despite the general build-log ignore rule. Report links are checked against tracked files, not merely working-tree presence.


### 2026-11-11T03:31:41+08:00 — Codex: synchronize compatibility acceptance ancestry

Merged the existing PR #12 compatibility merge commit into the QA branch to satisfy protected-branch freshness. No production change or candidate replacement; 1.6.0 remains held for the documented runtime blocker.


### 2026-11-11T03:35:11+08:00 — Codex: clarify native destination-name verification

The acceptance report now states the tested HowToGo name contract precisely: MTR display separators (vertical bars) become spaces, while the native name content and floored block coordinates are preserved. This matches the production bridge and recorded assertion; no product or candidate changed.


### 2026-11-11T09:43:56+08:00 — Codex: claim Forge 1.20.4 startup repair

User requests an attempted fix for the reproduced quick-play failure. Work is isolated on `fix/forge1204-quickplay` from the current compatibility branch. Investigate the verified startup-order defect and repair the loader completion boundary, preserving native load warnings/errors and keeping unrelated targets unchanged. Version stays 1.6.0; publication remains conditional.


### 2026-11-11T09:48:52+08:00 — Codex: repair Forge 49 initial resource callback ordering

Added a Forge-1.20.4-only Mixin in the target-specific source set. The verified Forge callback `lambda$new$6` keeps its self-test/resource-tracker work, then runs the original ClientModLoader.completeModLoading guard before vanilla onResourceLoadFinished can synchronously quick-play. A true guard result cancels the callback, preserving Forge warning/error pages; the obsolete late call is redirected away so completion runs once. Both sites require exactly one match. Normal Mixin remapping resolves the vanilla boundary to production m_293750_; no direct event-bus start, synthetic server event, Xaero state injection or exception suppression.

Forge 1.20.4 production/QA builds and existing 110-pass/1-opt-in-skip tests pass. JAR inspection confirms the target-only class/config and correct refmap. Runtime validation is pending; version stays 1.6.0 and the old sealed candidate is unchanged.


### 2026-11-11T09:53:50+08:00 — Codex: native startup warning/error acceptance fixtures

Added opt-in properties to the separate QA mod: `mtrmap.qa.loadWarning` submits a native ModLoadingWarning through Forge's public API; `mtrmap.qa.failClientSetup` deliberately fails that QA mod's client setup event. These default-off fixtures test that the corrected completion guard preserves warning/error screens and does not enter a quick-play world after a loading failure. No dependency JAR, metadata, or production code is modified by the fixtures. QA build passed.


### 2026-11-11T09:56:38+08:00 — Codex: isolate local startup test profiles

Ignored the project-local .cache directory used for cloned singleplayer saves, original dependency JARs and launcher state. Reviewable source and selected validation evidence remain tracked explicitly; generated worlds and binaries are not part of the change.


### 2026-11-11T10:04:13+08:00 — Codex: preserve vanilla loading-complete state before the Forge guard

Native warning acknowledgement exposed a real boundary defect in the first patch: cancelling before onResourceLoadFinished skipped vanilla gameLoadFinished, leaving menus visually stale. The guard now runs at onGameLoadFinished HEAD, after vanilla sets that state and before initial screens/quick-play are built. Its vanilla method maps normally; the obsolete Forge callback completion remains redirected. This preserves the native completion marker and the warning/error guard together, without fabricating either. The earlier binary's normal/quick-play passes are preliminary and will not substitute for retesting the final JAR. The first retry command failed before compilation because PowerShell split an unquoted target property. The corrected quoted-property invocation subsequently passed the production build and existing tests; final runtime matrix remains pending.


### 2026-11-11T10:05:11+08:00 — Codex: confirm final-boundary build with the exact target property

Rebuilt using the quoted `-Pminecraft_version=1.20.4` argument. Compilation, refmap generation, production packaging and the existing 110-pass/1-opt-in-skip suite pass for the final onGameLoadFinished boundary. No failed configuration attempt is counted as validation.


### 2026-11-11T10:06:30+08:00 — Codex: observe the real vanilla loading-complete marker

QA now reads the 1.20.4 public isGameLoadFinished getter when available, without accessing or modifying private state. The final warning/error tests require this marker to be true while no world/server lifecycle has started. Older loaders without that extension remain supported by the optional QA collector. QA build passed.


### 2026-11-11T10:07:29+08:00 — Codex: verify production naming of the read-only QA getter

The native getter is mapped in production. The QA collector now uses its exact verified Mojang/SRG names (`isGameLoadFinished` / `m_293453_` from the selected mappings), retaining optional absence on 1.20.1. No private state or game logic is changed. QA build passed.


### 2026-11-11T10:12:04+08:00 — Codex: document the final callback control flow precisely

Clarified that cancelling onGameLoadFinished preserves the native diagnostic screen but still lets its outer resource callback return normally. The removed late completion call must stay removed for both success and diagnostic results. This is a comment-only clarification with unchanged source line counts and runtime instructions.


### 2026-11-11T10:21:59+08:00 — Codex: finalize the Forge 1.20.4 repair matrix

Final production JAR SHA-256 df9beb778c262f71522c9d1834cc54ef542e1c25b3ff835a93d3c7224fd9b047 passes both Forge versions, quick-play/menu/warning acknowledgement, 48 two-map/four-locale native cases and four loading diagnostic guards. Native completion marker is true on diagnostic pages, no world starts before acknowledgement or after an error, and actual lifecycle/Xaero initialization occurs once after valid world entry. Final CI 42944368485 passes all four targets; the patch class is byte-identical to the tested class, isolated to Forge1.20.4, with QA absent from production. Selected evidence and full local raw archive are retained; report links checked. Version remains1.6.0; old candidate is not replaced or approved for publication.


### 2026-11-11T10:42:05+08:00 — Codex: isolate QA resources during sealed-candidate acceptance

Candidate42946243079 first launch exposed an old QA JAR carrying mtrmap.refmap.json. Its stale map could shadow the production mod's correct refmap. Production and locally tested refmaps match byte for byte; the old QA refmap lacks the new hook. Claimed packaging repair under the existing release task: exclude production Mixin resources from the separate QA artifacts, then retry the unchanged sealed candidate. No injector is disabled and no candidate binary is edited.


### 2026-11-11T10:43:59+08:00 — Codex: synchronize QA-isolation branch ancestry

The two worktrees share Git branch names; compatibility QA packaging is on its distinct `test/qa-resource-isolation-mc120` branch. Merged the existing protected compatibility merge commit, preserving the QA resource exclusion. No production source or sealed candidate changes.


### 2026-11-11T10:46:58+08:00 — Codex: validate QA isolation across six outputs

Added a reviewable QA artifact checker for production Mixin resources/manifest registration, QA-only class identity and loader metadata. All six rebuilt QA JARs pass; the original colliding QA JAR is correctly rejected. The sealed candidate remains untouched.

### 2026-11-11T10:58:12+08:00 — Respect the native MTR beta-notice countdown

The fresh 1.21.1 Fabric profile reached MTR's BetaWarningScreen before its reading countdown allowed Escape. A single early Escape was ignored and the acceptance runner timed out. Retry ordinary native Escape while that same screen remains, bounded by a timeout; preserve the countdown and native close/configuration behavior. Production candidate 42946243079 is unchanged. Retain the failed attempt separately and rerun the fresh profile; do not count the timeout as a pass.


### 2026-11-11T11:02:04+08:00 — Observe native map and rendered train data in opt-in QA

Expose native map counts/server-sync availability and read the existing rendered-car cache (global stream or nearby MTR collector) in QA snapshots. This supports exact-candidate server/fallback assertions and native train-hover input without manufacturing vehicles or packets. The observer does not start subscriptions or overwrite state; production sources and sealed candidate remain unchanged. QA remains a separate opt-in artifact.


### 2026-11-11T11:09:12+08:00 — Observe the native map projection for hover acceptance

The MTR-only fixture legitimately supplies only one nearby station, so inferring projection from two station markers is invalid. QA now observes Xaero's existing accessor values or JourneyMap's existing renderer projection, without changing camera/state. Use these native coordinates for car-hover input and review raw captures. The sealed production JARs are unchanged.


### 2026-11-11T11:10:36+08:00 — Record repaired candidate singleplayer acceptance

Candidate42946243079 passed six production targets, 48 native cases. Record exact manifest/JAR identity, fixture evidence and unsuccessful QA attempts separately. Dedicated sync/fallback remains pending, so release is still held. Production JARs have not changed. See docs/validation/release-1.6.0-fixed-candidate.md.

### 2026-11-11T11:23:20+08:00 — Fix Forge 1.20.1 optional-channel negotiation

The sealed-candidate MTR-only dedicated-server check exposed three rejected Overlay channels. Forge 47.4.0 NetworkRegistry.ABSENT is a ChannelData record; comparing that object with a predicate's String version always rejects a missing channel. Use Forge's public acceptMissingOr helper for static, train and station-overlay channel predicates in both directions. It preserves exact protocol matching, accepts the loader's absent/vanilla sentinels, and rejects other installed protocol versions. Forge1.20.4 uses its separate override and is unchanged. Candidate42946243079 is now held; build and verify a new candidate before publishing. Native failed handshake and mismatch-page evidence is retained.


### 2026-11-11T11:25:43+08:00 — Hold the candidate for the Forge1.20.1 fallback defect

Retain the actual mismatched-channel screen, inventories and native logs. Update both-language release notes to include the native optional-channel correction. Existing singleplayer passes remain historical; Candidate42946243079 cannot be published. The repaired Forge1.20.4 full/fallback dedicated results pass, but the next release must seal and validate the additional Forge1.20.1 fix.

### 2026-11-11T11:28:19+08:00 — Verify optional-channel correction on a native MTR-only server

The corrected Forge1.20.1 production build passes both native maps against the same original MTR-only server through the native multiplayer list. Local rail/route/station/car caches are nonempty; remote Overlay catalog/arrivals are absent and Navigate is correctly disabled. Preserve the real mismatch and passing logs side by side. Existing suite: 110 pass/one explicit generator skip. Seal a new candidate before release; see docs/validation/forge1201-optional-channel-fix.md.

### 2026-11-11T11:36:44+08:00 — Seal both corrections and retain a plain Forge47 control

PR19/20 merged after successful gates; a Maven Central429 transient was retried without changing dependencies or weakening checks. Candidate42953810741 now builds all six targets. The Forge1.20.1 optional-channel fix passed full-sync and MTR-only fallback on both maps. A plain four-mod client without Overlay/QA independently reproduces quick-play handshake timeout; native multiplayer-list entry is the valid completed path. Keep that observation explicit and test the new candidate through normal menus on Forge47.4.0.

### 2026-11-11T12:11:04+08:00 — Complete exact final-candidate acceptance

Candidate42953810741 passed six production clients, 48 native map/locale cases plus 24 dedicated full/fallback cases. Verify loaded archive/hash, native data sources, actual arrivals/timestamps and both maps native train tooltips. Preserve the Forge47 quick-play control limitation and old failed candidates explicitly. Record complete local archive identity and selected raw evidence. Production JARs remain sealed; Publish dry-run42956402233 passed without tags or release writes. Publication remains held pending the Forge47 quick-play scope choice; do not treat elapsed time or an unsubmitted preselected option as approval.

### 2026-11-11T12:11:59+08:00 — Keep the release gate explicit

Update the active release task with completed normal-entry acceptance and the pending Forge47 quick-play scope choice. The user required all platforms to have no issues before publication; an unsubmitted choice is not approval. Do not create tags or dispatch real Publish until this condition is resolved.

### 2026-11-11T12:17:53+08:00 — Extend the release blocker to native Forge47 quick-play

The user explicitly chose to repair automatic multiplayer entry before release. Preserve all 72 normal-entry passes and the verified Forge1.20.4 patch; do not publish the existing candidate under an exception. Claim native Forge47 handshake investigation. Current Forge47 source already completes mod loading before initial screens, so do not blindly apply the Forge49 startup patch. Read native connection/handshake state and capture the actual failing operation before choosing a correction.

### 2026-11-11T12:19:20+08:00 — Add a read-only native handshake probe

Add an opt-in JDK JDI CLI observer for NetworkHooks registration/payload dispatch, NetworkInstance event-bus flags/error callbacks, caught Forge network exceptions and handshake lookup results. It reads fields/stack frames only; it never invokes target methods, rewrites fields, patches dependencies or fabricates packets. Compile with the installed JDK21 jdk.jdi module. Use a loopback-only suspended debug JVM in an isolated native fixture to capture the first actual Forge47 quick-play failure before choosing a product fix.


### 2026-11-11T12:26:43+08:00 — Reduce probe overhead and recover the owned fixture cleanly

The initial JDI method-exit request made SecureJar package scanning run too slowly to reach the handshake before the diagnostic timeout. Exclude that attempt from product evidence and remove method-exit tracking; retain only targeted breakpoints and caught Forge-network exceptions. Its controller timed out before a game window appeared and lost its stdin pipe. Add an explicit fixture-directory-checked attach helper that calls Forge's public current-server/native halt API, allowing normal save/shutdown instead of forcing process termination. Compile the helper for Java17; it does not redefine classes or patch dependencies. The native handshake observer itself remains read-only.


### 2026-11-11T12:33:54+08:00 — Observe the actual inbound login packet listener

The bounded read-only probe reproduces the timeout and sees client login-channel registration, but no Forge payload-dispatch entry or network exception. Extend observation to the native Connection read/protocol boundary and custom-query listener, and dump only that listener's actual VM bytecode/constant pool for comparison. This distinguishes a transport/protocol stall from a transformed listener bypass without rewriting classes or invoking target methods. The first clean diagnostic exits client/server/probe normally; no diagnosis is yet claimed from missing callbacks alone.


### 2026-11-11T12:41:05+08:00 — Snapshot only after the native stall is established

A breakpoint-delayed control joined successfully, but the observer hit JDWP INVALID_SLOT reading a synthetic bridge frame and detached; do not call this a product fix. Skip bridge argument reads. Add a late-attach snapshot mode that suspends briefly only after the unmodified startup has stalled, reads the current ConnectScreen connection, packet counters, channel protocol/handshake attributes and auto-read flag, then resumes/detaches. It neither invokes target methods nor rewrites fields, and avoids inserting debugger pauses into the startup ordering being investigated.

