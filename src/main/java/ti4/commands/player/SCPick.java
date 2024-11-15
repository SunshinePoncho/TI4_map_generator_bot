package ti4.commands.player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.game.StartPhase;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.status.ListTurnOrder;
import ti4.commands2.CommandHelper;
import ti4.generator.MapGenerator;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class SCPick extends PlayerSubcommandData {
    public SCPick() {
        super(Constants.SC_PICK, "Pick a Strategy Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC2, "2nd choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC3, "3rd"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC4, "4th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC5, "5th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC6, "6th"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        Collection<Player> activePlayers = game.getRealPlayers();
        if (activePlayers.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No active players found");
            return;
        }

        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer <= 0) maxSCsPerPlayer = 1;

        int playerSCCount = player.getSCs().size();
        if (playerSCCount >= maxSCsPerPlayer) {
            MessageHelper.sendMessageToEventChannel(event, "Player may not pick another strategy card. Max strategy cards per player for this game is " + maxSCsPerPlayer + ".");
            return;
        }

        int scPicked = event.getOption(Constants.STRATEGY_CARD, 0, OptionMapping::getAsInt);

        boolean pickSuccessful = attemptToPickSC(event, game, player, scPicked);
        Set<Integer> playerSCs = player.getSCs();

        // If FoW, try to use additional choices
        if (!pickSuccessful && game.isFowMode()) {
            String[] scs = { Constants.SC2, Constants.SC3, Constants.SC4, Constants.SC5, Constants.SC6 };
            int c = 0;
            while (playerSCs.isEmpty() && c < 5 && !pickSuccessful) {
                OptionMapping scOption = event.getOption(scs[c]);
                if (scOption != null) {
                    pickSuccessful = attemptToPickSC(event, game, player, scOption.getAsInt());
                }
                playerSCs = player.getSCs();
                c++;
            }
            if (!pickSuccessful) {
                return;
            }
        }
        //ONLY DEAL WITH EXTRA PICKS IF IN FoW
        if (playerSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card picked.");
            return;
        }
        doAdditionalStuffAfterPickingSC(event, player, game, scPicked);
    }

    @ButtonHandler("scPick_")
    public static void scPickUsingButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String num = buttonID.replace("scPick_", "");
        int scPick = Integer.parseInt(num);

        // Handle Public Disgrace - block the pick
        if (game.getStoredValue("Public Disgrace") != null
            && game.getStoredValue("Public Disgrace").contains("_" + scPick)
            && (game.getStoredValue("Public Disgrace Only").isEmpty() || game.getStoredValue("Public Disgrace Only").contains(player.getFaction()))) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (game.getStoredValue("Public Disgrace").contains(p2.getFaction())
                    && p2.getActionCards().containsKey("disgrace")) {
                    PlayAC.playAC(event, game, p2, "disgrace", game.getMainGameChannel());
                    game.setStoredValue("Public Disgrace", "");
                    Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();

                    String msg = player.getRepresentationUnfogged() +
                        "\n> Picked: " + Helper.getSCRepresentation(game, scPick);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation()
                            + " you have been Public Disgrace'd because someone preset it to occur when the number " + scPick
                            + " was chosen. If this is a mistake or the Public Disgrace is Sabo'd, feel free to pick the strategy card again. Otherwise, pick a different strategy card.");
                    return;
                }
            }
        }

        // Handle Deflection (Action Deck 2 Action Card id: deflection)
        if (game.getStoredValue("deflectedSC").equalsIgnoreCase(num)) {
            if (player.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " You cant pick this SC because it has the " + Emojis.ActionCard + " Deflection ability on it and you have no strat CC to spend");
                return;
            } else {
                player.setStrategicCC(player.getStrategicCC() - 1);
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " spent 1 strat CC due to deflection");
            }
        }

        if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
            SCPick.secondHalfOfSCPickWhenChecksNBalances(event, player, game, scPick);
        } else {
            boolean pickSuccessful = SCPick.attemptToPickSC(event, game, player, scPick);
            if (pickSuccessful) {
                SCPick.doAdditionalStuffAfterPickingSC(event, player, game, scPick);
                ButtonHelper.deleteMessage(event);
            }
        }
    }

    public static List<Button> getPlayerOptionsForChecksNBalances(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
        List<Button> buttons = new ArrayList<>();
        List<Player> activePlayers = game.getRealPlayers();

        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }
        int minNumOfSCs = 10;
        for (Player p2 : activePlayers) {
            if (p2.getSCs().size() < minNumOfSCs) {
                minNumOfSCs = p2.getSCs().size();
            }
        }
        if (minNumOfSCs == maxSCsPerPlayer) {
            return buttons;
        }
        for (Player p2 : activePlayers) {
            if (p2 == player) {
                continue;
            }
            if (p2.getSCs().size() < maxSCsPerPlayer) {
                if (game.isFowMode()) {
                    buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + p2.getFaction(), p2.getColor()));
                } else {
                    buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + p2.getFaction(), " ").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
            }
        }
        if (buttons.isEmpty()) {
            buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + player.getFaction(), " ").withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
        }

        return buttons;
    }

    public static void secondHalfOfSCPickWhenChecksNBalances(ButtonInteractionEvent event, Player player, Game game, int scPicked) {
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, game, scPicked);

        for (Player playerStats : game.getRealPlayers()) {
            if (playerStats.getSCs().contains(scPicked)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getSCName(scPicked, game) + " is already picked.");
                return;
            }
        }

        Integer tgCountOnSC = game.getScTradeGoods().get(scPicked);
        if (tgCountOnSC != null && tgCountOnSC != 0) {
            String gainTG = player.gainTG(tgCountOnSC);
            game.setScTradeGood(scPicked, 0);
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " gained " + Emojis.tg(tgCountOnSC) + " " + gainTG + " from picking " + Helper.getSCRepresentation(game, scPicked));
            if (game.isFowMode()) {
                String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " gained " + Emojis.tg(tgCountOnSC) + " " + gainTG + " from picking " + Helper.getSCRepresentation(game, scPicked);
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
            }
            CommanderUnlockCheck.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            if (scPicked == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCountOnSC; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " choose which player to give this strategy card to:", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("checksNBalancesPt2_")
    public static void resolvePt2ChecksNBalances(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String scPicked = buttonID.split("_")[1];
        int scpick = Integer.parseInt(scPicked);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(factionPicked);

        SCPick.attemptToPickSC(event, game, p2, scpick);

        String recipientMessage = p2.getRepresentationUnfogged() + " was given " + Helper.getSCName(scpick, game)
            + (!game.isFowMode() ? " by " + player.getFactionEmoji() : "");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), recipientMessage);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), p2.getColor() + " was given " + Helper.getSCName(scpick, game));

        }
        event.getMessage().delete().queue();
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, game, scpick);
        if (buttons.isEmpty()) {
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : game.getRealPlayers()) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
            game.incrementScTradeGoods();

            for (int sc : scPickedList) {
                game.setScTradeGood(sc, 0);
            }
            StartPhase.startActionPhase(event, game);
            game.setStoredValue("willRevolution", "");
        } else {
            boolean foundPlayer = false;
            Player privatePlayer = null;
            List<Player> players = game.getRealPlayers();
            if (game.isReverseSpeakerOrder() || !game.getStoredValue("willRevolution").isEmpty()) {
                Collections.reverse(players);
            }
            for (Player p3 : players) {
                if (p3.getFaction().equalsIgnoreCase(game.getStoredValue("politicalStabilityFaction"))) {
                    continue;
                }
                if (foundPlayer) {
                    privatePlayer = p3;
                    foundPlayer = false;
                }
                if (p3 == player) {
                    foundPlayer = true;
                }
            }
            if (privatePlayer == null) {
                privatePlayer = game.getRealPlayers().getFirst();
            }
            game.setPhaseOfGame("strategy");
            game.updateActivePlayer(privatePlayer);
            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                privatePlayer.getRepresentationUnfogged() + "Use buttons to pick which strategy card you want to give someone else.", Helper.getRemainingSCButtons(event, game, privatePlayer));
        }
    }

    public static boolean attemptToPickSC(GenericInteractionCreateEvent event, Game game, Player player, int scNumber) {
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Can only pick strategy card if both faction and color have been picked.");
            return false;
        }
        if (!scTradeGoods.containsKey(scNumber)) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Strategy Card must be from possible ones in Game: " + scTradeGoods.keySet());
            return false;
        }

        Map<String, Player> players = game.getPlayers();
        for (Player playerStats : players.values()) {
            if (playerStats.getSCs().contains(scNumber)) {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    Helper.getSCName(scNumber, game) + " is already picked.");
                return false;
            }
        }

        player.addSC(scNumber);
        if (game.isFowMode()) {
            String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " picked " + Helper.getSCName(scNumber, game);
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNumber).orElse(null);

        // WARNING IF PICKING TRADE WHEN PLAYER DOES NOT HAVE THEIR TRADE AGREEMENT
        if (scModel.usesAutomationForSCID("pok5trade") && !player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
            String message = player.getRepresentationUnfogged() + " heads up, you just picked Trade but don't currently hold your Trade Agreement";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged())
            .append("\n> Picked " + Helper.getSCRepresentation(game, scNumber));

        Integer tgCountOnSC = scTradeGoods.get(scNumber);
        if (tgCountOnSC == null || tgCountOnSC == 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        } else {
            String gainTG = player.gainTG(tgCountOnSC);
            sb.append(", gaining ").append(Emojis.tg(tgCountOnSC)).append(" ").append(gainTG);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());

            if (game.isFowMode()) {
                String fowMessage = player.getFactionEmojiOrColor() + " gained " + Emojis.tg(tgCountOnSC) + " " + gainTG + " from picking " + Helper.getSCRepresentation(game, scNumber);
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, fowMessage);
            }

            CommanderUnlockCheck.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            if (scNumber == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCountOnSC; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        return true;
    }

    public static void doAdditionalStuffAfterPickingSC(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        List<Player> activePlayers = game.getRealPlayers();
        if (game.isReverseSpeakerOrder() || !game.getStoredValue("willRevolution").isEmpty()) {
            Collections.reverse(activePlayers);
        }
        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }

        // Handle an Exhausted SC (some of Absol's stuff)
        if (!game.getStoredValue("exhaustedSC" + scPicked).isEmpty()) {
            game.setSCPlayed(scPicked, true);
        }

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += player_.getRepresentationUnfogged() + " to pick strategy card.";
                game.setPhaseOfGame("strategy");
                privatePlayer = player_;
                allPicked = false;
                break;
            }
            if (player_ == player) {
                nextCorrectPing = true;
            }
            if (player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                players.add(player_);
            }
        }

        //INFORM ALL PLAYER HAVE PICKED
        if (allPicked) {
            msgExtra += "\nAll players picked strategy cards.";
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : activePlayers) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
            game.incrementScTradeGoods();

            for (int sc : scPickedList) {
                game.setScTradeGood(sc, 0);
            }

            Player nextPlayer = null;
            int lowestSC = 100;
            for (Player player_ : activePlayers) {
                int playersLowestSC = player_.getLowestSC();
                String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player_, Integer.toString(playersLowestSC));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    nextPlayer = player_; //no further processing, this player has the 0 token
                    break;
                }
                if (playersLowestSC < lowestSC) {
                    lowestSC = playersLowestSC;
                    nextPlayer = player_;
                }
            }

            //INFORM FIRST PLAYER IS UP FOR ACTION
            if (nextPlayer != null) {
                msgExtra += " " + nextPlayer.getRepresentation() + " is up for an action";
                privatePlayer = nextPlayer;
                game.updateActivePlayer(nextPlayer);
                ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, game);
                ButtonHelperFactionSpecific.resolveKolleccAbilities(nextPlayer, game);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, nextPlayer, "started turn");
                }
                game.setStoredValue("willRevolution", "");
                game.setPhaseOfGame("action");
                if (!game.isFowMode()) {
                    ButtonHelper.updateMap(game, event,
                        "Start of Action Phase For Round #" + game.getRound());
                }
            }
        }

        // SEND EXTRA MESSAGE
        if (isFowPrivateGame) {
            sendFogOfWarExtraMessage(event, game, msgExtra, allPicked, privatePlayer);
        } else {
            sendExtraMessage(event, game, msgExtra, allPicked, privatePlayer);
        }

        // END STRAT PHASE REMINDERS
        if (allPicked) {
            sendEndOfStrategyPhaseReminders(event, game);
        }
    }

    private static void sendExtraMessage(GenericInteractionCreateEvent event, Game game, String msgExtra, boolean everyoneHasPickedSC, Player player) {
        if (everyoneHasPickedSC) {
            ListTurnOrder.turnOrder(event, game);
        }
        if (!msgExtra.isEmpty()) {
            if (!everyoneHasPickedSC) {
                game.updateActivePlayer(player);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra + "\nUse buttons to pick your strategy card.", Helper.getRemainingSCButtons(event, game, player));
                game.setPhaseOfGame("strategy");
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                player.setTurnCount(player.getTurnCount() + 1);
                if (game.isShowBanners()) {
                    MapGenerator.drawBanner(player);
                }
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "\n Use Buttons to do turn.",
                    TurnStart.getStartOfTurnButtons(player, game, false, event));
                if (player.getGenSynthesisInfantry() > 0) {
                    if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                            "Use buttons to revive infantry. You have " + player.getGenSynthesisInfantry() + " infantry left to revive.",
                            ButtonHelper.getPlaceStatusInfButtons(game, player));
                    } else {
                        player.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                    }
                }
                game.setPhaseOfGame("action");
            }
        }
    }

    private static void sendFogOfWarExtraMessage(GenericInteractionCreateEvent event, Game game, String msgExtra, boolean everyoneHasPickedSC, Player player) {
        if (everyoneHasPickedSC) {
            msgExtra = player.getRepresentationUnfogged() + " UP NEXT";
        }
        String fail = "User for next faction not found. Report to ADMIN";
        String success = "The next player has been notified";
        MessageHelper.sendPrivateMessageToPlayer(player, game, event, msgExtra, fail, success);
        game.updateActivePlayer(player);

        if (!everyoneHasPickedSC) {
            game.setPhaseOfGame("strategy");
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Use buttons to pick your strategy card.", Helper.getRemainingSCButtons(event, game, player));
        } else {
            player.setTurnCount(player.getTurnCount() + 1);
            if (game.isShowBanners()) {
                MapGenerator.drawBanner(player);
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.",
                TurnStart.getStartOfTurnButtons(player, game, false, event));
            if (player.getGenSynthesisInfantry() > 0) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, player).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + player.getGenSynthesisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(game, player));
                } else {
                    player.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                }
            }

        }
    }

    private static void sendEndOfStrategyPhaseReminders(GenericInteractionCreateEvent event, Game game) {
        for (Player player : game.getRealPlayers()) {
            offerHacanQDNButtons(player);
            offerImperialArbiterButtons(game, player);
            ButtonHelperActionCards.checkForAssigningCoup(game, player);
            playNaaluPNGiftIfPreset(event, game, player);
        }
    }

    private static void playNaaluPNGiftIfPreset(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game.getStoredValue("Play Naalu PN") != null &&
            game.getStoredValue("Play Naalu PN").contains(player.getFaction()) &&
            !player.getPromissoryNotesInPlayArea().contains("gift") &&
            player.getPromissoryNotes().containsKey("gift")) {

            PlayPN.resolvePNPlay("gift", player, game, event);
        }
    }

    private static void offerImperialArbiterButtons(Game game, Player player) {
        if (game.getLaws().containsKey("arbiter") && game.getLawsInfo().get("arbiter").equalsIgnoreCase(player.getFaction())) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startArbiter", "Use Imperial Arbiter", Emojis.Agenda));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you have the opportunity to use " + Emojis.Agenda + "Imperial Arbiter", buttons);
        }
    }

    private static void offerHacanQDNButtons(Player player) {
        if (player.hasTechReady("qdn") && player.getTg() > 2 && player.getStrategicCC() > 0) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startQDN", "Use QDN", Emojis.CyberneticTech));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            String message = player.getRepresentationUnfogged() + " you have the opportunity to use " + Emojis.CyberneticTech + "Quantum Datahub Node\n-# You have " + Emojis.tg(player.getTg()) + " and CCs are " + player.getCCRepresentation();
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
    }
}
