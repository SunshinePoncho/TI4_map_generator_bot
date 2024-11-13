package ti4.commands.milty;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class MiltyCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new DebugMilty(),
            new ForcePick(),
            new SetupMilty(),
            new StartMilty(),
            new ShowMilty()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.MILTY;
    }

    @Override
    public String getDescription() {
        return "Milty draft";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
