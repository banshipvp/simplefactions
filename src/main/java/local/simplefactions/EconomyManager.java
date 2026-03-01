package local.simplefactions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private Economy economy;
    private boolean enabled = false;

    public EconomyManager() {
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Bukkit.getLogger().info("SimpleFactions: Vault not found. Economy disabled.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            Bukkit.getLogger().info("SimpleFactions: No economy plugin found. Economy disabled.");
            return;
        }

        economy = rsp.getProvider();
        enabled = economy != null;
        if (enabled) {
            Bukkit.getLogger().info("SimpleFactions: Economy enabled via " + economy.getName());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getBalance(Player player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    public boolean withdrawPlayer(Player player, double amount) {
        if (!enabled) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean depositPlayer(Player player, double amount) {
        if (!enabled) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
