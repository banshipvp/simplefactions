package local.simplefactions;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class UpgradeListener implements Listener {

    private final FactionManager manager;
    private final UpgradeGUI upgradeGUI;
    private final EconomyManager economy;

    public UpgradeListener(FactionManager manager, UpgradeGUI upgradeGUI, EconomyManager economy) {
        this.manager = manager;
        this.upgradeGUI = upgradeGUI;
        this.economy = economy;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Faction Upgrades")) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            return;
        }

        String upgradeType = null;
        
        switch (slot) {
            case 10 -> upgradeType = "maxmembers";
            case 12 -> upgradeType = "spawnermult";
            case 14 -> upgradeType = "maxwarps";
            case 16 -> upgradeType = "chestslots";
        }

        if (upgradeType == null) {
            return;
        }

        int currentLevel = faction.getUpgradeLevel(upgradeType);
        if (currentLevel >= 5) {
            player.sendMessage("§cThis upgrade is already maxed out!");
            return;
        }

        double cost = getUpgradeCost(upgradeType, currentLevel + 1);
        if (!economy.has(player, cost)) {
            player.sendMessage("§cInsufficient funds! Need §f$" + formatNumber((long) cost) + "§c.");
            return;
        }

        if (!economy.withdrawPlayer(player, cost)) {
            player.sendMessage("§cFailed to process payment!");
            return;
        }

        faction.setUpgradeLevel(upgradeType, currentLevel + 1);
        
        player.sendMessage("§a✓ Upgrade purchased!");
        player.sendMessage("§f" + upgradeType + "§a is now level §f" + (currentLevel + 1) + "§a.");
        
        // Refresh the GUI
        upgradeGUI.openUpgradeGUI(player);
    }

    private double getUpgradeCost(String upgradeType, int nextLevel) {
        return switch (upgradeType.toLowerCase()) {
            case "maxmembers" -> 100_000 * nextLevel;
            case "spawnermult" -> 150_000 * nextLevel;
            case "maxwarps" -> 50_000 * nextLevel;
            case "chestslots" -> 75_000 * nextLevel;
            default -> 0;
        };
    }

    private String formatNumber(long num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1_000_000) return (num / 1000) + "K";
        return (num / 1_000_000) + "M";
    }
}
