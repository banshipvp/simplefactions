package local.simplefactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class FactionManager {

    public static class Faction {

        private final String name;
        private String description = "No description set.";
        private final UUID leader;

        // roles map replaces members set
        private final Map<UUID, Role> roles = new HashMap<>();

        // claims are stored as "world:x:z"
        private final Set<String> claims = new HashSet<>();

        private final Inventory chest;

        // home
        private String homeWorld;
        private double homeX, homeY, homeZ;
        private float homeYaw, homePitch;   

        // power system
        private double power = 0.0;

        // relationships stored by faction name (lowercase)
        private final Set<String> allies = new HashSet<>();
        private final Set<String> enemies = new HashSet<>();

        // claim flags
        private final java.util.Map<ClaimFlag, Boolean> flags = new java.util.EnumMap<>(ClaimFlag.class);

        // economy balance placeholder (Vault integration can replace)
        private double balance = 0.0;

        // faction TNT bank
        private int tntBank = 0;
        
        // spawner tracking by type (e.g. "cow", "zombie", etc.)
        private final java.util.Map<String, Integer> spawnersByType = new java.util.HashMap<>();
        
        // warps stored as "name" -> Location
        private final java.util.Map<String, Location> warps = new java.util.HashMap<>();
        
        // player titles stored as UUID -> title
        private final java.util.Map<UUID, String> playerTitles = new java.util.HashMap<>();
        
        // upgrades: maxMembers, spawnerMultiplier, maxWarps, chestSlots
        private int upgradeMaxMembers = 0;      // level 0 = 10 base
        private int upgradeSpawnerMult = 0;     // level 0 = 1x multiplier
        private int upgradeMaxWarps = 0;        // level 0 = 5 base
        private int upgradeChestSlots = 0;      // level 0 = 1 hotbar (9 slots)

        public Faction(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.roles.put(leader, Role.LEADER);

            // starting values
            this.power = 0.0;
            this.balance = 0.0;

            for (ClaimFlag flag : ClaimFlag.values()) {
                flags.put(flag, true);
            }

            this.chest = Bukkit.createInventory(
                    null,
                    54,
                    Component.text("Faction Chest: " + name)
            );
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public void setDescription(String desc) { this.description = desc; }

        public UUID getLeader() { return leader; }

        public Set<UUID> getMembers() { return roles.keySet(); }
        public boolean isMember(UUID uuid) { return roles.containsKey(uuid); }

        public Role getRole(UUID uuid) { return roles.get(uuid); }
        public void setRole(UUID uuid, Role role) { roles.put(uuid, role); }
        public void removeMember(UUID uuid) { roles.remove(uuid); }

        public Set<String> getClaims() { return claims; }

        public Inventory getChest() { return chest; }

        public int maxClaims() { return 1000; }

        public boolean hasHome() { return homeWorld != null; }

        public void setHome(Location loc) {
            this.homeWorld = loc.getWorld().getName();
            this.homeX = loc.getX();
            this.homeY = loc.getY();
            this.homeZ = loc.getZ();
            this.homeYaw = loc.getYaw();
            this.homePitch = loc.getPitch();
        }

        // ----- power -----
        public double getPower() { return power; }
        public void setPower(double p) { this.power = p; }
        public void addPower(double delta) { this.power += delta; }

        // ----- relationships -----
        public Set<String> getAllies() { return allies; }
        public boolean isAlly(String factionName) { return allies.contains(factionName.toLowerCase()); }
        public void addAlly(String factionName) { allies.add(factionName.toLowerCase()); }
        public void removeAlly(String factionName) { allies.remove(factionName.toLowerCase()); }

        public Set<String> getEnemies() { return enemies; }
        public boolean isEnemy(String factionName) { return enemies.contains(factionName.toLowerCase()); }
        public void addEnemy(String factionName) { enemies.add(factionName.toLowerCase()); }
        public void removeEnemy(String factionName) { enemies.remove(factionName.toLowerCase()); }

        // ----- flags -----
        public Map<ClaimFlag, Boolean> getFlags() { return flags; }
        public boolean isFlagEnabled(ClaimFlag flag) { return flags.getOrDefault(flag, false); }
        public void setFlag(ClaimFlag flag, boolean enabled) { flags.put(flag, enabled); }

        // ----- economy -----
        public double getBalance() { return balance; }
        public void setBalance(double b) { balance = b; }
        public void addBalance(double d) { balance += d; }

        // ----- TNT bank -----
        public int getTntBank() { return tntBank; }
        public void setTntBank(int amount) { tntBank = Math.max(0, amount); }
        public void addTnt(int amount) { if (amount > 0) tntBank += amount; }
        public boolean removeTnt(int amount) {
            if (amount <= 0) return false;
            if (tntBank < amount) return false;
            tntBank -= amount;
            return true;
        }

        // ----- spawners -----
        public java.util.Map<String, Integer> getSpawnersByType() { return spawnersByType; }
        public int getTotalSpawnerCount() { return spawnersByType.values().stream().mapToInt(Integer::intValue).sum(); }
        public void addSpawner(String type) { spawnersByType.put(type, spawnersByType.getOrDefault(type, 0) + 1); }
        public void removeSpawner(String type) { 
            int count = spawnersByType.getOrDefault(type, 0);
            if (count > 1) spawnersByType.put(type, count - 1);
            else spawnersByType.remove(type);
        }
        
        public double getSpawnerValue() { 
            double base = getTotalSpawnerCount() * 1000.0;
            return base * (1.0 + (0.1 * upgradeSpawnerMult));
        }
        public double getWealthValue() { return balance + getSpawnerValue() + (power * 10.0); }
        
        // ----- upgrades -----
        public int getUpgradeLevel(String upgradeType) {
            return switch(upgradeType.toLowerCase()) {
                case "maxmembers" -> upgradeMaxMembers;
                case "spawnermult" -> upgradeSpawnerMult;
                case "maxwarps" -> upgradeMaxWarps;
                case "chestslots" -> upgradeChestSlots;
                default -> 0;
            };
        }
        public void setUpgradeLevel(String upgradeType, int level) {
            switch(upgradeType.toLowerCase()) {
                case "maxmembers" -> upgradeMaxMembers = level;
                case "spawnermult" -> upgradeSpawnerMult = level;
                case "maxwarps" -> upgradeMaxWarps = level;
                case "chestslots" -> upgradeChestSlots = level;
            }
        }
        public int getMaxMembers() { return 10 + (5 * upgradeMaxMembers); }
        public int getMaxWarps() { return 5 + (2 * upgradeMaxWarps); }
        public int getChestSlots() { return 9 * (1 + upgradeChestSlots); }
        public double getSpawnerMultiplier() { return 1.0 + (0.1 * upgradeSpawnerMult); }
        
        // ----- warps -----
        public java.util.Map<String, Location> getWarps() { return warps; }
        public void addWarp(String name, Location loc) { warps.put(name.toLowerCase(), loc); }
        public void removeWarp(String name) { warps.remove(name.toLowerCase()); }
        public Location getWarp(String name) { return warps.get(name.toLowerCase()); }

        // ----- titles -----
        public String getPlayerTitle(UUID player) { return playerTitles.getOrDefault(player, ""); }
        public void setPlayerTitle(UUID player, String title) { playerTitles.put(player, title); }
        public void removePlayerTitle(UUID player) { playerTitles.remove(player); }

        public Location getHome() {
            if (homeWorld == null) return null;
            World w = Bukkit.getWorld(homeWorld);
            if (w == null) return null;
            return new Location(w, homeX, homeY, homeZ, homeYaw, homePitch);
        }
    }

    private static class Invite {
        final String factionKey;
        final long expiresAtMs;

        Invite(String factionKey, long expiresAtMs) {
            this.factionKey = factionKey;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final long INVITE_TTL_MS = 5L * 60_000L; // 5 minutes

    private final Map<String, Faction> factions = new HashMap<>();      // key = lowercase faction name
    private final Map<UUID, String> playerFaction = new HashMap<>();    // player -> factionKey
    private final Map<String, String> claims = new HashMap<>();         // "world:x:z" -> factionKey
    private final Map<UUID, Invite> invites = new HashMap<>();          // player -> invite
    private final Set<UUID> factionChatPlayers = new HashSet<>();       // players in faction chat mode
    private final Set<UUID> autoMapPlayers = new HashSet<>();           // players with /f map auto enabled
    private final CoreChunkManager coreChunkManager = new CoreChunkManager(); // core chunk system

    /* ================= LOOKUPS ================= */

    public Faction getFaction(UUID player) {
        String key = playerFaction.get(player);
        return key == null ? null : factions.get(key);
    }

    public Faction getFactionByPlayer(org.bukkit.entity.Player player) {
        return getFaction(player.getUniqueId());
    }

    public Faction getFactionByName(String name) {
        if (name == null) return null;
        return factions.get(name.toLowerCase());
    }

    public Faction getFactionByChunk(String world, int x, int z) {
        String key = chunkKey(world, x, z);
        String factionKey = claims.get(key);
        return factionKey == null ? null : factions.get(factionKey);
    }

    public boolean isLeader(org.bukkit.entity.Player player) {
        Faction f = getFaction(player.getUniqueId());
        return f != null && f.getLeader().equals(player.getUniqueId());
    }

    public boolean hasPerm(UUID player, FactionPermission perm) {
        Faction f = getFaction(player);
        if (f == null) return false;

        Role role = f.getRole(player);
        if (role == null) return false;

        if (role == Role.LEADER) return true;

        // Simple defaults:
        // MEMBER: chest/home read only
        // OFFICER: invite/kick/claim/unclaim/sethome/desc/promote-demote
        return switch (perm) {
            case OPEN_CHEST, HOME, POWER, BALANCE -> role.atLeast(Role.MEMBER);
            case INVITE, KICK, CLAIM, UNCLAIM, UNCLAIM_ALL, SET_HOME, SET_DESC, PROMOTE, 
                 ALLY, ENEMY, UNALLY, UNENEMY, SET_POWER, ADD_POWER, FLAG -> role.atLeast(Role.OFFICER);
            case DISBAND -> false;
        };
    }

    /* ================= CREATE ================= */

    public boolean createFaction(String name, UUID owner) {
        if (name == null || name.isBlank()) return false;

        String key = name.toLowerCase();
        if (factions.containsKey(key)) return false;
        if (playerFaction.containsKey(owner)) return false;

        Faction faction = new Faction(name, owner);
        factions.put(key, faction);
        playerFaction.put(owner, key);
        return true;
    }

    /* ================= INVITES ================= */

    public void invite(UUID inviter, UUID target) {
        String factionKey = playerFaction.get(inviter);
        if (factionKey != null) {
            invites.put(target, new Invite(factionKey, System.currentTimeMillis() + INVITE_TTL_MS));
        }
    }

    public boolean hasValidInvite(UUID player) {
        Invite inv = invites.get(player);
        if (inv == null) return false;
        if (System.currentTimeMillis() > inv.expiresAtMs) {
            invites.remove(player);
            return false;
        }
        return factions.containsKey(inv.factionKey);
    }

    public String getInviteFactionName(UUID player) {
        Invite inv = invites.get(player);
        if (inv == null) return null;
        if (System.currentTimeMillis() > inv.expiresAtMs) {
            invites.remove(player);
            return null;
        }
        Faction f = factions.get(inv.factionKey);
        return f == null ? null : f.getName();
    }

    public boolean join(UUID player, String optionalFactionName) {
        if (playerFaction.containsKey(player)) return false;

        Invite inv = invites.get(player);
        if (inv == null) return false;

        if (System.currentTimeMillis() > inv.expiresAtMs) {
            invites.remove(player);
            return false;
        }

        if (optionalFactionName != null && !optionalFactionName.isBlank()) {
            if (!inv.factionKey.equalsIgnoreCase(optionalFactionName.toLowerCase())) {
                return false;
            }
        }

        Faction faction = factions.get(inv.factionKey);
        if (faction == null) {
            invites.remove(player);
            return false;
        }

        invites.remove(player);
        faction.setRole(player, Role.MEMBER);
        playerFaction.put(player, inv.factionKey);
        return true;
    }

    /* ================= LEAVE ================= */

    public boolean leave(UUID player) {
        Faction f = getFaction(player);
        if (f == null) return false;

        if (f.getLeader().equals(player)) return false;

        f.removeMember(player);
        playerFaction.remove(player);
        return true;
    }

    public void setAutoMap(UUID playerId, boolean enabled) {
        if (enabled) {
            autoMapPlayers.add(playerId);
        } else {
            autoMapPlayers.remove(playerId);
        }
    }

    public boolean isAutoMapEnabled(UUID playerId) {
        return autoMapPlayers.contains(playerId);
    }

    public void clearAutoMap(UUID playerId) {
        autoMapPlayers.remove(playerId);
    }

    /* ================= DISBAND ================= */

    public boolean disband(UUID player) {
        Faction f = getFaction(player);
        if (f == null) return false;

        if (!f.getLeader().equals(player)) return false;

        String key = f.getName().toLowerCase();
        factions.remove(key);

        // Remove all member mappings
        for (UUID member : new HashSet<>(f.getMembers())) {
            playerFaction.remove(member);
        }

        // Remove all claims
        for (String claimKey : new HashSet<>(f.getClaims())) {
            claims.remove(claimKey);
        }
        f.getClaims().clear();

        // Destroy the core chunk if it exists
        coreChunkManager.destroyCoreChunk(f.getName());

        return true;
    }

    /* ================= CLAIM ================= */

    public boolean claimChunk(Faction faction, String world, int x, int z) {
        String key = chunkKey(world, x, z);

        if (claims.containsKey(key)) return false;
        if (faction.getClaims().size() >= faction.maxClaims()) return false;

        String factionKey = faction.getName().toLowerCase();

        claims.put(key, factionKey);
        faction.getClaims().add(key);
        
        // Create core chunk on first claim
        if (faction.getClaims().size() == 1) {
            coreChunkManager.createCoreChunk(faction.getName(), key);
        }
        
        return true;
    }

    public boolean unclaimChunk(Faction faction, String world, int x, int z) {
        String key = chunkKey(world, x, z);

        if (!faction.getClaims().contains(key)) return false;

        faction.getClaims().remove(key);
        claims.remove(key);
        return true;
    }

    public void unclaimAll(Faction faction) {
        for (String key : new HashSet<>(faction.getClaims())) {
            claims.remove(key);
        }
        faction.getClaims().clear();
        
        // Destroy the core chunk when unclaimAll is used (the only way to remove it)
        coreChunkManager.destroyCoreChunk(faction.getName());
    }

    public String getClaimOwner(String world, int x, int z) {
        return claims.get(chunkKey(world, x, z));
    }

    /* ================= RANK ACTIONS ================= */

    public boolean kick(UUID actor, UUID target) {
        Faction f = getFaction(actor);
        if (f == null) return false;
        if (!f.isMember(target)) return false;
        if (f.getLeader().equals(target)) return false;

        if (!hasPerm(actor, FactionPermission.KICK)) return false;

        Role actorRole = f.getRole(actor);
        Role targetRole = f.getRole(target);
        if (actorRole == null || targetRole == null) return false;

        // actor must be higher rank than target
        if (actorRole.ordinal() >= targetRole.ordinal()) return false;

        f.removeMember(target);
        playerFaction.remove(target);
        return true;
    }

    public boolean promoteToOfficer(UUID actor, UUID target) {
        Faction f = getFaction(actor);
        if (f == null) return false;
        if (!f.isMember(target)) return false;

        if (!hasPerm(actor, FactionPermission.PROMOTE)) return false;

        Role targetRole = f.getRole(target);
        if (targetRole == null) return false;

        if (targetRole != Role.MEMBER) return false; // only member -> officer
        if (f.getLeader().equals(target)) return false;

        f.setRole(target, Role.OFFICER);
        return true;
    }

    public boolean demoteToMember(UUID actor, UUID target) {
        Faction f = getFaction(actor);
        if (f == null) return false;
        if (!f.isMember(target)) return false;

        if (!hasPerm(actor, FactionPermission.PROMOTE)) return false;

        Role targetRole = f.getRole(target);
        if (targetRole == null) return false;

        if (targetRole != Role.OFFICER) return false; // only officer -> member
        if (f.getLeader().equals(target)) return false;

        f.setRole(target, Role.MEMBER);
        return true;
    }

    public boolean setHome(UUID actor, Location loc) {
        Faction f = getFaction(actor);
        if (f == null) return false;
        if (!hasPerm(actor, FactionPermission.SET_HOME)) return false;
        
        // Check if player is in their faction's territory
        Faction chunkOwner = getFactionByChunk(loc.getWorld().getName(), loc.getChunk().getX(), loc.getChunk().getZ());
        if (chunkOwner == null || !chunkOwner.getName().equalsIgnoreCase(f.getName())) {
            return false; // Not in faction territory
        }

        f.setHome(loc);
        return true;
    }

    /* ================= CHAT MODE ================= */

    public boolean isInFactionChat(UUID player) {
        return factionChatPlayers.contains(player);
    }

    public void setFactionChatMode(UUID player, boolean enabled) {
        if (enabled) {
            factionChatPlayers.add(player);
        } else {
            factionChatPlayers.remove(player);
        }
    }

    /* ================= HELPERS ================= */

    private String chunkKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }
    
    /**
     * Get all factions sorted by wealth (highest to lowest)
     */
    public java.util.List<Faction> getAllFactionsSortedByWealth() {
        return factions.values().stream()
                .sorted((a, b) -> Double.compare(b.getWealthValue(), a.getWealthValue()))
                .toList();
    }
    
    /* ================= CORE CHUNK ================= */
    
    public CoreChunkManager getCoreChunkManager() {
        return coreChunkManager;
    }
}