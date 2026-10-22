# TASKS.md — 任务看板

> 认领规则：把任务改成 `[进行中]` 并写上操作者+时间；完成后改成 `[完成]`（附 commit）。
> 中途放弃改回 `[待领]` 并注明原因。新任务加到对应分区末尾。
> 同一时间一个任务只能有一个操作者。改别人的任务前先在 MESSAGES.md 沟通。

## 进行中

### [待领，性能/兼容后续] 全网列车大网与复杂网络性能
- 2026-10-21 20:09，Codex：全图简化锚点功能、双地图/加载器接入、异常边界和NeoForge单列远处运行验证已完成，见[验证记录](../validation/global-trains.md)。仍需真实1000/5000列、多观看者、复杂网络、高速小半径、跨维度的性能/复杂网络矩阵（六平台小网格功能回归已完成）；不能把codec大小当成FPS或总出站实测。
- 继续使用TSC全网实测锚点，客户端无模拟/沿轨求解；版本1.5.2/静态v8/动态v1/纯2D保持。距离/ETA暂停，旧stash不恢复。

### [功能] 非 TRAIN 轨道渲染开关
- `TrackSampler` 目前只采样 TRAIN 模式轨道。增加配置项按 TransportMode 过滤（BOAT/CABLE_CAR 可选）。
- 验收：配置切换后地图轨道层随之变化。

### [健壮性] Xaero mixin 未命中时显式提示
- `require = 0` 静默跳过导致用户不知道路径层失效。方案：MixinPlugin postApply 已有日志；
  补一个"应用了 mixin 但首帧日志 60 秒未出现"的启动后检查日志（或 README 已有说明，评估是否足够）。

### [文档] walkthrough.md 英文化（可选）
- 面向国际用户的英文版 walkthrough（当前为中文）。

## 完成

- [完成] GitHub v1.5.2正式发布 — Codex，2026-10-22 15:01。用户明确授权发布；六个附件沿用版本-MC-MTR-loader命名，附SHA256SUMS.txt及下载环境标签，中英文说明覆盖相对v1.5.1新增/优化/修复，末尾附v1.5.1...v1.5.2 diff。已设Latest并核对远端7附件大小/哈希/正文；main tag v1.5.2，兼容tag v1.5.2-mc1.20。版本继续固定1.5.2。[Release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)。

- [完成] 最新1.5.2移植及六平台回归 — Codex，2026-10-22 10:00，见本条所在提交。兼容分支四目标各89项测试，主线86项共用测试及六构建通过；六平台Xaero/JourneyMap实机通过，修复主线Fabric JourneyMap入口和开发嵌套依赖。Forge1.20.4首轮GLFW原生崩溃，单客户端重试通过，保留报告且根因未定。详见docs/validation/platform-matrix-1.5.2.md；测试包../test-builds/v1.5.2-platform-matrix，版本保持1.5.2。

- [完成] Create 参考车辆显示与视口成本优化 — Codex，2026-10-22 00:34，见本条所在提交。双帧扫掠包围盒索引、共用三级缩放/提前尾车剔除、简化小符号与缩放长度连续；86测试通过/1跳过，双构建通过，NeoForge两地图过弯/缩放/悬停检查通过，Xaero native nearby=0仍全图移动。2000列×8节合成局部查询约0.29→0.002ms，增加低频安装成本；不代表FPS。详见docs/validation/train-display-optimization.md，测试包../test-builds/v1.5.2-train-display。版本1.5.2，TSC唯一模拟/纯2D/全图覆盖保持，距离/ETA暂停。

- [完成] 全图简化车厢同步实施 — Codex，2026-10-21 20:09，见本条所在提交。共享TSC采集、24B/节两锚点、低频内容定义、动态v1/租约/原子分片/队列预算、无模拟插值及双地图/双加载器接入；修复Xaero单机暂停。81测试通过/1跳过，双构建通过，NeoForge native nearby=0仍持续显示1列3节过弯与悬停，125B/帧；仅MTR专服启动通过。详见global-trains.md，大网FPS/Fabric实机待测，版本1.5.2，距离/ETA暂停。测试包../test-builds/v1.5.2-global-trains。

- [完成] Create 参考抽象车厢数据量优化（仅规划）— Codex，2026-10-21 19:25，见本条所在提交。全网方案改为每节两个 XYZ，移除 tilt/完整 bogie 和客户端 3D/光照；坐标项128→24B/节，保留局部原点精度；1000列×8节×4Hz含原点约864kB/s，仅估算。同步历史入口、STATE/TASKS；未改功能代码、未构建/实测，1.5.2不变。

- [完成] 全图原生姿态下发方案重规划（仅文档）— Codex，2026-10-21 19:16，见本条所在提交。直接核对 MTR/TSC 公共源码，重写 global-train-sync.md；固定全网完整姿态、客户端无模拟/沿轨求解、共享采集、完整帧语义、双平台阶段与真实带宽估算。同步旧设计入口及协作状态；仅文档检查，未构建/实测，1.5.2 不变。

- [完成] 高铁与普通 Rail 等宽 — Codex，2026-10-21 17:30，见本条所在提交。高铁权重 1.0，轻铁 0.7，高铁彩白块保留；68 普通测试与双构建通过，版本仍 1.5.2。

- [完成] 接头过渡与 Create 参考缩放 — Codex，2026-10-21 14:39，见本条所在提交。共享截面/实际底衬宽度、结束色带平滑收束及白块防反转；轨道世界宽度随地图缩放、整束一个细线下限。68 普通测试、双构建及 NeoForge Xaero/JourneyMap 检查通过，见 docs/validation/route-joins-zoom.md。版本 1.5.2 / 协议 v8 / 纯 2D 保持。

- [完成] Create 参考列车可辨识样式 — Codex，2026-10-21 14:08，见本条所在提交。最小 7 GUIpx、白色轮廓/深框/线路色侧边/浅顶，实际倒车端金色箭头，缩远定向图标。65 普通测试、双构建及 NeoForge 两地图显示/悬停检查通过。详见 docs/validation/train-style.md；真实 XYZ、纯 2D、版本 1.5.2、协议 v8 保持，全图动态流/ETA 未改动。

- [完成] 细线原生曲线与屏幕误差控制 — Codex，2026-10-21 13:38，commit ec49c1b。NativeRailCurve + 原生切向、屏幕误差细分、逐顶点 alpha 过渡、每 host 有界 LOD 缓存，静态协议升 v8、mod 仍 1.5.2。61 普通测试通过，双构建通过；NeoForge 的 Xaero 高倍绿色轻铁和 JourneyMap 曲线实机检查，详见 docs/validation/native-curves.md。全网动态车辆和 ETA 未改动。

- [完成] NeoForge 1.21.1 实机调试附近列车缺失与线路拼接 — Codex，2026-10-21 12:55，commit 5c98b43。两地图实际显示 3 节停放车辆并悬停读 XYZ；修复状态过滤、缩远对比轮廓、轨道共享接头/主线相位、共线渐变/同色去重及 Depot 回退抑制。53 普通测试和双构建通过，存档生成显式 opt-in。详见 docs/validation/runtime-trains-routes.md；全网动态车辆、ETA、营运移动仍未验收，不计入本次完成范围。版本仍 1.5.2。

- [完成] Create 式静态/动态绘制缓存优化 — Codex，2026-10-21 11:55，commit `22698e0`；PlanarMapCache 与双 host 生命周期、静态/动态 list 零拷贝组合、guard 预取和 100000 quads 保留上限、源弱引用；48 测试及 NeoForge/Fabric 构建通过。合成 120 帧 static builds 120→1、线程分配 673.835→13.665MiB，仅几何阶段，未游戏内 FPS 实测。版本 1.5.2/纯 2D/协议 v7 保持，全网动态流仍待实现；见 `docs/validation/planar-cache.md`。

- [完成] 地图绘制改为纯 2D — Codex，2026-10-21 00:44，commit `1cfb7ba`；固定底衬→色带→白块→车厢，primitive 平面四边形、无高度细分或排序、车厢专用 hover 目标；XYZ/协议 v7/版本 1.5.2 保留。39 测试及 NeoForge/Fabric 构建通过，坡度数量实验均 52000 quads，合成预览检查通过，未游戏内实测。

- [完成] 真实高度下穿遮挡时空复杂度分析 — Codex，2026-10-21 00:32，commit `208cb9d`；报告 `docs/plans/underpass-complexity.md`，生产几何数量工具 `tools/HeightComplexityProbe.java` 编译/运行通过。当前每帧含全输入扫描及 O(Q log Q) 排序、O(Q) 工作空间；合成平地/坡道 52000→209000 quads，不是帧时间实测。明确缓存/索引/独立深度通道的成本与精度边界；运行代码和 1.5.2 不变。

- [完成] Create 列车地图源码研究与可借鉴实现 — Codex，2026-10-21 00:25，commit `6a93a4c`；固定 Create `7ff2760`，追踪全局数据/定义、租约/动态 codec、插值、图集、静态缓存、高度、悬停和图校验修复。报告 `docs/plans/create-train-map-research.md`；更新 G0 顺序与静态缓存优先级。仅文档、未运行功能/性能测试；版本 1.5.2 不变。

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
