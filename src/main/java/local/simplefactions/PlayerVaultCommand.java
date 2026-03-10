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

    private static final Map<Integer, Material> ICON_PICKER_SLOTS;
    private static final Map<Material, String> ICON_PICKER_NAMES;
    static {
        Map<Integer, Material> slots = new LinkedHashMap<>();
        slots.put(9,  Material.DIAMOND);
        slots.put(10, Material.EMERALD);
        slots.put(11, Material.GOLD_INGOT);
        slots.put(12, Material.IRON_INGOT);
        slots.put(13, Material.REDSTONE);
        slots.put(14, Material.TNT);
        slots.put(15, Material.SPAWNER);
        slots.put(16, Material.STONE);
        slots.put(17, Material.GRASS_BLOCK);
        slots.put(18, Material.OAK_PLANKS);
        slots.put(19, Material.GLOWSTONE_DUST);
        slots.put(20, Material.SUGAR);
        slots.put(21, Material.PAPER);
        slots.put(22, Material.INK_SAC);
        slots.put(23, Material.DIAMOND_HELMET);
        slots.put(24, Material.DIAMOND_CHESTPLATE);
        slots.put(25, Material.DIAMOND_LEGGINGS);
        slots.put(26, Material.DIAMOND_BOOTS);
        slots.put(27, Material.DIAMOND_SWORD);
        slots.put(28, Material.DIAMOND_AXE);
        slots.put(29, Material.DIAMOND_PICKAXE);
        slots.put(30, Material.EXPERIENCE_BOTTLE);
        slots.put(31, Material.BOOK);
        slots.put(32, Material.BEACON);
        slots.put(33, Material.ENDER_EYE);
        slots.put(34, Material.ENDER_PEARL);
        ICON_PICKER_SLOTS = java.util.Collections.unmodifiableMap(slots);

        Map<Material, String> names = new HashMap<>();
        names.put(Material.DIAMOND,           "Diamond");
        names.put(Material.EMERALD,           "Emerald");
        names.put(Material.GOLD_INGOT,        "Gold");
        names.put(Material.IRON_INGOT,        "Iron");
        names.put(Material.REDSTONE,          "Redstone");
        names.put(Material.TNT,               "TNT");
        names.put(Material.SPAWNER,           "Spawner");
        names.put(Material.STONE,             "Stone");
        names.put(Material.GRASS_BLOCK,       "Grass");
        names.put(Material.OAK_PLANKS,        "Wood Planks");
        names.put(Material.GLOWSTONE_DUST,    "Glowstone Dust");
        names.put(Material.SUGAR,             "Sugar");
        names.put(Material.PAPER,             "Paper");
        names.put(Material.INK_SAC,           "Ink Sac");
        names.put(Material.DIAMOND_HELMET,    "Diamond Helmet");
        names.put(Material.DIAMOND_CHESTPLATE,"Diamond Chestplate");
        names.put(Material.DIAMOND_LEGGINGS,  "Diamond Leggings");
        names.put(Material.DIAMOND_BOOTS,     "Diamond Boots");
        names.put(Material.DIAMOND_SWORD,     "Diamond Sword");
        names.put(Material.DIAMOND_AXE,       "Diamond Axe");
        names.put(Material.DIAMOND_PICKAXE,   "Diamond Pickaxe");
        names.put(Material.EXPERIENCE_BOTTLE, "XP Bottle");
        names.put(Material.BOOK,              "Book");
        names.put(Material.BEACON,            "Beacon");
        names.put(Material.ENDER_EYE,         "Eye of Ender");
        names.put(Material.ENDER_PEARL,       "Ender Pearl");
        ICON_PICKER_NAMES = java.util.Collections.unmodifiableMap(names);
    }

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

        if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.CREATIVE) {
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

        if (event.getSlot() == 49) {
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
        player.sendMessage("§aPV " + index + " icon set to §f" + ICON_PICKER_NAMES.getOrDefault(selected, selected.name().replace('_', ' ')) + "§a.");
        openVaultMenu(player);
    }

    private void openIconPicker(Player player, int index) {
        Inventory picker = Bukkit.createInventory(null, 54, ICON_PICKER_PREFIX + index);

        for (Map.Entry<Integer, Material> entry : ICON_PICKER_SLOTS.entrySet()) {
            ItemStack icon = new ItemStack(entry.getValue());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String name = ICON_PICKER_NAMES.getOrDefault(entry.getValue(),
                        entry.getValue().name().replace('_', ' '));
                meta.setDisplayName("§b" + name);
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
        picker.setItem(49, back);

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
