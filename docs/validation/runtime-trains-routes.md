# NeoForge 1.21.1：列车缺失与线路接头实机调试

操作者：Codex，2026-10-21 12:53（Asia/Taipei）。开发版本仍为 **1.5.2**，静态协议仍为 v7，绘制仍为纯 2D。

## 复现与原因

- 已实际运行 `gradlew runClient -Pquickplay=TestWorld --no-daemon`，加载 NeoForge 21.1.249 / MTR 4.1.0-beta.2 / Xaero World Map 1.45.0 / JourneyMap 6.0.8。使用 computer-use 查看游戏，实际打开两种地图。
- 原 TestWorld 的 siding 没有车型、maxVehicles=0；原生成器的轨道端点方向还导致站台失效。它不能验证车辆显示。已先备份 MTR 存档到 `build/runtime-debug/backup-20261021/mtr`，修复成三站、两段 S 弯、三车厢测试场景；Core 原生寻路结果为 SUCCESSFUL，3 个有效站台、6 条轨道。
- 实际客户端在出生点取得 0 vehicles；传送至车库后取得 1 train / 3 cars / 0 failures。因此远处缺车是数据边界，不能解释为所有渲染钩子都失效。
- 实际加载 JAR 的 SHA-256 为 `ff2b753d03f2bcc000d053a9014e4250c8ba720daa9b69b6899fe7dc26be1efa`。REA 核对包身份，JDK javap 核对 `Vehicle.getIsOnRoute()` 字节码：只是 `railProgress > defaultPosition`。此前过滤排除了真实存在、可定位的待发车辆。
- 跨物理轨道接头原本分别计算法向，且用浮点端点直接作图身份；曲线微小尾差会重置线路方向和白块相位。共线数量变化时直接改变带宽，同色往返线路还可能重复占带。
- Depot 将全部 routeIds 提前标成“已用真实路径染色”，但共站台选路可能只实际染其中一条，使其余线路跳过回退而缺失。

## 修改

- 显示所有已有合法姿态的附近 TRAIN，保留车型/车厢尺寸/路径验证。无当前营运线路时使用中性色；打开 debugLog 后每 5 秒输出采集计数和第一个真实 XYZ；异常限频 warn 带堆栈，不再静默丢弃。
- 缩远后的整列标记增加黑边、白边和彩色中心，与同色线路区分；车厢仍使用真实尺寸和姿态，不引入 Y 排序或下穿判断。
- 端点按 0.0001 格精度归一并处理负零；共享有限 miter 截面。岔口优先延续共享线路最多、方向相容的主走廊，避免主线重新开始相位和交换色带侧别。不同真实 Y 的交叉仍不误连。
- 共线带宽变化在最长 12 GUI 像素内渐变（不超过轨道长度一半）；长直线也在渐变边界切分。高铁白块保留累计里程相位与彩色边框；底衬同步渐变。线路改为不透明，减少接头重复混色。
- 同色且同样式的不同 Core routeIds 只画一条色带，站点/线路悬停元数据仍保留各条线路。只有实际选中的 Depot 线路抑制寻路回退。
- TestWorldGeneratorTest 改为显式 `-PgenerateTestWorld` 才执行，普通 build/test 不再重写运行存档。

## 验证

- 普通 NeoForge build：53 项通过，生成存档用例 1 项跳过；此前显式生成构建中 54 项全部通过。Fabric build 通过，既有 Xaero 可选方法重映射警告仍存在；本次未启动 Fabric。
- 新回归用例覆盖曲线尾差/负零、不同高度不误连、跨轨道共用色带边缘、长线宽度渐变、岔口主线相位、同色去重和缩远标记对比度/拾取。
- Xaero 与 JourneyMap 均实际显示 3 节停放车辆；悬停第二节显示车型 `m_train_end_1`、`2/3`、XYZ `540.50 / -59.00 / 0.50`。两张地图的红白高铁 S 弯、绿色轻铁均已查看。静态快照恢复为 3 routes / 6 rails / 7 landmarks。
- 最后重载复验不透明线路和缩远标记轮廓；调试客户端保持打开。双平台 1.5.2 测试包、SHA-256 清单与原生 F2 截图保存在仓库旁 `../test-builds/v1.5.2-runtime-debug/`，悬停截图分别为 `xaero-car-hover.png`、`journeymap-car-hover.png`。

## 尚未覆盖

- **全网动态列车流仍未实现**，目前只读 MTR 本来发送给玩家的附近车辆。远处车辆缺失已复现，后续工作见 [global-train-sync.md](../plans/global-train-sync.md)；不能把本次截图当作全图车辆验收。
- 实机验证车辆为待发/停放状态；尚未完成营运车辆长时间移动、当前线路/终点站切换、跨维度、远坐标和大型复杂编织岔口验收。测试车辆未处于营运线路时显示空线路/目的地，未虚构 ETA。
- 不包含 PIDS ETA、全网动态通信或游戏 FPS/内存性能基准。新接头布局随静态缓存失效构建；正常姿态更新仍只重建车厢。
- 日志有 MTR 自身 `transparent.png Invalid model!`，未观察到 MTR Map Overlay 采集/渲染异常。Xaero 单人地图会暂停集成服务器，不能把静止画面当作移动插值测试。
