package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleFactionsPlugin extends JavaPlugin {

    private FactionManager factionManager;
    private TechnoFactionsBridge bridge;
    private UpgradeGUI upgradeGUI;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        factionManager = new FactionManager();
        economyManager = new EconomyManager();
        upgradeGUI = new UpgradeGUI(factionManager, economyManager);

        FCommand fCommand = new FCommand(factionManager, upgradeGUI);
        getCommand("f").setExecutor(fCommand);
        getCommand("help").setExecutor(new HelpCommand());
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(factionManager), this);
        getServer().getPluginManager().registerEvents(new ClaimMapListener(), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(factionManager, upgradeGUI, economyManager), this);
        getServer().getPluginManager().registerEvents(new FactionWandListener(this, factionManager), this);
        getServer().getPluginManager().registerEvents(new FactionMapAutoListener(factionManager, fCommand), this);

        // ✅ Register the mod bridge
        bridge = new TechnoFactionsBridge(this, factionManager);
        bridge.register();

        getLogger().info("SimpleFactions enabled.");
        getLogger().info("Core Chunk system initialized.");
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.unregister();
        }
        getLogger().info("SimpleFactions disabled.");
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }
    
    public CoreChunkManager getCoreChunkManager() {
        return factionManager.getCoreChunkManager();
    }
}