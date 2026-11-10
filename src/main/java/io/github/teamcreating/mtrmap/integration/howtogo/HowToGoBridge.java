package io.github.teamcreating.mtrmap.integration.howtogo;

import io.github.teamcreating.mtrmap.api.MtrMapApi.Station;
import java.lang.reflect.*;
import java.util.Optional;

/** Optional public HowToGo entry points, verified against its published destination/navigation classes. */
public final class HowToGoBridge {
    private final Constructor<?> destination;
    private final Method setTarget;
    private HowToGoBridge(Constructor<?> destination, Method setTarget) {
        this.destination = destination; this.setTarget = setTarget;
    }
    public static Optional<HowToGoBridge> find(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type;
        try { type = Class.forName("bili.dongsz.howtogo.route.Destination", false, loader); }
        catch (ClassNotFoundException absent) { return Optional.empty(); }
        var destination = type.getConstructor(String.class, int.class, int.class, int.class, String.class);
        var navigation = Class.forName("bili.dongsz.howtogo.client.Navigation", false, loader);
        var setTarget = navigation.getMethod("setTarget", type);
        if (!Modifier.isStatic(setTarget.getModifiers()) || setTarget.getReturnType() != void.class)
            throw new NoSuchMethodException("Unsupported HowToGo navigation signature");
        return Optional.of(new HowToGoBridge(destination, setTarget));
    }
    public static boolean supports(Station station) {
        return station != null && coordinate(station.position().x()) && coordinate(station.position().y()) && coordinate(station.position().z());
    }
    private static boolean coordinate(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE && value < (double)Integer.MAX_VALUE + 1;
    }
    public Object destination(Station station) throws ReflectiveOperationException {
        if (!supports(station)) throw new IllegalArgumentException("Station position is outside the navigation coordinate range");
        var p = station.position();
        return destination.newInstance(station.name().replace('|', ' '), (int)Math.floor(p.x()), (int)Math.floor(p.y()),
                (int)Math.floor(p.z()), "mtrmap");
    }
    public void navigate(Station station) throws ReflectiveOperationException { setTarget.invoke(null, destination(station)); }
}
