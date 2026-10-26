package io.github.teamcreating.mtrmap.config;

/** Stable configuration values shared by loaders. */
public enum PidsStyle {
    BRITISH("british", 0xFFD34E, 0xD9CFAD, 0x181510, false),
    EUROPEAN("european", 0xF2F3F5, 0xBFC6D1, 0x252E3C, true);

    public final String key;
    public final int destination, secondary, header;
    public final boolean stripes;
    PidsStyle(String key, int destination, int secondary, int header, boolean stripes) {
        this.key = key; this.destination = destination; this.secondary = secondary; this.header = header; this.stripes = stripes;
    }
    public static PidsStyle parse(String key) { return "european".equals(key) ? EUROPEAN : BRITISH; }
}
