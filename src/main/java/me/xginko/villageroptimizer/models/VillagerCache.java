package me.xginko.villageroptimizer.models;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

public class VillagerCache {

    private final Cache<UUID, WrappedVillager> villagerCache;

    public VillagerCache(long expireAfterWriteSeconds) {
        this.villagerCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds)).build();
    }

    public @NotNull Collection<WrappedVillager> getAll() {
        return this.villagerCache.asMap().values();
    }

    public @Nullable WrappedVillager get(@NotNull UUID uuid) {
        WrappedVillager wrappedVillager = villagerCache.getIfPresent(uuid);
        return wrappedVillager == null && Bukkit.getEntity(uuid) instanceof Villager villager ? add(villager) : wrappedVillager;
    }

    public @NotNull WrappedVillager getOrAdd(@NotNull Villager villager) {
        WrappedVillager wrappedVillager = villagerCache.getIfPresent(villager.getUniqueId());
        return wrappedVillager == null ? add(new WrappedVillager(villager)) : add(wrappedVillager);
    }

    public @NotNull WrappedVillager add(@NotNull WrappedVillager villager) {
        villagerCache.put(villager.villager().getUniqueId(), villager);
        return villager;
    }

    public @NotNull WrappedVillager add(@NotNull Villager villager) {
        return add(new WrappedVillager(villager));
    }

    public boolean contains(@NotNull UUID uuid) {
        return villagerCache.getIfPresent(uuid) != null;
    }

    public boolean contains(@NotNull WrappedVillager villager) {
        return villagerCache.getIfPresent(villager.villager().getUniqueId()) != null;
    }

    public boolean contains(@NotNull Villager villager) {
        return villagerCache.getIfPresent(villager.getUniqueId()) != null;
    }
}
