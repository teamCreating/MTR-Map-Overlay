package com.lx862.mtrsurveyor.mapdata;

/** A server-synced station, platform or depot used by map-only overlays. */
public record MapLandmark(String id, Type type, int x, int y, int z,
        String name, String symbol, String description, boolean hasRoutes) {

    public enum Type {
        STATION,
        PLATFORM,
        DEPOT
    }

    public MapLandmark {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        symbol = symbol == null ? "" : symbol;
        description = description == null ? "" : description;
    }
}
