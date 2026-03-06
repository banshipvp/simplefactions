package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /claim — redeem the prize for placing in the top 3 of the last daily challenge.
 */
public class ClaimCommand implements CommandExecutor {

    private final ChallengeManager manager;

    public ClaimCommand(ChallengeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can claim challenge prizes.");
            return true;
        }

        long[] result = manager.tryClaim(player.getUniqueId());
        ChallengeManager.ClaimResult status = ChallengeManager.ClaimResult.values()[(int) result[0]];
        long prize = result[1];

        switch (status) {
            case NO_CHALLENGE ->
                player.sendMessage("§cThere is no completed challenge to claim from yet.");
            case NOT_PLACED ->
                player.sendMessage("§cYou didn't place in the top §e3 §cof the last challenge.");
            case ALREADY_CLAIMED ->
                player.sendMessage("§cYou have already claimed your prize for the last challenge.");
            case SUCCESS -> {
                player.sendMessage("§6✦ §ePrize claimed! §6$" + ChallengeManager.fmt(prize)
                        + " §ehas been added to your balance.");
                player.sendMessage("§7Last challenge: §f"
                        + (manager.getLastChallenge() != null
                            ? manager.getLastChallenge().displayName : "?"));
            }
        }
        return true;
    }
}
