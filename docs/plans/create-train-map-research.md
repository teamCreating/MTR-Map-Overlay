# Create 列车地图源码研究：实现与 MTR Map Overlay 可借鉴点

日期：2026-10-21（Asia/Taipei），操作者：Codex。MTR Map Overlay 基线 `0cf9c95`，版本保持 **1.5.2**。

研究固定在 Create `mc1.21.1/dev` 提交 **`7ff2760e4924d35d4bc2e22c4b19923f0178cd16`**。这是源码快照，不代表某个已发布 JAR；没有启动 Create/Minecraft 或测量运行性能。下文区分“源码事实”和“我们的设计判断”，不把静态分析推测写成实测结果。

## 1. 结论

Create 以全局逻辑铁路数据为基础：编组/名称/轨道图先同步，打开地图时另订阅动态车厢锚点，客户端插值并绘制简化车厢图集。静态轨道缓存为纹理，列车和悬停单独处理。它不是靠附近实体扫描实现全图，也没有使用“路径定义＋列车进度”的地图流。

最值得借鉴的是订阅生命周期、定义与动态状态分离、共享全网采集、静态轨道缓存和网络更新/渲染帧率分离。需要保持我们自身方案的是 Core 模拟线程安全、局部真实高度、远坐标精度、可靠的分片语义与 PIDS ETA。

## 2. 源码路径与调用链

以下链接全部固定到上述提交。

| 边界 | 主要源码 | 作用 |
| --- | --- | --- |
| 玩家登录、全局集合 | [GlobalRailwayManager](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/content/trains/GlobalRailwayManager.java#L58) | 登录时发轨道图、信号组和所有列车定义；管理全局 trains |
| 列车定义 | [AddTrainPacket](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/content/trains/entity/AddTrainPacket.java)、[Train](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/content/trains/entity/Train.java#L89) | UUID、编组、车间距、名称、颜色等建立客户端对象 |
| 地图入口 | [TrainMapEvents](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapEvents.java)、[XaeroTrainMap](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/XaeroTrainMap.java) | 检测地图、开关和输入，提交请求及坐标变换 |
| 服务端动态流 | [TrainMapSync](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSync.java) | 订阅表、定时采集、位置/状态 entry 和 codec |
| 动态包 | [TrainMapSyncPacket](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSyncPacket.java)、[RequestPacket](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSyncRequestPacket.java) | 空请求续订；回复 light 标志＋UUID/entry 集合 |
| 客户端缓存 | [TrainMapSyncClient](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSyncClient.java) | 前后状态、到包时刻、整集合对账和关闭清理 |
| 地图内容 | [TrainMapManager](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapManager.java) | 车厢插值/图集/悬停、轨道栅格化、交叉阴影 |
| 静态缓存 | [TrainMapRenderer](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapRenderer.java) | 128×128 分块纹理、可见范围剔除、按需上传和释放 |
| 图同步/修复 | [TrackGraphSync](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/content/trains/graph/TrackGraphSync.java)、[RollCallPacket](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/content/trains/graph/TrackGraphRollCallPacket.java) | 图增量、完整图分批、校验与按图重取 |

调用链为：地图适配器 tick → TrainMapSyncClient.requestData → requestReceived → 服务端 tick 生成全网 entry → Packet.handle/receive → Manager 插值并绘制/拾取。另一条登录/图同步链提供编组与铁路图，不能只拷贝动态包就期待地图能画完整列车。

## 3. 全图数据与订阅

**源码事实。** 动态 send 遍历 `Create.RAILWAYS.trains.values()`，未按玩家距离、视口或玩家维度过滤。在轨位置从 carriage 的前后 TravellingPoint 沿逻辑 TrackGraph 求得；脱轨才尝试现有车厢实体的 rotation anchors。每节车厢另带维度，跨维度边界或无法定位时可标为 null。因此“全局逻辑列车”和“附近已渲染实体”是两个不同数据来源。

客户端开地图且叠加层启用时，每 5 客户端 tick 发一次空请求。服务端用 20 tick 的 TickBasedCache 续订；地图关闭后客户端立即清状态并停止请求，服务端等待租约过期。不使用可靠的显式退订消息作为必需条件。正常 20TPS 下约每 0.25 秒续订，租约约 1 秒；TPS 下降时不能仍当作固定墙钟秒数。

定时位置发送每 5 服务端 tick 一次，10 tick 时选择 full，其余为 light；正常 20TPS 下总计约 **4Hz**，其中 full/light 各约 2Hz，不能加成 6Hz。无订阅者提前返回，正常一轮构建一个逻辑 packet 后发给全部订阅者。首次新订阅还会立即调用全局 send，连续多人开图可能额外触发整轮采集；它不是每次 C2S 续订都全采集。

**可借鉴。** 采用全局逻辑实例、短租约、无人订阅零动态导出、一次采集服务多玩家。我们改用查看维度/视口控制细节，并把首开请求合并进共享调度器。Core 在自己的线程运行，不能照搬 Create 在服务端 tick 里直接读取自身铁路对象的方式。

## 4. 位置包与依赖：它实际传了什么

**源码事实。** 每节车厢动态位置为两个 XYZ 锚点，即六个 float，裸坐标 **24B/节/帧**，不是六个 double。其余 entry 包含逐车厢维度、运行/信号状态、方向、燃料、目标站距离、拥有者/目标站文本和等待列车 UUID。列车名称、编组及 bogeySpacing 等来自先前的 AddTrainPacket，而非这些位置字段自行生成。

light/full 共享同一个 entry codec：文本和 waitingForTrain 仍声明在 codec 中；createEntry 也不因 light 跳过这些字段的采集。light 的明显区别在 updateFrom：客户端不覆盖一部分低频显示字段。因此，源码不能支持“light 包已经省掉文本带宽/采集成本”的说法。最终压缩和实际编码 CPU 未实测，也不能由一个共享 packet 对象推导出底层只编码一次。

按裸坐标算，1000 列×8 节×24B×4Hz = **768kB/s/客户端**；100 列同编组为 76.8kB/s。这是不含 UUID、维度、状态、文本、包头和压缩的算术，不能当成实际服务器流量。也不能拿它直接对比我们之前“double 两点=48B”而不说明精度差异。

**可借鉴。** 位置、编组、服务元数据拆开；一列只传一次名称/服务，车厢通过稳定索引关联。我们的模型只导出地图必要信息，不复制所有 Core 行车对象。

**需要区别。** MTR 的双转向架姿态不等于 Create 的两个锚点：Core 可能为两个 bogie 各给前后方向采样点并含侧倾，不对称 bogie 还影响车体中心。直接只拿两点中值，会丢失原生语义。原生姿态原型先保留这些数据，测完后再选择公共纯数学 body pose 或进度编码；不可直接按 24B 宣称已能精确传输 MTR 车厢。

## 5. 插值与删除

**源码事实。** 每次 receive 记录本地到包时刻，将已有坐标保留为 prevPositions。绘制时用“距最近到包经过的客户端 tick / 5”并夹到 0–1，前后锚点做 Vec3 线性插值；到下包前显示平滑过渡，没有持续无界外推。维度改变时把旧锚点改为新锚点，避免跨维度连线。

收到一轮 entries 后，以完整集合清掉没有出现的 UUID。关闭地图将接收状态清零，迟到回复被忽略。这个设计依赖每个位置包包含完整集合；该动态 codec 自身没有应用级分片、采样时间、订阅 generation 或增量作用域。

**可借鉴。** 网络采样频率与画面帧率分开；保留前后状态、避免跨维度插值、关闭后拒绝迟到数据。

**需要区别。** 本地到包时刻不能校正服务端卡顿和抖动；两点 XYZ 直线插值可能切弯。MTR 高速、小半径、折返等情况下要验证误差，正式进度模式沿真实路径插值。我们的分片/视口/增量协议只能在完整作用域成功提交后按缺失清理；普通增量里没出现某列，不能借用 Create 的整包删除规则。

## 6. 车厢、悬停与高度

**源码事实。** Manager 用前后锚点确定方向，图集有八档朝向，车头/车尾/中段按 bogeySpacing 拼出近似长度。它没有在地图里绘制完整 contraption 模型。悬停采用近似矩形范围并返回整列 Train；目标站与距离来自导航状态，所读地图详情路径没有 ETA 字段，也没有以距离/速度计算 ETA。

真实 Y 有参与显示，但分成两套近似机制：静态轨道把每约 4 格的高度等级存入 alpha 通道，比较等级保留较高像素，对高度碰撞附近增加阴影；动态车厢采用整列平均 Y 再叠加地图 Z 相关偏置调整 GUI 层次。纹理平面本身的顶点 Z 为 0，车厢使用另一个正向 GUI 层；没有将轨道和逐车厢局部真实高度统一成物理深度。

**可借鉴。** 简化图形而不渲染复杂模型、整列共享 tooltip、交叉处的下层暗化/桥缘可读性。

**不能照搬。** 用户要求坡道/上下穿的局部准确遮挡，我们应保留每段 Y、现有 HeightMapScene 的可见顺序及拾取一致性，不能改回整列平均高度。其静态量化高度与 GUI 层次也不能证明能正确遮住高架下方列车。Create 近似 hover 先命中即返回，并不自动等于实际最上层可见车厢。MTR 名称/目的地及 PIDS 预测继续来自 MTR，不能从 Create 的站距显示推导其已有同类 ETA 实现。

## 7. 静态轨道缓存：最值得补到我们这里的性能设计

**源码事实。** 轨道图 version、查看维度或主题变化触发 redrawAll；先背景、后直线/曲线，再交叉阴影。TrainMapRenderer 把绘制结果放入 128×128 DynamicTexture 分块，正常帧只绘制可见分块。CPU 图像更新后标记待上传，实际 draw 时再上传，未触及分块会释放。当前版本变化时整体重画，不能说它已做“精确到受影响分块的增量更新”。

一块 RGBA 图像约 64KiB，GPU 存储和对象开销另计；缓存可以省每帧重建，但分块很多时仍需内存预算和释放。它还使用非透明混合的专用 RenderType，alpha 的高度编码不能当作普通透明度直接挪进我们的材质。

**我们的现状。** HeightMapScene.build 每帧重新枚举轨道，生成/分配可见四边形，再把静态轨道和动态车厢一起排序。视口剔除减少几何量，却没有复用上一帧的静态几何。在全网车辆接入后，只优化网络不足以避免绘制端开销。

**建议。** 优先缓存静态几何片段、包围盒、高度排序和空间索引，按地图数据 revision、样式、缩放档失效，渲染帧只查询可见片段并合并少量动态车厢。不要把所有静态内容只烘成一张无高度背景：这样低处列车会失去被高处轨道遮挡的机会。后续若做纹理，需保留独立高度/遮挡信息或按高度分层，并验证并线彩白纹理的缩放表现。

因此借鉴的是“静态结果缓存、动态独立更新”的边界，不必照搬它的像素栅格或图集美术。

## 8. 地图适配与铁路图自修复

**源码事实。** Xaero 和 JourneyMap 适配器分别处理地图开关、坐标变换和可见范围，复用 Manager。Xaero 显式取正在查看的维度。轨道图同步与位置流分离：登录完整图分批、运行中图变更合批、后续 roll call 对 netId/checksum；客户端缺图或校验不一致会按图请求重取，删除多余图。

**可借鉴。** 地图宿主只提交上下文，数据流和几何求解共享；用定义清单/内容摘要发现失配并按缺失定义修复，不为一辆车反复拉整个网络。

**需要区别。** 我们继续使用已验证的 JourneyMap 仿射投影/拖动处理和 double 相对相机坐标，不因源码可参考就替换成其他版本依赖的坐标公式。两个地图共用一个订阅控制器，减少适配器切换时重复请求/清缓存的机会。图协议的分批/校验不代表 Create 动态列车包也自动具有相同语义；动态流的独立原子提交仍需我们实现。

## 9. 对上一轮全图规划的调整

此前正式目标“全网概要＋视口细节、可缓存路径/编组＋进度”仍有规模上的价值。源码研究带来的调整是：**先增加全网原生姿态端到端原型，实测后决定何时需要进度优化，不把复杂路径重建当成看见远处列车的前置条件。**

| 顺序 | 现在建议的交付 | 原因 |
| --- | --- | --- |
| A | 在 Core 模拟线程枚举全网，订阅全图；先用受控场景原生 bogie 姿态跑通服务端→客户端→两地图 | 直接证明远处每节车厢和现有绘制是否正确，建立进度求解的真值基准 |
| B | 独立编组/服务定义、完整作用域原子分片、租约和来源切换；增加静态几何缓存 | 这些收益不依赖选择哪一种位置编码，也不能延后到性能失控才补 |
| C | 实测 100/1000/5000 列及多订阅者，比较全网原生姿态、概要＋视口姿态、概要＋进度 | Create 证明直接锚点方案可以实现功能，没有证明它适用于 MTR 的所有编组规模 |
| D | 达到带宽/CPU 阈值时启用共享路径和进度；保留原生姿态作对照/特殊车辆回退 | 省下持续按车厢发送的流量，但新增 accessor、几何修订和沿轨求解成本要值得 |

原型不是将全网轨道静态快照每帧重发，也不是交付“只有全图小点”后结束。用户的完成条件仍是任意远处放大后完整车厢、局部真实高度、悬停及异常恢复通过。原生姿态模式也必须预算化：初始受控 2Hz，不承诺对每个玩家无限发送所有车厢。

**可直接借鉴的优先级：** 地图订阅/租约、全网共享采集、定义与状态分开、静态缓存、平滑渲染。

**必须改造的部分：** Core 线程、MTR bogie/body 语义、远坐标 double 或局部原点编码、真实 Y 和可见拾取、原子分片/修订/删除、服务状态/PIDS。

**本轮未做：** 没有移植 Create 代码/纹理，没有改运行功能、网络包或版本，没有性能实测。以上实现建议与预算不能当成已达成指标。
