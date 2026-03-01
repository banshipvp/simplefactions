# SimpleFactions Core Chunk System

## Overview

I've successfully implemented a **Core Chunk System** for your SimpleFactions plugin. This system creates a faction's core chunk when they make their first claim, and allows other plugins (like your SimpleFactionsRaiding plugin) to interact with and damage this core.

## What Was Added

### 1. **CoreChunk.java** - Core Chunk Data Model
Represents a single faction's core chunk with:
- **Current Health/Points**: Tracks the core's current status (0 = destroyed)
- **Point Accumulation**:
  - **PVP Points**: From player-vs-player activity
  - **Spawner Points**: From controlled spawners (lost when mined/destroyed)
  - **Challenge Points**: From faction challenges
  - **Core Chunk Notes**: Resources/notes gathered
- **Damage Tracking**: Records total damage taken and last damage time
- Methods to repair, take damage, and check status

### 2. **CoreChunkManager.java** - Core Chunk Management
Centralized manager for all core chunks with methods to:
- ✅ Create core chunks on first faction claim
- ✅ Track and retrieve cores by faction name
- ✅ Add/remove points from all sources
- ✅ Deal raid damage and manage repairs
- ✅ Configure point values and damage amounts
- ✅ Destroy cores (only via unclaimAll)

### 3. **Updated FactionManager.java**
Integrated core chunk system:
- Creates a core chunk automatically on a faction's first claim
- Destroys core chunk only when faction uses unclaimAll (as requested)
- Destroys core chunk when faction is disbanded
- Exposes CoreChunkManager via `getCoreChunkManager()` method

### 4. **Updated SimpleFactionsPlugin.java**
- Added `getCoreChunkManager()` method for easy access
- Added system initialization message

### 5. **CoreChunkRaidListener.java** - Example Implementation
Complete example listener showing how to:
- Detect core block destruction
- Damage cores during raids
- Award PVP points
- Handle spawner conversions
- Track challenge completions
- Display core status information

## Key Features

### ✨ Core Chunk Lifecycle
1. **Creation**: Automatically created when faction claims first chunk
2. **Growth**: Points accumulate from PVP, spawners, challenges, and resources
3. **Raiding**: Other plugins can damage the core during raids
4. **Destruction**: Only removed when faction executes /f unclaimall (as requested)
5. **Persistence**: Damage history and point contributions are tracked

### 🛡️ Point System
Core strength is determined by accumulated points from multiple sources:
```
Total Core Points = PVP Points + Spawner Points + Challenge Points + Core Notes
```

**Default Values** (configurable):
- Base Max Points: 10,000
- Per Spawner: 500 points
- Per PVP Kill: 100 points
- Per Challenge: 250 points
- Damage Per Raid Hit: 50 points

### 🎮 Integration with Raiding Plugin

Your SimpleFactionsRaiding plugin can now:

```java
// Get the core chunk manager
CoreChunkManager coreManager = plugin.getCoreChunkManager();

// Check if faction has a core
if (coreManager.hasCoreChunk("FactionName")) {
    // Deal damage during raid
    boolean coreStillActive = coreManager.damageCore("FactionName", damageAmount);
    
    if (!coreStillActive) {
        // Core destroyed!
    }
}

// Gain points from various activities
coreManager.addPvpKill("FactionName");                    // PVP activity
coreManager.addSpawnerPoints("FactionName", 5);           // Spawners controlled
coreManager.removeSpawnerPoints("FactionName", 2);        // Spawners destroyed
coreManager.addChallengeCompletion("FactionName");        // Challenge completion
coreManager.addCoreChunkNotes("FactionName", 1000);       // Resources gathered
```

## Configuration

All point values are configurable at runtime:

```java
CoreChunkManager manager = factionManager.getCoreChunkManager();

manager.setBaseMaxPoints(10000.0);          // Base maximum points
manager.setPointsPerSpawner(500.0);         // Points per spawner
manager.setPointsPerPvpKill(100.0);         // Points per PVP kill
manager.setPointsPerChallenge(250.0);       // Points per challenge
manager.setDamagePerRaidHit(50.0);          // Damage per raid hit
```

## Core Chunk Info Methods

Get detailed core information:

```java
CoreChunk core = coreManager.getCoreChunk("FactionName");

// Health information
core.getCurrentPoints();      // Current health
core.getMaxPoints();          // Maximum health
core.getHealthPercentage();   // As percentage (0-100%)

// Point contributions
core.getPvpPoints();          // From PVP activity
core.getSpawnerPoints();      // From spawners
core.getChallengePoints();    // From challenges
core.getCoreChunkNotes();     // From resources
core.getTotalPoints();        // Sum of all contributions

// Damage tracking
core.getTotalDamageTaken();   // Cumulative damage
core.getLastDamageTime();     // Timestamp of last hit

// Display information
core.toString();              // Nice formatted string
```

## How to Use in Your SimpleFactionsRaiding Plugin

### Step 1: Register the Listener
```java
SimpleFactionsPlugin sf = (SimpleFactionsPlugin) Bukkit.getPluginManager().getPlugin("SimpleFactions");
Bukkit.getPluginManager().registerEvents(new CoreChunkRaidListener(sf.getFactionManager()), this);
```

### Step 2: Customize Raid Mechanics
Extend `CoreChunkRaidListener` or create your own listener that:
- Detects when raid blocks are broken
- Calls `coreManager.damageCore()` when core takes damage
- Tracks PVP kills and calls `coreManager.addPvpKill()`
- Monitors spawner changes
- Awards rewards for successful raids

### Step 3: Display Core Status
```java
CoreChunkRaidListener listener = new CoreChunkRaidListener(factionManager);
listener.broadcastCoreStatus();  // Displays all active cores
```

## Files Created/Modified

**Created:**
- ✅ `CoreChunk.java` - Core chunk data model
- ✅ `CoreChunkManager.java` - Core chunk management system
- ✅ `CoreChunkRaidListener.java` - Example raid listener
- ✅ `CORE_CHUNK_INTEGRATION_GUIDE.MD` - Detailed integration guide

**Modified:**
- ✅ `FactionManager.java` - Integrated core chunk creation/destruction
- ✅ `SimpleFactionsPlugin.java` - Added core chunk manager access

## Important Notes

⚠️ **Core Destruction:**
- Cores are **ONLY** destroyed when a faction executes `/f unclaimall` (as requested)
- Cores cannot be removed by claiming/unclaiming individual chunks
- Disbanding a faction also destroys its core

📊 **Point System:**
- Points accumulate automatically as factions engage in activities
- Points are tracked separately by source for transparency
- Total points are capped at the faction's max (configurable)
- Damaged cores don't heal unless explicitly repaired

🛡️ **Raid Integration:**
- Your raiding plugin controls how much damage is dealt per hit
- The core chunk manager only tracks and applies damage
- Full raid mechanics (rewards, announcements, etc.) are up to your implementation

## Next Steps

1. **Customize Point Values**: Adjust the default point values in CoreChunkManager based on your balance
2. **Integrate with Raid Events**: Use CoreChunkRaidListener as a base or create your own raid listener
3. **Add Commands**: Create admin commands to view core status (/f coreinfo, /f corestatus, etc.)
4. **Add Repair Mechanics**: Implement ways for factions to heal their core (quests, resources, etc.)
5. **Add Visuals**: Create particle effects or sounds when cores are hit or destroyed

## Example Commands to Add

```
/f core - View your faction's core status
/f coreinfo <faction> - View another faction's core status
/f repair [amount] - Repair your faction's core (if implemented)
/admin coredamage <faction> <amount> - Admin command to damage core
/admin corerepair <faction> <amount> - Admin command to repair core
/admin corestatus - View all faction core statuses
```

## Summary

The Core Chunk System is now fully integrated into SimpleFactions! 🎉

Your SimpleFactionsRaiding plugin can now:
- ✅ Interact with faction cores
- ✅ Deal damage to cores during raids
- ✅ Track point contributions
- ✅ Determine core status for game mechanics
- ✅ Award rewards for successful raids

All code is compiled and ready to use. Start integrating with your raiding plugin!
