package local.simplefactions;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationManager {

    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public void mute(UUID playerId) {
        mutedUntil.put(playerId, Long.MAX_VALUE);
    }

    public void tempMute(UUID playerId, long durationMs) {
        mutedUntil.put(playerId, System.currentTimeMillis() + Math.max(1L, durationMs));
    }

    public void unmute(UUID playerId) {
        mutedUntil.remove(playerId);
    }

    public boolean isMuted(UUID playerId) {
        Long until = mutedUntil.get(playerId);
        if (until == null) return false;
        if (until == Long.MAX_VALUE) return true;
        if (System.currentTimeMillis() <= until) return true;
        mutedUntil.remove(playerId);
        return false;
    }

    public long getRemainingMuteMillis(UUID playerId) {
        Long until = mutedUntil.get(playerId);
        if (until == null) return 0L;
        if (until == Long.MAX_VALUE) return Long.MAX_VALUE;
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            mutedUntil.remove(playerId);
            return 0L;
        }
        return remaining;
    }

    public boolean toggleFreeze(UUID playerId) {
        if (frozenPlayers.contains(playerId)) {
            frozenPlayers.remove(playerId);
            return false;
        }
        frozenPlayers.add(playerId);
        return true;
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    public boolean toggleVanish(Player player) {
        UUID playerId = player.getUniqueId();
        boolean vanishNow;
        if (vanishedPlayers.contains(playerId)) {
            vanishedPlayers.remove(playerId);
            vanishNow = false;
        } else {
            vanishedPlayers.add(playerId);
            vanishNow = true;
        }

        if (vanishNow) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(playerId)) continue;
                if (!online.hasPermission("simplefactions.staff.vanish.see")) {
                    online.hidePlayer(SimpleFactionsPlugin.getInstance(), player);
                }
            }
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(playerId)) continue;
                online.showPlayer(SimpleFactionsPlugin.getInstance(), player);
            }
        }
        return vanishNow;
    }

    public void applyVanishVisibilityForViewer(Player viewer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isVanished(online.getUniqueId())) continue;
            if (viewer.getUniqueId().equals(online.getUniqueId())) continue;

            if (viewer.hasPermission("simplefactions.staff.vanish.see")) {
                viewer.showPlayer(SimpleFactionsPlugin.getInstance(), online);
            } else {
                viewer.hidePlayer(SimpleFactionsPlugin.getInstance(), online);
            }
        }
    }

    public boolean isVanished(UUID playerId) {
        return vanishedPlayers.contains(playerId);
    }

    public void tempBan(String playerName, long durationMs, String reason, String source) {
        Date expires = new Date(System.currentTimeMillis() + Math.max(1L, durationMs));
        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expires, source);
    }

    public void ban(String playerName, String reason, String source) {
        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, null, source);
    }

    public void unban(String playerName) {
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
    }

    public static String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) return "permanent";
        if (millis <= 0L) return "0s";

        long seconds = millis / 1000L;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public static Long parseDurationMillis(String token) {
        if (token == null || token.isBlank()) return null;
        String value = token.trim().toLowerCase();

        try {
            if (value.endsWith("d")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 24L * 60L * 60L * 1000L;
            }
            if (value.endsWith("h")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 60L * 60L * 1000L;
            }
            if (value.endsWith("m")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 60L * 1000L;
            }
            if (value.endsWith("s")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1000L;
            }
            return Long.parseLong(value) * 60L * 1000L;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
