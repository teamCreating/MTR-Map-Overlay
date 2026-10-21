# 车辆显示优化（2026-10-22）

操作者：Codex，2026-10-22 00:31。版本仍为 **1.5.2**；静态 v8 / 动态 v1 未变。

## 参考与范围

对照本地公开源码 Create `7ff2760e4924d35d4bc2e22c4b19923f0178cd16` 的
[TrainMapManager.drawTrains](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapManager.java)：
双锚点插值、绘制前判断地图范围、轻量车厢符号、一个车厢命中返回所属列车。Create 用图集分片和离散方向；这里沿用已有连续朝向四边形，保留弯道平滑度，不复制其贴图或高度排序。

本次新增的包围盒层级索引和三级细节是本项目的优化，并非声称 Create 原样采用这一套索引/LOD。
全图接收、TSC 唯一模拟、两点实测插值与真实 XYZ 保持；没有速度积分、路径求解或视口网络订阅。下一站距离/ETA 仍暂停。

## 改动与成本

- 每次完整帧安装时，先判定可连续插值的前后帧，建立每列所有车厢的保守 X/Z 扫掠包围盒及平衡层级索引。包围盒覆盖双帧端点、车体长宽与非对称 bogie 偏移，额外屏幕边距在查询时扩展。
- 动画帧先查索引，再对候选列车做原有车厢剔除、线性插值及矩形生成。索引不改变完整帧成员；远处/未加载区块中的列车继续同步，平移地图即可查询。
- 设 N 为列数、C 为总节数：安装约 O(C + N log N)，额外索引空间 O(N)。局部查询通常只访问少量节点和候选车厢；最坏大量重叠或全图可见仍可到 O(N + C)。不会因为索引而省掉屏幕上所有可见车辆的实际绘制。
- 缩远（车长 <13.4 GUIpx）：只画实际 leading 端的 12×7 GUIpx 定向列车图标，保留全列节数用于悬停；倒车选择最后一节。
- 中景（13.4～26 GUIpx）：每节保持白轮廓、深框、线路色和浅顶，车头箭头；移除难以辨认的亚像素窗和倒角。
- 近景（≥26 GUIpx）：保留原有倒角和双端窗。车宽最小 7 GUIpx，物理尺寸/真实位置不变。
- 缩远/完整车厢切换长度连续：13.4−两端 0.7 = 12 GUIpx，修复旧阈值 9 时从 12px 突然缩为 7.6px。buffer 提前跳过尾车与几何层共用阈值。

| 场景 | 原四边形 | 现在 | 减少 |
|---|---:|---:|---:|
| 远景每列图标 | 9 | 5 | 44.4% |
| 中景三节（一个车头） | 25 | 13 | 48% |
| 近景三节 | 25 | 25 | 0% |

## 合成测量（不是游戏 FPS）

工具 `tools/TrainDisplayProbe.java`，JDK 21.0.11，2000 列×8 节、间隔 2000 格，前后实测帧相差 10 格。
先预热，查询使用 MethodHandle；分配用当前线程 ThreadMXBean。基线是 `b012316` 的原 `TrainAnchorBuffer`，仅改类名 `LegacyTrainAnchorBuffer`，放入 ignored `build/display-probe`。客户端已关闭，分别启动三次 JVM。无 GPU、地图 host、TSC 采集、网络和多人负载。

| 工作负载 | 基线（三次范围） | 新实现（三次范围） |
|---|---:|---:|
| 局部查询，8 个可见车厢 | 0.289～0.306 ms | 约 0.002 ms |
| 局部查询分配 | 31.2 KiB | 2.0 KiB |
| 全网视口，2000 个缩远图标 | 0.346～0.392 ms | 0.293～0.349 ms |
| 全网查询分配 | 515.2 KiB | 486.0 KiB |
| 完整帧安装 | 0.423～0.453 ms | 1.273～1.766 ms |

这是用低频安装的额外工作替换高频全网遍历；全网视口收益有限，不能把局部查询的改善外推成整体 FPS 倍数。安装指标包含 Frame 构造，未独立测索引耗时；运行时间会受 JIT/GC/系统调度影响。工具只测 buffer，四边形减量由生产几何测试验证。

可复现：先 `gradlew build`，用 javac 将探针编到 `build/display-probe/classes`，再运行：

```powershell
& "$env:JAVA_HOME/bin/javac.exe" -cp build/classes/java/main -d build/display-probe/classes tools/TrainDisplayProbe.java
& "$env:JAVA_HOME/bin/java.exe" -cp 'build/classes/java/main;build/display-probe/classes' TrainDisplayProbe
```

基线需从 `git show b012316:src/main/java/com/lx862/mtrmap/train/TrainAnchorBuffer.java` 另存并替换类名，编译时加入相同 classpath；不要覆盖当前源码。

## 验证

- 86 项普通测试通过，1 个显式存档生成用例跳过。随机远坐标与穷举包围盒结果一致；两万辆车同点没有遗漏/重复/递归深度异常；转弯中途、偏移、删除/clear、倒车 leading 端及细节切换长度/命中框有覆盖。
- NeoForge 与 Fabric 构建通过；Fabric 沿用现有 Xaero remap 警告，未做 Fabric 游戏运行验证。
- NeoForge 1.21.1 / MTR 4.1.0-beta.2，独立 `GlobalTrainSyncTest`。Xaero：玩家 (2000,-59,2000)，native nearby=0，仍持续收到 1 列3节、125B/位置正文；3× 近景、约1.447× 中景三节以及0.698× 单图标均检查，过弯连续，悬停 Train / Local → Charlie / 3 carriages。
- JourneyMap：先确认全网状态，随后仅为定位方便将测试玩家移到 (300,-59,40)，复查缩远一个图标、放大三节过弯及相同悬停内容。此段不作为 native nearby=0 的额外证据。
- 本次客户端已经保存退出。真实千列车队 FPS、多人和差网络仍未实测；不改变上一轮验证的边界。
