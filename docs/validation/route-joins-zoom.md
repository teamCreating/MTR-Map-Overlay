# Route joins and world-space zoom — 2026-10-21

Update, Codex, 2026-10-21 17:30: per user request, high-speed width is now 1.0, equal to normal rail; light rail remains 0.7 and coloured/white blocks remain. 68 ordinary tests and both builds pass; no new game session for this parameter change. The 1.4 weights and measured widths below describe the earlier build.

Codex, 14:39. Version remains **1.5.2**, pure 2D, protocol v8 unchanged.

## Findings and change

The screenshot identified two real faults. At a shared-to-single-route join, only the narrower side borrowed the wider section and a route absent from that side kept its old endpoint width. The ending colour therefore left a square lip. At a coloured-to-uncoloured join, the backing profile added the neighbour's route width to the local physical backing instead of interpolating actual backing widths, making the grey wedge.

Both sides now agree on a common section. Continuing lanes fill that section; ending lanes contract to its nearest boundary on their own rail. Actual backing widths interpolate directly. Colour/style changes and uncoloured continuations share the same outer boundary. Smoothstep tapers have explicit samples even on long two-point rails; high-speed white-block insets clamp to the remaining lane width to prevent inverted white quads at a terminating tip. Existing physical endpoint identity, common normals, branch continuation policy and stripe phase remain in use.

Create reference: pinned source `7ff2760e4924d35d4bc2e22c4b19923f0178cd16`, `XaeroTrainMap.onRender` scales the world-coordinate overlay with `mapScale / guiScale / interfaceScale`; `TrainMapRenderer` places cached 128×128 world tiles inside that transform. Its rails shrink with the map. We retain native vector curves and adopt world-space rail widths, rather than its raster tile implementation. Source: [XaeroTrainMap](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/XaeroTrainMap.java), [TrainMapRenderer](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapRenderer.java).

- Track base half-width is 1.25 world blocks. Route type weights remain light rail 0.7 / rail 1 / high speed 1.4, with the existing four-route total-width cap.
- Apply a 0.3 GUI-pixel half-width floor once to the entire route bundle. No per-lane readability floor is accumulated. The shoulder is world-space with a 0.1 GUI-pixel floor; its alpha fringe also narrows with the backing.
- At GUI scale=0.1, four high-speed routes now occupy 1.4 GUI pixels of colour instead of 14; at scale=0.01 the entire backing is 0.8 GUI pixels instead of 14.8, excluding the transparent edge fringe. These are geometric widths, not frame-time results.
- High-speed white blocks remain at ordinary scale=0.75 and simplify when the lane is below 1.8 GUI pixels or blocks below three GUI pixels. Very distant lanes can no longer all be individually legible; hover data remains complete.
- Train symbols, true XYZ, station markers, protocol and data collection are unchanged.

## Verification and cost

68 ordinary NeoForge tests pass, plus one explicitly skipped world generator (69 total). NeoForge/Fabric builds pass; existing Fabric optional Xaero method-remapping warnings remain. Tests cover whole-bundle far-zoom width, world/screen consistency, reversed shared-to-single boundaries, ending-lane contraction, grey backing continuity over four zoom levels, high-speed white containment and preservation at ordinary zoom, alongside existing curve/protocol/cache tests.

Actual NeoForge TestWorld: Xaero 3.0× shows the red/white shared corridor narrowing smoothly into green and the green/grey continuation without the previous wedge; 1.403× shows the same corridor shrinking with the map. Screenshots capture the final build. JourneyMap uses the same scene implementation; runtime check recorded alongside artifacts. Fabric runtime and large-network FPS are not tested.

Profile work remains proportional to track/lane input, with at most 64 samples per changed endpoint; unchanged sections add no taper cuts. Taper geometry is static-cache work and is reused while cars move. Wider world-space ribbons also require a larger coarse-culling margin at high zoom. No new packets, sorting or underpass work is introduced. The 0.06 GUI-pixel width-interpolation target applies until the 64-sample safety cap; it is not a combined rasterization error guarantee.

Artifacts: `../test-builds/v1.5.2-route-joins-zoom/` relative to repository root. Both JARs, SHA-256 hashes, actual screenshots, runtime log and `tools/RouteJoinZoomPreview.java` production-geometry preview. Java2D preview omits the per-vertex-alpha fringe and uses synthetic geometry; it is not a game screenshot.
