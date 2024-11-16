package ti4.commands.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player being replaced @playerName").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction being replaced").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Collection<Player> players = game.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }

        if (players.stream().noneMatch(player -> player.getUserID().equals(event.getUser().getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players or Bothelpers can replace a player.");
            return;
        }

        OptionMapping removedPlayerFactionOption = event.getOption(Constants.FACTION_COLOR);
        OptionMapping removedPlayerOption = event.getOption(Constants.PLAYER);
        if (removedPlayerFactionOption == null && removedPlayerOption == null) {
            MessageHelper.replyToMessage(event, "Specify player to remove.");
            return;
        }

        Player removedPlayer = CommandHelper.getPlayerFromEvent(game, event);
        if (removedPlayer == null) {
            if (removedPlayerOption != null) {
                MessageHelper.replyToMessage(event, "Could not find the specified player, try using the faction/color option.");
            } else {
                MessageHelper.replyToMessage(event, "Could not find the specified faction/color, try using the player option.");
            }
            return;
        }

        OptionMapping addedPlayerOption = event.getOption(Constants.PLAYER2);
        if (addedPlayerOption == null) {
            MessageHelper.replyToMessage(event, "Specify player to be replaced.");
            return;
        }

        User addedUser = addedPlayerOption.getAsUser();
        Player playerToAdd = game.getPlayer(addedUser.getId());
        if (playerToAdd != null && playerToAdd.getFaction() != null) {
            MessageHelper.replyToMessage(event, "Specify player that is **__not__** in the game to be the replacement");
            return;
        } else if (playerToAdd != null) {
            game.removePlayer(addedUser.getId()); // spectators or others
        }

        Guild guild = game.getGuild();
        Member addedMember = guild.getMemberById(addedUser.getId());
        if (addedMember == null) {
            MessageHelper.replyToMessage(event, "Added player must be on the game's server.");
            return;
        }

        //REMOVE ROLE
        Member removedMember = guild.getMemberById(removedPlayer.getUserID());
        List<Role> roles = guild.getRolesByName(game.getName(), true);
        if (removedMember != null && roles.size() == 1) {
            guild.removeRoleFromMember(removedMember, roles.getFirst()).queue();
        }

        //ADD ROLE
        if (roles.size() == 1) {
            guild.addRoleToMember(addedMember, roles.getFirst()).queue();
        }

        String message = "Game: " + game.getName() + "  Player: " + removedPlayer.getUserName() + " replaced by player: " + addedUser.getName();
        boolean speaker = removedPlayer.isSpeaker();
        Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
        for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
            List<String> value = poEntry.getValue();
            boolean removed = value.remove(removedPlayer.getUserID());
            if (removed) {
                value.add(addedUser.getId());
            }
        }

        removedPlayer.setUserName(addedUser.getName());
        removedPlayer.setUserID(addedUser.getId());
        removedPlayer.setTotalTurnTime(0);
        removedPlayer.setNumberTurns(0);
        if (removedPlayer.getUserID().equals(game.getSpeakerUserID())) {
            game.setSpeakerUserID(addedUser.getId());
        }
        if (removedPlayer.getUserID().equals(game.getActivePlayerID())) {
            // do not update stats for this action
            game.setActivePlayerID(addedUser.getId());
        }

        Helper.fixGameChannelPermissions(event.getGuild(), game);
        ThreadChannel mapThread = game.getBotMapUpdatesThread();
        if (mapThread != null && !mapThread.isLocked()) {
            mapThread.getManager().setArchived(false).queue(success -> mapThread.addThreadMember(addedMember).queueAfter(5, TimeUnit.SECONDS), BotLogger::catchRestError);
        }

        String removedPlayerID = removedPlayer.getUserID();
        game.getMiltyDraftManager().replacePlayer(game, removedPlayerID, removedPlayer.getUserID());

        if (speaker) {
            game.setSpeakerUserID(removedPlayer.getUserID());
        }
        GameSaveLoadManager.saveGame(game, event);
        // Load the new game instance so that we can repost the milty draft
        game = GameSaveLoadManager.reload(game.getName());
        if (game.getMiltyDraftManager().getDraftIndex() < game.getMiltyDraftManager().getDraftOrder().size()) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        }

        if (FoWHelper.isPrivateGame(game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
        }

    }
}
