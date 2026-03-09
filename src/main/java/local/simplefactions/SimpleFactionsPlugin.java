package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SimpleFactionsPlugin extends JavaPlugin {

    private static SimpleFactionsPlugin instance;

    public static SimpleFactionsPlugin getInstance() { return instance; }


    private FactionManager factionManager;
    private WarzoneManager warzoneManager;
    private TechnoFactionsBridge bridge;
    private UpgradeGUI upgradeGUI;
    private EconomyManager economyManager;
    private PlayerRankManager playerRankManager;
    private HubQueueManager hubQueueManager;
    private SpawnerStackManager spawnerStackManager;
    private MilestoneManager milestoneManager;
    private ChallengeManager challengeManager;
    private ModerationManager moderationManager;
    private PlayerHomeManager playerHomeManager;
    private PlayerVaultManager playerVaultManager;
    private GlobalWarpManager globalWarpManager;
    private EnvoyManager envoyManager;
    private RoamCommand roamCommand;
    private TeleportTimerManager teleportTimerManager;
    private BukkitTask autoSaveTask;
    private BukkitTask timeLockTask;

    @Override
    public void onEnable() {
        instance = this;
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
        FactionAccessMapGui accessMapGui = new FactionAccessMapGui(factionManager, accessGui);

        FCommand fCommand = new FCommand(factionManager, upgradeGUI, accessGui, economyManager);
        fCommand.setWarzoneManager(warzoneManager);
        fCommand.setAccessMapGui(accessMapGui);
        getCommand("f").setExecutor(fCommand);
        getCommand("f").setTabCompleter(fCommand);
        getCommand("help").setExecutor(new HelpCommand());
        getCommand("helpadmin").setExecutor(new HelpAdminCommand());

        // Milestones / /fund
        milestoneManager = new MilestoneManager(getDataFolder());
        FundCommand fundCmd = new FundCommand(milestoneManager, economyManager);
        getCommand("fund").setExecutor(fundCmd);
        getCommand("fund").setTabCompleter(fundCmd);

        // Challenges (automatic 24-h rotation)
        challengeManager = new ChallengeManager(economyManager, this);
        challengeManager.start(); // loads persisted data + starts scheduler
        ChallengeGUI challengeGUI = new ChallengeGUI(challengeManager, this);
        ChallengeCommand challengeCmd = new ChallengeCommand(challengeManager, challengeGUI);
        getCommand("challenges").setExecutor(challengeCmd);
        getCommand("challenges").setTabCompleter(challengeCmd);
        getCommand("challenge").setExecutor(challengeCmd);
        getCommand("challenge").setTabCompleter(challengeCmd);
        getCommand("claim").setExecutor(new ClaimCommand(challengeManager));
        Bukkit.getPluginManager().registerEvents(new ChallengeListener(challengeManager), this);
        Bukkit.getPluginManager().registerEvents(challengeGUI, this);

        // Rank manager must be initialized before listeners that depend on it.
        playerRankManager = new PlayerRankManager(this);
        playerRankManager.loadData(getDataFolder());

        Bukkit.getPluginManager().registerEvents(new ProtectionListener(factionManager, economyManager, playerRankManager, warzoneManager), this);
        Bukkit.getPluginManager().registerEvents(new TeleportProtectionListener(factionManager, warzoneManager), this);
        getServer().getPluginManager().registerEvents(new SpawnerStackListener(spawnerStackManager, factionManager), this);
        getServer().getPluginManager().registerEvents(new MobCombatListener(), this);
        getServer().getPluginManager().registerEvents(new ClaimMapListener(warzoneManager, economyManager), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(factionManager, upgradeGUI, economyManager), this);
        getServer().getPluginManager().registerEvents(new FactionWandListener(this, factionManager), this);
        getServer().getPluginManager().registerEvents(new FactionMapAutoListener(factionManager, fCommand), this);
        getServer().getPluginManager().registerEvents(accessGui, this);
        getServer().getPluginManager().registerEvents(accessMapGui, this);
        getServer().getPluginManager().registerEvents(new MobDropListener(spawnerStackManager), this);
        getServer().getPluginManager().registerEvents(new EnderPearlCooldownListener(), this);

        // Rank & Queue system
        moderationManager = new ModerationManager();
        playerHomeManager = new PlayerHomeManager(this);
        playerHomeManager.load();
        playerVaultManager = new PlayerVaultManager(this);
        playerVaultManager.load();
        globalWarpManager = new GlobalWarpManager(this);
        globalWarpManager.load();

        StaffToolsCommand staffTools = new StaffToolsCommand(playerRankManager, moderationManager);
        getCommand("staffhelp").setExecutor(staffTools);
        getCommand("staffhelp").setTabCompleter(staffTools);
        getCommand("rankset").setExecutor(staffTools);
        getCommand("rankset").setTabCompleter(staffTools);
        getCommand("v").setExecutor(staffTools);
        getCommand("v").setTabCompleter(staffTools);
        getCommand("vanish").setExecutor(staffTools);
        getCommand("vanish").setTabCompleter(staffTools);
        getCommand("tempmute").setExecutor(staffTools);
        getCommand("tempmute").setTabCompleter(staffTools);
        getCommand("unmute").setExecutor(staffTools);
        getCommand("unmute").setTabCompleter(staffTools);
        getCommand("mute").setExecutor(staffTools);
        getCommand("mute").setTabCompleter(staffTools);
        getCommand("tempban").setExecutor(staffTools);
        getCommand("tempban").setTabCompleter(staffTools);
        getCommand("ban").setExecutor(staffTools);
        getCommand("ban").setTabCompleter(staffTools);
        getCommand("unban").setExecutor(staffTools);
        getCommand("unban").setTabCompleter(staffTools);
        getCommand("warn").setExecutor(staffTools);
        getCommand("warn").setTabCompleter(staffTools);
        getCommand("tpto").setExecutor(staffTools);
        getCommand("tpto").setTabCompleter(staffTools);
        getCommand("freeze").setExecutor(staffTools);
        getCommand("freeze").setTabCompleter(staffTools);
        getCommand("unfreeze").setExecutor(staffTools);
        getCommand("unfreeze").setTabCompleter(staffTools);
        getServer().getPluginManager().registerEvents(staffTools, this);

        getServer().getPluginManager().registerEvents(
            new ChatListener(factionManager, playerRankManager, moderationManager, economyManager), this);

        hubQueueManager = new HubQueueManager(this, playerRankManager);
        hubQueueManager.start();

        RankCommand rankCmd = new RankCommand(playerRankManager);
        getCommand("rank").setExecutor(rankCmd);
        getCommand("rankinfo").setExecutor(rankCmd);

        // Teleport timer — created after playerRankManager, factionManager, and warzoneManager are ready
        teleportTimerManager = new TeleportTimerManager(this, factionManager, warzoneManager, playerRankManager);
        getServer().getPluginManager().registerEvents(teleportTimerManager, this);
        fCommand.setTeleportTimerManager(teleportTimerManager);

        QueueCommand queueCmd = new QueueCommand(hubQueueManager, playerRankManager);
        getCommand("queue").setExecutor(queueCmd);

        HomeCommand homeCommand = new HomeCommand(playerHomeManager, teleportTimerManager);
        SetHomeCommand setHomeCommand = new SetHomeCommand(playerHomeManager, playerRankManager);
        PlayerVaultCommand vaultCommand = new PlayerVaultCommand(this, playerVaultManager, playerRankManager);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        getCommand("homes").setExecutor(homeCommand);
        getCommand("homes").setTabCompleter(homeCommand);
        getCommand("sethome").setExecutor(setHomeCommand);
        WarpCommand warpCommand = new WarpCommand(globalWarpManager);
        getCommand("warp").setExecutor(warpCommand);
        getCommand("warp").setTabCompleter(warpCommand);
        getCommand("setwarp").setExecutor(warpCommand);
        getCommand("setwarp").setTabCompleter(warpCommand);
        getCommand("delwarp").setExecutor(warpCommand);
        getCommand("delwarp").setTabCompleter(warpCommand);


        getCommand("pv").setExecutor(vaultCommand);
        getCommand("pv").setTabCompleter(vaultCommand);
        getCommand("pvfilter").setExecutor(vaultCommand);
        getCommand("pvfilter").setTabCompleter(vaultCommand);
        getCommand("pvsearch").setExecutor(vaultCommand);
        getCommand("pvsearch").setTabCompleter(vaultCommand);
        getCommand("pvdump").setExecutor(vaultCommand);
        getCommand("pvdump").setTabCompleter(vaultCommand);

        getServer().getPluginManager().registerEvents(new XpBottleListener(playerRankManager), this);
        getServer().getPluginManager().registerEvents(hubQueueManager, this);
        getServer().getPluginManager().registerEvents(
                new FactionFlyListener(this, factionManager, warzoneManager, playerRankManager), this);
        getServer().getPluginManager().registerEvents(homeCommand, this);
        getServer().getPluginManager().registerEvents(vaultCommand, this);
        getServer().getPluginManager().registerEvents(warpCommand, this);

        // Hub
        HubCommand hubCommand = new HubCommand(this);
        getCommand("hub").setExecutor(hubCommand);
        getCommand("sethub").setExecutor(hubCommand);
        getServer().getPluginManager().registerEvents(new HubJoinListener(this, hubCommand), this);
        getServer().getPluginManager().registerEvents(new HubCommandRestrictionListener(this, hubCommand, hubQueueManager, playerRankManager), this);

        // Roam command
        roamCommand = new RoamCommand(this);
        getCommand("roam").setExecutor(roamCommand);
        getCommand("roam").setTabCompleter(roamCommand);
        getServer().getPluginManager().registerEvents(roamCommand, this);

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

        if (challengeManager != null) {
            challengeManager.saveData();
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
            Bukkit.getOnlinePlayers().forEach(playerRankManager::clearPlayerRuntimeState);
        }

        if (playerHomeManager != null) {
            playerHomeManager.save();
        }

        if (playerVaultManager != null) {
            playerVaultManager.save();
        }

        if (globalWarpManager != null) {
            globalWarpManager.save();
        }

        if (envoyManager != null) {
            envoyManager.stopAutoSpawnScheduler();
            envoyManager.save();
            envoyManager.clearActiveEnvoys();
        }

        if (roamCommand != null) {
            roamCommand.cleanup();
        }

        getLogger().info("SimpleFactions disabled.");
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public HubQueueManager getHubQueueManager() {
        return hubQueueManager;
    }

    public GlobalWarpManager getGlobalWarpManager() {
        return globalWarpManager;
    }

    public WarzoneManager getWarzoneManager() {
        return warzoneManager;
    }
    
    public CoreChunkManager getCoreChunkManager() {
        return factionManager.getCoreChunkManager();
    }
}