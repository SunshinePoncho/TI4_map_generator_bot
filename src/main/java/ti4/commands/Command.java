package ti4.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.stream.Collectors;

public interface Command {
    String getActionID();

    //If command can be executed for given command text
    boolean accept(SlashCommandInteractionEvent event);

    //Command action execution method
    void execute(SlashCommandInteractionEvent event);

    void registerCommands(CommandListUpdateAction commands);

    default void postExecute(SlashCommandInteractionEvent event) {
        event.getHook().deleteOriginal().submit();
    }
}
