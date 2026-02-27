package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleFactionsPlugin extends JavaPlugin {

    private FactionManager factionManager;
    private TechnoFactionsBridge bridge;

    @Override
    public void onEnable() {
        factionManager = new FactionManager();

        getCommand("f").setExecutor(new FCommand(factionManager));
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(factionManager), this);
        getServer().getPluginManager().registerEvents(new ClaimMapListener(), this);

        // ✅ Register the mod bridge
        bridge = new TechnoFactionsBridge(this, factionManager);
        bridge.register();

        getLogger().info("SimpleFactions enabled.");
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
}