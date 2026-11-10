package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import java.util.*;
import java.util.function.*;

/** Client-thread provider lifetime and per-target availability. No game or optional mod classes. */
public final class NavigationRegistry {
    public record Provider(String owner, String name, Predicate<NavigationTarget> available, Consumer<NavigationTarget> handler) {}
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private final BiConsumer<String, Throwable> failure;
    public NavigationRegistry(BiConsumer<String, Throwable> failure) { this.failure = Objects.requireNonNull(failure); }
    public List<Provider> all() { return List.copyOf(providers.values()); }
    public Registration register(String owner, String name, Predicate<NavigationTarget> available, Consumer<NavigationTarget> handler) {
        if (owner == null || !owner.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || name == null || name.isBlank()
                || name.length() > 80 || providers.size() >= 8 || providers.containsKey(owner))
            throw new IllegalArgumentException("Invalid or duplicate navigation provider / capacity reached");
        var value = new Provider(owner, name, Objects.requireNonNull(available), Objects.requireNonNull(handler));
        providers.put(owner, value);
        return () -> providers.remove(owner, value);
    }
    public List<Provider> available(NavigationTarget target) {
        return target == null ? List.of() : providers.values().stream().filter(p -> available(p, target)).toList();
    }
    private boolean available(Provider provider, NavigationTarget target) {
        if (providers.get(provider.owner()) != provider) return false;
        try { return provider.available().test(target); }
        catch (RuntimeException | LinkageError e) { failed(provider, e); return false; }
    }
    public boolean navigate(Provider provider, NavigationTarget target) {
        if (target == null || !available(provider, target)) return false;
        try { provider.handler().accept(target); return true; }
        catch (RuntimeException | LinkageError e) { failed(provider, e); return false; }
    }
    private final Set<String> reported = new HashSet<>();
    private void failed(Provider provider, Throwable error) {
        if (reported.add(provider.owner())) failure.accept(provider.owner(), error);
    }
}
