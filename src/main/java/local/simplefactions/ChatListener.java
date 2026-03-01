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

    public ChatListener(FactionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        FactionManager.Faction faction = manager.getFaction(playerUUID);
        if (faction == null) {
            return; // No faction - use default chat format
        }

        // Get player's faction role (for asterisks)
        Role role = faction.getRole(playerUUID);
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
            // Faction chat: ***Title PlayerName: message
            String title = faction.getPlayerTitle(playerUUID);
            String displayName = title.isEmpty() ? player.getName() : title;
            
            formattedMessage = Component.empty()
                    .append(Component.text(asterisks))
                    .append(Component.text(displayName, NamedTextColor.GREEN))
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(player.getName(), NamedTextColor.GRAY))
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(messageText, NamedTextColor.WHITE));
        } else {
            // Public chat: ***FactionName PlayerName: message
            formattedMessage = Component.empty()
                    .append(Component.text(asterisks))
                    .append(Component.text(faction.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(player.getName(), NamedTextColor.GREEN))
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(messageText, NamedTextColor.WHITE));
        }

        event.message(formattedMessage);
    }
}
