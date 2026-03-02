package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SimpleFactionsPlugin extends JavaPlugin {

    private FactionManager factionManager;
    private WarzoneManager warzoneManager;
    private TechnoFactionsBridge bridge;
    private UpgradeGUI upgradeGUI;
    private EconomyManager economyManager;
    private BukkitTask autoSaveTask;
    private BukkitTask timeLockTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        factionManager = new FactionManager();
        
        // Load saved data
        factionManager.loadData(getDataFolder());

        // Ensure the Warzone system faction exists
        if (factionManager.getFactionByName("Warzone") == null) {
            factionManager.createSystemFaction("Warzone",
                    "System-managed warzone and safezone regions. No players belong to this faction.");
        }

        warzoneManager = new WarzoneManager(getDataFolder());
        WarzoneCommand warzoneCmd = new WarzoneCommand(factionManager, warzoneManager);
        getCommand("warzone").setExecutor(warzoneCmd);
        getCommand("warzone").setTabCompleter(warzoneCmd);
        getServer().getPluginManager().registerEvents(
                new WarzoneListener(warzoneManager, warzoneCmd, factionManager), this);
        economyManager = new EconomyManager();
        upgradeGUI = new UpgradeGUI(factionManager, economyManager);
        FactionAccessGui accessGui = new FactionAccessGui(factionManager);

        FCommand fCommand = new FCommand(factionManager, upgradeGUI, accessGui);
        getCommand("f").setExecutor(fCommand);
        getCommand("f").setTabCompleter(fCommand);
        getCommand("help").setExecutor(new HelpCommand());
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(factionManager), this);
        getServer().getPluginManager().registerEvents(new ClaimMapListener(), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(factionManager, upgradeGUI, economyManager), this);
        getServer().getPluginManager().registerEvents(new FactionWandListener(this, factionManager), this);
        getServer().getPluginManager().registerEvents(new FactionMapAutoListener(factionManager, fCommand), this);
        getServer().getPluginManager().registerEvents(accessGui, this);

        // Hub
        HubCommand hubCommand = new HubCommand(this);
        getCommand("hub").setExecutor(hubCommand);
        getCommand("sethub").setExecutor(hubCommand);
        getServer().getPluginManager().registerEvents(new HubJoinListener(this, hubCommand), this);
        getServer().getPluginManager().registerEvents(new HubCommandRestrictionListener(this, hubCommand), this);

        timeLockTask = new WorldTimeLockTask(this).runTaskTimer(this, 0L, 100L);

        // ✅ Register the mod bridge
        bridge = new TechnoFactionsBridge(this, factionManager);
        bridge.register();
        
        // Auto-save every 5 minutes (6000 ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            getLogger().info("Auto-saving faction data...");
            factionManager.saveData(getDataFolder());
        }, 6000L, 6000L);

        getLogger().info("SimpleFactions enabled.");
        getLogger().info("Core Chunk system initialized.");
    }

    @Override
    public void onDisable() {
        // Cancel auto-save task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        if (timeLockTask != null) {
            timeLockTask.cancel();
        }
        
        // Save all data
        if (factionManager != null) {
            getLogger().info("Saving faction data...");
            factionManager.saveData(getDataFolder());
        }
        
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