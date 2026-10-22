package com.lx862.mtrmap.mapdata;

/** Shared by early allocation culling and both hosts' symbol geometry. */
public final class TrainDisplayScale {
    private TrainDisplayScale() {}

    // Full car length minus two .7px coupling gaps meets the 12px overview symbol.
    public static boolean marker(double length, double scale) { return length * scale < 13.4; }
    public static boolean detailed(double length, double scale) { return length * scale >= 26; }
}
