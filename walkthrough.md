# walkthrough.md — MTR:Xaero Mapper 开发全导览

> 面向接手本项目的开发者或 AI agent。读完这一篇，你应当能独立完成构建、测试、排错和二次开发。
> **开始任何工作前，请先阅读 [AGENTS.md](AGENTS.md)（多 agent 协作协议）。**

## 1. 项目是什么

MTR:Xaero Mapper 是一个 **Minecraft NeoForge 1.21.1 客户端 mod**，把
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
- 历史：项目 2026-09-05 从 Forge 1.20.1 整体移植到 NeoForge 1.21.1。**main 分支只有 NeoForge 版本**，旧的 Forge 1.20.1 代码在 git 历史里（`77121f5` 之前）。
- **MTR 4.1 包名注意**：mod 侧类在 `org.mtr.client.*`（不是 4.0 的 `org.mtr.mod.client.*`），入口类 `org.mtr.MTR`（持有 `private static Main main`）。mtr-core 层（`org.mtr.core.data.*`、`org.mtr.core.simulation.Simulator`）未变。

## 3. 目录结构

```
src/main/java/com/lx862/mtrsurveyor/
├── MTRSurveyor.java            # @Mod 入口：mod 总线/游戏总线注册、配置注册
├── CommandRegistration.java    # /mtrsurveyor 客户端命令树
├── MTRDataSummary.java         # 车站→路线 摘要（地图图标 hover 信息用）
├── config/MTRSurveyorConfig.java   # ModConfigSpec（TOML）
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
└── mtrsurveyor.mixins.json     # mixin 清单（plugin: XaeroMixinPlugin）
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
客户端 LoggingIn → +3s / 每 refreshIntervalSeconds / /mtrsurveyor syncRoutes
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
./gradlew build            # 出包 build/libs/CRTools-MTR-Xaero-Mapper-<ver>.jar
./gradlew compileJava      # 只编译（快）
./gradlew runClient        # 开发客户端
./gradlew runClient -Pquickplay=TestWorld   # 直接进入 run/saves/TestWorld 世界
./gradlew runServer        # 开发服务器（游戏目录 run-server/）
```

测试环境准备（本仓库 `run/mods/` 已就绪，换机器需重新下载）：

1. 下载 MTR（neoforge 4.1.0-beta.2+1.21.1）、Xaero 双图 jar 放入 `run/mods/`（和 `run-server/mods/`）。
2. `run/options.txt` 首行加 `onboardAccessibility:false`——否则首次启动卡在无障碍引导页，quickplay 不会执行。
3. 测试世界 `run/saves/TestWorld` 由 `run-server` 生成后拷贝而来（超平坦、和平难度、allowCommands=1；level.dat 是 NBT，改配置用 python gzip+NBT 补丁）。

## 7. 测试清单（每次大改后过一遍）

启动 `runClient -Pquickplay=TestWorld`，对照 `run/logs/latest.log`：

| # | 检查点 | 期望日志（Grep 关键字） |
|---|---|---|
| 1 | mod 加载 | `MTR:Xaero Mapper 1.2.0 (mtrsurveyor)` |
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
3. `./gradlew build` → 验证 `build/libs/*.jar`。
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
