package local.simplefactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FactionManager {

    /**
     * InventoryHolder used to tag faction chest views so they can be identified
     * in InventoryCloseEvent without relying on fragile title-string matching.
     */
    public static class FactionChestHolder implements InventoryHolder {
        private final String factionName;
        public FactionChestHolder(String factionName) { this.factionName = factionName; }
        public String getFactionName() { return factionName; }
        @Override public Inventory getInventory() { return null; } // not used
    }

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

        // external access control (non-faction members)
        private final java.util.Map<UUID, java.util.Set<ClaimAccessPermission>> playerAccessPermissions = new java.util.HashMap<>();
        private final java.util.Map<UUID, java.util.Set<String>> playerChunkAccess = new java.util.HashMap<>();
        private final java.util.Set<UUID> playerAllClaimsAccess = new java.util.HashSet<>();
        
        // upgrades: maxMembers, spawnerMultiplier, maxWarps, chestSlots, auto-tnt2gunpowder, auto-ingot2block, successrate
        private int upgradeMaxMembers = 0;      // level 0 = 10 base
        private int upgradeSpawnerMult = 0;     // level 0 = 1x multiplier
        private int upgradeMaxWarps = 0;        // level 0 = 5 base
        private int upgradeChestSlots = 0;      // level 0 = 1 hotbar (9 slots)
        private int upgradeTnt2Gunpowder = 0;  // level 0 = off, 1 = on (converts TNT->Gunpowder in collection chests)
        private int upgradeIngot2Block = 0;    // level 0 = off, 1 = on (auto-compresses 9 ingots->block)
        private int upgradeSuccessRate = 0;    // level 0-5: +0/2/4/6/8/10% custom book success rate

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
                case "tnt2gunpowder" -> upgradeTnt2Gunpowder;
                case "ingot2block" -> upgradeIngot2Block;
                case "successrate" -> upgradeSuccessRate;
                default -> 0;
            };
        }
        public void setUpgradeLevel(String upgradeType, int level) {
            switch(upgradeType.toLowerCase()) {
                case "maxmembers" -> upgradeMaxMembers = level;
                case "spawnermult" -> upgradeSpawnerMult = level;
                case "maxwarps" -> upgradeMaxWarps = level;
                case "chestslots" -> upgradeChestSlots = level;
                case "tnt2gunpowder" -> upgradeTnt2Gunpowder = level;
                case "ingot2block" -> upgradeIngot2Block = level;
                case "successrate" -> upgradeSuccessRate = level;
            }
        }
        public int getMaxMembers() { return 10 + (5 * upgradeMaxMembers); }
        public int getMaxWarps() { return 5 + (2 * upgradeMaxWarps); }
        public int getChestSlots() { return 9 * (1 + upgradeChestSlots); }
        public double getSpawnerMultiplier() { return 1.0 + (0.1 * upgradeSpawnerMult); }
        /** Returns the bonus success rate percentage (0, 2, 4, 6, 8 or 10) for custom book application. */
        public int getSuccessRateBonus() { return upgradeSuccessRate * 2; }

        /**
         * Opens a correctly-sized faction chest view (slot count = getChestSlots()).
         * Items from the backing 54-slot store are copied into the view.
         * Close the view via {@link #syncChestView(org.bukkit.inventory.Inventory)} to persist changes.
         */
        public org.bukkit.inventory.Inventory openChestView() {
            int slots = Math.max(9, Math.min(54, getChestSlots()));
            FactionChestHolder holder = new FactionChestHolder(name);
            org.bukkit.inventory.Inventory view = Bukkit.createInventory(
                    holder, slots,
                    net.kyori.adventure.text.Component.text("§6Faction Chest: " + name));
            for (int i = 0; i < slots && i < chest.getSize(); i++) {
                view.setItem(i, chest.getItem(i));
            }
            return view;
        }

        /** Syncs items from an open chest view back into the backing 54-slot inventory. */
        public void syncChestView(org.bukkit.inventory.Inventory view) {
            int slots = view.getSize();
            for (int i = 0; i < slots && i < chest.getSize(); i++) {
                chest.setItem(i, view.getItem(i));
            }
        }
        
        // ----- warps -----
        public java.util.Map<String, Location> getWarps() { return warps; }
        public void addWarp(String name, Location loc) { warps.put(name.toLowerCase(), loc); }
        public void removeWarp(String name) { warps.remove(name.toLowerCase()); }
        public Location getWarp(String name) { return warps.get(name.toLowerCase()); }

        // ----- titles -----
        public String getPlayerTitle(UUID player) { return playerTitles.getOrDefault(player, ""); }
        public void setPlayerTitle(UUID player, String title) { playerTitles.put(player, title); }
        public void removePlayerTitle(UUID player) { playerTitles.remove(player); }

        // ----- external claim access -----
        public java.util.Map<UUID, java.util.Set<ClaimAccessPermission>> getPlayerAccessPermissions() { return playerAccessPermissions; }
        public java.util.Map<UUID, java.util.Set<String>> getPlayerChunkAccess() { return playerChunkAccess; }
        public java.util.Set<UUID> getPlayerAllClaimsAccess() { return playerAllClaimsAccess; }

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

    /** Fixed UUID used as the leader placeholder for system factions (Warzone, Safezone, etc.). */
    public static final UUID SYSTEM_FACTION_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<String, Faction> factions = new HashMap<>();      // key = lowercase faction name
    private final Map<UUID, String> playerFaction = new HashMap<>();    // player -> factionKey
    private final Map<String, String> claims = new HashMap<>();         // "world:x:z" -> factionKey
    private final Map<UUID, Invite> invites = new HashMap<>();          // player -> invite
    private final Set<UUID> factionChatPlayers = new HashSet<>();       // players in faction chat mode
    private final Set<UUID> autoMapPlayers = new HashSet<>();           // players with /f map auto enabled
    private final CoreChunkManager coreChunkManager = new CoreChunkManager(); // core chunk system
    private final SpawnerTracker spawnerTracker = new SpawnerTracker();           // placed spawner tracking (legacy fallback)
    private SpawnerStackManager spawnerStackManager;                               // new stacking system (set after construction)

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

    public java.util.List<String> getFactionNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Faction faction : factions.values()) {
            names.add(faction.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
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

    public boolean canAccessClaim(UUID player, String world, int x, int z, ClaimAccessPermission permission) {
        String ownerKey = claims.get(chunkKey(world, x, z));
        if (ownerKey == null) return true;

        Faction ownerFaction = factions.get(ownerKey);
        if (ownerFaction == null) return true;

        // Faction members always have base access handled by role/commands.
        String playerFactionKey = playerFaction.get(player);
        if (playerFactionKey != null && playerFactionKey.equalsIgnoreCase(ownerKey)) {
            return true;
        }

        java.util.Set<ClaimAccessPermission> perms = ownerFaction.getPlayerAccessPermissions().get(player);
        if (perms == null || !perms.contains(permission)) {
            return false;
        }

        if (ownerFaction.getPlayerAllClaimsAccess().contains(player)) {
            return true;
        }

        java.util.Set<String> chunkAccess = ownerFaction.getPlayerChunkAccess().get(player);
        return chunkAccess != null && chunkAccess.contains(chunkKey(world, x, z));
    }

    public boolean grantAccessChunk(UUID actor, UUID target, String world, int chunkX, int chunkZ) {
        Faction faction = getFaction(actor);
        if (faction == null) return false;
        if (!hasPerm(actor, FactionPermission.FLAG)) return false;
        if (faction.isMember(target)) return false;

        faction.getPlayerChunkAccess()
                .computeIfAbsent(target, ignored -> new java.util.HashSet<>())
                .add(chunkKey(world, chunkX, chunkZ));

        ensureDefaultAccessPerms(faction, target);
        faction.getPlayerAllClaimsAccess().remove(target);
        return true;
    }

    public int grantAccessRadius(UUID actor, UUID target, String world, int centerChunkX, int centerChunkZ, int radius) {
        Faction faction = getFaction(actor);
        if (faction == null) return 0;
        if (!hasPerm(actor, FactionPermission.FLAG)) return 0;
        if (faction.isMember(target)) return 0;

        int granted = 0;
        java.util.Set<String> allowed = faction.getPlayerChunkAccess()
                .computeIfAbsent(target, ignored -> new java.util.HashSet<>());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                String key = chunkKey(world, centerChunkX + dx, centerChunkZ + dz);
                if (faction.getClaims().contains(key) && allowed.add(key)) {
                    granted++;
                }
            }
        }

        ensureDefaultAccessPerms(faction, target);
        faction.getPlayerAllClaimsAccess().remove(target);
        return granted;
    }

    public boolean grantAccessAll(UUID actor, UUID target) {
        Faction faction = getFaction(actor);
        if (faction == null) return false;
        if (!hasPerm(actor, FactionPermission.FLAG)) return false;
        if (faction.isMember(target)) return false;

        faction.getPlayerAllClaimsAccess().add(target);
        faction.getPlayerChunkAccess().remove(target);
        ensureDefaultAccessPerms(faction, target);
        return true;
    }

    public java.util.Set<UUID> getAccessPlayers(Faction faction) {
        java.util.Set<UUID> players = new java.util.HashSet<>();
        players.addAll(faction.getPlayerAccessPermissions().keySet());
        players.addAll(faction.getPlayerChunkAccess().keySet());
        players.addAll(faction.getPlayerAllClaimsAccess());
        return players;
    }

    public java.util.Set<ClaimAccessPermission> getAccessPermissions(Faction faction, UUID target) {
        ensureDefaultAccessPerms(faction, target);
        return faction.getPlayerAccessPermissions().getOrDefault(target, java.util.EnumSet.noneOf(ClaimAccessPermission.class));
    }

    public void setAccessPermission(UUID actor, UUID target, ClaimAccessPermission permission, boolean allowed) {
        Faction faction = getFaction(actor);
        if (faction == null) return;
        if (!hasPerm(actor, FactionPermission.FLAG)) return;
        if (faction.isMember(target)) return;

        ensureDefaultAccessPerms(faction, target);
        java.util.Set<ClaimAccessPermission> perms = faction.getPlayerAccessPermissions().get(target);
        if (allowed) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
    }

    public boolean hasAllClaimsAccess(Faction faction, UUID target) {
        return faction.getPlayerAllClaimsAccess().contains(target);
    }

    public int getSpecificChunkAccessCount(Faction faction, UUID target) {
        java.util.Set<String> chunks = faction.getPlayerChunkAccess().get(target);
        return chunks == null ? 0 : chunks.size();
    }

    private void ensureDefaultAccessPerms(Faction faction, UUID target) {
        faction.getPlayerAccessPermissions().computeIfAbsent(target, ignored -> java.util.EnumSet.allOf(ClaimAccessPermission.class));
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

    /**
     * Create a system faction (e.g. "Warzone") that has no real player leader.
     * The faction is not added to playerFaction so no player is "in" it.
     */
    public boolean createSystemFaction(String name, String description) {
        String key = name.toLowerCase();
        if (factions.containsKey(key)) return false;
        Faction faction = new Faction(name, SYSTEM_FACTION_UUID);
        faction.roles.clear(); // system factions have no real members
        faction.setDescription(description);
        factions.put(key, faction);
        return true;
    }

    /** Returns true if this faction is a system-managed faction (no real leader). */
    public boolean isSystemFaction(FactionManager.Faction faction) {
        return faction != null && SYSTEM_FACTION_UUID.equals(faction.getLeader());
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
        f.getPlayerAccessPermissions().clear();
        f.getPlayerChunkAccess().clear();
        f.getPlayerAllClaimsAccess().clear();

        // Destroy the core chunk if it exists
        coreChunkManager.destroyCoreChunk(f.getName());

        // Remove all tracked spawners for this faction
        if (spawnerStackManager != null) {
            spawnerStackManager.removeFactionStacks(f.getName());
        }
        spawnerTracker.removeFactionSpawners(f.getName());

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
        if (isCoreChunk(faction, world, x, z)) return false;

        removeTrackedSpawnersInChunk(faction, world, x, z);

        faction.getClaims().remove(key);
        claims.remove(key);
        return true;
    }

    /**
     * Claim a chunk directly to the "Warzone" system faction.
     * Bypasses core chunk creation and claim limits.
     */
    public boolean claimChunkWarzone(String world, int x, int z) {
        Faction wz = factions.get("warzone");
        if (wz == null) return false;
        String key = chunkKey(world, x, z);
        if (claims.containsKey(key)) return false;
        claims.put(key, "warzone");
        wz.getClaims().add(key);
        return true;
    }

    /** Unclaim a chunk from the Warzone system faction. */
    public boolean unclaimChunkWarzone(String world, int x, int z) {
        Faction wz = factions.get("warzone");
        if (wz == null) return false;
        String key = chunkKey(world, x, z);
        if (!wz.getClaims().contains(key)) return false;
        wz.getClaims().remove(key);
        claims.remove(key);
        return true;
    }

    /** Public chunk-key builder for use by warzone commands. */
    public String toChunkKey(String world, int x, int z) {
        return chunkKey(world, x, z);
    }

    public boolean isCoreChunk(Faction faction, String world, int x, int z) {
        CoreChunk core = coreChunkManager.getCoreChunk(faction.getName());
        if (core == null) return false;
        return core.getChunkKey().equalsIgnoreCase(chunkKey(world, x, z));
    }

    public void unclaimAll(Faction faction) {
        for (String key : new HashSet<>(faction.getClaims())) {
            ParsedChunkKey parsed = parseChunkKey(key);
            if (parsed != null) {
                removeTrackedSpawnersInChunk(faction, parsed.world, parsed.x, parsed.z);
            }
            claims.remove(key);
        }
        faction.getClaims().clear();
        
        // Destroy the core chunk when unclaimAll is used (the only way to remove it)
        coreChunkManager.destroyCoreChunk(faction.getName());

        // Remove any stragglers not attached to currently-claimed chunks
        if (spawnerStackManager != null) {
            decrementSpawnerTypeCounts(faction, spawnerStackManager.removeFactionStacks(faction.getName()));
        }
        spawnerTracker.removeFactionSpawners(faction.getName());
        faction.getSpawnersByType().clear();
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

    private void removeTrackedSpawnersInChunk(Faction faction, String world, int chunkX, int chunkZ) {
        if (faction == null || world == null) return;

        if (spawnerStackManager != null) {
            Map<String, Integer> removed = spawnerStackManager.removeFactionStacksInChunk(faction.getName(), world, chunkX, chunkZ);
            decrementSpawnerTypeCounts(faction, removed);
        } else {
            Map<String, Integer> removed = spawnerTracker.removeFactionSpawnersInChunk(faction.getName(), world, chunkX, chunkZ);
            decrementSpawnerTypeCounts(faction, removed);
        }
    }

    private void decrementSpawnerTypeCounts(Faction faction, Map<String, Integer> removedByType) {
        if (faction == null || removedByType == null || removedByType.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : removedByType.entrySet()) {
            String type = entry.getKey();
            int amount = Math.max(0, entry.getValue());
            for (int i = 0; i < amount; i++) {
                faction.removeSpawner(type);
            }
        }
    }

    private ParsedChunkKey parseChunkKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            return new ParsedChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static class ParsedChunkKey {
        final String world;
        final int x;
        final int z;

        ParsedChunkKey(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
    }
    
    /**
     * Get all factions sorted by wealth (highest to lowest).
     * Spawner value is taken from SpawnerStackManager (or legacy SpawnerTracker).
     */
    public java.util.List<Faction> getAllFactionsSortedByWealth() {
        return factions.values().stream()
                .sorted((a, b) -> Double.compare(
                        b.getBalance() + getTrackedSpawnerValue(b.getName()) + b.getPower() * 10.0,
                        a.getBalance() + getTrackedSpawnerValue(a.getName()) + a.getPower() * 10.0))
                .toList();
    }

    /**
     * Returns the current time-ramped spawner value for a faction.
     * Uses SpawnerStackManager if available, otherwise legacy SpawnerTracker.
     */
    public double getTrackedSpawnerValue(String factionName) {
        if (spawnerStackManager != null) return spawnerStackManager.getTotalValueForFaction(factionName);
        return spawnerTracker.getTotalValueForFaction(factionName);
    }
    
    /* ================= CORE CHUNK ================= */
    
    public CoreChunkManager getCoreChunkManager() {
        return coreChunkManager;
    }

    /* ================= SPAWNER TRACKER ================= */

    public SpawnerTracker getSpawnerTracker() { return spawnerTracker; }
    public SpawnerStackManager getSpawnerStackManager() { return spawnerStackManager; }
    public void setSpawnerStackManager(SpawnerStackManager m) { this.spawnerStackManager = m; }

    /**
     * Register a spawner placed in a faction chunk.
     * Delegates to SpawnerStackManager when available, otherwise SpawnerTracker.
     */
    public void onSpawnerPlaced(String world, int x, int y, int z,
                                String entityType, String factionName) {
        if (spawnerStackManager != null) {
            spawnerStackManager.placeSpawner(world, x, y, z, entityType, factionName);
        } else {
            spawnerTracker.addSpawner(world, x, y, z, entityType, factionName);
        }
        Faction faction = getFactionByName(factionName);
        if (faction != null) faction.addSpawner(entityType);
    }

    /**
     * Unregister the LAST spawner from a stack (stack count ≥ 1 → 0).
     * Called only when the physical block is actually destroyed (count == 1).
     * For stacks > 1, use {@link #popSpawnerFromStack} to peel one off.
     */
    public PlacedSpawnerRecord onSpawnerRemoved(String world, int x, int y, int z) {
        if (spawnerStackManager != null) {
            SpawnerStack stack = spawnerStackManager.getStack(world, x, y, z);
            long ts = spawnerStackManager.removeTop(world, x, y, z);
            if (stack != null) {
                Faction faction = getFactionByName(stack.getFactionName());
                if (faction != null) faction.removeSpawner(stack.getEntityTypeKey());
                // Return a synthetic record so ProtectionListener can show the value message
                return new PlacedSpawnerRecord(
                        SpawnerStackManager.locKey(world, x, y, z),
                        stack.getEntityTypeKey(), stack.getFactionName(),
                        ts > 0 ? ts : System.currentTimeMillis());
            }
            return null;
        }
        // Legacy path
        PlacedSpawnerRecord rec = spawnerTracker.removeSpawner(world, x, y, z);
        if (rec != null) {
            Faction faction = getFactionByName(rec.getFactionName());
            if (faction != null) faction.removeSpawner(rec.getEntityType());
        }
        return rec;
    }

    /**
     * Give the ENTIRE spawner stack to the player and remove the block tracking.
     * Used for silk-touch mining where the player gets all stacked spawners at once.
     *
     * @return number of spawners given (1 if no stack manager), or 0 on failure
     */
    public int popWholeSpawnerStack(String world, int x, int y, int z, Player player) {
        int count = 1;
        String entityTypeKey = null;

        if (spawnerStackManager != null) {
            SpawnerStack stack = spawnerStackManager.getStack(world, x, y, z);
            if (stack != null) {
                count = stack.getCount();
                entityTypeKey = stack.getEntityTypeKey();
                // Remove entire stack
                spawnerStackManager.removeStack(world, x, y, z);
                Faction faction = getFactionByName(stack.getFactionName());
                if (faction != null) {
                    for (int i = 0; i < count; i++) faction.removeSpawner(entityTypeKey);
                }
            }
        }

        if (entityTypeKey == null) {
            // Legacy: resolve from block state then clean up
            PlacedSpawnerRecord rec = onSpawnerRemoved(world, x, y, z);
            entityTypeKey = rec != null ? rec.getEntityType() : "pig";
            count = 1;
        }

        // Give all spawner items to player
        for (int i = 0; i < count; i++) {
            ItemStack spawnerItem = buildSpawnerItem(entityTypeKey);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(spawnerItem);
            leftover.forEach((idx, ls) -> player.getWorld().dropItemNaturally(player.getLocation(), ls));
        }

        SpawnerType st = SpawnerType.fromEntityKey(entityTypeKey);
        String displayName = st != null ? st.getDisplayName() : entityTypeKey;
        if (count > 1) {
            player.sendMessage(ChatColor.GREEN + "\u2713 Received §e" + count + "x §f" + displayName
                    + ChatColor.GREEN + " Spawners!");
        } else {
            player.sendMessage(ChatColor.YELLOW + "\u26a0 " + displayName + " Spawner removed.");
        }
        return count;
    }

    /**
     * If the block has a stack with count > 1: removes one spawner, gives the
     * player a matching spawner item, and informs them of the remaining count.
     *
     * @return {@code true} if one was popped (caller should cancel the break event)
     */
    public boolean popSpawnerFromStack(String world, int x, int y, int z, Player player) {
        if (spawnerStackManager == null) return false;
        SpawnerStack stack = spawnerStackManager.getStack(world, x, y, z);
        if (stack == null || stack.getCount() <= 1) return false;

        spawnerStackManager.removeTop(world, x, y, z);
        Faction faction = getFactionByName(stack.getFactionName());
        if (faction != null) faction.removeSpawner(stack.getEntityTypeKey());

        // Give one spawner item of the correct type to the player
        ItemStack spawnerItem = buildSpawnerItem(stack.getEntityTypeKey());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(spawnerItem);
        leftover.forEach((i, ls) -> player.getWorld().dropItemNaturally(player.getLocation(), ls));

        int remaining = stack.getCount();
        SpawnerType st = SpawnerType.fromEntityKey(stack.getEntityTypeKey());
        String name = st != null ? st.getDisplayName() : stack.getEntityTypeKey();
        player.sendMessage(ChatColor.YELLOW + "⚠ Removed 1 §e" + name
                + ChatColor.YELLOW + " Spawner from the stack."
                + ChatColor.GRAY + " (" + remaining + "/" + SpawnerStack.MAX_STACK + " remaining)");
        return true;
    }

    /** Build a spawner ItemStack tagged with the correct entity type. */
    public static ItemStack buildSpawnerItem(String entityTypeKey) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta raw = item.getItemMeta();
        if (raw instanceof BlockStateMeta bsm) {
            BlockState state = bsm.getBlockState();
            if (state instanceof CreatureSpawner cs) {
                try { cs.setSpawnedType(EntityType.valueOf(entityTypeKey.toUpperCase())); }
                catch (IllegalArgumentException ignored) {}
                bsm.setBlockState(cs);
            }
            SpawnerType st = SpawnerType.fromEntityKey(entityTypeKey);
            bsm.setDisplayName("\u00a76" + (st != null ? st.getDisplayName() : entityTypeKey) + " Spawner");
            bsm.setLore(List.of("\u00a77Place in faction territory to earn value / stack"));
            item.setItemMeta(bsm);
        }
        return item;
    }
    
    /* ================= DATA PERSISTENCE ================= */
    
    /**
     * Save all faction data to disk
     */
    public void saveData(File dataFolder) {
        File factionsFile = new File(dataFolder, "factions.yml");
        File playersFile = new File(dataFolder, "players.yml");
        
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            // Save factions
            YamlConfiguration factionsYaml = new YamlConfiguration();
            for (Map.Entry<String, Faction> entry : factions.entrySet()) {
                String key = entry.getKey();
                Faction faction = entry.getValue();
                ConfigurationSection factionSection = factionsYaml.createSection("factions." + key);
                
                factionSection.set("name", faction.getName());
                factionSection.set("description", faction.getDescription());
                factionSection.set("leader", faction.getLeader().toString());
                factionSection.set("power", faction.getPower());
                factionSection.set("balance", faction.getBalance());
                factionSection.set("tntBank", faction.getTntBank());
                
                // Save roles
                ConfigurationSection rolesSection = factionSection.createSection("roles");
                for (Map.Entry<UUID, Role> roleEntry : faction.roles.entrySet()) {
                    rolesSection.set(roleEntry.getKey().toString(), roleEntry.getValue().name());
                }
                
                // Save claims
                factionSection.set("claims", new ArrayList<>(faction.getClaims()));
                
                // Save home
                if (faction.hasHome()) {
                    Location home = faction.getHome();
                    ConfigurationSection homeSection = factionSection.createSection("home");
                    homeSection.set("world", home.getWorld().getName());
                    homeSection.set("x", home.getX());
                    homeSection.set("y", home.getY());
                    homeSection.set("z", home.getZ());
                    homeSection.set("yaw", home.getYaw());
                    homeSection.set("pitch", home.getPitch());
                }
                
                // Save allies and enemies
                factionSection.set("allies", new ArrayList<>(faction.getAllies()));
                factionSection.set("enemies", new ArrayList<>(faction.getEnemies()));
                
                // Save flags
                ConfigurationSection flagsSection = factionSection.createSection("flags");
                for (Map.Entry<ClaimFlag, Boolean> flagEntry : faction.getFlags().entrySet()) {
                    flagsSection.set(flagEntry.getKey().name(), flagEntry.getValue());
                }
                
                // Save spawners
                ConfigurationSection spawnersSection = factionSection.createSection("spawners");
                for (Map.Entry<String, Integer> spawnerEntry : faction.getSpawnersByType().entrySet()) {
                    spawnersSection.set(spawnerEntry.getKey(), spawnerEntry.getValue());
                }
                
                // Save warps
                ConfigurationSection warpsSection = factionSection.createSection("warps");
                for (Map.Entry<String, Location> warpEntry : faction.getWarps().entrySet()) {
                    ConfigurationSection warpLocSection = warpsSection.createSection(warpEntry.getKey());
                    Location warpLoc = warpEntry.getValue();
                    warpLocSection.set("world", warpLoc.getWorld().getName());
                    warpLocSection.set("x", warpLoc.getX());
                    warpLocSection.set("y", warpLoc.getY());
                    warpLocSection.set("z", warpLoc.getZ());
                    warpLocSection.set("yaw", warpLoc.getYaw());
                    warpLocSection.set("pitch", warpLoc.getPitch());
                }
                
                // Save player titles
                ConfigurationSection titlesSection = factionSection.createSection("titles");
                for (Map.Entry<UUID, String> titleEntry : faction.playerTitles.entrySet()) {
                    titlesSection.set(titleEntry.getKey().toString(), titleEntry.getValue());
                }
                
                // Save upgrades
                ConfigurationSection upgradesSection = factionSection.createSection("upgrades");
                upgradesSection.set("maxMembers", faction.upgradeMaxMembers);
                upgradesSection.set("spawnerMult", faction.upgradeSpawnerMult);
                upgradesSection.set("maxWarps", faction.upgradeMaxWarps);
                upgradesSection.set("chestSlots", faction.upgradeChestSlots);
                upgradesSection.set("tnt2Gunpowder", faction.upgradeTnt2Gunpowder);
                upgradesSection.set("ingot2Block", faction.upgradeIngot2Block);
                upgradesSection.set("successRate", faction.upgradeSuccessRate);

                // Save external access
                ConfigurationSection accessSection = factionSection.createSection("access");
                java.util.List<String> allClaims = faction.getPlayerAllClaimsAccess().stream().map(UUID::toString).toList();
                accessSection.set("allClaims", allClaims);

                ConfigurationSection permsSection = accessSection.createSection("permissions");
                for (Map.Entry<UUID, java.util.Set<ClaimAccessPermission>> permEntry : faction.getPlayerAccessPermissions().entrySet()) {
                    java.util.List<String> permNames = permEntry.getValue().stream().map(Enum::name).toList();
                    permsSection.set(permEntry.getKey().toString(), permNames);
                }

                ConfigurationSection chunkAccessSection = accessSection.createSection("chunks");
                for (Map.Entry<UUID, java.util.Set<String>> chunkEntry : faction.getPlayerChunkAccess().entrySet()) {
                    chunkAccessSection.set(chunkEntry.getKey().toString(), new java.util.ArrayList<>(chunkEntry.getValue()));
                }
                
                // Save chest inventory
                ItemStack[] contents = faction.getChest().getContents();
                List<Map<String, Object>> itemsList = new ArrayList<>();
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] != null) {
                        Map<String, Object> itemData = new HashMap<>();
                        itemData.put("slot", i);
                        itemData.put("item", contents[i].serialize());
                        itemsList.add(itemData);
                    }
                }
                factionSection.set("chest", itemsList);
            }
            
            factionsYaml.save(factionsFile);

            // Save spawner data (delegated to SpawnerStackManager when available)
            if (spawnerStackManager != null) {
                spawnerStackManager.saveData(dataFolder);
            } else {
                // Legacy fallback
                File spawnersFile = new File(dataFolder, "spawners.yml");
                YamlConfiguration spawnersYaml = new YamlConfiguration();
                List<Map<String, Object>> spawnerList = new ArrayList<>();
                for (PlacedSpawnerRecord rec : spawnerTracker.getAllRecords()) {
                    Map<String, Object> entry2 = new HashMap<>();
                    entry2.put("locationKey", rec.getLocationKey());
                    entry2.put("entityType",  rec.getEntityType());
                    entry2.put("factionName", rec.getFactionName());
                    entry2.put("placedAt",    rec.getPlacedAt());
                    spawnerList.add(entry2);
                }
                spawnersYaml.set("spawners", spawnerList);
                spawnersYaml.save(spawnersFile);
            }

            // Save player mappings
            YamlConfiguration playersYaml = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : playerFaction.entrySet()) {
                playersYaml.set(entry.getKey().toString(), entry.getValue());
            }
            playersYaml.save(playersFile);
            
            Bukkit.getLogger().info("[SimpleFactions] Saved " + factions.size() + " factions and "
                    + playerFaction.size() + " player mappings.");
            
        } catch (IOException e) {
            Bukkit.getLogger().severe("[SimpleFactions] Failed to save data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load all faction data from disk
     */
    public void loadData(File dataFolder) {
        File factionsFile = new File(dataFolder, "factions.yml");
        File playersFile = new File(dataFolder, "players.yml");
        
        if (!factionsFile.exists() || !playersFile.exists()) {
            Bukkit.getLogger().info("[SimpleFactions] No existing data found, starting fresh.");
            return;
        }
        
        try {
            // Load factions
            YamlConfiguration factionsYaml = YamlConfiguration.loadConfiguration(factionsFile);
            ConfigurationSection factionsSection = factionsYaml.getConfigurationSection("factions");
            
            if (factionsSection != null) {
                for (String key : factionsSection.getKeys(false)) {
                    ConfigurationSection factionSection = factionsSection.getConfigurationSection(key);
                    if (factionSection == null) continue;
                    
                    String name = factionSection.getString("name");
                    UUID leader = UUID.fromString(factionSection.getString("leader"));
                    
                    // Create faction
                    Faction faction = new Faction(name, leader);
                    faction.setDescription(factionSection.getString("description", "No description set."));
                    faction.setPower(factionSection.getDouble("power", 0.0));
                    faction.setBalance(factionSection.getDouble("balance", 0.0));
                    faction.setTntBank(factionSection.getInt("tntBank", 0));
                    
                    // Load roles (clear default leader role first)
                    faction.roles.clear();
                    ConfigurationSection rolesSection = factionSection.getConfigurationSection("roles");
                    if (rolesSection != null) {
                        for (String uuidStr : rolesSection.getKeys(false)) {
                            UUID uuid = UUID.fromString(uuidStr);
                            Role role = Role.valueOf(rolesSection.getString(uuidStr));
                            faction.roles.put(uuid, role);
                        }
                    }
                    
                    // Load claims
                    List<String> claimsList = factionSection.getStringList("claims");
                    faction.getClaims().addAll(claimsList);
                    for (String claimKey : claimsList) {
                        claims.put(claimKey, key);
                    }
                    
                    // Load home
                    ConfigurationSection homeSection = factionSection.getConfigurationSection("home");
                    if (homeSection != null) {
                        World world = Bukkit.getWorld(homeSection.getString("world"));
                        if (world != null) {
                            Location home = new Location(
                                world,
                                homeSection.getDouble("x"),
                                homeSection.getDouble("y"),
                                homeSection.getDouble("z"),
                                (float) homeSection.getDouble("yaw"),
                                (float) homeSection.getDouble("pitch")
                            );
                            faction.setHome(home);
                        }
                    }
                    
                    // Load allies and enemies
                    faction.getAllies().addAll(factionSection.getStringList("allies"));
                    faction.getEnemies().addAll(factionSection.getStringList("enemies"));
                    
                    // Load flags
                    ConfigurationSection flagsSection = factionSection.getConfigurationSection("flags");
                    if (flagsSection != null) {
                        for (String flagName : flagsSection.getKeys(false)) {
                            try {
                                ClaimFlag flag = ClaimFlag.valueOf(flagName);
                                faction.setFlag(flag, flagsSection.getBoolean(flagName));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    
                    // Load spawners
                    ConfigurationSection spawnersSection = factionSection.getConfigurationSection("spawners");
                    if (spawnersSection != null) {
                        for (String spawnerType : spawnersSection.getKeys(false)) {
                            faction.getSpawnersByType().put(spawnerType, spawnersSection.getInt(spawnerType));
                        }
                    }
                    
                    // Load warps
                    ConfigurationSection warpsSection = factionSection.getConfigurationSection("warps");
                    if (warpsSection != null) {
                        for (String warpName : warpsSection.getKeys(false)) {
                            ConfigurationSection warpLocSection = warpsSection.getConfigurationSection(warpName);
                            if (warpLocSection != null) {
                                World world = Bukkit.getWorld(warpLocSection.getString("world"));
                                if (world != null) {
                                    Location warpLoc = new Location(
                                        world,
                                        warpLocSection.getDouble("x"),
                                        warpLocSection.getDouble("y"),
                                        warpLocSection.getDouble("z"),
                                        (float) warpLocSection.getDouble("yaw"),
                                        (float) warpLocSection.getDouble("pitch")
                                    );
                                    faction.addWarp(warpName, warpLoc);
                                }
                            }
                        }
                    }
                    
                    // Load player titles
                    ConfigurationSection titlesSection = factionSection.getConfigurationSection("titles");
                    if (titlesSection != null) {
                        for (String uuidStr : titlesSection.getKeys(false)) {
                            UUID uuid = UUID.fromString(uuidStr);
                            faction.setPlayerTitle(uuid, titlesSection.getString(uuidStr));
                        }
                    }
                    
                    // Load upgrades
                    ConfigurationSection upgradesSection = factionSection.getConfigurationSection("upgrades");
                    if (upgradesSection != null) {
                        faction.upgradeMaxMembers = upgradesSection.getInt("maxMembers", 0);
                        faction.upgradeSpawnerMult = upgradesSection.getInt("spawnerMult", 0);
                        faction.upgradeMaxWarps = upgradesSection.getInt("maxWarps", 0);
                        faction.upgradeChestSlots = upgradesSection.getInt("chestSlots", 0);
                        faction.upgradeTnt2Gunpowder = upgradesSection.getInt("tnt2Gunpowder", 0);
                        faction.upgradeIngot2Block = upgradesSection.getInt("ingot2Block", 0);
                        faction.upgradeSuccessRate = upgradesSection.getInt("successRate", 0);
                    }

                    // Load external access
                    ConfigurationSection accessSection = factionSection.getConfigurationSection("access");
                    if (accessSection != null) {
                        for (String uuidStr : accessSection.getStringList("allClaims")) {
                            try {
                                faction.getPlayerAllClaimsAccess().add(UUID.fromString(uuidStr));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }

                        ConfigurationSection permsSection = accessSection.getConfigurationSection("permissions");
                        if (permsSection != null) {
                            for (String uuidStr : permsSection.getKeys(false)) {
                                try {
                                    UUID target = UUID.fromString(uuidStr);
                                    java.util.Set<ClaimAccessPermission> permSet = java.util.EnumSet.noneOf(ClaimAccessPermission.class);
                                    for (String permName : permsSection.getStringList(uuidStr)) {
                                        try {
                                            permSet.add(ClaimAccessPermission.valueOf(permName));
                                        } catch (IllegalArgumentException ignored) {
                                        }
                                    }
                                    if (!permSet.isEmpty()) {
                                        faction.getPlayerAccessPermissions().put(target, permSet);
                                    }
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                        }

                        ConfigurationSection chunksSection = accessSection.getConfigurationSection("chunks");
                        if (chunksSection != null) {
                            for (String uuidStr : chunksSection.getKeys(false)) {
                                try {
                                    UUID target = UUID.fromString(uuidStr);
                                    java.util.Set<String> chunkSet = new java.util.HashSet<>(chunksSection.getStringList(uuidStr));
                                    if (!chunkSet.isEmpty()) {
                                        faction.getPlayerChunkAccess().put(target, chunkSet);
                                    }
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                        }
                    }
                    
                    // Load chest inventory
                    List<?> chestList = factionSection.getList("chest");
                    if (chestList != null) {
                        for (Object obj : chestList) {
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> itemData = (Map<String, Object>) obj;
                                int slot = (Integer) itemData.get("slot");
                                @SuppressWarnings("unchecked")
                                Map<String, Object> itemMap = (Map<String, Object>) itemData.get("item");
                                ItemStack item = ItemStack.deserialize(itemMap);
                                faction.getChest().setItem(slot, item);
                            }
                        }
                    }
                    
                    factions.put(key, faction);
                }
            }
            
            // Load spawner data – when SpawnerStackManager is wired in, it handles its own
            // persistence (spawner-stacks.yml, with migration from spawners.yml).
            // The legacy spawnerTracker path is only used as a fallback.
            if (spawnerStackManager == null) {
                File spawnersFile = new File(dataFolder, "spawners.yml");
                if (spawnersFile.exists()) {
                    YamlConfiguration spawnersYaml = YamlConfiguration.loadConfiguration(spawnersFile);
                    List<?> spawnerList = spawnersYaml.getList("spawners");
                    if (spawnerList != null) {
                        for (Object obj : spawnerList) {
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> rec = (Map<String, Object>) obj;
                                String locationKey = (String) rec.get("locationKey");
                                String entityType  = (String) rec.get("entityType");
                                String factionName = (String) rec.get("factionName");
                                Object placedAtObj = rec.get("placedAt");
                                if (locationKey != null && entityType != null && factionName != null && placedAtObj != null) {
                                    long placedAt = ((Number) placedAtObj).longValue();
                                    String[] parts = locationKey.split(":");
                                    if (parts.length == 4) {
                                        try {
                                            String world2 = parts[0];
                                            int bx = Integer.parseInt(parts[1]);
                                            int by = Integer.parseInt(parts[2]);
                                            int bz = Integer.parseInt(parts[3]);
                                            spawnerTracker.addSpawner(world2, bx, by, bz, entityType, factionName, placedAt);
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                            }
                        }
                    }
                    Bukkit.getLogger().info("[SimpleFactions] Loaded " + spawnerTracker.getAllRecords().size() + " legacy spawner records.");
                }
            } else {
                Bukkit.getLogger().info("[SimpleFactions] Spawner data will be loaded by SpawnerStackManager.");
            }

            // Load player mappings
            YamlConfiguration playersYaml = YamlConfiguration.loadConfiguration(playersFile);
            for (String uuidStr : playersYaml.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String factionKey = playersYaml.getString(uuidStr);
                playerFaction.put(uuid, factionKey);
            }
            
            Bukkit.getLogger().info("[SimpleFactions] Loaded " + factions.size() + " factions and " + playerFaction.size() + " player mappings.");
            
        } catch (Exception e) {
            Bukkit.getLogger().severe("[SimpleFactions] Failed to load data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}