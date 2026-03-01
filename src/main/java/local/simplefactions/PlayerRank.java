package local.simplefactions;

import java.util.UUID;

public enum PlayerRank {
    MEMBER(1, "§7Member", "§7"),
    VIP(2, "§bVIP", "§7"),
    PREMIUM(3, "§aPremium", "§7"),
    ELITE(4, "§6Elite", "§f"),
    LEGENDARY(5, "§5Legendary", "§f"),
    ADMIN(6, "§cAdmin", "§f");

    private final int level;
    private final String displayName;
    private final String chatColor;

    PlayerRank(int level, String displayName, String chatColor) {
        this.level = level;
        this.displayName = displayName;
        this.chatColor = chatColor;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getChatColor() {
        return chatColor;
    }

    public boolean canFly() {
        return level >= 4;
    }

    public static PlayerRank fromLevel(int level) {
        for (PlayerRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return MEMBER;
    }
}
