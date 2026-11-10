package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationRegistryTest {
    final NavigationTarget target = new NavigationTarget(new WorldRef(UUID.randomUUID(), "minecraft:overworld"), "123");
    @Test void unavailableAndAbsentProvidersCannotBeCalled() {
        var registry = new NavigationRegistry((owner,error) -> fail(error));
        assertTrue(registry.available(target).isEmpty());
        var calls = new AtomicInteger(); var ready = new AtomicBoolean(false);
        registry.register("test:provider", "Test", t -> ready.get(), t -> calls.incrementAndGet());
        assertTrue(registry.available(target).isEmpty()); ready.set(true);
        var provider = registry.available(target).get(0); ready.set(false);
        assertFalse(registry.navigate(provider,target)); assertEquals(0,calls.get());
        ready.set(true); assertTrue(registry.navigate(provider,target)); assertEquals(1,calls.get());
    }
    @Test void closingAndReplacingDoesNotLeaveStaleClickableProviders() {
        var registry = new NavigationRegistry((owner,error) -> fail(error));
        var registration=registry.register("test:provider", "Test", t -> true, t -> {});
        var old=registry.available(target).get(0); registration.close();
        registry.register("test:provider", "Replacement", t -> true, t -> {}); registration.close();
        assertEquals(1,registry.available(target).size()); assertFalse(registry.navigate(old,target));
        assertTrue(registry.available(null).isEmpty());
    }
    @Test void brokenOptionalProviderDoesNotDisableOtherProvidersOrFloodDiagnostics() {
        var errors=new AtomicInteger(); var calls=new AtomicInteger();
        var registry=new NavigationRegistry((owner,error)->errors.incrementAndGet());
        registry.register("test:broken", "Broken", t -> {throw new NoClassDefFoundError("Optional dependency");}, t -> {});
        registry.register("test:good", "Good", t -> t.stationId().equals("123"), t -> calls.incrementAndGet());
        assertEquals(1,registry.available(target).size()); assertEquals(1,registry.available(target).size());
        assertEquals(1,errors.get()); assertTrue(registry.navigate(registry.available(target).get(0),target)); assertEquals(1,calls.get());
    }
}
