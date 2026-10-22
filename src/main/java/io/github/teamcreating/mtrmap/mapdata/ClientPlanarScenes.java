package io.github.teamcreating.mtrmap.mapdata;

/** Independent host views, shared lifecycle; no optional map classes are loaded by cache cleanup. */
public final class ClientPlanarScenes {
    public static final PlanarMapCache XAERO = new PlanarMapCache();
    public static final PlanarMapCache JOURNEYMAP = new PlanarMapCache();

    private ClientPlanarScenes() {
    }

    public static void clear() {
        XAERO.clear();
        JOURNEYMAP.clear();
    }
}
