package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import net.kyori.adventure.text.Component;

import java.util.*;

public class FactionManager {

    public static class Faction {

        private final String name;
        private String description = "No description set.";
        private final UUID leader;
        private final Set<UUID> members = new HashSet<>();
        private final Set<String> claims = new HashSet<>();
        private final Inventory chest;

        public Faction(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
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
        public Set<UUID> getMembers() { return members; }
        public Set<String> getClaims() { return claims; }
        public Inventory getChest() { return chest; }

        public int maxClaims() {
    return 1000;
}
    }

    private final Map<String, Faction> factions = new HashMap<>();
    private final Map<UUID, String> playerFaction = new HashMap<>();
    private final Map<String, String> claims = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();

    /* ================= LOOKUPS ================= */

    public Faction getFaction(UUID player) {
        String name = playerFaction.get(player);
        return name == null ? null : factions.get(name);
    }

    public Faction getFactionByPlayer(org.bukkit.entity.Player player) {
        return getFaction(player.getUniqueId());
    }

    public Faction getFactionByChunk(String world, int x, int z) {
        String key = world + ":" + x + ":" + z;
        String factionName = claims.get(key);
        return factionName == null ? null : factions.get(factionName);
    }

    public boolean isLeader(org.bukkit.entity.Player player) {
        Faction faction = getFaction(player.getUniqueId());
        return faction != null && faction.getLeader().equals(player.getUniqueId());
    }

    /* ================= CREATE ================= */

    public boolean createFaction(String name, UUID owner) {
        if (factions.containsKey(name.toLowerCase())) return false;
        if (playerFaction.containsKey(owner)) return false;

        Faction faction = new Faction(name, owner);
        factions.put(name.toLowerCase(), faction);
        playerFaction.put(owner, name.toLowerCase());
        return true;
    }
public void unclaimAll(Faction faction) {

    for (String key : faction.getClaims()) {
        claims.remove(key);
    }

    faction.getClaims().clear();
}
    /* ================= INVITES ================= */

    public void invite(UUID inviter, UUID target) {
        String faction = playerFaction.get(inviter);
        if (faction != null) {
            invites.put(target, faction);
        }
    }

    public boolean join(UUID player) {
        String factionName = invites.remove(player);
        if (factionName == null) return false;

        Faction faction = factions.get(factionName);
        if (faction == null) return false;

        faction.getMembers().add(player);
        playerFaction.put(player, factionName);
        return true;
    }

    /* ================= LEAVE ================= */

    public boolean leave(UUID player) {

        Faction faction = getFaction(player);
        if (faction == null) return false;

        if (faction.getLeader().equals(player))
            return false; // leader cannot leave

        faction.getMembers().remove(player);
        playerFaction.remove(player);
        return true;
    }

    /* ================= DISBAND ================= */

    public boolean disband(UUID player) {

        Faction faction = getFaction(player);
        if (faction == null) return false;

        if (!faction.getLeader().equals(player))
            return false;

        factions.remove(faction.getName().toLowerCase());

        for (UUID member : faction.getMembers()) {
            playerFaction.remove(member);
        }

        // Remove claims
        claims.entrySet().removeIf(entry ->
                entry.getValue().equalsIgnoreCase(faction.getName()));

        return true;
    }

    /* ================= CLAIM ================= */

    public boolean claimChunk(Faction faction, String world, int x, int z) {

        String key = world + ":" + x + ":" + z;

        if (claims.containsKey(key)) return false;
        if (faction.getClaims().size() >= faction.maxClaims()) return false;

        claims.put(key, faction.getName().toLowerCase());
        faction.getClaims().add(key);
        return true;
    }

    public String getClaimOwner(String world, int x, int z) {
        return claims.get(world + ":" + x + ":" + z);
    }
    public boolean unclaimChunk(Faction faction, String world, int x, int z) {

    String key = world + ":" + x + ":" + z;

    if (!faction.getClaims().contains(key))
        return false;

    faction.getClaims().remove(key);
    claims.remove(key);

    return true;
}
}