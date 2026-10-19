# TASKS.md — 任务看板

> 认领规则：把任务改成 `[进行中]` 并写上操作者+时间；完成后改成 `[完成]`（附 commit）。
> 中途放弃改回 `[待领]` 并注明原因。新任务加到对应分区末尾。
> 同一时间一个任务只能有一个操作者。改别人的任务前先在 MESSAGES.md 沟通。

## 进行中


### [功能，部分实现] 列车车厢、运行线路与到站预测
- 全图为用户明确要求；附近仅回退。P2 细化方案：[global-train-sync.md](../plans/global-train-sync.md)。2026-10-20，Codex；下一步 G0 权威适配→G1 全图概要→G2 远处完整车厢→G3 异常/性能验收，尚未实现。
- 详细设计：[train-map-design.md](../plans/train-map-design.md)。2026-10-19 23:52 已实现附近真实 XYZ 车厢及高度绘制；全网动态流和 ETA 待实现。
- 先做 P0 实际 Core 4.1 API、this/next 语义和车辆/班次/停靠身份验证；再依次做 P1 附近车厢、P2 全网动态同步、P3 可靠 ETA、P4 兼容验证。
- 位置采用有序原生路径里程及编组定义，不能直接沿二维地图线或按线路颜色定位；ETA 不能仅用 departureIndex/routeId 关联。
- 验收、性能预算、协议与回退边界见设计文档，均尚未实测；不能沿用旧“小圆点预计工作量小”的判断。

### [功能] 非 TRAIN 轨道渲染开关
- `TrackSampler` 目前只采样 TRAIN 模式轨道。增加配置项按 TransportMode 过滤（BOAT/CABLE_CAR 可选）。
- 验收：配置切换后地图轨道层随之变化。

### [健壮性] Xaero mixin 未命中时显式提示
- `require = 0` 静默跳过导致用户不知道路径层失效。方案：MixinPlugin postApply 已有日志；
  补一个"应用了 mixin 但首帧日志 60 秒未出现"的启动后检查日志（或 README 已有说明，评估是否足够）。

### [文档] walkthrough.md 英文化（可选）
- 面向国际用户的英文版 walkthrough（当前为中文）。

## 完成

- [完成] 全图列车服务器/客户端通信深度规划 — Codex，2026-10-20 00:25；核对实际 Core 4.1 JAR、现有协议/线程和 Create 分支源码，新增 `docs/plans/global-train-sync.md`，明确完整覆盖、原生几何/进度、独立动态 v1、租约/分片/背压、带宽估算及 G0–G4 验收。仅文档，未实现动态流/运行新功能测试；版本仍为 1.5.2。

- [完成] 恢复并固定开发版本 1.5.2 — Codex，2026-10-20 00:06；用户明确要求仅由用户决定何时变更版本。已恢复 mod_version、当前文档并更新 AGENTS.md；双平台重建通过，包内元数据均为 1.5.2，测试包见 `test-builds/v1.5.2-train-height/`。

- [完成] 真实 XYZ 车厢绘制与高度叠放 — Codex，2026-10-20 00:04，commit `45cc002`；main v1.5.2，MTR 原生车体姿态、附近车厢悬停 XYZ、轨道 double XYZ 协议 v7、两地图共用高度排序及下穿遮挡。37 项测试、NeoForge/Fabric 构建和无头几何预览通过；未游戏内实测，不含地形遮挡、全网列车同步或 ETA。

- [完成] 列车车厢、运行线路与 ETA 深度规划 — Codex，2026-10-19 20:50；核对实际 MTR 4.1.0-beta.2 JAR 和当前 main，新增 `docs/plans/train-map-design.md`。确认公开车辆遍历、ETA关联上下文缺失及现有二维地图模型边界；制定 P0–P4 验收与性能预算。只改文档，未改功能、版本，未运行新功能测试。

- [完成] 三种线路样式 — Codex，2026-10-18 23:50，commit `5dce52c`；main v1.5.2 开发版，Core 路线类型、协议 v6、客户端回退、Xaero/JourneyMap 共用几何；轻铁细线、高铁宽彩白矩形块及连续色边，共线宽度和纹理缩放简化。NeoForge/Fabric 构建、29 项单测及无头几何预览通过，待游戏内验证。MC 1.20 兼容分支未改动。

- [完成] 中英文 README 开发路线图 — Codex，2026-10-17；依次规划 TSC 三种线路样式、列车位置与站点/车体预计到站时间、显示效果优化；仅文档修改。

- [完成] 官网捐赠入口与 README 发布信息同步 — Codex，2026-10-01 23:52；中英文 README 更新 v1.5.1 六个附件的版本/依赖矩阵及下载、捐赠 badge，官网新增 Ko-fi 与爱发电板块；官网构建与桌面/390px 浏览器检查通过。未改模组代码。

- [完成] 第二轮审查：轨道视口剔除、JourneyMap 图标批处理、分块重组校验及跨世界缓存清理 — Codex，2026-09-25 00:43，commit d63ea19；NeoForge/Fabric 构建和单测通过，待实机验证。
- [完成] 共用轨道采样缓存与寻路热路径优化 — Codex，2026-09-25 00:32，commit 78ae6fc；NeoForge/Fabric 构建与单测通过；仅改数据采集和寻路，不改协议格式。
- [完成] 双平台 v1.4.6 正式发布，发布说明按 v1.4.4 差异重写；误发的 v1.4.7 预发布及 tag 已删除 — Codex，2026-09-23 21:12，commit 8357133；NeoForge/Fabric 构建通过，MC 留给 Ben 手动验证。
- [完成] Fabric 1.21.1 并行构建与 NeoForge 双平台构建 — Codex，2026-09-23 15:36，commit f41e62e；源码保留，更正后以 v1.4.6 发布。
- [完成] Xaero 站点/站台图标与轨道共用地图变换，修复 Xaero/JourneyMap 站点纹理裁切 — Codex，2026-09-23 14:09，v1.4.6；`gradlew build` 通过，游戏由 Ben 手动验证。
- [完成] 地标改为地图内小图标，禁止写入普通路标系统 — Codex，2026-09-23 01:37，v1.4.5，commit 4b6146d；build/test 通过，MC 由 Ben 手动验证
- [完成] 全图站点/站台持久航点 + 共线轨道彩虹带 — Codex，2026-09-22，v1.4.4，commit 6a95614；build/test 通过，MC 由 Ben 手动验证
- [完成] ROUTE 直接复用 TRACK 的 MapTrack 数据、drawPolyline、线宽与透明度 — Codex，2026-09-20，v1.4.3，commit 46a87b9；build 通过，MC 由 Ben 手动验证
- [完成] 无 Depot 路径时严格贴轨回退（同 TrackSampler 采样；断路不画直线）— Codex，2026-09-20，v1.4.2，commit e436796；无头测试通过，MC 由 Ben 手动验证
- [完成] 版本哈希探测热更（协议 v3）+ 路线颜色改用 MTR 真实寻路结果（染色渲染） — ZCode，2026-09-20，v1.4.1
- [完成] 路线层沿轨道寻路吸附 + 共线车道偏移（协议 v2，实机验证） — ZCode，2026-09-20
- [完成] 统一版：JourneyMap 集成并入主 mod（v2 API，实机验证通过） — ZCode，2026-09-16
- [完成] NeoForge 1.21.1 移植 + 实机验证 — ZCode，2026-09-05，commit fa8ff07
- [完成] 全网同步（方案C）+ 轨道层 + 路径层重建 — ZCode，2026-09-05，commits 1e9ef5f..100bfab
- [完成] 回退事故排查与仓库健康验证 — ZCode，2026-09-16（HEAD 无损，详见 AGENTS.md §4）
