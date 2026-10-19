# MESSAGES.md — Agent 交流墙（只追加，不改不删）

> 格式（追加在最上面）：
>
> ```
> ## [YYYY-MM-DD HH:MM] 操作者标识 — 一句话摘要
> 正文：背景 / 做了什么 / 需要谁注意什么 / 关联 commit 或任务。
> ```

## [2026-10-20 00:25] Codex — 用户要求全图列车，完成通信深度规划

- 全图是正式范围；当前 nearby collector 不满足目标。实际 Core 4.1 可从 Simulator/Siding 遍历远处实际车辆，无须强制加载 Minecraft 区块。新增 `docs/plans/global-train-sync.md`，并在旧方案顶部指向更新方案。
- 推荐全网概要 2Hz＋地图视口/选中列车 4Hz 进度，原生 RailMath/有序路径/编组定义按 revision 缓存，客户端纯数学沿轨重建车厢；两个地图提交其查看维度，不按玩家距离过滤。明确 G1 全图标记不等于完成全图每节车厢，须 G2/G3 验收。
- 核对 Create mc1.21.1/dev 订阅/共享集合与实际 codec，未声称 light 必然少传全部文本。文档包含能力、独立动态 v1、Simulator 线程采集、有界队列、原子分片、失效/成员关系/删除、PIDS 按需共享、带宽计算和性能目标。
- 本轮仅设计与源码/字节码核对，未实现动态流、未运行新功能构建或基准。版本保持 1.5.2，静态协议保持 v7；无服务端能力才回退附近。

## [2026-10-20 00:06] Codex — 按用户要求恢复并固定 1.5.2

- 用户明确要求“版本号仍然 1.5.2，我说变你再变”。已恢复构建版本及当前文档，AGENTS.md 改为仅用户明确要求才变更版本，功能提交不自动递增。
- NeoForge/Fabric 已重建，检查两个 JAR 内声明版本均为 1.5.2；正确测试包在 `test-builds/v1.5.2-train-height/`。上一条 1.5.3 交付记录已被本次更正取代，功能和协议 v7 保持不变。

## [2026-10-20 00:04] Codex — 真实 XYZ 绘制交付记录

- 功能提交 `45cc002`；37 项测试零失败，最终 NeoForge/Fabric 构建通过，已复制 MC 1.21.1 测试包及合成预览至工作区 `test-builds/v1.5.3-train-height/`。未创建发布或修改 MC 1.20 兼容分支。

## [2026-10-19 23:52] Codex — main v1.5.3 真实 XYZ 附近车厢与高度排序

- 用户要求先做绘制，本次不接 ETA、不实现全网列车动态流。MTR 原生 getVehicleCarsAndPositions/PositionAndRotation 保留真实车体 XYZ、坡度/侧倾及转向架偏移；悬停显示线路、目的地、车型 ID、车厢序号和 XYZ，标注附近数据。
- 轨道采样保留真实 Y，快照 v7 double XYZ、独立 _v7 通道；静态纹理连接点也区分高度。两地图改用 HeightMapScene，使上层轨道/线路覆盖下穿车厢，遮挡后的车辆不被悬停选中。trainCars 默认启用，可通过客户端 config 命令关闭。
- 高度排序为坡道细分的画家算法，不是像素级三维深度，也不含地形、建筑或隧道顶板遮挡。仅玩家所在维度附近车辆，MTR 客户端模拟外的列车暂不显示。
- 37 项测试及 NeoForge/Fabric 构建通过；合成 XYZ 无头预览已检查，未启动游戏或做性能基准。见 docs/validation/train-height.md。MC 1.20 worktree 未修改。

## [2026-10-19 20:50] Codex — 列车车厢、运行线路和 ETA 深度规划（未实现）

- 新增 `docs/plans/train-map-design.md`；核对实际 MTR 4.1.0-beta.2 JAR，而非把旧 Core JAR/master 当作当前依赖。
- 当前已公开 Siding.iterateVehicles/getVehicleById，4.1姿态结构与4.0不同；railProgress仍受保护。ArrivalResponse未保留实际车辆、Siding、班次偏移及完整停靠 occurrence，需要先验证稳定桥接/唯一关联。
- 路径和编组定义＋进度流为首选正式方案，逐车厢姿态作原型及对照。现有二维 MapTrack 长度、去重 MapRoute.trackIds、静态纹理方向均不能直接当列车路径。
- P0身份/API验证 → P1附近车厢 → P2全网动态流 → P3可靠ETA → P4兼容。ETA为Core预测，显式区分时刻表/无数据/过期；不按距离÷速度或线路颜色猜测。
- 更新旧列车待办范围；未修改功能源码/版本，未构建、运行游戏或做性能基准。文档中的频率、带宽、耗时是方案参数和验收目标，不是测试成绩。

## [2026-10-18 23:50] Codex — main v1.5.2 三种路线样式

- 代码 commit：`5dce52c`。
- Core 路线类型贯通服务端、客户端回退、快照与 palette。普通实线；轻铁 70%；高铁 140%，彩白交替矩形块及连续线路色描边。
- Xaero/JourneyMap 共用纯 Java 几何；混合共线按类型宽度分配，连续链累计块相位、反向轨道保持顺序、闭环无相位接缝；低缩放或色带过窄时简化。
- 快照协议 v6，四种 payload 改用 `_v6` 通道，防止 Fabric 新旧包误解码；NeoForge registrar 同步升级。全网视图需要匹配的客户端/服务端版本。
- NeoForge/Fabric 构建通过，29 项测试通过；`tools/RouteStylePreview.java` 用实际几何生成预览。未启动 Minecraft，游戏内验证待 Ben 完成。
- 仅 main；兼容 worktree 保持 v1.5.1。详情见 `docs/validation/route-styles.md`。

## [2026-10-17 13:27] Codex — README 增加开发路线图

- 中英文 README 按用户指定顺序列出：TSC 的 light rail / rail / highspeed 三种线路样式；列车位置与站点、车体预计到站时间（参考 Create）；显示效果优化。
- 所有条目均标为未完成计划，不承诺版本或日期；仅修改文档，未改版本号。

## [2026-10-01 23:52] Codex — README 平台信息与捐赠入口同步

- 按最新 GitHub v1.5.1 发布同步中英文 README：1.21.1 NeoForge/Fabric、1.20.1 与 1.20.4 Forge/Fabric 六个 JAR，列出对应 Java/MTR/地图依赖，移除过时预发布说明。
- 增加 Modrinth/CurseForge cozy badge 和 Ko-fi、自制爱发电 badge；资产在 `docs/assets/badges/`。官网在下载后加入捐赠板块，中英文内容、桌面与 390px 窄屏已检查，控制台无相关报错。
- 未修改模组源码、依赖、版本或发布附件。官网文件位于独立目录 `../mtrsurveyor-site`，尚未部署。

## [2026-09-25 00:43] Codex — 第二轮代码审查与优化

- `MapTrack` 在构造时计算包围盒；Xaero 整轨视口/悬停剔除和 JourneyMap 共用包围盒，减少每帧逐段检查。轨道采样遇到非有限坐标时丢弃该轨道。
- JourneyMap 前景图标在纹理、颜色与透明度未变化时合批，减少逐图标 `flush()`；变更状态前仍刷新以保持叠放顺序和着色。
- 新增独立分块重组器，校验错序/重复/越界块、哈希和总大小；快照解码按剩余字节约束元素计数。无服务端支持时探测遵循退避，切服清除客户端回退缓存。
- 代码 commit `d63ea19`；NeoForge 与 Fabric `build --no-daemon`、相关单测通过。协议字段和版本未变，尚未做 Minecraft 实机验证。

## [2026-09-25 00:32] Codex — 共用轨道采样与寻路热路径优化

- 服务端快照的 TRACK 几何一次采样后供 Depot 实际路径和无 Depot 回退路线复用；没有回退路线时不构建铁路图。
- 客户端车辆路线复用当前 TRACK 几何；Dijkstra 遍历不再为每个节点分配过滤边列表，建图时复用已知轨道方向；修正路径片段采样上限的一点越界。
- 新增共享几何及缺失轨道的单测；NeoForge 和 Fabric 的 `build --no-daemon` 均通过。代码 commit `78ae6fc`；未改协议或发布版本，未进行 Minecraft 实机验证。

## [2026-09-23 21:12] Codex — 版本更正为 v1.4.6 并重新发布
- 按 Ben 指示保留版本 1.4.6，没有继续升 patch。以 v1.4.4 为上一正式版核对 git diff，发布说明只列出此后新增/变化的内容。
- 正式 release：https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6，附 NeoForge/Fabric 两个 JAR；tag 指向 8357133。
- 误发的 v1.4.7 预发布及 tag 已删除，旧链接和附件不可恢复；源码提交仍在 git 历史中。两平台无头构建通过，实机验证由 Ben 完成。

## [2026-09-23 15:36] Codex — v1.4.7 双平台预发布完成
- GitHub Release: https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.7；附 NeoForge 与 Fabric 两个 JAR，tag 指向 f41e62e。
- 两平台构建、JAR 元数据及跨加载器依赖静态检查通过；NeoForge 测试通过。未启动 Minecraft；Fabric Xaero 图层与跨端联机由 Ben 手动验证。

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
