package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerVaultCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String MENU_TITLE = "§9§lPersonal Vaults";
    private static final String VAULT_PREFIX = "§9§lPV #";
    private static final String ICON_PICKER_PREFIX = "§d§lPV Icon Picker #";

    private final JavaPlugin plugin;
    private final PlayerVaultManager vaultManager;
    private final PlayerRankManager rankManager;

    private final Map<UUID, Map<Integer, Integer>> menuSlotToVault = new HashMap<>();
    private final Map<UUID, String> filters = new HashMap<>();
    private final Map<UUID, Integer> awaitingRename = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> openIconPickers = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingFilterInput = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reopenMenuOnClose = ConcurrentHashMap.newKeySet();
            private static final Map<Integer, Material> ICON_PICKER_SLOTS = Map.ofEntries(
                Map.entry(10, Material.ENDER_PEARL),
                Map.entry(11, Material.DIAMOND_BLOCK),
                Map.entry(12, Material.DIAMOND_ORE),
                Map.entry(13, Material.ENDER_EYE),
                Map.entry(14, Material.PAPER),
                Map.entry(15, Material.BOOK),
                Map.entry(16, Material.SUGAR),
                Map.entry(19, Material.GLOWSTONE_DUST),
                Map.entry(20, Material.BEACON),
                Map.entry(21, Material.EXPERIENCE_BOTTLE),
                Map.entry(22, Material.INK_SAC)
            );

    public PlayerVaultCommand(JavaPlugin plugin, PlayerVaultManager vaultManager, PlayerRankManager rankManager) {
        this.plugin = plugin;
        this.vaultManager = vaultManager;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        return handlePlayerCommand(player, command.getName(), args);
    }

    private boolean handlePlayerCommand(Player player, String commandName, String[] args) {
        String cmd = commandName.toLowerCase(Locale.ROOT);

        if (cmd.equals("pv") || cmd.equals("pvs")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("search")) {
                if (args.length == 1) {
                    filters.remove(player.getUniqueId());
                    player.sendMessage("§aPV filter cleared.");
                    openVaultMenu(player);
                    return true;
                }
                String filter = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        .trim().toLowerCase(Locale.ROOT);
                filters.put(player.getUniqueId(), filter);
                player.sendMessage("§aPV filter set to: §f" + filter);
                openVaultMenu(player);
                return true;
            }
        }

        if (cmd.equals("pvfilter") || cmd.equals("pvsearch")) {
            if (args.length == 0) {
                filters.remove(player.getUniqueId());
                player.sendMessage("§aPV filter cleared.");
                openVaultMenu(player);
                return true;
            }
            String filter = String.join(" ", args).trim().toLowerCase(Locale.ROOT);
            filters.put(player.getUniqueId(), filter);
            player.sendMessage("§aPV filter set to: §f" + filter);
            openVaultMenu(player);
            return true;
        }

        if (cmd.equals("pvdump")) {
            if (args.length != 1) {
                player.sendMessage("§cUsage: /pvdump <pv-number>");
                return true;
            }
            Integer index = parseVaultIndex(args[0]);
            if (index == null) {
                player.sendMessage("§cPV number must be a positive number.");
                return true;
            }
            dumpInventoryToVault(player, index);
            return true;
        }

        if (args.length == 0) {
            openVaultMenu(player);
            return true;
        }

        Integer index = parseVaultIndex(args[0]);
        if (index == null) {
            openVaultMenu(player);
            return true;
        }

        openVaultInventory(player, index, false);
        return true;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 2 || message.charAt(0) != '/') return;

        String raw = message.substring(1).trim();
        if (raw.isEmpty()) return;

        String[] parts = raw.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) return; // namespaced command already explicit

        if (!label.equals("pv") && !label.equals("pvs") && !label.equals("pvsearch")) {
            return;
        }

        event.setCancelled(true);

        String[] args = new String[Math.max(0, parts.length - 1)];
        if (parts.length > 1) {
            System.arraycopy(parts, 1, args, 0, parts.length - 1);
        }

        handlePlayerCommand(event.getPlayer(), label, args);
    }

    private void openVaultMenu(Player player) {
        UUID playerId = player.getUniqueId();
        int maxVaults = rankManager.getMaxVaults(playerId);
        String filter = filters.getOrDefault(playerId, "");

        Inventory menu = Bukkit.createInventory(null, 54, MENU_TITLE);
        Map<Integer, Integer> slotMap = new LinkedHashMap<>();

        int slot = 0;
        for (int index = 1; index <= maxVaults && slot < 45; index++) {
            PlayerVaultManager.VaultData vault = vaultManager.getOrCreateVault(playerId, index);
            if (!filterMatches(vault, filter, index)) continue;

            menu.setItem(slot, createVaultMenuItem(index, vault, filter));
            slotMap.put(slot, index);
            slot++;
        }

        ItemStack search = new ItemStack(Material.NAME_TAG);
        ItemMeta searchMeta = search.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName("§ePV Search");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current filter: §f" + (filter.isBlank() ? "none" : filter));
            lore.add("§7Left click: search via chat");
            lore.add("§7Right click: clear filter");
            searchMeta.setLore(lore);
            search.setItemMeta(searchMeta);
        }
        menu.setItem(49, search);

        menuSlotToVault.put(playerId, slotMap);
        player.openInventory(menu);
    }

    private ItemStack createVaultMenuItem(int index, PlayerVaultManager.VaultData vault, String filter) {
        ItemStack item = new ItemStack(vault.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = "§b" + vault.getName();
            if (filter != null && !filter.isBlank()) {
                displayName = highlight(displayName, filter);
            }
            meta.setDisplayName(displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§7PV Number: §f" + index);
            lore.add("§7Icon: §f" + (filter == null || filter.isBlank()
                    ? vault.getIcon().name().toLowerCase(Locale.ROOT)
                    : highlight(vault.getIcon().name().toLowerCase(Locale.ROOT), filter)));
            lore.add("§7");
            lore.add("§aLeft-click: Open");
            lore.add("§eMiddle-click: Rename");
            lore.add("§dRight-click: Change item type");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean filterMatches(PlayerVaultManager.VaultData vault, String filter, int index) {
        if (filter == null || filter.isBlank()) return true;
        String normalized = filter.toLowerCase(Locale.ROOT);

        // Check vault name, icon name, and vault number
        if (vault.getName().toLowerCase(Locale.ROOT).contains(normalized)
                || vault.getIcon().name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(normalized)
                || String.valueOf(index).contains(normalized)) {
            return true;
        }

        // Check item contents
        ItemStack[] contents = vault.getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR) continue;
                // Check material name (e.g. "chicken_spawner" → "chicken spawner")
                if (item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(normalized)) return true;
                // Check custom display name (strip colour codes)
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        String displayName = meta.getDisplayName()
                                .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                                .toLowerCase(Locale.ROOT);
                        if (displayName.contains(normalized)) return true;
                    }
                }
            }
        }
        return false;
    }

    private void openVaultInventory(Player player, int index, boolean fromMenu) {
        int maxVaults = rankManager.getMaxVaults(player.getUniqueId());
        if (index < 1 || index > maxVaults) {
            player.sendMessage("§cYou only have access to PV 1-" + maxVaults + ".");
            return;
        }

        PlayerVaultManager.VaultData vault = vaultManager.getOrCreateVault(player.getUniqueId(), index);
        Inventory inventory = Bukkit.createInventory(player, 54, VAULT_PREFIX + index);
        inventory.setContents(cloneContents(vault.getContents()));
        if (fromMenu) {
            reopenMenuOnClose.add(player.getUniqueId());
        }
        player.openInventory(inventory);
    }

    private void saveVaultInventory(Player player, Inventory inventory, int index) {
        PlayerVaultManager.VaultData data = vaultManager.getOrCreateVault(player.getUniqueId(), index);
        data.setContents(cloneContents(inventory.getContents()));
        vaultManager.save();
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[54];
        if (contents == null) return copy;

        for (int i = 0; i < Math.min(54, contents.length); i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private void dumpInventoryToVault(Player player, int index) {
        int maxVaults = rankManager.getMaxVaults(player.getUniqueId());
        if (index < 1 || index > maxVaults) {
            player.sendMessage("§cYou only have access to PV 1-" + maxVaults + ".");
            return;
        }

        PlayerVaultManager.VaultData data = vaultManager.getOrCreateVault(player.getUniqueId(), index);
        Inventory vaultInventory = Bukkit.createInventory(null, 54);
        vaultInventory.setContents(cloneContents(data.getContents()));

        ItemStack[] storage = player.getInventory().getStorageContents();
        List<ItemStack> toMove = new ArrayList<>();
        for (ItemStack stack : storage) {
            if (stack != null && stack.getType() != Material.AIR) {
                toMove.add(stack.clone());
            }
        }

        if (toMove.isEmpty()) {
            player.sendMessage("§eYou have no items to dump.");
            return;
        }

        for (ItemStack stack : toMove) {
            Map<Integer, ItemStack> leftovers = vaultInventory.addItem(stack);
            if (!leftovers.isEmpty()) {
                player.sendMessage("§cNot enough room in PV " + index + ". Please choose another PV.");
                return;
            }
        }

        ItemStack[] playerStorage = player.getInventory().getStorageContents();
        for (int i = 0; i < playerStorage.length; i++) {
            ItemStack stack = playerStorage[i];
            if (stack != null && stack.getType() != Material.AIR) {
                playerStorage[i] = null;
            }
        }
        player.getInventory().setStorageContents(playerStorage);

        data.setContents(cloneContents(vaultInventory.getContents()));
        vaultManager.save();
        player.sendMessage("§aDumped inventory into PV " + index + ".");
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!MENU_TITLE.equals(title)) return;

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();

        if (event.getSlot() == 49) {
            if (event.getClick().isRightClick()) {
                filters.remove(playerId);
                player.sendMessage("§aPV filter cleared.");
                openVaultMenu(player);
                return;
            }

            awaitingFilterInput.add(playerId);
            awaitingRename.remove(playerId);
            openIconPickers.remove(playerId);
            player.closeInventory();
            player.sendMessage("§eType your PV search in chat. Type §fcancel §eto abort or §fclear §eto reset filter.");
            return;
        }

        Map<Integer, Integer> slotMap = menuSlotToVault.get(playerId);
        if (slotMap == null) return;

        Integer index = slotMap.get(event.getSlot());
        if (index == null) return;

        if (event.getClick() == ClickType.MIDDLE) {
            awaitingRename.put(playerId, index);
            openIconPickers.remove(playerId);
            player.closeInventory();
            player.sendMessage("§eType a new name for PV " + index + " in chat. Type 'cancel' to abort.");
            return;
        }

        if (event.getClick().isRightClick()) {
            awaitingRename.remove(playerId);
            openIconPicker(player, index);
            return;
        }

        openVaultInventory(player, index, true);
    }

    @EventHandler
    public void onVaultClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title.startsWith(ICON_PICKER_PREFIX)) {
            UUID playerId = player.getUniqueId();
            if (openIconPickers.remove(playerId) != null) {
                Bukkit.getScheduler().runTask(plugin, () -> openVaultMenu(player));
            }
            return;
        }

        if (!title.startsWith(VAULT_PREFIX)) return;

        Integer index = parseVaultIndex(title.substring(VAULT_PREFIX.length()).trim());
        if (index == null) return;

        saveVaultInventory(player, event.getInventory(), index);

        UUID playerId = player.getUniqueId();
        if (reopenMenuOnClose.remove(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> openVaultMenu(player));
        }
    }

    @EventHandler
    public void onChatInput(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Integer renameIndex = awaitingRename.get(playerId);
        boolean waitingForFilter = awaitingFilterInput.contains(playerId);
        if (renameIndex == null && !waitingForFilter) return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;

            if (input.equalsIgnoreCase("cancel")) {
                awaitingRename.remove(playerId);
                openIconPickers.remove(playerId);
                awaitingFilterInput.remove(playerId);
                player.sendMessage("§ePV edit cancelled.");
                openVaultMenu(player);
                return;
            }

            if (waitingForFilter) {
                awaitingFilterInput.remove(playerId);
                if (input.equalsIgnoreCase("clear")) {
                    filters.remove(playerId);
                    player.sendMessage("§aPV filter cleared.");
                } else {
                    String filter = input.trim().toLowerCase(Locale.ROOT);
                    filters.put(playerId, filter);
                    player.sendMessage("§aPV filter set to: §f" + filter);
                }
                openVaultMenu(player);
                return;
            }

            if (renameIndex != null) {
                PlayerVaultManager.VaultData data = vaultManager.getOrCreateVault(playerId, renameIndex);
                String trimmed = input.length() > 32 ? input.substring(0, 32) : input;
                data.setName(trimmed);
                vaultManager.save();
                awaitingRename.remove(playerId);
                player.sendMessage("§aPV " + renameIndex + " renamed to §f" + trimmed + "§a.");
                openVaultMenu(player);
                return;
            }
        });
    }

    @EventHandler
    public void onIconPickerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!title.startsWith(ICON_PICKER_PREFIX)) return;

        event.setCancelled(true);

        UUID playerId = player.getUniqueId();
        Integer index = openIconPickers.get(playerId);
        if (index == null) {
            player.closeInventory();
            return;
        }

        if (event.getSlot() == 40) {
            openIconPickers.remove(playerId);
            openVaultMenu(player);
            return;
        }

        Material selected = ICON_PICKER_SLOTS.get(event.getSlot());
        if (selected == null) return;

        PlayerVaultManager.VaultData data = vaultManager.getOrCreateVault(playerId, index);
        data.setIcon(selected);
        vaultManager.save();
        openIconPickers.remove(playerId);
        player.sendMessage("§aPV " + index + " icon set to §f" + selected.name() + "§a.");
        openVaultMenu(player);
    }

    private void openIconPicker(Player player, int index) {
        Inventory picker = Bukkit.createInventory(null, 45, ICON_PICKER_PREFIX + index);

        for (Map.Entry<Integer, Material> entry : ICON_PICKER_SLOTS.entrySet()) {
            ItemStack icon = new ItemStack(entry.getValue());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b" + entry.getValue().name().toLowerCase(Locale.ROOT));
                meta.setLore(List.of("§7Click to set this as your PV icon"));
                icon.setItemMeta(meta);
            }
            picker.setItem(entry.getKey(), icon);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cBack");
            back.setItemMeta(backMeta);
        }
        picker.setItem(40, back);

        openIconPickers.put(player.getUniqueId(), index);
        player.openInventory(picker);
    }

    private Integer parseVaultIndex(String value) {
        try {
            int index = Integer.parseInt(value);
            return index > 0 ? index : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        int maxVaults = rankManager.getMaxVaults(player.getUniqueId());

        if (cmd.equals("pv") || cmd.equals("pvs") || cmd.equals("pvdump")) {
            if (args.length == 1) {
                if (cmd.equals("pv") || cmd.equals("pvs")) {
                    if ("search".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        java.util.List<String> values = new java.util.ArrayList<>();
                        values.add("search");
                        for (int i = 1; i <= maxVaults; i++) {
                            values.add(String.valueOf(i));
                        }
                        return values;
                    }
                }
                List<String> out = new ArrayList<>();
                String prefix = args[0].toLowerCase(Locale.ROOT);
                for (int i = 1; i <= maxVaults; i++) {
                    String value = String.valueOf(i);
                    if (value.startsWith(prefix)) out.add(value);
                }
                return out;
            }
            if ((cmd.equals("pv") || cmd.equals("pvs")) && args.length >= 2 && args[0].equalsIgnoreCase("search")) {
                return List.of("diamond", "book", "ink_sac", "paper", "weapon");
            }
            return List.of();
        }

        if (cmd.equals("pvfilter") || cmd.equals("pvsearch")) {
            if (args.length == 1) {
                return List.of("diamond", "book", "ink_sac", "paper", "weapon");
            }
            return List.of();
        }

        return List.of();
    }

    private String highlight(String text, String filter) {
        if (text == null || filter == null || filter.isBlank()) return text;

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerFilter = filter.toLowerCase(Locale.ROOT);
        int idx = lowerText.indexOf(lowerFilter);
        if (idx < 0) return text;

        int end = idx + filter.length();
        return text.substring(0, idx) + "§a" + text.substring(idx, end) + "§f" + text.substring(end);
    }
}
