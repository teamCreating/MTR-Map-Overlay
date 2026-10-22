package com.lx862.mtrmap.mapdata;

/** Map appearance of Core's route classification, independent of transport and renderer classes. */
public enum RouteLineStyle {
    NORMAL(1.0), LIGHT_RAIL(0.7), HIGH_SPEED(1.0);

    public final double width;

    RouteLineStyle(double width) {
        this.width = width;
    }

    public static RouteLineStyle fromName(String name) {
        for (RouteLineStyle style : values()) {
            if (style.name().equals(name)) {
                return style;
            }
        }
        return NORMAL;
    }
}
