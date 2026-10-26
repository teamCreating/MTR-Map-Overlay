# MTR Map Overlay client API 1.0.0 (1.5.3)

The implementation is `io.github.teamcreating.mtrmap.api.MtrMapApi`. Use the separately built `build/api/mtrmap-api-1.0.0.jar` as **compileOnly**; do not shade or duplicate its classes. All API types are pure Java 17. Check that Overlay is installed before loading your integration class, then use `MtrMapApi.findClient()`. No Navigation/howtogo-specific dependency is required.

## Thread and lifecycle

Call all client operations and close handles on the Minecraft client thread. Listeners run on that thread. Values are immutable snapshots. `world("minecraft:overworld")` starts an optional catalog request and returns empty until a server-backed world identity is available. Require `catalog(world).status() == READY` before submitting a view. World identity includes a server epoch: do not reuse handles, IDs or revisions after disconnect/reconnect. View and station subscription handles are invalidated on disconnect; navigation provider registrations remain registered until explicitly closed, so a mod registers its entry once after findClient() becomes available (for example, on a client tick).

The static v8 and train v1 transports remain unchanged. Station/catalog requests use a separate optional overlay-1 transport. Unsupported servers keep ordinary map functionality. A station board is LOADING, READY, LIMITED, STALE, NOT_FOUND, UNSUPPORTED or ERROR; LIMITED is not proof that no train exists. Native `arrivalAt`, `departureAt`, `deviation`, `realtime` and `terminating` values are preserved. Do not add deviation to timestamps a second time.

## Native sections and dimming

Use native route IDs and stop ordinals from Catalog. IDs must not be inferred from names/colours. `resolve(world, revision, sections)` clips already-existing Core Depot paths; it never runs another route finder. Multiple path variants produce AMBIGUOUS and candidate IDs. Explicitly select the intended variant. Rail fractions use canonical rail direction and native distance (including slopes), not X/Z chord length. Rail IDs retain Core's exact hex spelling; station/platform/route IDs use unsigned lowercase unpadded hex.

```java
// Called by your planner on the client thread, after its navigation/progress changed.
var request = MtrMapApi.ViewRequest.navigation(
    world, catalog.revision(), remainingSpans, completedSpans,
    0.35, // completed intervals retain colour at 35% opacity
    0.15  // unused routes/railways at 15% opacity
);
var result = focus.replace(request); // CompletionStage<Status>
```

Open `focus` with `client.openView("yourmod:journey", "Your planner", Purpose.FOCUS, 0)`. `remainingSpans` and `completedSpans` are lists of SelectedSpan supplied by the caller. The factory selects both and attaches completed-span overrides, with `unmatched=DIM`. Upcoming intervals win if a later journey traverses a completed physical interval again. Overlay does not infer progress or simulate a train. Cancel the journey by closing **your** focus handle; the next valid view or normal map is restored. Replacing a view is atomic: an invalid revision/selection leaves the previous valid view installed. The user can temporarily show the full map without destroying the caller's session.

For finer control, construct a ViewRequest with `spanStyles` and `unmatchedOpacity`. Each `SpanStyle(SelectedSpan, Style)` applies to only that native interval; empty routeId means physical rail/all its bands. Last overlapping override wins. A local colour/pattern may inherit the route-wide override; opacity and width multiply the route-wide style, with final width clamped to [.25, 4]. Unmatched DIM additionally multiplies by unmatchedOpacity. Route bands, white blocks, feathering and the physical rail backing dim together; shared backing uses the most visible band. Train symbols retain their original appearance. Static source data is never mutated. Selections/overrides are bounded to 20000 entries each, with at most 256 combined intervals per physical rail across active sessions. Geometry is cached until a view/source change, so submit progress changes rather than an identical request every frame.

`Unmatched.KEEP`, `DIM`, `HIDE`, explicit `wholeRoutes`, `stationIds`, colour/opacity/width and `INHERIT`, `SOLID`, `COLOUR_WHITE` patterns are implemented. An empty stationIds set retains all markers. No arbitrary clipping rectangle, custom dash/outline renderer, arbitrary UI injection or Maven repository publication is promised by this version. The earlier draft remains a design history, not a callable contract.

## Reserved navigation entry

```java
// Register once. Keep and close the returned registration when unloading the integration.
var registration = client.registerNavigation(
    "yourmod:planner", "Your planner",
    target -> openYourPlanner(target.world(), target.stationId())
);
```

A user's Navigate click passes only the selected server-backed station identity to the handler. One provider is invoked directly; multiple providers show a chooser. Without a provider the reserved action is disabled. Registration never performs pathfinding, starts navigation automatically, or inspects another mod. Up to eight unique owner IDs may register. Closing a registration removes only that owner. Keep your own planner UI and use the view/session API after the user confirms a journey.

## Station board and in-game preferences

`client.watchStation(world, stationId, listener)` returns a BoardSubscription. Read latest() for its initial state; close it when no longer needed. Up to six different client station interests share bounded, renewable server snapshots. The built-in UI subscribes only while its board is expanded and the map is open; collapse/close releases its interest.

Both map hosts use the same classic station tooltip, original route metadata, default collapsed state, Expand, and optional Navigate action. Expanded tables put destination first, then line, platform and right-aligned countdown. Long labels remain accessible in a row tooltip. Small windows compact the duplicated metadata and scroll rows without zooming the map. Prediction warnings are separate from a missing service row. No prediction is displayed as a dash, not zero minutes.

- `/mtrmap config pidsStyle` queries the setting.
- `/mtrmap config pidsStyle british` selects black/amber, the default.
- `/mtrmap config pidsStyle european` selects white text, a blue-gray header, subtle alternating rows and amber countdowns.

The preference persists in the loader's existing local config. It changes presentation only, with identical native arrival data.
