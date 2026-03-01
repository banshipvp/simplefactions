// ==========================================
// CORE CHUNK SYSTEM - INTEGRATION GUIDE
// ==========================================

/*
The SimpleFactions plugin now includes a Core Chunk System that tracks faction strength
and enables raiding mechanics. Here's how to use it in your SimpleFactionsRaiding plugin.

KEY CONCEPTS:
- Core Chunk: Established when a faction claims their first chunk
- Core Points: Accumulated from PVP activity, spawners, challenges, and resources
- Raiding: Other plugins can damage the core and check its status
- Destruction: Core is ONLY removed when a faction does /f unclaimall
*/

// ==========================================
// ACCESSING THE CORE CHUNK MANAGER
// ==========================================

// From your raiding plugin:
SimpleFactionsPlugin simpleFactions = (SimpleFactionsPlugin) Bukkit.getPluginManager().getPlugin("SimpleFactions");
CoreChunkManager coreManager = simpleFactions.getCoreChunkManager();

// Or through the FactionManager:
FactionManager factionManager = simpleFactions.getFactionManager();
CoreChunkManager coreManager = factionManager.getCoreChunkManager();


// ==========================================
// CHECKING CORE CHUNK STATUS
// ==========================================

// Check if a faction has a core chunk
if (coreManager.hasCoreChunk("FactionName")) {
    CoreChunk core = coreManager.getCoreChunk("FactionName");
    
    // Get current health/points
    double currentPoints = core.getCurrentPoints();
    double maxPoints = core.getMaxPoints();
    double healthPercent = core.getHealthPercentage();
    
    System.out.println("Core HP: " + currentPoints + "/" + maxPoints + " (" + healthPercent + "%)");
}


// ==========================================
// DEALING DAMAGE DURING RAIDS
// ==========================================

// In your raid event handler, when core is hit:

// Method 1: Deal direct damage
double damageAmount = 150.0;
boolean coreStillExists = coreManager.damageCore("FactionName", damageAmount);

if (!coreStillExists) {
    // Core is destroyed! Notify players
    Bukkit.broadcastMessage("The core of FactionName has been destroyed!");
}

// Method 2: Deal damage based on number of hits
int hitCount = 3;
boolean coreStillExists = coreManager.applyRaidDamage("FactionName", hitCount);


// ==========================================
// GAINING CORE POINTS
// ==========================================

// PVP Activity
for (Player victim : deadPlayers) {
    String factionName = factionManager.getFactionByPlayer(victim).getName();
    coreManager.addPvpKill(factionName);  // Adds default PVP points
    // or
    coreManager.addPvpPoints(factionName, 250.0);  // Custom amount
}

// Spawner Control (add when spawners come under faction control)
coreManager.addSpawnerPoints("FactionName", 5);  // 5 spawners

// Spawner Destruction (remove when spawners are mined or exploded)
coreManager.removeSpawnerPoints("FactionName", 2);  // 2 destroyed

// Challenge Completion
coreManager.addChallengeCompletion("FactionName");  // Adds default challenge points
// or
coreManager.addChallengePoints("FactionName", 500.0);  // Custom amount

// Core Chunk Notes/Resources
coreManager.addCoreChunkNotes("FactionName", 1000.0);


// ==========================================
// REPAIR OPERATIONS
// ==========================================

// Partial repair
coreManager.repairCore("FactionName", 500.0);

// Full repair (back to max health)
coreManager.fullRepairCore("FactionName");


// ==========================================
// CONFIGURATION (Set in plugin startup)
// ==========================================

CoreChunkManager coreManager = factionManager.getCoreChunkManager();

// Base maximum points for a core
coreManager.setBaseMaxPoints(10000.0);

// Points earned from activities
coreManager.setPointsPerSpawner(500.0);
coreManager.setPointsPerPvpKill(100.0);
coreManager.setPointsPerChallenge(250.0);

// Damage dealt during raids
coreManager.setDamagePerRaidHit(50.0);


// ==========================================
// EXAMPLE: RAID EVENT LISTENER
// ==========================================

public class RaidEventListener implements Listener {
    
    private SimpleFactionsPlugin simpleFactions;
    
    public RaidEventListener(SimpleFactionsPlugin simpleFactions) {
        this.simpleFactions = simpleFactions;
    }
    
    @EventHandler
    public void onCoreHit(CoreHitEvent event) {  // Your custom event
        FactionManager factionManager = simpleFactions.getFactionManager();
        CoreChunkManager coreManager = factionManager.getCoreChunkManager();
        
        String targetFactionName = event.getTargetFaction().getName();
        
        // Check if faction has a core
        if (!coreManager.hasCoreChunk(targetFactionName)) {
            event.getAttacker().sendMessage("§cThis faction has no core to raid!");
            return;
        }
        
        // Get current core status
        CoreChunk core = coreManager.getCoreChunk(targetFactionName);
        double damageDealt = event.getDamageAmount();
        
        // Deal damage to core
        boolean coreStillActive = coreManager.damageCore(targetFactionName, damageDealt);
        
        // Broadcast damage dealt
        Bukkit.broadcastMessage(
            event.getAttacker().getName() + " damaged " + targetFactionName + "'s core! " +
            "Core now at: " + core.getCurrentPoints() + "/" + core.getMaxPoints() + " HP"
        );
        
        // Check if core was destroyed
        if (!coreStillActive) {
            Bukkit.broadcastMessage(
                "§c§l[RAID] §r§c" + targetFactionName + "'s core has been destroyed by " + 
                event.getAttacker().getName() + "!"
            );
            event.setCoreLoot(5000);  // Custom loot from destroyed core
        }
    }
}


// ==========================================
// EXAMPLE: SPAWNER EVENT LISTENER
// ==========================================

public class SpawnerEventListener implements Listener {
    
    private SimpleFactionsPlugin simpleFactions;
    
    public SpawnerEventListener(SimpleFactionsPlugin simpleFactions) {
        this.simpleFactions = simpleFactions;
    }
    
    @EventHandler
    public void onSpawnerDestroyed(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof CreatureSpawner)) return;
        
        CreatureSpawner spawner = (CreatureSpawner) event.getEntity();
        
        FactionManager factionManager = simpleFactions.getFactionManager();
        CoreChunkManager coreManager = factionManager.getCoreChunkManager();
        
        String chunkKey = spawner.getLocation().getChunk().getX() + ":" + 
                         spawner.getLocation().getChunk().getZ();
        
        // Find faction that owns this chunk
        FactionManager.Faction owner = factionManager.getFactionByChunk(
            spawner.getWorld().getName(),
            spawner.getLocation().getChunk().getX(),
            spawner.getLocation().getChunk().getZ()
        );
        
        if (owner != null) {
            // Remove spawner points from core
            coreManager.removeSpawnerPoints(owner.getName(), 1);
            
            Bukkit.broadcastMessage(
                "§c[Warning] Spawner destroyed in " + owner.getName() + " territory! " +
                "Core strength decreased."
            );
        }
    }
    
    @EventHandler
    public void onSpawnerPlaced(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        
        FactionManager factionManager = simpleFactions.getFactionManager();
        CoreChunkManager coreManager = factionManager.getCoreChunkManager();
        FactionManager.Faction faction = factionManager.getFactionByPlayer(event.getPlayer());
        
        if (faction != null && coreManager.hasCoreChunk(faction.getName())) {
            coreManager.addSpawnerPoints(faction.getName(), 1);
            event.getPlayer().sendMessage("§a+Spawner points to your core!");
        }
    }
}


// ==========================================
// GETTING ALL CORE CHUNK INFO
// ==========================================

// Get all active cores (for admin commands or leaderboards)
Collection<CoreChunk> allCores = coreManager.getAllCoreChunks();

for (CoreChunk core : allCores) {
    System.out.println(core.toString());
    // Output: CoreChunk[world:100:200] Health: 7500/10000 (75%) | 
    //         PVP: 2000, Spawner: 3000, Notes: 1500, Challenges: 1000
}

// Get specific core info
CoreChunk core = coreManager.getCoreChunk("FactionName");
if (core != null) {
    System.out.println("Chunk Key: " + core.getChunkKey());
    System.out.println("Current Health: " + core.getCurrentPoints());
    System.out.println("Health %: " + core.getHealthPercentage() + "%");
    System.out.println("PVP Points: " + core.getPvpPoints());
    System.out.println("Spawner Points: " + core.getSpawnerPoints());
    System.out.println("Challenge Points: " + core.getChallengePoints());
    System.out.println("Notes: " + core.getCoreChunkNotes());
    System.out.println("Total Points: " + core.getTotalPoints());
    System.out.println("Total Damage Taken: " + core.getTotalDamageTaken());
}
