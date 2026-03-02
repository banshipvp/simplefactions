package local.simplefactions;

import local.simpleshop.ShopGUI;
import local.simpleshop.SimpleShopPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class FactionWandListener implements Listener {

    private final JavaPlugin plugin;
    private final FactionManager manager;

    public FactionWandListener(JavaPlugin plugin, FactionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (WandItems.isTntWand(inHand)) {
            handleTntWand(event, player, container);
            return;
        }

        if (WandItems.isSellWand(inHand)) {
            handleSellWand(event, player, container);
        }
    }

    private void handleTntWand(PlayerInteractEvent event, Player player, Container container) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou must be in a faction to use TNT Wand.");
            return;
        }

        Inventory inv = container.getInventory();
        int available = countTnt(inv.getContents());
        if (available <= 0) {
            player.sendMessage("§cThis container has no TNT.");
            return;
        }

        int removed = removeTnt(inv, available);
        if (removed <= 0) {
            player.sendMessage("§cCould not transfer TNT from this container.");
            return;
        }

        faction.addTnt(removed);
        container.update();
        event.setCancelled(true);

        int total = faction.getTntBank();
        player.sendMessage("§aTNT Wand transferred §f" + removed + " TNT §ato faction bank.");
        player.sendMessage("§7Faction TNT Bank: §f" + total + " TNT §8(" + (total / 64) + " stacks, " + (total % 64) + " individual)");
    }

    private void handleSellWand(PlayerInteractEvent event, Player player, Container container) {
        event.setCancelled(true);

        // Get Vault economy
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            player.sendMessage("§cEconomy service unavailable.");
            return;
        }
        Economy economy = rsp.getProvider();

        // Get SimpleShop sell prices
        SimpleShopPlugin shopPlugin = (SimpleShopPlugin) Bukkit.getPluginManager().getPlugin("SimpleShop");
        if (shopPlugin == null) {
            player.sendMessage("§cSimpleShop plugin not found.");
            return;
        }
        ShopGUI shopGUI = shopPlugin.getShopGUI();

        // Sell directly from chest — no inventory transfer
        Inventory chestInv = container.getInventory();
        ItemStack[] contents = chestInv.getContents();

        double total = 0;
        int itemsSold = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;

            double price = shopGUI.getSellPrice(stack.getType());
            if (price < 0) continue; // not in shop

            total += price * stack.getAmount();
            itemsSold += stack.getAmount();
            chestInv.setItem(i, null);
        }

        if (itemsSold == 0) {
            player.sendMessage("§cThis container has no sellable items.");
            return;
        }

        container.update();
        economy.depositPlayer(player, total);

        player.sendMessage("§a✓ Sell Wand sold §e" + itemsSold + "§a item(s) for §e$" + String.format("%.2f", total) + "§a.");
        player.sendMessage("§7New balance: §a$" + String.format("%.2f", economy.getBalance(player)));
    }

    private int countTnt(ItemStack[] contents) {
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.TNT) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int removeTnt(Inventory inventory, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != Material.TNT) continue;

            int take = Math.min(stack.getAmount(), remaining);
            int left = stack.getAmount() - take;

            if (left <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(left);
                inventory.setItem(slot, stack);
            }

            remaining -= take;
        }

        return amount - remaining;
    }
}
