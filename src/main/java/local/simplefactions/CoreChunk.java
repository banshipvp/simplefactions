package local.simplefactions;

/**
 * Represents a faction's core chunk with accumulated points from various sources.
 * The core chunk is established on the first claim and can only be removed via unclaimAll.
 */
public class CoreChunk {
    
    private String chunkKey;           // "world:x:z" format
    private String factionName;        // For reference
    private double currentPoints;      // Current health/points
    private double maxPoints;          // Maximum health
    
    // Point accumulation tracking
    private double pvpPoints;          // Points from PVP activity
    private double spawnerPoints;      // Points from spawners (lost when mined/exploded)
    private double coreChunkNotes;     // Resources/notes accumulated
    private double challengePoints;    // Points from completing challenges
    
    // Damage tracking for raiding
    private double totalDamageTaken;   // Total damage taken from raids
    private long lastDamageTime;       // When the core was last damaged
    
    public CoreChunk(String chunkKey, String factionName, double initialMaxPoints) {
        this.chunkKey = chunkKey;
        this.factionName = factionName;
        this.maxPoints = initialMaxPoints;
        this.currentPoints = initialMaxPoints;
        
        this.pvpPoints = 0.0;
        this.spawnerPoints = 0.0;
        this.coreChunkNotes = 0.0;
        this.challengePoints = 0.0;
        this.totalDamageTaken = 0.0;
        this.lastDamageTime = 0;
    }
    
    // === GETTERS ===
    public String getChunkKey() { return chunkKey; }
    public String getFactionName() { return factionName; }
    public double getCurrentPoints() { return currentPoints; }
    public double getMaxPoints() { return maxPoints; }
    public double getHealthPercentage() { return (currentPoints / maxPoints) * 100.0; }
    
    public double getPvpPoints() { return pvpPoints; }
    public double getSpawnerPoints() { return spawnerPoints; }
    public double getCoreChunkNotes() { return coreChunkNotes; }
    public double getChallengePoints() { return challengePoints; }
    public double getTotalPoints() { return pvpPoints + spawnerPoints + coreChunkNotes + challengePoints; }
    
    public double getTotalDamageTaken() { return totalDamageTaken; }
    public long getLastDamageTime() { return lastDamageTime; }
    
    // ===  POINT ACCUMULATION ===
    
    /**
     * Add PVP points from player-vs-player activity
     */
    public void addPvpPoints(double amount) {
        this.pvpPoints = Math.max(0, pvpPoints + amount);
        updateCurrentPoints();
    }
    
    /**
     * Add spawner points (increases core strength)
     */
    public void addSpawnerPoints(double amount) {
        this.spawnerPoints = Math.max(0, spawnerPoints + amount);
        updateCurrentPoints();
    }
    
    /**
     * Remove spawner points when spawners are mined or exploded
     */
    public void removeSpawnerPoints(double amount) {
        this.spawnerPoints = Math.max(0, spawnerPoints - amount);
        updateCurrentPoints();
    }
    
    /**
     * Add core chunk notes (resources)
     */
    public void addCoreChunkNotes(double amount) {
        this.coreChunkNotes = Math.max(0, coreChunkNotes + amount);
        updateCurrentPoints();
    }
    
    /**
     * Add challenge points
     */
    public void addChallengePoints(double amount) {
        this.challengePoints = Math.max(0, challengePoints + amount);
        updateCurrentPoints();
    }
    
    // === DAMAGE & RAIDING ===
    
    /**
     * Take damage to the core chunk from a raid
     * @param damageAmount The amount of damage to apply
     * @return true if the core is still active, false if destroyed (points <= 0)
     */
    public boolean takeDamage(double damageAmount) {
        this.currentPoints = Math.max(0, currentPoints - damageAmount);
        this.totalDamageTaken += damageAmount;
        this.lastDamageTime = System.currentTimeMillis();
        return currentPoints > 0;
    }
    
    /**
     * Repair the core chunk (for faction activities)
     */
    public void repair(double repairAmount) {
        this.currentPoints = Math.min(maxPoints, currentPoints + repairAmount);
    }
    
    /**
     * Fully repair the core chunk back to max
     */
    public void fullRepair() {
        this.currentPoints = maxPoints;
    }
    
    // === INTERNAL ===
    
    /**
     * Recalculates current points based on all contributing sources
     */
    private void updateCurrentPoints() {
        double basePoints = getTotalPoints();
        // The max possible points is determined by the maximum contributions
        // but we cap it at maxPoints
        this.currentPoints = Math.min(maxPoints, Math.max(0, basePoints));
    }
    
    /**
     * Reset all points (used when core is destroyed via unclaimAll)
     */
    public void reset() {
        this.pvpPoints = 0.0;
        this.spawnerPoints = 0.0;
        this.coreChunkNotes = 0.0;
        this.challengePoints = 0.0;
        this.currentPoints = maxPoints;
        this.totalDamageTaken = 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("CoreChunk[%s] Health: %.1f/%.1f (%.1f%%) | PVP: %.1f, Spawner: %.1f, Notes: %.1f, Challenges: %.1f",
                chunkKey, currentPoints, maxPoints, getHealthPercentage(),
                pvpPoints, spawnerPoints, coreChunkNotes, challengePoints);
    }
}
