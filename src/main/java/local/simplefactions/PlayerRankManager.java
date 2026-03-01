package local.simplefactions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerRankManager {
    private final Map<UUID, PlayerRank> playerRanks = new HashMap<>();

    public PlayerRank getRank(UUID player) {
        return playerRanks.getOrDefault(player, PlayerRank.MEMBER);
    }

    public void setRank(UUID player, PlayerRank rank) {
        playerRanks.put(player, rank);
    }

    public void setRankLevel(UUID player, int level) {
        playerRanks.put(player, PlayerRank.fromLevel(level));
    }

    public boolean canFly(UUID player) {
        return getRank(player).canFly();
    }

    public String getRankDisplayName(UUID player) {
        return getRank(player).getDisplayName();
    }

    public String getRankChatColor(UUID player) {
        return getRank(player).getChatColor();
    }
}
