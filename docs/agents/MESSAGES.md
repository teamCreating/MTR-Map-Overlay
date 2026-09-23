# MESSAGES.md — Agent 交流墙（只追加，不改不删）

> 格式（追加在最上面）：
>
> ```
> ## [YYYY-MM-DD HH:MM] 操作者标识 — 一句话摘要
> 正文：背景 / 做了什么 / 需要谁注意什么 / 关联 commit 或任务。
> ```

## [2026-09-23 15:32] Codex — v1.4.7 Fabric 并行构建与双平台发布准备
- 新增 Fabric Loom 独立构建；共用数据/渲染/协议 v5，平台入口、配置、客户端命令和网络适配各自实现，NeoForge 未移除。
- Fabric 与 NeoForge 无头构建已通过，最终 1.4.7 JAR 与 GitHub Release 待完成；没有自动启动 Minecraft，待 Ben 手动实机验证。

## [2026-09-23 14:09] Codex — v1.4.6 图标稳定性修复准备提交
- 项目改名已由独立提交完成；本次仅提交 Xaero/JourneyMap 图标修复与版本、任务记录，不包含改名变更。
- `mod_version` 升至 1.4.6，构建通过后推送；Minecraft 仍由 Ben 手动验证。

## [2026-09-23 14:09] Codex — 站点/站台图标固定到地图变换并修复贴图裁切
- Xaero 图标锚点改用与轨道相同的世界坐标 PoseStack；仅图标尺寸抵消缩放，悬停继续使用连续屏幕坐标。
- Xaero 与 JourneyMap 的站点/车辆段贴图都改为完整采样 32×32 PNG；`gradlew build --no-daemon` 通过，未启动 Minecraft，待 Ben 手动验证。
- 当前工作区已有另一批项目改名的暂存及未暂存改动，本修复暂未提交或推送，以免将他人的改名工作卷入提交。

## [2026-09-23 01:37] Codex — v1.4.5 地标改为全屏地图内小图标
- 删除 Xaero 普通 waypoint 创建实现和 `syncWaypoints` 命令；Xaero World Map 直接绘制 12px 站点、5px 站台和 10px 车辆段图标。
- JourneyMap `MarkerOverlay` 限定 `Context.UI.Fullscreen`，图标缩小且无常驻文字标签，不出现在 Minimap。
- 两套地图都使用协议 v5 全量 `MapLandmark`；纯客户端回退也会生成附近图标。旧 `[MTR]` Xaero waypoint 自动清理。
- `gradlew build --rerun-tasks --no-daemon` 通过；未启动 Minecraft，实机视觉验证由 Ben 手动完成。commit `4b6146d`。

## [2026-09-22 20:31] Codex — v1.4.4 全图航点与共线彩虹带完成
- 协议升级 v5：`MapTrack` 使用稳定 rail ID，路线只传 rail 引用；完整快照新增 station/platform/depot landmarks。
- Xaero 对完整快照做增量航点对账，默认同时显示站点和站台；半径回退不再删除已保存的远处航点。
- 每根物理轨道在路线层只画一次，共线颜色按 route ID 稳定排序并横向切分为 TRACK 同形 ribbon；悬停检测真实轨道。
- `gradlew build --rerun-tasks --no-daemon` 通过；按 Ben 要求未启动 Minecraft。实现 commit `6a95614`，发布收尾 commit 后补。

## [2026-09-20 22:33] Codex — v1.4.3 ROUTE 改为直接复用 TRACK 管线
- 删除路线专用的拼接 path 数据模型；每条路线现在持有若干个 TRACK 同款 `MapTrack`，每根物理轨道均由
  `TrackSampler.sample()` 生成，Depot、纯客户端车辆路径和铁路网回退三条数据源统一。
- ROUTE/TRACK 共同调用同一个 `drawPolyline()`，线宽、透明度、逐段视口裁剪和 quad 生成完全相同；
  ROUTE 只替换颜色。由逐点裁剪造成的断线也随独立绘制逻辑一起移除。
- 快照协议升级到 v4，按路线传输独立轨道折线，客户端不再拼接轨道。
- `gradlew build --rerun-tasks --no-daemon` 通过；按 Ben 要求未启动 Minecraft。commit `46a87b9`。

## [2026-09-20 18:35] Codex — 接管 ZCode 中断工作并完成 v1.4.2 严格贴轨回退
- 已从 ZCode session `sess_48dca44a-81eb-4a22-a9d7-b45b673d658f` 接管因配额中断的未提交改动。
- Depot 真实路径仍优先；无 Depot 路径时在 `positionsToRail` 上寻路，并逐轨复用
  `TrackSampler.sample()`，保证路线顶点与灰色轨道层采样一致、lane=0。
- 移除所有站点间直线回退：断路时跳过该路线并记录 warning；环线末站→首站独立寻路。
- `gradlew build --rerun-tasks --no-daemon` 通过；产物 pack 与 mod 元数据均为 v1.4.2。
- 按 Ben 要求未启动 Minecraft，实机视觉验证由 Ben 手动执行。实现 commit `e436796`。

## [2026-09-20 05:10] ZCode — v1.5.0：真实路径染色 + 哈希探测热更
- 按需求移除自研 Dijkstra 寻路，路线颜色改用 MTR 车辆段生成的真实驾驶路径
  （`Depot.getPath()`，公开 getter；`writePathCache()` 刷新轨道引用）。
  颜色按列车途经站台解析（platform.routes ∩ depot.routes），染色叠加在轨道几何上。
- 热更：新增 `NetworkSyncProbe`/`NetworkProbeResponse`（协议 v3）。
  服务端哈希 = 轨道 hexId 摘要 + 路线 id/名 + 车辆段 lastGeneratedMillis；
  客户端只对哈希变化的维度发起按维度过滤的全量请求。
- 数据生成器经验：MTR 的 Depot 生成需要 siding 真实连入铁路网
  （siding rail 必须加入 simulator.rails，否则 updateRailCache 失败被 prune）；
  生成管线在游戏 tick 中异步完成，instantDeployDepots 不保证同步生成 path。
## [2026-09-20 03:10] ZCode — v1.4.0 路线寻路吸附上线
- 路线层不再走直线：服务端快照对每条路线做铁路网 Dijkstra（站台平台轨道为锚点），
  输出真实轨道几何；共线路线按方向分组分配 ±1/±2 车道偏移，渲染为平行走线。
- 协议 v2：路由条目新增 path 点列（x/z/lane）。旧协议直接失效（同 jar 双端，无兼容负担）。
- 重要经验：MTR 数据对象（Station/Platform/Route）的 `data` 背引用必须指向所属 Simulator，
  否则 `updateRailCache` 在错误的 positionsToRail 上查找，saved rail 会被 sync 当作无效删除。
- 新增两个测试：`RoutePathfinderTest`（无头寻路测试 5 断言）与
  `TestWorldGeneratorTest`（生成带真实 MTR 网络的 TestWorld 存档，实机验证用）。

## [2026-09-16 02:20] ZCode — v1.3.0 统一版：JourneyMap 集成并入主 mod
- journeymap 分支的地标功能已移植进 main（NeoForge 1.21.1）。注意 JourneyMap 6.x 移除了旧
  `journeymap.client.api`（v1），现在使用 **v2 API**（`journeymap.api.v2.*`），依赖来自官方 maven
  `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1-SNAPSHOT`（jm.gserv.me，已在 build.gradle 配置）。
- 集成入口 `integration/journeymap/JourneyMapIntegration`（无 JM imports，类加载隔离）；
  JM API 触点全部在 `JourneyMapLandmarkManager` 与 `MTRJourneyMapPlugin`（仅 JM 在场时加载）。
- 标记纹理现在位于 `assets/mtrsurveyor/textures/atlas/marker/`（旧分支的 mtrjourneymap 命名空间
  与实际 RL 不匹配的隐患已一并修复）。
- 实机验证：JM 6.0.8 下插件被发现、landmark 同步运行、testMarker 在全屏地图上实际渲染（有截图）。
- journeymap 分支自此可视为归档（功能已并入 main）。


## [2026-09-16 01:05] ZCode — 回退事故排查完毕，仓库健康，协作协议上线
- 今晚 00:50 有 agent 对本仓库执行了 `git reset`，此前曾把工作区+暂存区整体回退为废弃的 Forge 1.20.1
  架构（-1574 行：network/ 包、neoforge.mods.toml、路径层渲染器全被删）。所幸该次回退只停留在暂存区，
  已被 reset 丢弃；HEAD(v1.2.0, fa8ff07) 完好，`git diff HEAD` 为空，`./gradlew build` 通过，
  远端 origin/main 与本地一致。
- 措施：新建 AGENTS.md（三条铁律+工作流程）、本交流墙、STATE.md、TASKS.md。
- 提醒：所有 agent 开工前必读 AGENTS.md；平台已从 Forge 1.20.1 迁移到 NeoForge 1.21.1，
  旧记忆里的 "Forge 版本/main 分支" 描述一律以 STATE.md 为准。
