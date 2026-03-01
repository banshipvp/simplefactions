package local.simplefactions;

import java.util.*;

/**
 * Manages core chunks for factions.
 * A core chunk is established on the first claim and contains accumulated points.
 * The core chunk can only be destroyed when a faction does unclaimAll.
 */
public class CoreChunkManager {
    
    // Map of faction name (lowercase) -> CoreChunk
    private final Map<String, CoreChunk> coreChunksByFaction = new HashMap<>();
    
    // Map of chunk key ("world:x:z") -> faction name (lowercase) for quick lookup
    private final Map<String, String> chunkToFaction = new HashMap<>();
    
    // Configuration
    private double baseMaxPoints = 10000.0;          // Base maximum points for a core chunk
    private double pointsPerSpawner = 500.0;         // Points gained per spawner
    private double pointsPerPvpKill = 100.0;         // Points from PVP activity
    private double pointsPerChallenge = 250.0;       // Points from challenges
    private double damagePerRaidHit = 50.0;          // Damage dealt per raid hit
    
    // ===== CORE CHUNK CREATION ====
    
    /**
     * Create a core chunk for a faction on their first claim
     */
    public CoreChunk createCoreChunk(String factionName, String chunkKey) {
        String key = factionName.toLowerCase();
        
        if (coreChunksByFaction.containsKey(key)) {
            return null; // Core already exists
        }
        
        CoreChunk core = new CoreChunk(chunkKey, factionName, baseMaxPoints);
        coreChunksByFaction.put(key, core);
        chunkToFaction.put(chunkKey, key);
        
        return core;
    }
    
    /**
     * Get the core chunk for a faction
     */
    public CoreChunk getCoreChunk(String factionName) {
        return coreChunksByFaction.get(factionName.toLowerCase());
    }
    
    /**
     * Check if a faction has a core chunk
     */
    public boolean hasCoreChunk(String factionName) {
        return coreChunksByFaction.containsKey(factionName.toLowerCase());
    }
    
    /**
     * Get all core chunks (for admin/debugging)
     */
    public Collection<CoreChunk> getAllCoreChunks() {
        return new HashSet<>(coreChunksByFaction.values());
    }
    
    // ===== DESTRUCTION ======
    
    /**
     * Destroy a core chunk (called when faction does unclaimAll)
     */
    public void destroyCoreChunk(String factionName) {
        String key = factionName.toLowerCase();
        CoreChunk core = coreChunksByFaction.remove(key);
        
        if (core != null) {
            chunkToFaction.remove(core.getChunkKey());
        }
    }
    
    /**
     * Reset all core chunks (used for server resets)
     */
    public void resetAllCoreChunks() {
        for (CoreChunk core : coreChunksByFaction.values()) {
            core.reset();
        }
    }
    
    // ===== POINT MANAGEMENT ====
    
    /**
     * Add PVP points to a faction's core
     */
    public void addPvpPoints(String factionName, double amount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.addPvpPoints(amount);
        }
    }
    
    /**
     * Add points from PVP kill
     */
    public void addPvpKill(String factionName) {
        addPvpPoints(factionName, pointsPerPvpKill);
    }
    
    /**
     * Add spawner points (when spawners are placed/come under control)
     */
    public void addSpawnerPoints(String factionName, int spawnerCount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.addSpawnerPoints(spawnerCount * pointsPerSpawner);
        }
    }
    
    /**
     * Remove spawner points when a spawner is destroyed/mined
     */
    public void removeSpawnerPoints(String factionName, int spawnerCount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.removeSpawnerPoints(spawnerCount * pointsPerSpawner);
        }
    }
    
    /**
     * Add core chunk notes/resources
     */
    public void addCoreChunkNotes(String factionName, double amount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.addCoreChunkNotes(amount);
        }
    }
    
    /**
     * Add challenge points to core
     */
    public void addChallengeCompletion(String factionName) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.addChallengePoints(pointsPerChallenge);
        }
    }
    
    /**
     * Add custom challenge points
     */
    public void addChallengePoints(String factionName, double amount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.addChallengePoints(amount);
        }
    }
    
    // ===== DAMAGE ====
    
    /**
     * Deal damage to a faction's core chunk during a raid
     * @return true if core still exists (points > 0), false if destroyed
     */
    public boolean damageCore(String factionName, double damageAmount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core == null) return true; // No core if faction doesn't exist
        
        return core.takeDamage(damageAmount);
    }
    
    /**
     * Deal damage based on raid hits
     * @return true if core still exists, false if destroyed
     */
    public boolean applyRaidDamage(String factionName, int hitCount) {
        return damageCore(factionName, hitCount * damagePerRaidHit);
    }
    
    /**
     * Repair a core chunk
     */
    public void repairCore(String factionName, double repairAmount) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.repair(repairAmount);
        }
    }
    
    /**
     * Fully repair a core chunk
     */
    public void fullRepairCore(String factionName) {
        CoreChunk core = getCoreChunk(factionName);
        if (core != null) {
            core.fullRepair();
        }
    }
    
    // ===== CONFIGURATION ====
    
    public void setBaseMaxPoints(double amount) { this.baseMaxPoints = amount; }
    public void setPointsPerSpawner(double amount) { this.pointsPerSpawner = amount; }
    public void setPointsPerPvpKill(double amount) { this.pointsPerPvpKill = amount; }
    public void setPointsPerChallenge(double amount) { this.pointsPerChallenge = amount; }
    public void setDamagePerRaidHit(double amount) { this.damagePerRaidHit = amount; }
    
    public double getBaseMaxPoints() { return baseMaxPoints; }
    public double getPointsPerSpawner() { return pointsPerSpawner; }
    public double getPointsPerPvpKill() { return pointsPerPvpKill; }
    public double getPointsPerChallenge() { return pointsPerChallenge; }
    public double getDamagePerRaidHit() { return damagePerRaidHit; }
}
