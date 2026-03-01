package local.simplefactions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class FactionMapAutoListener implements Listener {

    private final FactionManager manager;
    private final FCommand command;

    public FactionMapAutoListener(FactionManager manager, FCommand command) {
        this.manager = manager;
        this.command = command;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getChunk().getX() == event.getTo().getChunk().getX()
                && event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!manager.isAutoMapEnabled(player.getUniqueId())) return;

        command.sendMap(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearAutoMap(event.getPlayer().getUniqueId());
    }
}
