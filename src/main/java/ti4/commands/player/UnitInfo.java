package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitInfo extends PlayerSubcommandData {
    public UnitInfo() {
		super(Constants.UNIT_INFO, "Send unit information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendUnitInfo(activeGame, player, event);
    }

    public static void sendUnitInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendUnitInfo(activeGame, player);
    }

    public static void sendUnitInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                "__**Unit Info:**__",
                getUnitMessageEmbeds(player),
                getUnitInfoButtons());
    }

    private static List<Button> getUnitInfoButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_UNIT_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getUnitMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        for (UnitModel unitModel : player.getUnitsOwned().stream().sorted().map(Mapper::getUnit).toList()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getRepresentationEmbed(false);
            messageEmbeds.add(unitRepresentationEmbed);
        }
        return messageEmbeds;
    }

}
