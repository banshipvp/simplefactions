package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages player ranks with:
 *  - LuckPerms as source of truth (primary)
 *  - Local YAML fallback / override file (ranks.yml)
 *  - XP-bottle cooldown tracking per player
 *  - Fly permission enforcement
 */
public class PlayerRankManager {

    private final JavaPlugin plugin;
    private final Logger log;

    /** Override map – only populated when a rank was explicitly set via /rank. */
    private final Map<UUID, PlayerRank> overrides = new HashMap<>();

    /** xpbottle last-use timestamps. */
    private final Map<UUID, Long> xpBottleCooldowns = new HashMap<>();

    public PlayerRankManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Rank resolution ───────────────────────────────────────────────────────

    /**
     * Returns the effective rank for a player.
     * Priority: local override → LuckPerms primary group → DEFAULT.
     */
    public PlayerRank getRank(UUID playerId) {
        // 1. Local override
        PlayerRank override = overrides.get(playerId);
        if (override != null) return override;

        // 2. LuckPerms
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            PlayerRank lp = getRankFromLuckPerms(p);
            if (lp != null) return lp;
        }

        return PlayerRank.DEFAULT;
    }

    public PlayerRank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    /** Returns rank from LuckPerms primary group, or null if unavailable. */
    private PlayerRank getRankFromLuckPerms(Player player) {
        try {
            var lp = net.luckperms.api.LuckPermsProvider.get();
            var user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return PlayerRank.fromGroupId(user.getPrimaryGroup());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ── Rank assignment ────────────────────────────────────────────────────────

    /**
     * Set a player's rank. Updates both the local override and their LuckPerms
     * primary group (if LuckPerms is available).
     */
    public void setRank(UUID playerId, PlayerRank rank) {
        overrides.put(playerId, rank);
        applyLuckPermsGroup(playerId, rank);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) applyFlyPermission(p, rank);
    }

    public void setRank(Player player, PlayerRank rank) {
        setRank(player.getUniqueId(), rank);
    }

    /** Applies fly permission based on rank. Called on login/rank change. */
    public void applyFlyPermission(Player player, PlayerRank rank) {
        boolean canFly = rank.canFly();
        if (!canFly && player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(canFly);
    }

    /** Updates LuckPerms primary group asynchronously, adding the new group and
     *  removing all other rank groups. */
    private void applyLuckPermsGroup(UUID playerId, PlayerRank rank) {
        try {
            var lp   = net.luckperms.api.LuckPermsProvider.get();
            var um   = lp.getUserManager();
            um.loadUser(playerId).thenAcceptAsync(user -> {
                if (user == null) return;
                var data = user.data();
                // Remove all rank group nodes first
                for (PlayerRank r : PlayerRank.values()) {
                    if (r == PlayerRank.DEFAULT) continue;
                    data.remove(net.luckperms.api.node.types.InheritanceNode.builder(r.getGroupId()).build());
                }
                // Add the new rank group
                if (rank != PlayerRank.DEFAULT) {
                    data.add(net.luckperms.api.node.types.InheritanceNode.builder(rank.getGroupId()).build());
                    user.setPrimaryGroup(rank.getGroupId());
                } else {
                    user.setPrimaryGroup("default");
                }
                um.saveUser(user);
            });
        } catch (Throwable ignored) {}
    }

    // ── Convenience helpers ────────────────────────────────────────────────────

    public boolean canFly(UUID playerId)              { return getRank(playerId).canFly(); }
    public String  getRankDisplayName(UUID playerId)  { return getRank(playerId).getDisplayName(); }
    public String  getRankChatColor(UUID playerId)    { return getRank(playerId).getChatColor(); }
    public int     getMaxHomes(UUID playerId)         { return getRank(playerId).getMaxHomes(); }
    public int     getMaxVaults(UUID playerId)        { return getRank(playerId).getMaxVaults(); }

    public boolean isDefaultRank(UUID playerId) {
        return getRank(playerId) == PlayerRank.DEFAULT;
    }

    // ── XP-bottle cooldown ─────────────────────────────────────────────────────

    /**
     * Checks whether the player may use /xpbottle right now.
     * @return true = allowed; false = still on cooldown.
     */
    public boolean checkXpBottleCooldown(Player player) {
        PlayerRank rank = getRank(player);
        if (!rank.hasXpExhaust()) return true;    // unlimited

        long now    = System.currentTimeMillis();
        Long lastUse = xpBottleCooldowns.get(player.getUniqueId());
        if (lastUse == null || now - lastUse >= rank.getXpExhaustMs()) {
            return true;
        }
        return false;
    }

    /** Records when the player last used /xpbottle. */
    public void recordXpBottleUse(UUID playerId) {
        xpBottleCooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * Returns remaining cooldown seconds, or 0 if not on cooldown.
     */
    public long getRemainingXpCooldownSeconds(Player player) {
        PlayerRank rank = getRank(player);
        if (!rank.hasXpExhaust()) return 0;
        Long lastUse = xpBottleCooldowns.get(player.getUniqueId());
        if (lastUse == null) return 0;
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = rank.getXpExhaustMs() - elapsed;
        return remaining > 0 ? remaining / 1000 : 0;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveData(File dataFolder) {
        File file = new File(dataFolder, "ranks.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerRank> entry : overrides.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue().getGroupId());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.severe("[PlayerRankManager] Failed to save ranks.yml: " + e.getMessage());
        }
    }

    public void loadData(File dataFolder) {
        File file = new File(dataFolder, "ranks.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String uuidStr : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String groupId = yaml.getString(uuidStr);
                PlayerRank rank = PlayerRank.fromGroupId(groupId);
                if (rank == PlayerRank.DEFAULT) {
                    rank = PlayerRank.fromLegacyName(groupId); // backward compat
                }
                overrides.put(uuid, rank);
            } catch (IllegalArgumentException ignored) {}
        }
        log.info("[PlayerRankManager] Loaded " + overrides.size() + " rank overrides.");
    }

    // ── Inner helper visible to the enum ─────────────────────────────────────

    /** Convenience: hasXpExhaust on PlayerRank */
    private static boolean hasXpExhaust(PlayerRank rank) {
        return rank.getXpExhaustMinutes() > 0;
    }
}

