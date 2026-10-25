# Overlay API v1 草案（1.5.3目标）

状态：2026-10-25设计草案，**没有已发布的API JAR，下面不是现有可编译用法**。背景/源码及实施门见[1.5.3计划](../plans/v1.5.3-station-board-extension-api.md)。

## 1. 契约

- 包：`io.github.teamcreating.mtrmap.api`；API主版本与模组版本独立。拟议compileOnly坐标为 `io.github.teamcreating:mtrmap-api:1.0.0`，尚未发布。
- Java17、不可变值对象；不依赖任何MC/loader/Core/地图/导航模组类。API类由Overlay提供，第三方不得打包重复副本。
- `MtrMapApi.findClient()` 返回Optional；安装/加载检查后才能引用此类。客户端提供门面，专服不加载客户端实现。注册/更新/close在客户端线程执行，异步解析结果切回该线程交付。
- `apiVersion()` 与 `capabilities(WorldRef)` 区分契约版本及服务器数据能力；公共API存在不代表服务端支持ETA/路径解析。
- 全部结果带状态，不用空集合同时表示无数据、尚未同步和真实空网。

## 2. 身份与目录

```java
record WorldRef(UUID serverEpoch, String dimensionId) {}
record StationRef(WorldRef world, String nativeId) {}
record PlatformRef(WorldRef world, String nativeId) {}
record RouteRef(WorldRef world, String nativeId) {}
record RailRef(WorldRef world, String nativeId) {}
record StopOccurrence(RouteRef route, long catalogRevision,
                      int ordinal, PlatformRef platform) {}
```

dimensionId采用MC标准 `namespace:path`，内部MTR斜杠写法只在适配器转换。station/platform/route的nativeId为规范化无符号hex字符串；rail保留Core的复合getHexId（有序端点坐标hex组合），不能将它解析成单个long。不能填显示名、`station:`展示前缀或线路颜色。各Ref须属于同一有效世界，不允许跨服重用。

`MapReadApi.catalog(world)` 返回Station/Platform/RouteStop关系、来源FULL_SERVER/NEARBY、revision/完整性及能力。`tracks(world, query)`按所需rail集合/有界范围返回只读几何快照，不提供live数组。返回真实XYZ；未知Y明确标未知，不能补一个高度假装真实。

目录revision仅在本API内有意义；第三方自己的hash不能直接充当catalogRevision。遇到重名、循环重复平台，必须使用本目录的StopOccurrence；索引失效后重新映射。

## 3. 范围选择与路径解析

```java
record RailSpan(RailRef rail, double startFraction, double endFraction) {}
record RouteSection(RouteRef route, StopOccurrence from,
                    StopOccurrence to, Optional<String> pathVariant) {}

// 缩略声明；具体builder/错误类型在P0验证后冻结。
interface MapReadApi {
    CatalogResult catalog(WorldRef world);
    GeometryResult tracks(WorldRef world, TrackQuery query);
    CompletionStage<ResolveResult> resolve(List<RouteSection> sections);
}
```

选择器支持整条路线、已解析的路线区段或明确RailSpan。RailSpan允许起止反向，fraction在[0,1]，表示该rail原生弧长的归一化距离。0.2→0.6只显示中间40%，不是整条轨道包围盒命中就全画。路线选择另保留routeId供同轨不同色带使用；几何可见与线路色带可见是不同集合。

目录须明确返回rail的canonical起止端点及原生长度参数约定；0对应canonical起点，不能随采样数组/渲染方向翻转。此长度遵循Core原生路径距离单位，不另按三维折线重算。缺少可验证的原生距离映射时返回UNAVAILABLE，不把XZ采样长度冒充原生长度。

RouteSection包含真实停站occurrence、可选原生路径variant和明确的环线跨圈意图；缩略声明未展开wrap字段。ResolveResult为EXACT、AMBIGUOUS、UNAVAILABLE、STALE或INVALID。EXACT才返回带几何修订的有序片段；多条原生路径不能静默选择第一条。没有可靠路径时不能退成站点直线。

resolve只读/截取现有原生path，不提供新的导航寻路器、ETA算法或客户端模拟。RouteSection能力在真实夹具通过后开放，未通过前仍可使用明确RailSpan；不能用整线过滤冒充精确区段支持。

## 4. 显示会话和样式

```java
interface MapViewApi {
    ViewSession open(ViewOwner owner, ViewPurpose purpose, int priority);
}
interface ViewSession extends AutoCloseable {
    CompletionStage<ApplyResult> replace(ViewRequest request);
    void clear();
    void close();
}
```

`ViewOwner` 为 `modId:sessionKey`，purpose为FOCUS或DECORATION。replace提交完整不可变请求，验证后原子安装；新请求失败不清除仍有效的旧请求，并向调用者返回原因。拓扑变化导致旧请求也失效时停止使用旧区段、回退下一有效视图/底图并通知调用方，不继续画错误路径。

同一句柄的replace按提交序号执行latest-wins：较旧异步解析完成不能覆盖较新请求；clear/close使未完成请求失效。close后不再交付监听通知，尚未完成的future以明确的取消/失效结果结束。查询与选择数量、坐标范围、请求排队和超时均有上限。

ViewRequest拟包含：

| 字段 | 语义 |
| --- | --- |
| world / catalogRevision | 限定世界和解析基线 |
| selection | whole routes / resolved sections / exact rail spans |
| unmatched | KEEP、DIM、HIDE；FOCUS可变更底图范围，DECORATION不可隐藏底图 |
| styles | 声明式选择器与StyleOverride的有界列表 |
| landmarks | KEEP或仅相关停站/换乘点；不影响宿主原生玩家/路标 |
| trains | KEEP或HIDE；v1不提供依据线路名字猜测的车辆筛选 |
| clipBounds | 可选有限世界矩形，真正裁剪图元，不是整线bbox粗筛 |

StyleOverride为可选颜色、opacity、相对widthScale、outline以及有限pattern（SOLID、COLOUR_WHITE、DASHED）。未填字段继承当前线路样式。数值必须有限/有范围，非法请求返回INVALID；不允许任意逐帧回调、宿主纹理对象或渲染器替换。

所有session只能操作自身句柄；clear撤销其请求但保留句柄，close幂等释放并停止回调。FOCUS优先级最高者控制范围，同优先级按激活顺序确定，replace不偷偷重新抢占；关闭后恢复下一会话。DECORATION按稳定优先级/所有者顺序叠加样式，不将多个导航范围求交。

用户全局enabled及各图层开关优先。样式和范围只作用显示，不改MapDataCache、MTR路线、轨道或配置文件。每次有效请求变化更新viewRevision，让两地图静态缓存正确失效；渲染不逐帧回调外部模组。

游戏内显示当前聚焦所有者；可选displayName为长度受限的纯文本，不参与owner身份判定。用户可“暂时看全图”而不销毁第三方会话/导航：安装成功与当前是否实际生效分开报告（例如用户覆盖导致SUSPENDED）。新的replace不取消该用户覆盖，恢复聚焦或换世界才解除。此为UI补充约定，具体状态类型实施时冻结；不开放第三方任意注入按钮/面板。

导航session在地图关闭后可保持有效，以便再次打开仍显示当前行程；这不会让关闭的地图继续订阅全图车辆。断线/换世界会话立即失效，不跨serverEpoch恢复。

## 5. 同源站点ETA

```java
interface StationBoardApi {
    BoardSubscription watch(StationRef station, BoardQuery query,
                            Consumer<BoardSnapshot> listener);
}
interface BoardSubscription extends AutoCloseable {
    BoardSnapshot latest();
    void close();
}
```

BoardSnapshot含generation、native sampledAt/serverNow、时钟估计误差、新鲜度、完整性与各服务分组状态。ArrivalEntry保留原生route/platform ID、destination、arrivalAt、departureAt、deviation、realtime、terminating；字段不是某辆MapCar的身份。

latest始终返回非null快照，首份有效响应前为LOADING。时间统一标明毫秒与服务端时钟域；收到时间不等于原生预测的生成时间，未提供真实sampledAt时标未知，不能伪造新鲜度。

线路是展示标题，方向/目的地和站台继续区分；不能用线路名称/颜色去重。BoardQuery区分到站显示与可登乘发车筛选，终到标志不丢失。默认内置站点面板使用到站语义；发车/登乘查询只过滤原生结果，不另算时间。

`LOADING / READY / LIMITED / STALE / UNSUPPORTED / NOT_FOUND / ERROR` 与READY下的空结果分开。缺组、截断、刚登记原生请求的空缓存都不能报完整无班次。过期值可灰显最后预测并带年龄，不能继续冒充实时。

内置展开面板和第三方消费者使用同一服务、同一站点缓存和去重请求。折叠面板只close自己的watch，不撤销别人订阅；最后兴趣关闭、超时或断线后服务端停止查询。额度、刷新最小间隔和优先级由Overlay控制，插件不能请求每帧全世界车站。

## 6. 典型调用流程（非特定模组适配）

```text
调用方确认导航
  → 获取Overlay API版本、WorldRef和只读目录
  → 将本方行程映射为route/platform/stop occurrences
  → resolve乘车区段，处理歧义/未就绪
  → 创建自己的FOCUS会话
  → replace(选中的区段 + unmatched=HIDE + 样式)
调用方调整/重算路线 → 原子replace
调用方取消导航       → close，恢复普通地图
切服/拓扑失效         → 收到状态，重新获取目录再解析
```

Overlay不主动订阅MTR-Navigation的NavigationApi，不反射howtogo的数据结构，也不对它们加运行时依赖。它们或其他作者可自行在上述流程中接入；准备阶段仅用公开源码验证这些输入形状能够表达实际需求。第一份集成示例应是无第三方依赖的测试消费者。

## 7. 发布与兼容门

- API签名、线程约束、错误状态、关闭语义及owner冲突规则冻结后才发布1.0.0。后续只做向后兼容新增；破坏性变化升级API主版本，不能跟着内部包/缓存随意移动。
- 六构建检查公共契约字节码为Java17且没有平台/Core依赖；API单独artifact与mod内API类须签名一致。调用方compileOnly示例在1.20/1.21上均验证。
- 测试覆盖两会话撤销/冲突、地图拖拽与面板点击、重复平台/环线跨圈/多路径、半轨截断/共线色带/白块相位、旧服务端能力、晚到响应、断线清理、原生ETA对照及费用基准。
