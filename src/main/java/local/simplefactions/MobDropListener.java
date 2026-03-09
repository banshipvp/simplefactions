package local.simplefactions;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Overrides mob drop tables with custom loot and handles stacked-mob mass death.
 *
 * <h3>Priority</h3>
 * Runs at {@link EventPriority#HIGHEST} so it fires before
 * {@link SpawnerStackManager} (which uses {@code NORMAL}) allowing us to
 * adjust the in-memory stack count before SpawnerStackManager decides how many
 * mobs to respawn.
 *
 * <h3>Mass-death scaling</h3>
 * When a stacked mob entity represents N mobs and dies from an environmental
 * cause, we compute how many actually die this "hit" and multiply drops:
 * <ul>
 *   <li>FALL / VOID: entire stack dies (all mobs drop)</li>
 *   <li>LAVA / FIRE: 5-10 % of the stack dies per hit</li>
 *   <li>DROWNING: ~10 % dies per tick</li>
 *   <li>Player kill: exactly 1 mob dies (normal behaviour)</li>
 * </ul>
 */
public class MobDropListener implements Listener {

    private static final NamespacedKey KEY_STACK_MOB   = new NamespacedKey("simplefactions", "stack_mob");
    private static final NamespacedKey KEY_STACK_COUNT = new NamespacedKey("simplefactions", "stack_count");

    private final SpawnerStackManager stackManager;
    private final Random rng = new Random();

    public MobDropListener(SpawnerStackManager stackManager) {
        this.stackManager = stackManager;
    }

    // ── Main handler ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        EntityType type = entity.getType();

        // ── Stack size detection ───────────────────────────────────────────────
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        boolean isStacked = pdc.has(KEY_STACK_MOB, PersistentDataType.BYTE);
        int stackCount = 1;
        if (isStacked && stackManager != null) {
            stackCount = stackManager.getMobStackCount(entity.getUniqueId());
        } else if (isStacked) {
            stackCount = pdc.getOrDefault(KEY_STACK_COUNT, PersistentDataType.INTEGER, 1);
        }

        // ── How many mobs die this event? ────────────────────────────────────
        int killedCount = computeKilledCount(entity, stackCount);

        // ── Adjust SpawnerStackManager so it respawns fewer mobs ─────────────
        if (isStacked && killedCount > 1 && stackManager != null) {
            // We'll kill killedCount; SpawnerStackManager will subtract 1 more.
            // So we pre-reduce by (killedCount - 1) now.
            stackManager.adjustMobStackCount(entity.getUniqueId(), killedCount - 1);
        }

        // ── Drop overrides ────────────────────────────────────────────────────
        if (hasCustomDrops(type)) {
            event.getDrops().clear();
            event.setDroppedExp(event.getDroppedExp() * killedCount);
            event.getDrops().addAll(buildCustomDrops(type, entity, killedCount));
        } else if (killedCount > 1) {
            // Vanilla drops, but multiply for mass kill
            List<ItemStack> scaled = scaleDrops(new ArrayList<>(event.getDrops()), killedCount);
            event.getDrops().clear();
            event.setDroppedExp(event.getDroppedExp() * killedCount);
            event.getDrops().addAll(scaled);
        }
    }

    // ── Kill-count calculation ─────────────────────────────────────────────────

    private int computeKilledCount(LivingEntity entity, int stackCount) {
        if (stackCount <= 1) return 1;

        EntityDamageEvent lastDmg = entity.getLastDamageCause();
        if (lastDmg == null) return 1;

        return switch (lastDmg.getCause()) {
            case FALL, VOID -> stackCount;                                     // everything dies
            case LAVA, FIRE, FIRE_TICK ->
                    Math.max(1, (int) Math.round(stackCount * (0.05 + rng.nextDouble() * 0.05))); // 5-10%
            case DROWNING -> Math.max(1, stackCount / 10);
            default -> 1;                                                       // player/projectile = 1
        };
    }

    // ── Custom drop table ─────────────────────────────────────────────────────

    /** Returns true for mobs whose drops this listener fully controls. */
    private boolean hasCustomDrops(EntityType type) {
        return switch (type) {
            case IRON_GOLEM, GHAST, MAGMA_CUBE,
                 ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER,
                 CREEPER, BLAZE, ENDERMAN, WITCH, WITHER_SKELETON -> true;
            default -> false;
        };
    }

    private List<ItemStack> buildCustomDrops(EntityType type, LivingEntity entity, int count) {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drops.addAll(dropsForOne(type, entity));
        }
        return combinedDrops(drops);
    }

    /** One kill's worth of custom drops for the given mob type. */
    private List<ItemStack> dropsForOne(EntityType type, LivingEntity entity) {
        List<ItemStack> d = new ArrayList<>();
        switch (type) {
            case IRON_GOLEM -> {
                // 2-5 iron ingots; NO poppies
                d.add(new ItemStack(Material.IRON_INGOT, 2 + rng.nextInt(4)));
            }
            case GHAST -> {
                // Emeralds (1-2) instead of ghast tears / gunpowder
                d.add(new ItemStack(Material.EMERALD, 1 + rng.nextInt(2)));
            }
            case MAGMA_CUBE -> {
                Slime slime = (Slime) entity;
                if (slime.getSize() >= 2) {
                    // Large magma cube → 1 diamond
                    d.add(new ItemStack(Material.DIAMOND, 1));
                } else if (rng.nextBoolean()) {
                    // Small → occasional magma cream
                    d.add(new ItemStack(Material.MAGMA_CREAM, 1));
                }
            }
            case ZOMBIE -> {
                int flesh = rng.nextInt(3); // 0-2
                if (flesh > 0) d.add(new ItemStack(Material.ROTTEN_FLESH, flesh));
                if (rng.nextDouble() < 0.05) d.add(new ItemStack(Material.IRON_INGOT, 1));
            }
            case SKELETON -> {
                int arrows = rng.nextInt(3);
                if (arrows > 0) d.add(new ItemStack(Material.ARROW, arrows));
                int bones = rng.nextInt(3);
                if (bones > 0) d.add(new ItemStack(Material.BONE, bones));
            }
            case SPIDER -> {
                int string = rng.nextInt(3);
                if (string > 0) d.add(new ItemStack(Material.STRING, string));
                if (rng.nextDouble() < 0.33) d.add(new ItemStack(Material.SPIDER_EYE, 1));
            }
            case CAVE_SPIDER -> {
                int string = rng.nextInt(3);
                if (string > 0) d.add(new ItemStack(Material.STRING, string));
            }
            case CREEPER -> {
                int gp = rng.nextInt(3);
                if (gp > 0) d.add(new ItemStack(Material.GUNPOWDER, gp));
            }
            case BLAZE -> {
                if (rng.nextBoolean()) d.add(new ItemStack(Material.BLAZE_ROD, 1));
            }
            case ENDERMAN -> {
                if (rng.nextDouble() < 0.5) d.add(new ItemStack(Material.ENDER_PEARL, 1));
            }
            case WITCH -> {
                Material mat = rng.nextBoolean() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER;
                d.add(new ItemStack(mat, 1 + rng.nextInt(2)));
            }
            case WITHER_SKELETON -> {
                int bones = rng.nextInt(3);
                if (bones > 0) d.add(new ItemStack(Material.BONE, bones));
                if (rng.nextDouble() < 0.25) d.add(new ItemStack(Material.COAL, 1));
                if (rng.nextDouble() < 0.025) d.add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
            }
            default -> { /* handled above by hasCustomDrops guard */ }
        }
        return d;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Multiply a list of drops by {@code factor}, combining same-material stacks
     * and splitting oversized stacks into max-size chunks.
     */
    private List<ItemStack> scaleDrops(List<ItemStack> original, int factor) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack is : original) {
            if (is != null && !is.getType().isAir()) {
                totals.merge(is.getType(), is.getAmount() * factor, Integer::sum);
            }
        }
        return splitToStacks(totals);
    }

    /** Merge and deduplicate drops into min-possible ItemStack list. */
    private List<ItemStack> combinedDrops(List<ItemStack> drops) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack is : drops) {
            if (is != null && !is.getType().isAir()) {
                totals.merge(is.getType(), is.getAmount(), Integer::sum);
            }
        }
        return splitToStacks(totals);
    }

    private List<ItemStack> splitToStacks(Map<Material, Integer> totals) {
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : totals.entrySet()) {
            int remaining = e.getValue();
            int maxStack = e.getKey().getMaxStackSize();
            while (remaining > 0) {
                int chunk = Math.min(remaining, maxStack);
                result.add(new ItemStack(e.getKey(), chunk));
                remaining -= chunk;
            }
        }
        return result;
    }
}
