package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class EnvoyCommand implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin plugin;
    private final EnvoyManager envoyManager;

    private final Map<UUID, String> openEditors = new HashMap<>();

    public EnvoyCommand(JavaPlugin plugin, EnvoyManager envoyManager) {
        this.plugin = plugin;
        this.envoyManager = envoyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("envoy")) {
            if (!canManage(sender)) {
                sender.sendMessage("§cOnly admin/dev/owner can manage envoys.");
                return true;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("nether")) {
                    EnvoyManager.ActiveEnvoy envoy = envoyManager.spawnHeroicNetherEnvoy();
                    if (envoy == null) {
                        sender.sendMessage("§cHeroic envoy not spawned. Ensure nether is set and at least 1 player is inside the nether warzone claim.");
                        return true;
                    }
                    Bukkit.broadcastMessage("§5[SimpleEnvoy] §dHeroic Nether Envoy has spawned at §fnether§d!");
                    sender.sendMessage("§aSpawned Heroic Nether Envoy at " + formatLocation(envoy.location()) + "§a.");
                    return true;
                }

                if (args.length >= 2 && !args[1].equalsIgnoreCase("default")) {
                    sender.sendMessage("§cUnknown envoy type: §f" + args[1]);
                    sender.sendMessage("§cUsage: /envoy start [default|nether]");
                    return true;
                }

                List<EnvoyManager.ActiveEnvoy> spawned = envoyManager.spawnStandardEnvoys();
                if (spawned.isEmpty()) {
                    sender.sendMessage("§cNo envoys spawned. Ensure spawn/desert/plains are set and each has at least 1 player inside its warzone claim.");
                    return true;
                }
                Bukkit.broadcastMessage("§6[SimpleEnvoy] §eEnvoys have spawned at §fspawn, desert, and plains§e!");
                sender.sendMessage("§aSpawned " + spawned.size() + " standard envoys.");
                for (EnvoyManager.ActiveEnvoy envoy : spawned) {
                    sender.sendMessage("§7- §f" + envoy.warpName() + " §7at " + formatLocation(envoy.location()));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("nether")) {
                EnvoyManager.ActiveEnvoy envoy = envoyManager.spawnHeroicNetherEnvoy();
                if (envoy == null) {
                    sender.sendMessage("§cHeroic envoy not spawned. Ensure nether is set and at least 1 player is inside the nether warzone claim.");
                    return true;
                }
                Bukkit.broadcastMessage("§5[SimpleEnvoy] §dHeroic Nether Envoy has spawned at §fnether§d!");
                sender.sendMessage("§aSpawned Heroic Nether Envoy.");
                return true;
            }

            if (args[0].equalsIgnoreCase("clear")) {
                envoyManager.clearActiveEnvoys();
                sender.sendMessage("§aCleared all active envoys.");
                return true;
            }

            sender.sendMessage("§cUsage: /envoy [start [default|nether]|nether|clear]");
            return true;
        }

        if (cmd.equals("envoyset")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }
            if (!canManage(player)) {
                player.sendMessage("§cOnly admin/dev/owner can use this command.");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("§cUsage: /envoyset <spawn|desert|plains|nether>");
                return true;
            }

            String warpName = args[0].toLowerCase(Locale.ROOT);
            if (!warpName.equals("spawn")
                    && !warpName.equals("desert")
                    && !warpName.equals("plains")
                    && !warpName.equals("nether")) {
                player.sendMessage("§cUsage: /envoyset <spawn|desert|plains|nether>");
                return true;
            }

            if (!envoyManager.setCenter(warpName, player.getLocation())) {
                player.sendMessage("§cFailed to set envoy center. Try again.");
                return true;
            }
            player.sendMessage("§aEnvoy center §f" + warpName + " §aset.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!canManage(player)) {
            player.sendMessage("§cOnly admin/dev/owner can use this command.");
            return true;
        }

        if (cmd.equals("envoyedit")) {
            if (args.length < 1) {
                player.sendMessage("§cUsage: /envoyedit <simple|rare|legendary|godly|heroic_nether>");
                return true;
            }
            String tier = envoyManager.normalizeTier(args[0]);
            openEditor(player, tier);
            return true;
        }

        if (cmd.equals("envoyadd")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /envoyadd <tier> <chancePercent>");
                return true;
            }

            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType() == Material.AIR) {
                player.sendMessage("§cHold the item you want to add in your main hand.");
                return true;
            }

            double chance;
            try {
                chance = Double.parseDouble(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§cChance must be a number.");
                return true;
            }

            if (chance <= 0) {
                player.sendMessage("§cChance must be greater than 0.");
                return true;
            }

            String tier = envoyManager.normalizeTier(args[0]);
            envoyManager.addLoot(tier, inHand.clone(), chance);
            player.sendMessage("§aAdded item to §f" + tier + " §aloot pool with §f" + chance + "% §achance.");
            return true;
        }

        return false;
    }

    private void openEditor(Player player, String tier) {
        List<EnvoyManager.LootEntry> entries = envoyManager.getLoot(tier);
        Inventory inventory = Bukkit.createInventory(null, 54, "§6Envoy Edit: " + tier);

        for (int i = 0; i < Math.min(entries.size(), 54); i++) {
            EnvoyManager.LootEntry entry = entries.get(i);
            ItemStack item = entry.item().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7");
                lore.add("§eChance: §f" + entry.chance() + "%");
                lore.add("§cRight-click to remove");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        openEditors.put(player.getUniqueId(), tier);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith("§6Envoy Edit: ")) return;

        event.setCancelled(true);

        String tier = openEditors.get(player.getUniqueId());
        if (tier == null) return;

        int slot = event.getSlot();
        if (slot < 0) return;

        if (event.getClick().isRightClick()) {
            if (envoyManager.removeLootAt(tier, slot)) {
                player.sendMessage("§aRemoved loot entry from §f" + tier + "§a.");
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, tier));
            }
        }
    }

    @EventHandler
    public void onInteractEnvoy(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        EnvoyManager.ActiveEnvoy envoy = envoyManager.getActiveEnvoy(block);
        if (envoy == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack reward = envoyManager.openEnvoy(block);

        if (reward == null) {
            player.sendMessage("§cThis envoy had no configured loot.");
            return;
        }

        Map<Integer, ItemStack> left = player.getInventory().addItem(reward);
        if (!left.isEmpty()) {
            left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        player.sendMessage("§aYou opened a §f" + envoy.tier() + " §aenvoy at §f" + envoy.warpName() + "§a.");

        SimpleFactionsPlugin sf = SimpleFactionsPlugin.getInstance();
        if (sf != null && sf.getChallengeManager() != null) {
            sf.getChallengeManager().increment(
                    player.getUniqueId(),
                    player.getName(),
                    ChallengeManager.TrackerType.ENVOY_OPEN,
                    1L
            );
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        // reserved for future in-chat editor UX; keep listener for possible extension
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (!canManage(sender)) return List.of();

        if (cmd.equals("envoy")) {
            if (args.length == 1) return filter(List.of("start", "nether", "clear"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) return filter(List.of("default", "nether"), args[1]);
            return List.of();
        }

        if (cmd.equals("envoyset")) {
            if (args.length == 1) return filter(List.of("spawn", "desert", "plains", "nether"), args[0]);
            return List.of();
        }

        if (cmd.equals("envoyedit") || cmd.equals("envoyadd")) {
            if (args.length == 1) return filter(envoyManager.getTiers(), args[0]);
            if (cmd.equals("envoyadd") && args.length == 2) return filter(List.of("5", "10", "20", "30", "50"), args[1]);
            return List.of();
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(option);
        }
        return out;
    }

    private boolean canManage(CommandSender sender) {
        return sender.hasPermission("simplefactions.envoy.manage")
                || sender.hasPermission("group.owner")
                || sender.hasPermission("group.admin")
                || sender.hasPermission("group.dev")
                || sender.isOp();
    }

    private String formatLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return "unknown";
        return location.getWorld().getName() + " "
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }
}
