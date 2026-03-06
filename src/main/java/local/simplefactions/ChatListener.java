package local.simplefactions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class ChatListener implements Listener {

    private final FactionManager manager;
    private final PlayerRankManager rankManager;
    private final ModerationManager moderationManager;

    public ChatListener(FactionManager manager, PlayerRankManager rankManager, ModerationManager moderationManager) {
        this.manager = manager;
        this.rankManager = rankManager;
        this.moderationManager = moderationManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (moderationManager.isMuted(playerUUID)) {
            event.setCancelled(true);
            long remaining = moderationManager.getRemainingMuteMillis(playerUUID);
            if (remaining == Long.MAX_VALUE) {
                player.sendMessage(Component.text("You are muted permanently.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(
                        "You are muted for another " + ModerationManager.formatDuration(remaining) + ".",
                        NamedTextColor.RED));
            }
            return;
        }
        
        FactionManager.Faction faction = manager.getFaction(playerUUID);
        if (faction == null) {
            return; // No faction - use default chat format
        }

        event.setCancelled(true);

        PlayerRank rank = rankManager.getRank(player);
        NamedTextColor rankNameColor = rank.getNamedColor();

        // Get player's faction role (for asterisks)
        Role role = faction.getRole(playerUUID);
        if (role == null) role = Role.MEMBER;
        String asterisks = switch (role) {
            case LEADER -> "***";
            case OFFICER -> "**";
            case MOD -> "*";
            case MEMBER -> "";
        };

        // Extract the message text
        String messageText = PlainTextComponentSerializer.plainText().serialize(event.message());

        Component formattedMessage;

        if (manager.isInFactionChat(playerUUID)) {
            // Faction chat: green title/name/text, only visible to same faction members
            String title = faction.getPlayerTitle(playerUUID);
            String cleanedTitle = title == null ? "" : title.trim();
                Component factionChatStars = asterisks.isEmpty()
                    ? Component.empty()
                    : Component.text(asterisks + " ", NamedTextColor.GREEN);
            
            formattedMessage = Component.empty()
                    .append(factionChatStars)
                    .append(cleanedTitle.isEmpty()
                            ? Component.empty()
                            : Component.text(cleanedTitle + " ", NamedTextColor.GREEN))
                    .append(Component.text(player.getName(), NamedTextColor.GREEN))
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(messageText, NamedTextColor.GREEN));

            for (var audience : event.viewers()) {
                if (audience instanceof Player other) {
                    FactionManager.Faction otherFaction = manager.getFaction(other.getUniqueId());
                    if (otherFaction != null && otherFaction.getName().equalsIgnoreCase(faction.getName())) {
                        other.sendMessage(formattedMessage);
                    }
                }
            }
        } else {
            // Public chat: ***FactionName PlayerName: message
            NamedTextColor messageColor;
            if (rank.isStaff()) {
                messageColor = rankNameColor;
            } else {
                messageColor = rank.getLevel() >= 3 ? NamedTextColor.WHITE : NamedTextColor.GRAY;
            }

            for (var audience : event.viewers()) {
                NamedTextColor relationColor = getRelationColor(playerUUID, faction, audience);
                Component relationStars = asterisks.isEmpty()
                        ? Component.empty()
                        : Component.text(asterisks + " ", relationColor);
                Component output = Component.empty()
                        .append(relationStars)
                        .append(Component.text(faction.getName(), relationColor))
                        .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(player.getName(), rankNameColor))
                        .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(messageText, messageColor));
                audience.sendMessage(output);
            }
        }
    }

    private NamedTextColor getRelationColor(UUID senderId, FactionManager.Faction senderFaction, net.kyori.adventure.audience.Audience viewer) {
        if (!(viewer instanceof Player playerViewer)) return NamedTextColor.WHITE;

        UUID viewerId = playerViewer.getUniqueId();
        if (viewerId.equals(senderId)) return NamedTextColor.BLUE; // truce/self

        FactionManager.Faction viewerFaction = manager.getFaction(viewerId);
        if (viewerFaction == null || senderFaction == null) return NamedTextColor.WHITE; // neutral

        if (viewerFaction.getName().equalsIgnoreCase(senderFaction.getName())) {
            return NamedTextColor.BLUE; // truce/self faction
        }

        if (viewerFaction.isEnemy(senderFaction.getName()) || senderFaction.isEnemy(viewerFaction.getName())) {
            return NamedTextColor.RED; // enemy
        }

        if (viewerFaction.isAlly(senderFaction.getName()) || senderFaction.isAlly(viewerFaction.getName())) {
            return NamedTextColor.LIGHT_PURPLE; // ally
        }

        return NamedTextColor.WHITE; // neutral
    }
}
