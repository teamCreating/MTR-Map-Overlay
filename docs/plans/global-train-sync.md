# 全图列车：服务端与客户端通信设计

> 当前要求更新（用户，2026-10-21）：纯 2D，取消本文旧的高度遮挡验收。真实 XYZ、全图覆盖、服务器/客户端通信和 PIDS 目标保持；当前绘制见 [验证记录](../validation/planar-map.md)。

日期：2026-10-20（Asia/Taipei）。操作者：Codex。基线：main `a5d6100`，MC 1.21.1、MTR 4.1.0-beta.2、NeoForge/Fabric，开发版本保持 **1.5.2**。

状态：深度规划，尚未实现。本文细化并更新 train-map-design.md 的 P2；以全图覆盖为正式目标，附近车辆只是无服务器能力时的回退。本文的频率、大小和耗时均为设计参数或估算，不是性能测试成绩。

2026-10-21 更新，Codex：已固定 Create 源码提交研究，详见 [create-train-map-research.md](create-train-map-research.md)。在 G0 增加“全网原生 bogie 姿态端到端原型”，先证明远处每节车厢并建立真值基准，再按实测规模决定进度优化时机；不把完整路径求解作为全图可见的前置条件。正式可扩展方案仍保留本文的概要/视口/定义边界，静态几何缓存应前移，不能只优化网络。

## 1. 为什么现在只显示附近，怎样才算全图

当前 ClientTrainCollector 读取 MinecraftClientData.vehicles。这是 MTR 发给玩家客户端的附近模拟集合；静态全网轨道快照没有携带远处动态车辆。当前绘制效果再准确，也无法从这个集合恢复没有下发的列车。

服务器的 Simulator 持有 sidings，通过 Siding.iterateVehicles 可以枚举服务器实际存在的列车。全图地图应读取这套权威集合，单独同步，不要求对应 Minecraft 区块、列车实体或玩家追踪范围已加载。全图指服务器当前有模拟实例的列车；未来时刻表班次、已被 Core 撤销的车辆不是正在运行的列车，不能凭空补画。

正式体验与验收定义：

- 地图缩到全网尺度，当前查看维度内每列实际在途列车都有概要标记，包括远离所有玩家的列车。
- 拖到任意远处并放大后，获取该处所有可见列车的完整编组，每节车厢保留真实 XYZ；不以玩家坐标决定同步范围。
- 默认包含运行、站内停车、人工驾驶及可定位的回库列车。待发车库内重叠实例另设可选过滤，不把所有 parked 实例堆到主图。不能继续简单用 getIsOnRoute 丢弃所有非客运营运状态。
- 跨维度按地图正在查看的维度订阅，允许查看与玩家不同的维度；可用维度和权限由服务器能力回复声明。无需默认同时播放所有维度。
- 缺少细节定义时保留真实概要标记并标注“车厢加载中”；不能让远处车辆突然消失。
- 超预算可以降低细节和频率，必须显式显示降级状态和覆盖计数；不能静默只给最近若干列却宣称全图。

服务器必须安装支持动态同步的 MTR Map Overlay。纯客户端无法取得服务器从未发送的全网实时状态；无服务端组件时明确显示“附近车辆”，不扩大 MTR 的正常车辆追踪半径。

## 2. 已核对的依据

### 当前工程与实际 Core JAR

以 Gradle 实际依赖的 NeoForge 4.1.0-beta.2 JAR 为准，缓存 SHA-1 目录 `3c25af1f04b5a9178bafa505ffc2fd265525e60d`，通过 javap 核对签名与相关字节码。

| 核对对象 | 事实 | 对方案的约束 |
| --- | --- | --- |
| ClientTrainCollector | 只读客户端 vehicles，50ms 姿态缓存，限制玩家所在维度 | 不是服务器全图数据源；正式流不能继续调用它作为主来源 |
| ServerNetworkCollector | 已用 Simulator.run 排队读取各维度，随后 server.execute 发送 | 可沿用线程切换机制；不能每个位置更新都调用静态 collectAndSend |
| Simulator.run | 将 Runnable 加入 queuedRuns；实际在 Simulator tick 中处理 | 入队不代表立即采样，必须记录实际执行时的时间，并防止队列积压 |
| Data.getCurrentMillis | 公开读取 Core 时间 | 位置采样以 Core 时间为准，另记录服务端单调时钟用于传输/活性判断 |
| Siding.iterateVehicles | 公开 Consumer<Vehicle> 遍历 | 全网枚举不需要打开私有车辆集合；Sidings 和车辆均在模拟线程访问 |
| Vehicle | 公开 getHeadPositionAndTiltAngle/getVehicleCarsAndPositions/getReversed | 概要 XYZ 与姿态对照可以直接读取；不要依赖 Minecraft 实体跟踪 |
| VehicleSchema | railProgress/speed 等为 protected | 进度流需最小只读 accessor，不能声称现成公共 getter 已够用 |
| VehicleExtraData | immutablePath/immutableVehicleCars 和当前服务元数据公开 | 路径与编组可缓存，但 mutable 对象不能跨线程泄漏 |
| PathData | 公开起止累计里程、顺序端点、reversePositions、getPositionAndTiltAngle | 必须保留有序重复轨道、方向、里程域，不能引用去重的 MapRoute.trackIds 替代 |
| Rail/RailMath | 原生几何含曲线、坡度、侧倾；RailMath 公开构造器及 getPosition/getTiltAngle | 可传最小原生几何参数，客户端重建纯计算对象；现有 8 格静态采样不保证车厢精度 |
| Rail/VehicleCar | serializeData 为 public，几何/车钩参数部分 protected | 首先验证一次性白名单导出；不在高频帧里序列化完整对象，必要时用小范围只读适配 |
| PositionAndRotation | 属于 MTR 渲染层，构造时涉及客户端光照 | 服务端不得调用它；客户端正式求解器分离纯数学和光照 |
| 现有协议 | 静态 v7；NeoForge optional registrar 7，Fabric 显式注册四个通道 | 新动态协议独立协商，不能以 mod_version=1.5.2 推断能力，也不改静态 v7 的字段 |
| MTRSimulatorMixin.sync | 目前只是 super.sync，注释曾称不能服务端广播 | 注释已不适用于已支持服务器的架构；后续不要直接在这里无条件发包，可仅标记定义失效 |

Fabric 实际 JAR 的相同字段/mixin 命中、专用服务器类加载仍需单独验证；本轮未据此宣称已实现兼容。

### Create 可借鉴什么

参考 Create `mc1.21.1/dev` 的 [TrainMapSync](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSync.java) 与 [TrainMapSyncClient](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/compat/trainmap/TrainMapSyncClient.java)。这里是分支源码比较，不是本项目运行验证。

该实现由地图请求维护订阅缓存，从全局列车表生成共享数据；位置使用每节车厢两端的三维锚点，客户端保留前后快照。服务端 light/full 调用间隔为 5/10 tick，客户端请求间隔为 5 tick，订阅缓存为 20 tick。具体 wire codec 仍包含完整 entry 字段，不能把 light 标志直接当成已证明省掉了所有文本字节。收到集合后，客户端清除集合中已不存在的车。

借鉴其“独立地图订阅、服务器全局集合、共享采集、客户端平滑”结构。我们另行设计可缓存定义与进度、全局概要、原子分片和显式覆盖范围；不照搬绝对 float 世界坐标，也不把分片/增量帧里的“没出现”直接当成实体删除。

## 3. 推荐架构：全网概要 + 按视口的完整车厢

两个层级都覆盖远处，细节与玩家距离无关。

| 层 | 范围 | 初始频率 | 内容与作用 |
| --- | --- | --- | --- |
| 全网概要 | 当前查看维度的全部可定位目标列车 | 2Hz | 真实参考点 XYZ、朝向、编组/服务引用、状态和保守包围范围；缩放到全网仍看得到所有列车 |
| 车厢细节 | 地图视口加缓冲区内全部列车，以及选中的列车 | 4Hz | 路径/编组定义的引用、绝对 Core 进度、运动连续性与状态；客户端重建每节车厢 |
| 定义 | 有引用且客户端未缓存的几何、路径、编组、服务文本 | 变化时 | 缓存复用，不随位置帧反复发送 |
| 悬停详情 | 当前悬停/选中对象 | 最多 1Hz | 下一停靠、沿轨距离、PIDS 结果及新鲜度；位置不等待 ETA |

概览包围范围必须覆盖整列车，服务端细节筛选不能只测车头是否在视口：长列车的尾部、弯道上的中段可能已经进入。可先以“参考点 ± 总车长 + 边距”的保守 XZ 包围盒筛选，再用缓存路径范围细化，允许多发一些而不漏车。客户端绘制也以车厢角点做视口剔除。

视口缓冲初始取屏幕边缘 25% 加上速度×约 1 秒的预取距离，并设置合理上限。拖动时合并约 150ms 的更新，最多 5 次/秒；不每个鼠标像素发一次请求。服务端取所有订阅者细节集合的并集，每维度采一次，再按订阅者筛选。

全网缩小时沿用一个标记/列车；放大后逐车厢绘制，位置来源完全相同。密集区域若触及可见车厢预算，退回列车标记并注明细节降级，不丢掉列车。数据完整性和绘制 LOD 分开。

## 4. 身份、定义和高频记录

### 身份与修订

- serverSession：服务器启动生成的随机身份，防止跨重启复用旧缓存。
- dimensionEpoch：对应 Simulator 实例/时间轴，模拟器重建、时间轴异常重置时更新。
- subscriptionId + generation：服务端接受的订阅及其最新范围；客户端切地图维度后旧回复不得生效。
- trainKey：session + dimension + sidingId + Core vehicleId。先保留完整上下文；若以后证明 vehicleId 在维度内全局唯一，再缩减内部索引。检测异常重复并记录，不能无声覆盖。
- trainHandle：本次流内的紧凑整数别名，不复用已删除 handle；首次映射保留 Core 身份。后续优化不每包重复长 ID。
- geometry/path/consist/service 都有 handle + revision。动态引用必须匹配修订，不能拿新进度驱动旧路径。
- motionEpoch：车辆跳转、折返、路径里程回绕、重新部署或不可连续修正时递增；与定义 revision 不同，车辆可以在同一路径上发生运动断点。

sequence 在 overview/detail/membership 各自流内单调递增，不能用不同流的帧号互相淘汰。用共同 captureId/sampleTime 关联同次采样，用对象 stateRevision、membershipVersion 和 motionEpoch 处理跨流一致性；低频概要不得覆盖更新的高频车厢姿态。

### 定义按内容拆开

1. RailGeometryDefinition：端点、端点方向、shape、垂直半径、侧倾参数等构建原生 RailMath 所需的白名单字段；双精度位置和参数，不携带信号占用、玩家、乘客或无关配置。原生参数优先于折线采样，可避免额外近似和静态采样间距限制。
2. PathDefinition：有序 occurrence 数组，每项引用 geometry revision，保留 Core start/end distance、行进方向和必要的路径边界；重复经过同一轨道必须保留。包含重复制式、总长度、合法进度区间等求解必需信息。路径属于车辆实际行程，不属于“某条线路的轨道集合”。
3. ConsistDefinition：有序车型 ID、长度、宽度、两个转向架位置、单转向架标志、两端车钩参数、总长度。车厢 index 在折返时稳定，运动前端单独标记。一次编组定义供多列相同编组复用。
4. ServiceDefinition：当前线路数值 ID、名称、编号、颜色、目的地文本、状态；显示字段独立于几何。贯通换线只需更新服务，不重传所有车厢/轨道。

路径和编组通过受控冷路径导出，建立规范化内容指纹并分配 revision；不会每 250ms 为每辆车遍历整条 immutablePath 算哈希。路径对象身份可作为缓存入口，但不是内容正确性的唯一证据；Core 数据同步/对象替换使相关缓存失效，关联几何需核对。没有可靠变更事件时，先增加共享、限预算的校验轮次并明确验证延迟，不能默认为不可变引用永远正确。

若几何修订尚未验证或定义构建未完成，保留本次服务器实测概要；相关精细姿态暂停或使用有界的原生姿态回退，不拿疑似旧轨道补算。首轮实现必须覆盖铁路编辑/路径再生成测试。

### 高频记录

OverviewUpsert：trainHandle、实测参考点 double XYZ、朝向、速度、状态、service/consist 引用、整列保守范围、对象状态 revision。包头统一 session/dimension/subscription/sequence/sampleTime。预算按 **64 字节/列** 估算，最终 codec 冻结时用实际长度替代；复杂可变字段可能超过预算。

DetailUpsert：trainHandle、path/consist revision、absoluteRailProgress(double)、Core speed（统一转换到米/秒）、reversed、motionEpoch、必要运行状态。预算按 **40 字节/列** 估算，不含一次性定义。进度使用绝对值，不发“比上一包前进几格”这种依赖包包相接的链。

服务信息与成员关系有独立更新；停车没有位置增量不代表车辆消失。全网概要先采用每轮完整记录集合以简化正确性，分片后原子提交；测量确认流量需要后再启用 absolute upsert/delete 增量和周期完整集合。协议预留两者，不在第一版同时引入复杂差分基线。

### 几何回退

原生几何参数重建必须与实际 JAR 做对照测试。特殊/无效路径可在当前视口范围内发送原生 bogie 姿态，或保留概要并标记细节不可用。该回退按可见车厢计费并受预算限制，不扩展成对每个玩家持续发送全网全部车厢。

服务端只能调用 Core getVehicleCarsAndPositions 生成 bogie 数据；不得加载 org.mtr.render.PositionAndRotation。bogie→body 的纯数学过程在客户端独立求解，并与原生渲染姿态对照；不能用“两个转向架中心平均”替代不对称转向架的真实车体中心。

## 5. Payload 和一次完整交互

静态快照仍为 v7。增加独立 `train_*_v1` 通道及动态 capabilityVersion；mod 版本继续 1.5.2，不能用相同版本字符串假设对端一定支持这些新增通道。NeoForge 动态 registrar 与静态分组分离，Fabric 两侧使用相同共享 DTO/codec。

| 方向 | 消息 | 关键内容 |
| --- | --- | --- |
| C→S | TrainHello | 支持的动态/几何 codec、客户端能力与 nonce |
| S→C | TrainCapabilities | 选择的协议、session、允许维度、最大频率/大小、支持的细节与 ETA 能力 |
| C→S | TrainSubscribe / UpdateSubscription | client request generation、地图维度、世界 XZ 视口、LOD、选中 trainKey、已持有定义摘要 |
| S→C | SubscriptionAccepted | subscriptionId/generation、实际范围/频率、是否全维度概要、容量限制与状态 |
| C→S | TrainKeepAlive / Unsubscribe | subscription/generation；续租同时可回报已完成定义与接收状态 |
| S→C | TrainOverviewFramePart | 完整概要集合或绝对增量，frameSeq、part index/count、采样时间、完整性信息 |
| S→C | TrainDefinitionPart | 定义类型、handle/revision、transferId、大小、分片与摘要 |
| C→S | TrainDefinitionsAck / MissingDefinitions | 确认本地已原子安装；未知引用请求去重并限频 |
| S→C | TrainDetailFramePart | 视口细节绝对状态，作用域、membershipVersion 与采样时间 |
| S→C | TrainMembership / TrainRemoved | ENTER/LEAVE_DETAIL、DESTROYED、HIDDEN_BY_POLICY、UNLOCATABLE 等不同原因 |
| C→S / S→C | TrainResync / StreamStatus | 缓存失配、预算降级、源数据失败和刷新状态；恢复明确，不伪装成空地图 |
| C→S / S→C | TrainDetailRequest / Response | 预留按需 PIDS 详情，requestId+trainKey+服务/停靠身份，异步回复 |

逻辑消息可以合并为少量 envelope payload，不要求真的创建同样数量的 Java payload 类；未知类型、数量和长度均要有边界。协议状态语义先固定，再减少类和包头。

流程：

1. 连接后仅在地图启用时探测动态通道，双方协商成功才发送动态订阅；无能力则明确回退。静态支持不等于动态支持。

   动态能力有独立探测状态和退避；对端不支持 train 通道，不能把已有静态 v7 标记为不支持或断开连接。
2. 服务端接受“查看维度”的全网概要订阅，分配 generation，并尽快推送同一采样时刻的初始完整集合。概要不等待整张静态图或 ETA。
3. 客户端立即绘制远处概要；按接受的视口范围，服务端主动发送缺少的共享定义。先编组/当前车厢所需轨道，再补整条路径；对于分段路径，可明确声明已具备的里程窗口，缺口不外推。

   里程窗口至少覆盖全部车厢、Core 前后 bogie 方向采样以及未来缓冲时间内的行进距离；运动反向和路径重复边界按原生规则展开。不能只发车头前方若干米而遗漏后部车厢。
4. 定义完整校验并安装后 ACK；对应 detail 状态可以入缓冲，尚未具备引用时只保留最新两帧，不按错误定义绘制。
5. 正常推送全网概要和视口进度；元数据变化按 revision 更新。鼠标移动到车厢时即时显示本地服务和 XYZ，PIDS 详情另行异步补充。
6. 视口变化更新 generation/细节 membership；切地图维度重新建立该维度数据流。旧 generation 包到达不得写入当前活跃视图。
7. 关闭地图立即取消订阅；每约 1 秒续租、初始租约 5 秒，用服务器单调时钟超时。断线、退出世界、服务端停机均清空订阅/工作任务与客户端动态状态。

同一个连接只保留一个活跃地图订阅；Xaero/JourneyMap 切换共享动态缓存和控制器，不重复订阅、采集或绘制同一列车。

## 6. 线程、缓存与背压

### 三段流水线

1. Minecraft 服务端线程：校验已认证玩家、能力、权限、维度、租约和请求限频；维护订阅表，为需要的 Simulator 提交最多一个采集任务。
2. Simulator 线程：统一采样当前维度所有目标列车，记录实际 Core sampleTime 和服务端单调时刻；概要 2Hz、细节并集 4Hz。输出自己拥有的 primitive DTO/数组；不把 Vehicle、PathData、Rail、Client 等可变引用交给编码线程。冷定义导出有独立限额，不在采样中重建静态地图/寻路。
3. 有界后台执行器：对只读快照筛选/编码、定义构建和必要压缩；回服务端线程做最终租约/generation 检查并发送。客户端后台解码/验证大定义，主线程原子安装，渲染只读 immutable 视图。

是否复用完全相同的 encoded frame 取决于订阅范围和已知定义是否相同；全维度概要可共享内容，订阅头单独组装。不能宣称所有玩家永远零额外成本：出站字节与玩家数相关，个性化筛选和队列也有成本。

### 边界与优先级

- 每 Simulator 同时最多一个 queued/in-flight 采集，下一次需求合并为 latestRequested；Core 忙时不不断排队。
- 位置帧发送前只保留最新待发状态；已发往 TCP/Netty 的旧字节无法撤回。必须在进入连接队列前做预算，不把“TCP 可靠”当成不会卡顿。
- latest-only 只替换可独立生效的绝对位置/完整概要；定义、销毁、成员关系和状态控制不能一起随旧位置丢弃。将来启用增量后，tombstone 保留到已确认的修复基线，或可靠发送后再回收，不能靠跳帧丢掉唯一删除记录。
- 优先级：删除/订阅状态与概要 → 运动进度 → 当前可见定义 → 非可见预取 → ETA。定义按小块轮转，避免整张路径阻塞位置。
- 初始建议每个应用分片 ≤32KiB，远低于现有静态 200KB 块；最终需在两加载器的实际 payload 限制内验证。批次允许分页，但不能因为单包大小截断列车集合。
- 可配置的初始上限：8MiB 单定义重组、16MiB 单帧重组、4 个并行重组，解压后亦检查总大小；超过时返回明确状态。超大路径改用有边界的窗口/分页，不无限加大内存。
- 为每连接配置位置和定义独立 token bucket、待发总字节上限及租约上限；预算不足先降定义预取/细节频率，再报告概要刷新变慢。若连全网概要也容不下，明确“不完整/等待分页”，不得以空列表表示全网零列车。
- 无订阅者时停止动态遍历、姿态计算和编码。租约检查本身可保留轻量 tick；既有静态地图同步不受影响。
- 不为支持全图调用强制加载区块；不重新运行 Core 行车、信号或调度模拟。

## 7. 完整性与故障处理

Minecraft 连接可靠有序，通常不需要自行做丢包重传。这里的 sequence/generation 和原子重组用来处理异步工作完成顺序、应用主动丢弃旧快照、切服和半成品定义，不能把它描述成 UDP 协议。

| 情况 | 应有行为 |
| --- | --- |
| 完整概要分多包 | 以 frameSeq/partCount/totalBytes 校验，完整后原子替换；收到第 1 片不能删除其他片中的车辆 |
| 等待完整集合时又收到更新 | 同流发布有序，首轮可把最新状态留到基线提交后发送；后续支持缓冲时设边界，溢出重新请求基线 |
| 完整帧少了某列 | 只有 COMPLETE_SCOPE 全集才有清除缺失对象的权限；前提是整个枚举成功 |
| 单车导出失败/路径无效 | 已知对象保留 UNLOCATABLE/错误状态，或将该轮标记 PARTIAL；不能失败一次就伪造 DESTROYED |
| 车移出视口 | LEAVE_DETAIL 释放精细数据，概要仍在全网表；不等于物理实体删除 |
| 车取消/撤销 | DESTROYED tombstone；旧 sequence 的 upsert 不得复活；新实例要有新 handle/身份 |
| 车停车几分钟 | 流 heartbeat/完整 membership 保持存在；不以最后一次运动变化时间淘汰 |
| 服务器卡住 | 位置停在最后实测点，约 3 秒无新样本标记过期；约 10 秒不再作为实时活动列车绘制，可选中面板保留“失去更新” |
| 只有 heartbeat，Core 采样没更新 | 标明“连接正常、模拟数据过期”；heartbeat 不能刷新 sampleTime 伪装车辆在更新 |
| 路径/编组/服务变更 | 引用 revision 一致后切换，motionEpoch 必要时递增；几何、服务和运动断点分别处理 |
| 时间倒退/模拟器重新初始化 | 新 dimensionEpoch，清插值历史并重新基线；不能跨时间轴插值 |
| 静态轨道 v7 暂未下载 | 全网概要和动态定义仍能用；缺静态背景不应阻塞动态位置 |
| 退出/重连/重启 | 清所有活跃状态、transfer、任务和别名；跨 session 定义仅可按完整内容摘要重新验证后复用，第一版不做磁盘持久化 |

未知定义不无限请求；每个 handle/revision 合并请求，超时使用受控重试，反复失败显示细节不可用。字符串、枚举、数量、NaN/Infinity、尺寸、里程区间、分片相互矛盾和压缩膨胀都需在构造大型数组前校验。C2S 选中 ID 和视口只是请求，不能允许客户端指定任意高频率或直接访问其他会话对象。

## 8. 客户端沿真实路径平滑，继续使用 Y 遮挡

动态流是地图显示数据，不塞进 MinecraftClientData.vehicles 让 Core 再模拟一遍，也不与附近车辆叠画两份。

- 建立统一 TrainDataProvider。全网流正常时该维度由服务器流独占；无动态能力才切到附近 provider。流故障时明确过期，切换来源要原子且清旧缓存，不能一半全网、一半附近重复补车。
- 近景保留约 250–350ms 初始插值缓冲，概览约 500–650ms，最终按 RTT/抖动实测调整。4Hz 网络更新不意味着地图只按 4FPS 绘制；渲染帧之间沿 Core 路径里程计算每节姿态。
- 对相同 path revision/motionEpoch 的相邻实测进度，在其时间区间内单调插值；禁止插值产生倒车或超出两端进度。先用线性里程插值，验证后再加单调受限曲线，不能直接做 XYZ 直线导致列车切弯。
- Core speed 单位是米/毫秒，协议规范统一成米/秒。速度可供 UI/预取和受限插值使用，不用于重跑制动或信号。第一版无无界外推；没有后帧时停止在最后可靠状态并显示新鲜度。
- 循环路径发生里程重置时发送连续性信息或明确 motionEpoch 断点；初版选择断点，不在不了解 repeatIndex 语义时对 double 进度取模猜圈次。
- 折返、瞬移、重新部署、定义替换禁止跨断点拉长车厢。车厢逻辑 index 不重排，leading 按原生方向确定。
- 求解复现 Core 的车钩 padding、总长、反向布局、单/双转向架、前后采样夹取、PathData 方向和侧倾，然后生成现有 MapCar/MapVertex。不沿彩色线路车道偏移；共线的列车属于实际物理轨道。
- 路径原点和世界坐标均 double；如使用相对 float，必须先减 double 局部原点并携带原点精度，不能在三千万坐标先转 float。
- 输出继续进入 HeightMapScene，保留真实 Y，让上下穿轨道/车厢按本地高度遮挡。现有细分画家算法的误差、无地形/建筑/隧道顶板遮挡等边界不因全网通信而自动解决。

不能仅说“平均中心点对了”就通过。原生 bogie/body 姿态是离线/游戏内对照基准，含最小半径弯道、高速、坡道侧倾和折返；初始几何目标为实测时刻角点误差 ≤0.25 格、朝向误差约 ≤2°。网络显示延迟与几何重建误差分别报告，不能把延迟造成的位置差当成求解器误差，也不能将切弯藏在平均值里。

## 9. 带宽和性能：明确哪些只是估算

按 Overview=64B/列/帧、2Hz；Detail=40B/列/帧、4Hz 估算，均为未压缩应用记录，不含帧头、TCP/游戏协议、首次定义和 ETA：

| 全网列车数 | 全网概要/客户端 | 视口 100 列的进度增量 | 合计/客户端 |
| --- | ---: | ---: | ---: |
| 100 | 12.8kB/s | 16.0kB/s | 28.8kB/s |
| 1000 | 128.0kB/s | 16.0kB/s | 144.0kB/s |
| 5000 | 640.0kB/s | 16.0kB/s | 656.0kB/s |

十进制 kB/MB。1000 列、10 个相同规模订阅者约 1.44MB/s 总出站，30 人约 4.32MB/s，未计其他开销。共享采样不等于共享物理网络出站。5000 列×30 人仅概要已约 19.2MB/s，必须测并按配置降频/增量化；不能称“全网任意规模几乎零成本”。

作对照，若逐帧传每节车厢两组 XYZ double，仅这六个 double 就是 48B/节；1000 列×8 节×2Hz 约 768kB/s/客户端，4Hz 约 1.536MB/s，尚无侧倾、第二组 bogie 采样和服务字段。当前 MapCar 的四角 XYZ 若直接全传则为 96B/节，2Hz 已约 1.536MB/s/客户端。进度流的高频负载按列计，编组长短主要影响首次定义和客户端可见求解。

定义成本单列：每条 RailMath 原生参数预计数百字节，PathDefinition 每个 occurrence 预计几十字节，ConsistDefinition 按实际车型字符串和参数计。必须用 codec 实际编码长度测量；相同几何/编组按 revision 共享。冷启动、路线重生成和快速拖图突发单独测，不能只报缓存热后的每秒位置流量。

初始验收预算（目标，不是已达成）：

- 1000 列时，概要/进度共享采集 Simulator 线程 P95 ≤2ms/次，另记原生头位置查询及对象分配；不含冷定义构建，但冷构建同样有单独预算。
- 客户端 1000 节实际可见车厢，新增求解+动态渲染 P95 ≤2ms/帧；现有 HeightMapScene 每帧重建/排序所有静态四边形也必须记录，避免将静态开销误归网络或完全漏算。
- 有界任务不会在稳定负载下持续增长；无人打开地图时，动态车辆采集次数/发送字节应为零。
- 热缓存拖到任意远处后，目标 1 秒内出现实测概要、2 秒内完成所需可见车厢（局域网、无服务端过载基线）。冷定义受传输大小影响，单独记录，不能无条件保证 2 秒。
- 新连接拿到完整集合后，按相同过滤规则与 Core 对比 activeCount/locatableCount/unknownCount，100% 对上；细节受限不影响概要完整性。

基准覆盖 100/1000/5000 列，1/8/16 节，1/10/30 个地图订阅者，静止/行进/高速/密集共线，多维度、100–300ms RTT 和抖动、反复开关地图与快拖、服务端 2–10 秒卡顿。测 P50/P95/P99 采集/编码/队列延迟、分配率/GC、出入站实际字节、定义缓存命中、客户端帧时、未完成重组和覆盖计数。用真实 Core 场景与纯 codec/状态机夹具分别验证。

## 10. PIDS/ETA 保留独立接口

按用户要求，悬停 ETA 对接车站 PIDS 已使用的预测来源、缓存和时间基准；本方案不另算 ETA，不用速度推算。

位置流只带足够稳定的车辆/服务/下一停靠引用。悬停或选中才请求预测；服务端合并相同 PIDS 查询，约 1Hz 刷新。同一停靠事件必须显示与站台 PIDS 一致的 arrival/departure/realtime/偏差语义。

actualVehicleId、siding/班次/停靠 occurrence 的可靠桥接仍是独立关卡。当前公开 ArrivalResponse 缺少完整车辆关联，不能按相同 routeId 或站台最近一班硬贴。关联未验证时，地图的全网位置照常交付，ETA 显示不可用，不阻塞本次全图通信。

## 11. 实施顺序与落地文件

| 阶段 | 内容 | 验收门 |
| --- | --- | --- |
| G0 权威导出适配 | CoreTrainAdapter，模拟线程全网枚举，头 XYZ、只读进度、时间和身份；RailMath/编组最小定义与纯姿态重建原型 | 不加载客户端类；实际 NeoForge/Fabric JAR 字段命中；远离玩家车辆、回库、折返、循环、轨道编辑可解释 |
| G1 全图概要流 | 能力协商、订阅/租约、独立 payload、2Hz 完整概要集合、分片原子提交、全网标记 | 任意远处概要可见、计数与 Core 一致；无订阅零动态采集；跨维度/重启/缺能力回退通过 |
| G2 任意视口完整车厢 | 原生几何/路径/编组 revision 缓存、4Hz 进度、沿轨插值、视口缓冲、现有高度绘制和 hover | 缩放到远处后每节车厢对位，不依赖区块加载；长车尾部进图不漏；未知定义仍有概要 |
| G3 故障与性能 | 删除/membership、失败/过期、突发限流、队列合并、关键帧恢复与并发基准 | 停车不消失，分片不误删，撤销不残留，Core 卡顿不假更新；有实际采集/带宽/帧时间数据 |
| G4 PIDS 详情 | 预测身份桥接、共享按需查询和时钟、新鲜度 | 与站台同事件一致，歧义不硬匹配；单独验收，不阻塞 G1–G3 |

G1 是“全图概要”可单独查看的中间成果；用户的“全图每节车厢”目标必须到 G2+G3 验收才算完成，不能交付 G1 后改口称全部完成。

建议代码边界：

- `train/core/CoreTrainAdapter`：唯一知道 Core 版本/受保护字段的地方；mixin 按实际 schema 声明，双方加载器验证。
- `train/server/TrainSubscriptionManager`、`TrainSnapshotCollector`、`TrainDefinitionRegistry`：租约、共享采集、定义缓存。
- `train/network/TrainProtocol` / codec：共享有限 DTO 和边界校验，NeoForge/Fabric 只做传输及事件适配。
- `train/client/ClientTrainStream`、`TrainDefinitionCache`、`TrainPoseResolver`、`TrainDataProvider`：状态机、缓存、纯姿态、来源切换。
- 改两个地图集成以提交“地图正在查看的维度/视口”，不从渲染回调直接发包；客户端 tick 驱动订阅。现有附近 collector 作为回退。
- 服务端生命周期显式注册 NeoForge ServerTick/Stopping 和 Fabric END_SERVER_TICK/STOPPED；从事件给出的 server 取得上下文，不依赖当前未赋值的 MTRMap.serverInstance。disconnect 立即删租约。

所需测试重点是状态机和真实几何/协议：乱完成的后台任务、旧 generation、半帧、完整/增量不同清除语义、定义错版、销毁后旧 upsert、静止活性、Core 时间跳变、恶意长度与数量、native bogie/body 对照。不能用只验证自己编码后自己解码成功的测试替代这些场景。

本轮仅规划与依赖/源码核对，不改功能、JAR 或版本号，不运行新功能构建/性能测试。当前可运行代码仍是附近车辆实现；下一步按 G0→G1→G2→G3 做全图通信，版本继续 **1.5.2**，直到用户要求变更。
