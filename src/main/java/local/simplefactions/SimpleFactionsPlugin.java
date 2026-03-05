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
    private PlayerRankManager playerRankManager;
    private HubQueueManager hubQueueManager;
    private SpawnerStackManager spawnerStackManager;
    private BukkitTask autoSaveTask;
    private BukkitTask timeLockTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        factionManager = new FactionManager();

        // SpawnerStackManager must be set before loadData() so spawner persistence
        // is handled by the new stacking system rather than the legacy SpawnerTracker.
        spawnerStackManager = new SpawnerStackManager(this);
        factionManager.setSpawnerStackManager(spawnerStackManager);

        // Load saved data
        factionManager.loadData(getDataFolder());
        spawnerStackManager.loadData(getDataFolder()); // reads spawner-stacks.yml (migrates spawners.yml if needed)
        spawnerStackManager.start();

        // Ensure the Warzone system faction exists
        if (factionManager.getFactionByName("Warzone") == null) {
            factionManager.createSystemFaction("Warzone",
                    "System-managed warzone and safezone regions. No players belong to this faction.");
        }

        warzoneManager = new WarzoneManager(getDataFolder());
        WarzoneCommand warzoneCmd = new WarzoneCommand(this, factionManager, warzoneManager);
        WorldEditSelectionListener worldEditSelectionListener = new WorldEditSelectionListener(this, warzoneCmd);
        ShowPosCommand showPosCommand = new ShowPosCommand(worldEditSelectionListener);
        getCommand("warzone").setExecutor(warzoneCmd);
        getCommand("warzone").setTabCompleter(warzoneCmd);
        getCommand("showpos").setExecutor(showPosCommand);
        getCommand("showpos").setTabCompleter(showPosCommand);
        getServer().getPluginManager().registerEvents(
                new WarzoneListener(warzoneManager, warzoneCmd, factionManager), this);
        getServer().getPluginManager().registerEvents(worldEditSelectionListener, this);
        economyManager = new EconomyManager();
        upgradeGUI = new UpgradeGUI(factionManager, economyManager);
        FactionAccessGui accessGui = new FactionAccessGui(factionManager);

        FCommand fCommand = new FCommand(factionManager, upgradeGUI, accessGui, economyManager);
        getCommand("f").setExecutor(fCommand);
        getCommand("f").setTabCompleter(fCommand);
        getCommand("help").setExecutor(new HelpCommand());
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(factionManager, economyManager), this);
        Bukkit.getPluginManager().registerEvents(new TeleportProtectionListener(factionManager, warzoneManager), this);
        getServer().getPluginManager().registerEvents(new SpawnerStackListener(spawnerStackManager, factionManager), this);
        getServer().getPluginManager().registerEvents(new MobCombatListener(), this);
        getServer().getPluginManager().registerEvents(new ClaimMapListener(), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(factionManager, upgradeGUI, economyManager), this);
        getServer().getPluginManager().registerEvents(new FactionWandListener(this, factionManager), this);
        getServer().getPluginManager().registerEvents(new FactionMapAutoListener(factionManager, fCommand), this);
        getServer().getPluginManager().registerEvents(accessGui, this);

        // Rank & Queue system
        playerRankManager = new PlayerRankManager(this);
        playerRankManager.loadData(getDataFolder());

        hubQueueManager = new HubQueueManager(this, playerRankManager);
        hubQueueManager.start();

        RankCommand rankCmd = new RankCommand(playerRankManager);
        getCommand("rank").setExecutor(rankCmd);
        getCommand("rankinfo").setExecutor(rankCmd);

        QueueCommand queueCmd = new QueueCommand(hubQueueManager, playerRankManager);
        getCommand("queue").setExecutor(queueCmd);

        getServer().getPluginManager().registerEvents(new XpBottleListener(playerRankManager), this);
        getServer().getPluginManager().registerEvents(hubQueueManager, this);
        getServer().getPluginManager().registerEvents(
                new FactionFlyListener(this, factionManager, warzoneManager, playerRankManager), this);

        // Hub
        HubCommand hubCommand = new HubCommand(this);
        getCommand("hub").setExecutor(hubCommand);
        getCommand("sethub").setExecutor(hubCommand);
        getServer().getPluginManager().registerEvents(new HubJoinListener(this, hubCommand), this);
        getServer().getPluginManager().registerEvents(new HubCommandRestrictionListener(this, hubCommand, hubQueueManager), this);

        timeLockTask = new WorldTimeLockTask(this).runTaskTimer(this, 0L, 100L);

        // ✅ Register the mod bridge
        bridge = new TechnoFactionsBridge(this, factionManager);
        bridge.register();
        
        // Auto-save every 5 minutes (6000 ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            getLogger().info("Auto-saving faction data...");
            factionManager.saveData(getDataFolder());
            if (spawnerStackManager != null) spawnerStackManager.saveData(getDataFolder());
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

        if (hubQueueManager != null) {
            hubQueueManager.stop();
        }

        if (spawnerStackManager != null) {
            spawnerStackManager.stop();
            getLogger().info("Saving spawner stack data...");
            spawnerStackManager.saveData(getDataFolder());
        }

        if (playerRankManager != null) {
            getLogger().info("Saving rank data...");
            playerRankManager.saveData(getDataFolder());
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