package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class TechnoFactionsBridge implements PluginMessageListener {

    private static final String CH_QUERY       = "technofactions:claim_query";
    private static final String CH_SNAPSHOT    = "technofactions:claim_snapshot";
    private static final String CH_CLAIM_REQ   = "technofactions:claim_request";
    private static final String CH_UNCLAIM_REQ = "technofactions:unclaim_request";
    private static final String CH_CLAIM_RES   = "technofactions:claim_result";

    private final JavaPlugin plugin;
    private final FactionManager manager;

    public TechnoFactionsBridge(JavaPlugin plugin, FactionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CH_QUERY, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CH_CLAIM_REQ, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CH_UNCLAIM_REQ, this);

        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CH_SNAPSHOT);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CH_CLAIM_RES);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            if (channel.equals(CH_QUERY)) {
                handleQuery(player, message);
                return;
            }

            if (channel.equals(CH_CLAIM_REQ)) {
                handleRectangle(player, message, Mode.CLAIM_ONLY);
                return;
            }

            if (channel.equals(CH_UNCLAIM_REQ)) {
                handleRectangle(player, message, Mode.UNCLAIM_ONLY);
                return;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Bridge error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private enum Mode {
        CLAIM_ONLY,
        UNCLAIM_ONLY
    }

    // =========================
    // SNAPSHOT
    // =========================
    private void handleQuery(Player player, byte[] message) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
        int centerX = in.readInt();
        int centerZ = in.readInt();
        int radius  = in.readInt();

        World world = player.getWorld();
        FactionManager.Faction playerFaction = manager.getFaction(player.getUniqueId());

        int count = (radius * 2 + 1) * (radius * 2 + 1);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);

        out.writeInt(count);

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;

                FactionManager.Faction owner = manager.getFactionByChunk(world.getName(), cx, cz);

                byte type = 0; // 0 = wilderness, 1 = yours, 2 = other
                String name = "Wilderness";

                if (owner != null) {
                    name = owner.getName();
                    if (playerFaction != null && owner.getName().equalsIgnoreCase(playerFaction.getName())) {
                        type = 1;
                    } else {
                        type = 2;
                    }
                }

                out.writeInt(cx);
                out.writeInt(cz);
                out.writeByte(type);
                writeStringVar(out, name, 64);
            }
        }

        player.sendPluginMessage(plugin, CH_SNAPSHOT, bos.toByteArray());
    }

    // =========================
    // RECTANGLE CLAIM / UNCLAIM (NO TOGGLE)
    // =========================
    private void handleRectangle(Player player, byte[] message, Mode mode) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
        int x1 = in.readInt();
        int z1 = in.readInt();
        int x2 = in.readInt();
        int z2 = in.readInt();

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            sendResult(player, false, "You are not in a faction.");
            return;
        }
        if (!manager.isLeader(player)) {
            sendResult(player, false, "Only the leader can manage land.");
            return;
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        World world = player.getWorld();

        int claimed = 0;
        int unclaimed = 0;
        int blocked = 0;
        int unchanged = 0;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                FactionManager.Faction owner = manager.getFactionByChunk(world.getName(), cx, cz);

                if (mode == Mode.CLAIM_ONLY) {
                    // Only claim wilderness. Never unclaim here.
                    if (owner == null) {
                        if (manager.claimChunk(faction, world.getName(), cx, cz)) claimed++;
                        else unchanged++;
                    } else {
                        blocked++;
                    }
                } else {
                    // UNCLAIM_ONLY: only unclaim your own. Never claim wilderness here.
                    if (owner == null) {
                        unchanged++;
                    } else if (owner.getName().equalsIgnoreCase(faction.getName())) {
                        if (manager.unclaimChunk(faction, world.getName(), cx, cz)) unclaimed++;
                        else unchanged++;
                    } else {
                        blocked++;
                    }
                }
            }
        }

        if (claimed == 0 && unclaimed == 0) {
            String what = (mode == Mode.CLAIM_ONLY) ? "claim" : "unclaim";
            sendResult(player, false, "Nothing changed (" + what + "). Blocked: " + blocked + " | Unchanged: " + unchanged);
            return;
        }

        sendResult(
                player,
                true,
                "Claimed: " + claimed + " | Unclaimed: " + unclaimed + " | Blocked: " + blocked + " | Unchanged: " + unchanged
        );
    }

    // =========================
    // RESULT
    // =========================
    private void sendResult(Player player, boolean ok, String msg) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);

        out.writeBoolean(ok);
        writeStringVar(out, msg, 256);

        player.sendPluginMessage(plugin, CH_CLAIM_RES, bos.toByteArray());
    }

    // =========================
    // STRING + VARINT
    // =========================
    private static void writeStringVar(DataOutputStream out, String s, int maxChars) throws IOException {
        if (s == null) s = "";
        if (s.length() > maxChars) s = s.substring(0, maxChars);
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }
}