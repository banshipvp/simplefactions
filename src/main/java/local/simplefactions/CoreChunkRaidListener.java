package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Example implementation showing how to integrate Core Chunk system with raiding.
 * This listener can be extended to create your SimpleFactionsRaiding plugin.
 * 
 * Usage:
 * In your raiding plugin, register this listener with your custom raid events.
 */
public class CoreChunkRaidListener implements Listener {
    
    private final FactionManager factionManager;
    private final CoreChunkManager coreChunkManager;
    
    public CoreChunkRaidListener(FactionManager factionManager) {
        this.factionManager = factionManager;
        this.coreChunkManager = factionManager.getCoreChunkManager();
    }
    
    /**
     * Example: When a core block is mined, damage the faction's core.
     * You would replace this with your custom raid event.
     */
    @EventHandler
    public void onCoreBlockDestroyed(BlockBreakEvent event) {
        Player breaker = event.getPlayer();
        
        // Check if this is a core chunk (you'd implement this check based on your block type)
        // For now, we'll use a placeholder check - replace with your actual core block detection
        if (!isCoreBlock(event.getBlock())) {
            return;
        }
        
        // Find the faction that owns this chunk
        FactionManager.Faction ownerFaction = factionManager.getFactionByChunk(
            event.getBlock().getWorld().getName(),
            event.getBlock().getChunk().getX(),
            event.getBlock().getChunk().getZ()
        );
        
        if (ownerFaction == null) {
            breaker.sendMessage("§cThis chunk is not claimed!");
            event.setCancelled(true);
            return;
        }
        
        // Check if the faction has a core chunk
        if (!coreChunkManager.hasCoreChunk(ownerFaction.getName())) {
            breaker.sendMessage("§cThis faction has no core to raid!");
            event.setCancelled(true);
            return;
        }
        
        // Damage the core
        double damageAmount = 500.0;  // Configurable
        boolean coreStillActive = coreChunkManager.damageCore(ownerFaction.getName(), damageAmount);
        
        CoreChunk core = coreChunkManager.getCoreChunk(ownerFaction.getName());
        
        // Notify players
        String damageMessage = String.format(
            "§c[RAID] %s destroyed a core block! %s's core: §c%.0f/%.0f§r (%.1f%%)",
            breaker.getName(),
            ownerFaction.getName(),
            core.getCurrentPoints(),
            core.getMaxPoints(),
            core.getHealthPercentage()
        );
        Bukkit.broadcastMessage(damageMessage);
        
        // If core is destroyed
        if (!coreStillActive) {
            Bukkit.broadcastMessage(
                "§4§l[FACTION DESTROYED] §r§c" + ownerFaction.getName() + 
                "'s core has been destroyed by " + breaker.getName() + "!"
            );
            
            // You can add rewards here
            giveRaidRewards(breaker, ownerFaction);
        }
    }
    
    /**
     * Example reward system for successful raids
     */
    private void giveRaidRewards(Player raider, FactionManager.Faction targetFaction) {
        // Example: Give money, items, or faction bonuses
        raider.sendMessage("§a✓ Raid successful! You received rewards.");
        
        // Add to player's balance or custom economy system
        // economyManager.addBalance(raider.getUniqueId(), 5000);
    }
    
    /**
     * Placeholder for detecting core blocks
     * Replace this with your actual core block detection logic
     */
    private boolean isCoreBlock(org.bukkit.block.Block block) {
        // Example: Check if it's a specific block type or has a custom tag
        // return block.getType() == Material.DIAMOND_BLOCK;
        return false; // Replace with actual logic
    }
    
    /**
     * Add PVP points when players are killed
     * Call this from your PVP plugin or player death event
     */
    public void onPlayerKilledInFactionTerritory(Player victim, Player killer) {
        FactionManager.Faction killerFaction = factionManager.getFactionByPlayer(killer);
        FactionManager.Faction victimFaction = factionManager.getFactionByPlayer(victim);
        
        // Add PVP points to killer's faction
        if (killerFaction != null && coreChunkManager.hasCoreChunk(killerFaction.getName())) {
            coreChunkManager.addPvpKill(killerFaction.getName());
            killer.sendMessage("§a+100 PVP points to core!");
        }
        
        // Subtract PVP points from victim's faction
        if (victimFaction != null && coreChunkManager.hasCoreChunk(victimFaction.getName())) {
            coreChunkManager.addPvpPoints(victimFaction.getName(), -50);  // Negative = loss
            victim.getKiller().sendMessage("§c-50 PVP points from your core!");
        }
    }
    
    /**
     * Add spawner points when faction gains control of spawners
     */
    public void onSpawnersConverted(String factionName, int spawnerCount) {
        if (coreChunkManager.hasCoreChunk(factionName)) {
            coreChunkManager.addSpawnerPoints(factionName, spawnerCount);
            Bukkit.broadcastMessage("§a" + factionName + " gained §a+" + 
                (spawnerCount * coreChunkManager.getPointsPerSpawner()) + 
                "§a spawner points!");
        }
    }
    
    /**
     * Remove spawner points when faction loses spawners
     */
    public void onSpawnerLost(String factionName, int spawnerCount) {
        if (coreChunkManager.hasCoreChunk(factionName)) {
            coreChunkManager.removeSpawnerPoints(factionName, spawnerCount);
            Bukkit.broadcastMessage("§c" + factionName + " lost §c-" + 
                (spawnerCount * coreChunkManager.getPointsPerSpawner()) + 
                "§c spawner points!");
        }
    }
    
    /**
     * Add challenge points when faction completes a challenge
     */
    public void onFactionChallengeCompleted(String factionName) {
        if (coreChunkManager.hasCoreChunk(factionName)) {
            coreChunkManager.addChallengeCompletion(factionName);
            Bukkit.broadcastMessage("§a" + factionName + " completed a challenge! " +
                "§a+250 core points!");
        }
    }
    
    /**
     * Add core chunk notes/resources
     */
    public void onCoreChunkResourcesGathered(String factionName, double amount) {
        if (coreChunkManager.hasCoreChunk(factionName)) {
            coreChunkManager.addCoreChunkNotes(factionName, amount);
        }
    }
    
    /**
     * Get core information for UI/info commands
     */
    public String getCoreChunkInfo(String factionName) {
        if (!coreChunkManager.hasCoreChunk(factionName)) {
            return "§cNo core chunk found for " + factionName;
        }
        
        CoreChunk core = coreChunkManager.getCoreChunk(factionName);
        
        return String.format(
            "§6═══════════════════════════════════════\n" +
            "§e%s§6 Core Chunk Status\n" +
            "§6═══════════════════════════════════════\n" +
            "§aHealth: §f%.0f/%.0f (%.1f%%)\n" +
            "§aLocation: §f%s\n" +
            "§aPVP Points: §f%.0f\n" +
            "§aSpawner Points: §f%.0f\n" +
            "§aChallenge Points: §f%.0f\n" +
            "§aCore Notes: §f%.0f\n" +
            "§aTotal Points: §f%.0f\n" +
            "§aTotal Damage: §f%.0f\n" +
            "§6═══════════════════════════════════════",
            factionName,
            core.getCurrentPoints(),
            core.getMaxPoints(),
            core.getHealthPercentage(),
            core.getChunkKey(),
            core.getPvpPoints(),
            core.getSpawnerPoints(),
            core.getChallengePoints(),
            core.getCoreChunkNotes(),
            core.getTotalPoints(),
            core.getTotalDamageTaken()
        );
    }
    
    /**
     * Display core chunk status bar for all active cores
     */
    public void broadcastCoreStatus() {
        for (CoreChunk core : coreChunkManager.getAllCoreChunks()) {
            String healthBar = generateHealthBar(core.getHealthPercentage());
            Bukkit.broadcastMessage(
                "§7[Core] §6" + core.getFactionName() + " " + healthBar + 
                " §f" + String.format("%.0f/%.0f", core.getCurrentPoints(), core.getMaxPoints())
            );
        }
    }
    
    /**
     * Generate a visual health bar
     */
    private String generateHealthBar(double percentage) {
        int bars = (int) (percentage / 10);
        StringBuilder bar = new StringBuilder("§c");
        
        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                if (i < 3) bar.append("§c█");
                else if (i < 6) bar.append("§e█");
                else bar.append("§a█");
            } else {
                bar.append("§7█");
            }
        }
        
        return bar.toString();
    }
}
