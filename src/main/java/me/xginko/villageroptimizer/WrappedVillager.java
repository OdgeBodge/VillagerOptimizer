package me.xginko.villageroptimizer;

import me.xginko.villageroptimizer.enums.Keyring;
import me.xginko.villageroptimizer.enums.OptimizationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public final class WrappedVillager {

    private final @NotNull Villager villager;
    private final @NotNull PersistentDataContainer dataContainer;
    private final boolean parseOther;

    WrappedVillager(@NotNull Villager villager) {
        this.villager = villager;
        this.dataContainer = villager.getPersistentDataContainer();
        this.parseOther = me.xginko.villageroptimizer.VillagerOptimizer.getConfiguration().support_other_plugins;
    }

    /**
     * @return The villager inside the wrapper.
     */
    public @NotNull Villager villager() {
        return villager;
    }

    /**
     * @return The data container inside the wrapper.
     */
    public @NotNull PersistentDataContainer dataContainer() {
        return dataContainer;
    }

    /**
     * @return True if the villager is optimized by either this plugin or a supported alternative, otherwise false.
     */
    public boolean isOptimized() {
        if (!parseOther) {
            return isOptimized(Keyring.Spaces.VillagerOptimizer);
        }
        for (Keyring.Spaces pluginNamespaces : Keyring.Spaces.values()) {
            if (isOptimized(pluginNamespaces)) return true;
        }
        return false;
    }

    /**
     * @return True if the villager is optimized by the supported plugin, otherwise false.
     */
    public boolean isOptimized(Keyring.Spaces namespaces) {
        return switch (namespaces) {
            case VillagerOptimizer -> dataContainer.has(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING);
            case AntiVillagerLag -> dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_ANY.getKey(), PersistentDataType.STRING)
                    || dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_WORKSTATION.getKey(), PersistentDataType.STRING)
                    || dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_BLOCK.getKey(), PersistentDataType.STRING);
        };
    }

    /**
     * @param cooldown_millis The configured cooldown in millis until the next optimization is allowed to occur.
     * @return True if villager can be optimized again, otherwise false.
     */
    public boolean canOptimize(final long cooldown_millis) {
        if (parseOther) {
            if (
                    dataContainer.has(Keyring.AntiVillagerLag.NEXT_OPTIMIZATION_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG)
                    && System.currentTimeMillis() <= 1000 * dataContainer.get(Keyring.AntiVillagerLag.NEXT_OPTIMIZATION_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG)
            ) {
                return false;
            }
        }
        return System.currentTimeMillis() > getLastOptimize() + cooldown_millis;
    }

    /**
     * @param type OptimizationType the villager should be set to.
     */
    public void setOptimizationType(OptimizationType type) {
        me.xginko.villageroptimizer.VillagerOptimizer.getFoliaLib().getImpl().runAtEntityTimer(villager, setOptimization -> {
            // Keep repeating task until villager is no longer trading with a player
            if (villager.isTrading()) return;

            if (type.equals(OptimizationType.NONE) && isOptimized()) {
                if (!parseOther || isOptimized(Keyring.Spaces.VillagerOptimizer))
                    dataContainer.remove(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey());
                villager.setAware(true);
                villager.setAI(true); // Done for stability so villager is guaranteed to wake up
            } else {
                dataContainer.set(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING, type.name());
                villager.setAware(false);
            }

            // End repeating task once logic is finished
            setOptimization.cancel();
        }, 0L, 1L, TimeUnit.SECONDS);
    }

    /**
     * @return The current OptimizationType of the villager.
     */
    public @NotNull OptimizationType getOptimizationType() {
        if (!parseOther) {
            return getOptimizationType(Keyring.Spaces.VillagerOptimizer);
        }
        OptimizationType optimizationType = getOptimizationType(Keyring.Spaces.VillagerOptimizer);
        if (optimizationType != OptimizationType.NONE) {
            return optimizationType;
        }
        return getOptimizationType(Keyring.Spaces.AntiVillagerLag);
    }

    public @NotNull OptimizationType getOptimizationType(Keyring.Spaces namespaces) {
        return switch (namespaces) {
            case VillagerOptimizer -> {
                if (isOptimized(Keyring.Spaces.VillagerOptimizer)) {
                    yield OptimizationType.valueOf(dataContainer.get(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING));
                }
                yield OptimizationType.NONE;
            }
            case AntiVillagerLag -> {
                if (dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_BLOCK.getKey(), PersistentDataType.STRING)) {
                    yield OptimizationType.BLOCK;
                }
                if (dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_WORKSTATION.getKey(), PersistentDataType.STRING)) {
                    yield OptimizationType.WORKSTATION;
                }
                if (dataContainer.has(Keyring.AntiVillagerLag.OPTIMIZED_ANY.getKey(), PersistentDataType.STRING)) {
                    yield OptimizationType.COMMAND; // Best we can do
                }
                yield OptimizationType.NONE;
            }
        };
    }

    /**
     * Saves the system time in millis when the villager was last optimized.
     */
    public void saveOptimizeTime() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_OPTIMIZE.getKey(), PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * @return The system time in millis when the villager was last optimized, 0L if the villager was never optimized.
     */
    public long getLastOptimize() {
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_OPTIMIZE.getKey(), PersistentDataType.LONG)) {
            return dataContainer.get(Keyring.VillagerOptimizer.LAST_OPTIMIZE.getKey(), PersistentDataType.LONG);
        }
        return 0L;
    }

    /**
     * Here for convenience so the remaining millis since the last stored optimize time
     * can be easily calculated.
     * This enables new configured cooldowns to instantly apply instead of them being persistent.
     *
     * @param cooldown_millis The configured cooldown in milliseconds you want to check against.
     * @return The time left in millis until the villager can be optimized again.
     */
    public long getOptimizeCooldownMillis(final long cooldown_millis) {
        long remainingMillis = 0L;

        if (parseOther) {
            if (dataContainer.has(Keyring.AntiVillagerLag.NEXT_OPTIMIZATION_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG)) {
                remainingMillis = System.currentTimeMillis() - dataContainer.get(Keyring.AntiVillagerLag.NEXT_OPTIMIZATION_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG);
            }
        }

        if (remainingMillis > 0) return remainingMillis;

        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_OPTIMIZE.getKey(), PersistentDataType.LONG)) {
            return System.currentTimeMillis() - (dataContainer.get(Keyring.VillagerOptimizer.LAST_OPTIMIZE.getKey(), PersistentDataType.LONG) + cooldown_millis);
        }

        return cooldown_millis;
    }

    /**
     * Here for convenience so the remaining millis since the last stored restock time
     * can be easily calculated.
     *
     * @param cooldown_millis The configured cooldown in milliseconds you want to check against.
     * @return True if the villager has been loaded long enough.
     */
    public boolean canRestock(final long cooldown_millis) {
        return getLastRestock() + cooldown_millis <= villager.getWorld().getFullTime();
    }

    /**
     * Restock all trading recipes.
     */
    public void restock() {
        for (MerchantRecipe recipe : villager.getRecipes()) {
            recipe.setUses(0);
        }
    }

    /**
     * Saves the time of the in-game world when the entity was last restocked.
     */
    public void saveRestockTime() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_RESTOCK.getKey(), PersistentDataType.LONG, villager.getWorld().getFullTime());
    }

    /**
     * @return The time of the in-game world when the entity was last restocked.
     */
    public long getLastRestock() {
        long lastRestock = 0L;
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_RESTOCK.getKey(), PersistentDataType.LONG)) {
            lastRestock = dataContainer.get(Keyring.VillagerOptimizer.LAST_RESTOCK.getKey(), PersistentDataType.LONG);
        }
        if (parseOther) {
            if (dataContainer.has(Keyring.AntiVillagerLag.LAST_RESTOCK_WORLDFULLTIME.getKey(), PersistentDataType.LONG)) {
                long lastAVLRestock = dataContainer.get(Keyring.AntiVillagerLag.LAST_RESTOCK_WORLDFULLTIME.getKey(), PersistentDataType.LONG);
                if (lastRestock < lastAVLRestock) {
                    lastRestock = lastAVLRestock;
                }
            }
        }
        return lastRestock;
    }

    public long getRestockCooldownMillis(final long cooldown_millis) {
        return dataContainer.has(Keyring.VillagerOptimizer.LAST_RESTOCK.getKey(), PersistentDataType.LONG) ? (villager.getWorld().getFullTime() - (dataContainer.get(Keyring.VillagerOptimizer.LAST_RESTOCK.getKey(), PersistentDataType.LONG) + cooldown_millis)) : cooldown_millis;
    }

    /**
     * @return The level between 1-5 calculated from the villagers experience.
     */
    public int calculateLevel() {
        // https://minecraft.fandom.com/wiki/Trading#Mechanics
        int vilEXP = villager.getVillagerExperience();
        if (vilEXP >= 250) return 5;
        if (vilEXP >= 150) return 4;
        if (vilEXP >= 70) return 3;
        if (vilEXP >= 10) return 2;
        return 1;
    }

    /**
     * @param cooldown_millis The configured cooldown in milliseconds you want to check against.
     * @return Whether the villager can be leveled up or not with the checked milliseconds
     */
    public boolean canLevelUp(final long cooldown_millis) {
        if (villager.getWorld().getFullTime() < getLastLevelUpTime() + cooldown_millis) {
            return false;
        }

        if (parseOther) {
            return !dataContainer.has(Keyring.AntiVillagerLag.NEXT_LEVELUP_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG)
                    || System.currentTimeMillis() > dataContainer.get(Keyring.AntiVillagerLag.NEXT_LEVELUP_SYSTIME_SECONDS.getKey(), PersistentDataType.LONG) * 1000;
        }

        return true;
    }

    /**
     * Saves the time of the in-game world when the entity was last leveled up.
     */
    public void saveLastLevelUp() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_LEVELUP.getKey(), PersistentDataType.LONG, villager.getWorld().getFullTime());
    }

    /**
     * Here for convenience so the remaining millis since the last stored level-up time
     * can be easily calculated.
     *
     * @return The time of the in-game world when the entity was last leveled up.
     */
    public long getLastLevelUpTime() {
        return dataContainer.has(Keyring.VillagerOptimizer.LAST_LEVELUP.getKey(), PersistentDataType.LONG) ? dataContainer.get(Keyring.VillagerOptimizer.LAST_LEVELUP.getKey(), PersistentDataType.LONG) : 0L;
    }

    public long getLevelCooldownMillis(final long cooldown_millis) {
        return dataContainer.has(Keyring.VillagerOptimizer.LAST_LEVELUP.getKey(), PersistentDataType.LONG) ? (villager.getWorld().getFullTime() - (dataContainer.get(Keyring.VillagerOptimizer.LAST_LEVELUP.getKey(), PersistentDataType.LONG) + cooldown_millis)) : cooldown_millis;
    }

    public void memorizeName(final Component customName) {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_OPTIMIZE_NAME.getKey(), PersistentDataType.STRING, MiniMessage.miniMessage().serialize(customName));
    }

    public @Nullable Component getMemorizedName() {
        return dataContainer.has(Keyring.VillagerOptimizer.LAST_OPTIMIZE_NAME.getKey(), PersistentDataType.STRING) ? MiniMessage.miniMessage().deserialize(dataContainer.get(Keyring.VillagerOptimizer.LAST_OPTIMIZE_NAME.getKey(), PersistentDataType.STRING)) : null;
    }

    public void forgetName() {
        dataContainer.remove(Keyring.VillagerOptimizer.LAST_OPTIMIZE_NAME.getKey());
    }
}