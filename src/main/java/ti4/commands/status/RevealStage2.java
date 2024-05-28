package ti4.commands.status;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealStage2 extends StatusSubcommandData {
    public RevealStage2() {
        super(Constants.REVEAL_STAGE2, "Reveal Stage2 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        revealS2(event, event.getChannel());
    }

    public void revealS2(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game game = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
        Map.Entry<String, Integer> objective = game.revealStage2();

        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 2 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
        if ("status".equalsIgnoreCase(game.getCurrentPhase())) {
            // first do cleanup if necessary
            int playersWithSCs = 0;
            for (Player player : game.getRealPlayers()) {
                if (player.getSCs() != null && player.getSCs().size() > 0 && !player.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }

            if (playersWithSCs > 0) {
                new Cleanup().runStatusCleanup(game);
                if (!game.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(channel,
                        ListPlayerInfoButton.representScoring(game, objective.getKey(), 0));
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + "Status Cleanup Run!");
                if (!game.isFoWMode()) {
                    DisplayType displayType = DisplayType.map;
                    MapGenerator.saveImage(game, displayType, event)
                        .thenAccept(fileUpload -> MessageHelper
                            .sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
                }
            }
        } else {
            if (!game.isFoWMode()) {
                MessageHelper.sendMessageToChannel(channel,
                    ListPlayerInfoButton.representScoring(game, objective.getKey(), 0));
            }
        }
    }

    public void revealTwoStage2(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game game = GameManager.getInstance().getUserActiveGame(event.getUser().getId());

        Map.Entry<String, Integer> objective1 = game.revealStage2();
        Map.Entry<String, Integer> objective2 = game.revealStage2();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 2 Public Objectives Revealed**");
        channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
            .queue(m -> m.pin().queue());

        int maxSCsPerPlayer;
        if (game.getRealPlayers().isEmpty()) {
            maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getPlayers().size());
        } else {
            maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
        }

        if (maxSCsPerPlayer == 0)
            maxSCsPerPlayer = 1;

        if (game.getRealPlayers().size() == 1) {
            maxSCsPerPlayer = 1;
        }
        game.setStrategyCardsPerPlayer(maxSCsPerPlayer);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(game, event);
    }
}
