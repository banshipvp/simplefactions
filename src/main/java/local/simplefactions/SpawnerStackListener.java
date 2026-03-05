package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Handles spawner-stack interactions:
 * <ul>
 *   <li><b>Left-click</b> a spawner → show stack info.</li>
 *   <li><b>Right-click</b> a spawner while holding a matching spawner → stack it.</li>
 * </ul>
 */
public class SpawnerStackListener implements Listener {

    private final SpawnerStackManager stackManager;
    private final FactionManager factionManager;

    public SpawnerStackListener(SpawnerStackManager stackManager, FactionManager factionManager) {
        this.stackManager  = stackManager;
        this.factionManager = factionManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Ignore off-hand events to prevent double-firing
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        String world = block.getWorld().getName();
        int bx = block.getX(), by = block.getY(), bz = block.getZ();

        // ── Claim access check ────────────────────────────────────────────────
        int cx = block.getChunk().getX(), cz = block.getChunk().getZ();
        String owner = factionManager.getClaimOwner(world, cx, cz);

        // ── LEFT CLICK → show stack info ──────────────────────────────────────
        if (action == Action.LEFT_CLICK_BLOCK) {
            SpawnerStack stack = stackManager.getStack(world, bx, by, bz);
            if (stack == null) {
                player.sendMessage(ChatColor.GRAY + "This spawner is not tracked by SimpleFactions.");
                return;
            }
            SpawnerType st = SpawnerType.fromEntityKey(stack.getEntityTypeKey());
            String name = st != null ? st.getDisplayName() : stack.getEntityTypeKey();

            int count    = stack.getCount();
            double mineCost = SpawnerType.getBaseValue(stack.getEntityTypeKey()) * 0.10;

            player.sendMessage(ChatColor.GOLD + "─── " + name + " Spawner Stack ───");
            player.sendMessage(ChatColor.YELLOW + "  Count:    §f" + count + "§e/" + SpawnerStack.MAX_STACK);
            player.sendMessage(ChatColor.YELLOW + "  Value:    §f$" + formatMoney(stack.getTotalCurrentValue()));
            player.sendMessage(ChatColor.YELLOW + "  Mine Cost (Silk): §f$" + formatMoney(mineCost));
            player.sendMessage(ChatColor.YELLOW + "  Faction:  §f" + capitalize(stack.getFactionName()));
            if (count < SpawnerStack.MAX_STACK) {
                player.sendMessage(ChatColor.GRAY + "  Right-click with a " + name
                        + " Spawner to add to this stack.");
            } else {
                player.sendMessage(ChatColor.RED + "  ✖ Stack is full (10/10). No more can be added.");
            }
            return;
        }

        // ── RIGHT CLICK → attempt stack ───────────────────────────────────────
        // Must be in a claimed chunk to stack
        if (owner == null) {
            // Unclaimed land – normal block interaction; don't interfere
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.SPAWNER) return;

        // Get the entity type from the held spawner
        String heldType = getEntityTypeFromItem(held);
        if (heldType == null) return;

        SpawnerStack existing = stackManager.getStack(world, bx, by, bz);
        if (existing == null) {
            player.sendMessage(ChatColor.RED + "There is no tracked spawner stack here.");
            return;
        }

        if (!existing.getEntityTypeKey().equalsIgnoreCase(heldType)) {
            SpawnerType existingSt = SpawnerType.fromEntityKey(existing.getEntityTypeKey());
            String existingName = existingSt != null ? existingSt.getDisplayName() : existing.getEntityTypeKey();
            player.sendMessage(ChatColor.RED + "✖ You can only stack §e" + existingName
                    + " §cSpawners on this block.");
            event.setCancelled(true);
            return;
        }

        if (existing.isFull()) {
            player.sendMessage(ChatColor.RED + "✖ This spawner stack is already at the maximum (10/10).");
            event.setCancelled(true);
            return;
        }

        // Permission check: must be able to place blocks in this claim
        FactionManager.Faction playerFaction = factionManager.getFaction(player.getUniqueId());
        boolean canStack = playerFaction != null && playerFaction.getName().equalsIgnoreCase(owner);
        if (!canStack && !player.hasPermission("simplefactions.admin")) {
            player.sendMessage(ChatColor.RED + "✖ You cannot stack spawners here — this claim belongs to §7"
                    + capitalize(owner) + "§c.");
            event.setCancelled(true);
            return;
        }

        // Do the stack
        boolean success = stackManager.tryStack(world, bx, by, bz, heldType);
        if (success) {
            // Consume 1 from hand
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            int newCount = existing.getCount(); // already incremented
            SpawnerType st = SpawnerType.fromEntityKey(heldType);
            String name  = st != null ? st.getDisplayName() : heldType;

            player.sendMessage(ChatColor.GREEN + "✦ Stacked §e" + name + " Spawner§a! "
                    + ChatColor.WHITE + "(" + newCount + "/" + SpawnerStack.MAX_STACK + ")");

            event.setCancelled(true); // prevent opening spawner GUI
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getEntityTypeFromItem(ItemStack item) {
        if (!item.hasItemMeta()) return getDefaultEntityType(item);
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return getDefaultEntityType(item);
        BlockState state = bsm.getBlockState();
        if (!(state instanceof CreatureSpawner cs)) return getDefaultEntityType(item);
        EntityType et = cs.getSpawnedType();
        if (et == null || et == EntityType.PIG) {
            // PIG is the Bukkit default for unset spawners; keep it but also
            // try the fall-through (no-meta spawner).
            return et == null ? getDefaultEntityType(item) : et.name().toLowerCase();
        }
        return et.name().toLowerCase();
    }

    private static String getDefaultEntityType(ItemStack item) {
        // Vanilla spawner with no meta → pig (Bukkit default)
        return "pig";
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
