/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

    This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
 */

package irccasino.blackjack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;
import irccasino.*;
import irccasino.cardgame.Card;
import irccasino.cardgame.CardDeck;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import irccasino.cardgame.PlayerRecord;

/**
 * Class for IRC Blackjack.
 * @author Yizhe Shen
 */
public class Blackjack extends CardGame {
    
    public enum BlackjackState {
        NONE, PRE_START, BETTING, PLAYING, CONTINUE_ROUND, END_ROUND
    }
    
    protected BlackjackPlayer dealer;
    protected ArrayList<HouseStat> houseStatsList;
    protected IdleShuffleTask idleShuffleTask;
    protected HouseStat house;
    // In-game properties
    protected BlackjackState state;
    protected boolean insuranceBets;

    public Blackjack() {
        super();
    }
    
    /**
     * The default constructor for Blackjack, subclass of CardGame.
     * This constructor loads the default INI file.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public Blackjack(GameManager parent, char commChar, Channel gameChannel) {
        this(parent, commChar, gameChannel, "blackjack.ini");
    }
    
    /**
     * Allows a custom INI file to be loaded.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run
     * @param customINI the file path to a custom INI file
     */
    public Blackjack(GameManager parent, char commChar, Channel gameChannel, String customINI) {
        super(parent, commChar, gameChannel, customINI);
    }

    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        // Commands available in Blackjack.
        if (command.equalsIgnoreCase("join") || command.equalsIgnoreCase("j")){
            join(nick, host);
        } else if (command.equalsIgnoreCase("leave") || command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("l") || command.equalsIgnoreCase("q")){
            leave(nick, params);
        } else if (command.equalsIgnoreCase("last")) {
            last(nick, params);
        } else if (command.equalsIgnoreCase("start") || command.equalsIgnoreCase("go")){
            start(nick, params);
        } else if (command.equalsIgnoreCase("stop")) {
            stop(nick, params);
        } else if (command.equalsIgnoreCase("bet") || command.equalsIgnoreCase("b")) {
            bet(nick, params);
        } else if (command.equalsIgnoreCase("allin") || command.equalsIgnoreCase("a")){
            allin(nick, params);
        } else if (command.equalsIgnoreCase("hit") || command.equalsIgnoreCase("h")) {
            hit(nick, params);
        } else if (command.equalsIgnoreCase("stand") || command.equalsIgnoreCase("stay") || command.equalsIgnoreCase("sit")) {
            stand(nick, params);
        } else if (command.equalsIgnoreCase("doubledown") || command.equalsIgnoreCase("dd")) {
            doubledown(nick, params);
        } else if (command.equalsIgnoreCase("surrender") || command.equalsIgnoreCase("surr")) {
            surrender(nick, params);
        } else if (command.equalsIgnoreCase("insure")) {
            insure(nick, params);
        } else if (command.equalsIgnoreCase("split")) {
            split(nick, params);
        } else if (command.equalsIgnoreCase("table")) {
            table(nick, params);
        } else if (command.equalsIgnoreCase("sum")) {
            sum(nick, params);
        } else if (command.equalsIgnoreCase("hand")) {
            hand(nick, params);
        } else if (command.equalsIgnoreCase("allhands")) {
            allhands(nick, params);
        } else if (command.equalsIgnoreCase("turn")) {
            turn(nick, params);
        } else if (command.equalsIgnoreCase("zc") || (command.equalsIgnoreCase("zen"))) {
            zen(nick, params);
        } else if (command.equalsIgnoreCase("hc") || (command.equalsIgnoreCase("hilo"))) {
            hilo(nick, params);
        } else if (command.equalsIgnoreCase("rc") || (command.equalsIgnoreCase("red7"))) {
            red7(nick, params);
        } else if (command.equalsIgnoreCase("count") || command.equalsIgnoreCase("c")){
            count(nick, params);
        } else if (command.equalsIgnoreCase("numcards") || command.equalsIgnoreCase("ncards")) {
            numcards(nick, params);
        } else if (command.equalsIgnoreCase("numdiscards") || command.equalsIgnoreCase("ndiscards")) {
            numdiscards(nick, params);
        } else if (command.equalsIgnoreCase("numdecks") || command.equalsIgnoreCase("ndecks")) {
            numdecks(nick, params);
        } else if (command.equalsIgnoreCase("players")) {
            players(nick, params);
        } else if (command.equalsIgnoreCase("house")) {
            house(nick, params);
        } else if (command.equalsIgnoreCase("cash") || command.equalsIgnoreCase("stack")) {
            cash(nick, params);
        } else if (command.equalsIgnoreCase("netcash") || command.equalsIgnoreCase("net")) {
            netcash(nick, params);
        } else if (command.equalsIgnoreCase("bank")) {
            bank(nick, params);
        } else if (command.equalsIgnoreCase("bankrupts")) {
            bankrupts(nick, params);
        } else if (command.equalsIgnoreCase("winnings")) {
            winnings(nick, params);
        } else if (command.equalsIgnoreCase("winrate")) {
            winrate(nick, params);
        } else if (command.equalsIgnoreCase("rounds")) {
            rounds(nick, params);
        } else if (command.equalsIgnoreCase("player") || command.equalsIgnoreCase("p")){
            player(nick, params);
        } else if (command.equalsIgnoreCase("deposit")) {
            deposit(nick, params);
        } else if (command.equalsIgnoreCase("withdraw")) {
            withdraw(nick, params);
        } else if (command.equalsIgnoreCase("waitlist")) {
            waitlist(nick, params);
        } else if (command.equalsIgnoreCase("blacklist")) {
            blacklist(nick, params);
        } else if (command.equalsIgnoreCase("rank")) {
            rank(nick, params);
        } else if (command.equalsIgnoreCase("top")) {
            top(nick, params);
        } else if (command.equalsIgnoreCase("away")){
            away(nick, params);
        } else if (command.equalsIgnoreCase("back")){
            back(nick, params);
        } else if (command.equalsIgnoreCase("ping")) {
            ping(nick, params);
        } else if (command.equalsIgnoreCase("simple")) {
            simple(nick, params);
        } else if (command.equalsIgnoreCase("stats")){
            stats(nick, params);
        } else if (command.equalsIgnoreCase("grules") || command.equalsIgnoreCase("gamerules")) {
            grules(nick, params);
        } else if (command.equalsIgnoreCase("ghelp") || command.equalsIgnoreCase("gamehelp")) {
            ghelp(nick, params);
        } else if (command.equalsIgnoreCase("gcommands") || command.equalsIgnoreCase("gamecommands")) {
            gcommands(user, nick, params);
        } else if (command.equalsIgnoreCase("game")) {
            game(nick, params);
        /* Op commands */
            } else if (command.equalsIgnoreCase("fj") || command.equalsIgnoreCase("fjoin")){
            fjoin(user, nick, params);
        } else if (command.equalsIgnoreCase("fl") || command.equalsIgnoreCase("fq") || command.equalsIgnoreCase("fquit") || command.equalsIgnoreCase("fleave")){
            fleave(user, nick, params);
        } else if (command.equalsIgnoreCase("flast")) {
            flast(user, nick, params);
        } else if (command.equalsIgnoreCase("fstart") || command.equalsIgnoreCase("fgo")){
            fstart(user, nick, params);
        } else if (command.equalsIgnoreCase("fstop")){
            fstop(user, nick, params);
        } else if (command.equalsIgnoreCase("fb") || command.equalsIgnoreCase("fbet")){
            fbet(user, nick, params);
        } else if (command.equalsIgnoreCase("fallin") || command.equalsIgnoreCase("fa")){
            fallin(user, nick, params);
        } else if (command.equalsIgnoreCase("fhit") || command.equalsIgnoreCase("fh")) {
            fhit(user, nick, params);
        } else if (command.equalsIgnoreCase("fstay") || command.equalsIgnoreCase("fstand") || command.equalsIgnoreCase("fsit")) {
            fstand(user, nick, params);
        } else if (command.equalsIgnoreCase("fdoubledown") || command.equalsIgnoreCase("fdd")) {
            fdoubledown(user, nick, params);
        } else if (command.equalsIgnoreCase("fsurrender") || command.equalsIgnoreCase("fsurr")) {
            fsurrender(user, nick, params);
        } else if (command.equalsIgnoreCase("fsplit")) {
            fsplit(user, nick, params);
        } else if (command.equalsIgnoreCase("finsure")) {
            finsure(user, nick, params);
        } else if (command.equalsIgnoreCase("fdeposit")) {
            fdeposit(user, nick, params);
        } else if (command.equalsIgnoreCase("fwithdraw")) {
            fwithdraw(user, nick, params);
        } else if (command.equalsIgnoreCase("shuffle")){
            shuffle(user, nick, params);
        } else if (command.equalsIgnoreCase("reload")){
            reload(user, nick, params);
        } else if (command.equalsIgnoreCase("cards")) {
            cards(user, nick, params);
        } else if (command.equalsIgnoreCase("discards")) {
            discards(user, nick, params);
        } else if (command.equalsIgnoreCase("settings")) {
            settings(user, nick, params);
        } else if (command.equalsIgnoreCase("set")){
            set(user, nick, params);
        } else if (command.equalsIgnoreCase("get")) {
            get(user, nick, params);
        } else if (command.equalsIgnoreCase("resetaway")){
            resetaway(user, nick, params);
        } else if (command.equalsIgnoreCase("resetsimple")) {
            resetsimple(user, nick, params);
        } else if (command.equalsIgnoreCase("trim")) {
            trim(user, nick, params);
        } else if (command.equalsIgnoreCase("test1")){
            test1(user, nick, params);
        }
    }

    ///////////////////////////////////////
    //// Command methods for Blackjack ////
    ///////////////////////////////////////
    
    /**
     * Starts a new round.
     * @param nick
     * @param params 
     */
    protected void start(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 1) {
            showMsg(getMsg("no_players"));
        } else if (startCount > 0) {
            informPlayer(nick, getMsg("no_manual_start"));
        } else {
            if (params.length > 0){
                try {
                    startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                } catch (NumberFormatException e) {
                    // Do nothing and proceed
                }
            }
            cancelIdleShuffleTask();
            state = BlackjackState.PRE_START;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Makes a bet for the current player.
     * @param nick
     * @param params 
     */
    protected void bet(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (currentPlayer != findJoined(nick)) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                bet(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Makes the current player go all in.
     * @param nick
     * @param params 
     */
    protected void allin(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (currentPlayer != findJoined(nick)) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.get("cash"));
        }
    }
    
    /**
     * Hits the current player with a card.
     * @param nick
     * @param params 
     */
    protected void hit(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            hit();
        }
    }
    
    /**
     * Lets the current player stand.
     * @param nick
     * @param params 
     */
    protected void stand(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            stay();
        }
    }
    
    /**
     * Lets the current player double down.
     * @param nick
     * @param params 
     */
    protected void doubledown(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            doubleDown();
        }
    }
    
    /**
     * Lets the current player surrender his hand.
     * @param nick
     * @param params 
     */
    protected void surrender(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            surrender();
        }
    }
    
    /**
     * Lets the current player insure his hand.
     * @param nick
     * @param params 
     */
    protected void insure(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                insure(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Lets the current player split his hand.
     * @param nick
     * @param params 
     */
    protected void split(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            split();
        }
    }
    
    /**
     * Displays all dealt hands.
     * @param nick
     * @param params 
     */
    protected void table(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            showTableHands(false);
        }
    }
    
    /**
     * Informs a player the sum of his current hand.
     * @param nick
     * @param params 
     */
    protected void sum(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            informPlayer(p.getNick(), getMsg("bj_hand_sum"), p.getHand().calcSum());
        }
    }
    
    /**
     * Informs a player of his hand.
     * @param nick
     * @param params 
     */
    protected void hand(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            informPlayer(p.getNick(), getMsg("bj_hand"), p.getHand(), p.getHand().getBet());
        }
    }
    
    /**
     * Informs a player of all his hands.
     * @param nick
     * @param params 
     */
    protected void allhands(String nick, String[] params) {
        informPlayer(nick, "This command is not implemented.");
    }
    
    /**
     * Displays who the current player is.
     * @param nick
     * @param params 
     */
    protected void turn(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else {
            BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
            if (p.hasSplit()){
                showTurn(p, p.get("currentindex") + 1);
            } else {
                showTurn(p, 0);
            }
        }
    }
    
    /**
     * Displays the current zen count.
     * @param nick
     * @param params 
     */
    protected void zen(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_zen"), getZen());
        }
    }
    
    /**
     * Displays the current hi-lo count.
     * @param nick
     * @param params 
     */
    protected void hilo(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_hilo"), getHiLo());
        }
    }
    
    /**
     * Displays the current red7 count.
     * @param nick
     * @param params 
     */
    protected void red7(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_red7"), getRed7());
        }
    }
    
    /**
     * Displays the current value of all counting methods.
     * @param nick
     * @param params 
     */
    protected void count(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_count"), deck.getNumberCards(), getHiLo(), getRed7(), getZen());
        }
    }
    
    /**
     * Displays the current number of cards in the dealer's shoe.
     * @param nick
     * @param params 
     */
    protected void numcards(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            showMsg(getMsg("bj_num_cards"), deck.getNumberCards());
        }
    }
    
    /**
     * Displays the current number of cards in the discard pile.
     * @param nick
     * @param params 
     */
    protected void numdiscards(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            showMsg(getMsg("num_discards"), deck.getNumberDiscards());
        }
    }
    
    /**
     * Displays the number of decks in the dealer's shoe.
     * @param nick
     * @param params 
     */
    protected void numdecks(String nick, String[] params) {
        showMsg(getMsg("num_decks"), getGameNameStr(), deck.getNumberDecks());
    }
    
    /**
     * Displays the players in the game.
     * @param nick
     * @param params 
     */
    protected void players(String nick, String[] params) {
        showMsg(getMsg("players"), getPlayerListString(joined));
    }
    
    /**
     * Displays the stats for the house.
     * @param nick
     * @param params 
     */
    protected void house(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            showHouseStat(get("decks"));
        } else {
            try {
                showHouseStat(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Attempts to force start a round.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstart(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 1) {
            showMsg(getMsg("no_players"));
        } else {
            if (params.length > 0){
                try {
                    startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                } catch (NumberFormatException e) {
                    // Do nothing and proceed
                }
            }
            cancelIdleShuffleTask();
            state = BlackjackState.PRE_START;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Forces a round to end. Use only as last resort. Data will be lost.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstop(User user, String nick, String[] params) {
        if (!channel.isOp(user)){
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()){
            informPlayer(nick, getMsg("no_start"));
        } else {
            cancelStartRoundTask();
            cancelIdleOutTask();
            for (Player p : joined) {
                resetPlayer(p);
            }
            resetGame();
            startCount = 0;
            showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
            setIdleShuffleTask();
            state = BlackjackState.NONE;
        }
    }
    
    /**
     * Forces the current player to bet the specified amount.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fbet(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                bet(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Forces the current player to go all in.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fallin(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else {
            bet(currentPlayer.get("cash"));
        }
    }
    
    /**
     * Forces the current player to hit.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fhit(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            hit();
        }
    }
    
    /**
     * Forces the current player to stand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstand(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            stay();
        }
    }
    
    /**
     * Forces the current player to double down.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fdoubledown(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            doubleDown();
        }
    }
    
    /**
     * Forces the current player to surrender.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fsurrender(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            surrender();
        }
    }
    
    /**
     * Forces the current player to split his hand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fsplit(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            split();
        }
    }
    
    /**
     * Forces the current player to insure his hand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void finsure(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                insure(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Merges discards and shuffles the dealer's shoe.
     * @param user
     * @param nick
     * @param params 
     */
    protected void shuffle(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            cancelIdleShuffleTask();
            shuffleShoe();
        }
    }
    
    /**
     * Reloads game settings and library files.
     * @param user
     * @param nick
     * @param params 
     */
    protected void reload(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            cancelIdleShuffleTask();
            awayList.clear();
            notSimpleList.clear();
            cmdMap.clear();
            opCmdMap.clear();
            aliasMap.clear();
            msgMap.clear();
            loadIni();
            loadHostList("away.txt", awayList);
            loadHostList("simple.txt", notSimpleList);
            loadStrLib(strFile);
            loadHelp(helpFile);
            showMsg(getMsg("reload"));
        }
    }
    
    /**
     * Performs test 1. Tests the dealer playing algorithm and underlying 
     * calculations.
     * @param user
     * @param nick
     * @param params 
     */
    protected void test1(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            String outStr; 
            BlackjackHand h;
            showMsg("Dealing cards to Dealer...");
            // Deal cards to the dealer
            dealHand(dealer);
            h = dealer.getHand();
            showPlayerHand(dealer, h, 0, true);
            // Deal more cards if necessary
            while (h.calcSum() < 17 || (h.isSoft17() && has("soft17hit"))) {
                dealCard(h);
                showPlayerHand(dealer, h, 0, true);
            }
            // Output result
            if (h.isBlackjack()) {
                outStr = dealer.getNickStr() + " has blackjack (" + h.toString() + ").";
            } else {
                outStr = dealer.getNickStr() + " has " + h.calcSum() + " (" + h.toString() + ").";
            }
            showMsg(outStr);
            resetPlayer(dealer);
            showMsg(getMsg("separator"));
        }
    }
    
    //////////////////////////////////
    //// Game settings management ////
    //////////////////////////////////
    
    @Override
    protected void set(String setting, int value) {
        super.set(setting, value);
        if (setting.equals("decks")) {
            cancelIdleShuffleTask();
            deck = new CardDeck(get("decks"));
            deck.shuffleCards();
            house = getHouseStat(get("decks"));
            if (house == null) {
                house = new HouseStat(get("decks"), 0, 0);
                houseStatsList.add(house);
            }
        }
    }
    
    @Override
    protected void initSettings() {
        // Do not use set()
        // Ini file settings
        settings.put("decks", 8);
        settings.put("cash", 1000);
        settings.put("idle", 60);
        settings.put("idlewarning", 45);
        settings.put("respawn", 600);
        settings.put("idleshuffle", 300);
        settings.put("count", 0);
        settings.put("hole", 0);
        settings.put("maxplayers", 15);
        settings.put("minbet", 5);
        settings.put("shufflepoint", 10);
        settings.put("soft17hit", 0);
        settings.put("autostarts", 10);
        settings.put("startwait", 5);
        settings.put("ping", 600);
    }
    
    @Override
    protected void initCustom() {
        name = "blackjack";
        helpFile = "blackjack.help";
        dealer = new BlackjackPlayer("Dealer", "");
        houseStatsList = new ArrayList<HouseStat>();
        
        initSettings();
        loadHelp(helpFile);
        loadGameStats();
        loadIni();
        state = BlackjackState.NONE;
        showMsg(getMsg("game_start"), getGameNameStr());
    }
    
    @Override
    protected final void loadIni() {
        super.loadIni();
        cancelIdleShuffleTask();
        deck = new CardDeck(get("decks"));
        deck.shuffleCards();
        house = getHouseStat(get("decks"));
        if (house == null) {
            house = new HouseStat(get("decks"), 0, 0);
            houseStatsList.add(house);
        }
    }
    @Override
    protected void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)));
            out.println("#Settings");
            out.println("#Number of decks in the dealer's shoe");
            out.println("decks=" + get("decks"));
            out.println("#Number of seconds before a player idles out");
            out.println("idle=" + get("idle"));
            out.println("#Number of seconds before a player is given a warning for idling");
            out.println("idlewarning=" + get("idlewarning"));
            out.println("#Number of seconds of idleness after a round ends before the deck is shuffled");
            out.println("idleshuffle=" + get("idleshuffle"));
            out.println("#Initial amount given to new and bankrupt players");
            out.println("cash=" + get("cash"));
            out.println("#Number of seconds before a bankrupt player is allowed to join again");
            out.println("respawn=" + get("respawn"));
            out.println("#Whether card counting functions are enabled");
            out.println("count=" + get("count"));
            out.println("#Whether player hands are shown with a hole card in the main channel");
            out.println("hole=" + get("hole"));
            out.println("#The minimum bet required to see a hand");
            out.println("minbet=" + get("minbet"));
            out.println("#The number of cards remaining in the shoe when the discards are shuffled back");
            out.println("shufflepoint=" + get("shufflepoint"));
            out.println("#The maximum number of players allowed to join the game");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#Whether or not the dealer hits on soft 17");
            out.println("soft17hit=" + get("soft17hit"));
            out.println("#The maximum number of autostarts allowed");
            out.println("autostarts=" + get("autostarts"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The rate-limit of the ping command");
            out.println("ping=" + get("ping"));
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }

    /////////////////////////////////////////////
    //// Game stats management for Blackjack ////
    /////////////////////////////////////////////
    
    @Override
    public final void loadGameStats() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            int ndecks, nrounds, cash;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#blackjack")) {
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            break;
                        }
                        st = new StringTokenizer(str);
                        ndecks = Integer.parseInt(st.nextToken());
                        nrounds = Integer.parseInt(st.nextToken());
                        cash = Integer.parseInt(st.nextToken());
                        houseStatsList.add(new HouseStat(ndecks, nrounds, cash));
                    }
                    break;
                }
            }
            in.close();
        } catch (IOException e) {
            manager.log("housestats.txt not found! Creating new housestats.txt...");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
                out.close();
            } catch (IOException f) {
                manager.log("Error creating housestats.txt!");
            }
        }
    }
    
    @Override
    public void saveGameStats() {
        boolean found = false;
        int index = 0;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            while (in.ready()) {
                //Add all lines until we find blackjack lines
                str = in.readLine();
                lines.add(str);
                if (str.startsWith("#blackjack")) {
                    found = true;
                    /* Store the index where blackjack stats go so they can be 
                     * overwritten. */
                    index = lines.size();
                    //Skip existing blackjack lines but add all the rest
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                                lines.add(str);
                                break;
                        }
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            /* housestats.txt is not found */
            manager.log("Error reading housestats.txt!");
        }
        if (!found) {
            lines.add("#blackjack");
            index = lines.size();
        }
        for (int ctr = 0; ctr < houseStatsList.size(); ctr++) {
            lines.add(index, houseStatsList.get(ctr).toFileString());
        }
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
            for (int ctr = 0; ctr < lines.size(); ctr++) {
                out.println(lines.get(ctr));
            }
            out.close();
        } catch (IOException e) {
            manager.log("Error writing to housestats.txt!");
        }
    }
    
    /**
     * Returns the house statistics for a given shoe size.
     * @param numDecks shoe size in number of decks
     * @return the house stats
     */
    private HouseStat getHouseStat(int numDecks) {
        for (HouseStat hs : houseStatsList) {
            if (hs.get("decks") == numDecks) {
                return hs;
            }
        }
        return null;
    }
    
    /**
     * Calculates the total number of rounds played by all players.
     * @return the total number of rounds
     */
    private int getTotalRounds(){
        int total=0;
        for (HouseStat hs : houseStatsList) {
            total += hs.get("rounds");
        }
        return total;
    }
    
    /**
     * Calculates the total amount won by the house.
     * @return the total amount won by the house
     */
    private int getTotalHouse(){
        int total=0;
        for (HouseStat hs : houseStatsList) {
            total += hs.get("cash");
        }
        return total;
    }
    
    ///////////////////////////////////////////////
    //// Game management methods for Blackjack ////
    ///////////////////////////////////////////////
    
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new BlackjackPlayer(nick, host));
    }
    
    @Override
    public void addWaitlistPlayer(String nick, String host) {
        Player p = new BlackjackPlayer(nick, host);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    
    @Override
    public void leave(String nick) {
        BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);

        switch (state) {
            case NONE: case PRE_START:
                removeJoined(p);
                showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                break;
            case BETTING:
                if (p == currentPlayer){
                    cancelIdleOutTask();
                    currentPlayer = getNextPlayer();
                    removeJoined(p);
                    showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                    if (currentPlayer == null) {
                        if (joined.isEmpty()) {
                            endRound();
                        } else {
                            dealTable();
                            currentPlayer = joined.get(0);
                            quickEval();
                        }
                    } else {
                        showTurn(currentPlayer, 0);
                        setIdleOutTask();
                    }
                } else {
                    if (p.has("initialbet")){
                        p.set("quit", 1);
                        informPlayer(p.getNick(), getMsg("remove_end_round"));
                    } else {
                        removeJoined(p);
                        showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                    }
                }
                break;
            case PLAYING:
                p.set("quit", 1);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                if (p == currentPlayer){
                    stay();
                }
                break;
            case CONTINUE_ROUND: case END_ROUND:
                p.set("quit", 1);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            default:
                break;
        }
    }
    
    @Override
    public void startRound() {
        if (joined.size() > 0) {
            state = BlackjackState.BETTING;
            showMsg(getMsg("players"), getPlayerListString(joined));
            currentPlayer = joined.get(0);
            showTurn(currentPlayer, 0);
            setIdleOutTask();
        } else {
            startCount = 0;
            endRound();
        }
    }
    
    @Override
    public void continueRound(){
        state = BlackjackState.CONTINUE_ROUND;
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        
        if (p.get("currentindex") < p.getNumberHands() - 1) {
            p.getNextHand();
            quickEval();
        } else {
            currentPlayer = getNextPlayer();
            if (currentPlayer == null) {
                endRound();
            } else {
                quickEval();
            }
        }
    }
    
    @Override
    public void endRound() {
        state = BlackjackState.END_ROUND;
        BlackjackPlayer p;
        BlackjackHand dHand;

        if (joined.size() >= 1) {
            house.increment("rounds");
            // Make dealer decisions
            if (needDealerPlay()) {
                showTurn(dealer, 0);
                dHand = dealer.getHand();
                showPlayerHand(dealer, dHand, 0, true);
                while (dHand.calcSum() < 17 || (dHand.isSoft17() && has("soft17hit"))) {
                    // Add a 1 second delay for dramatic effect
                    try { Thread.sleep(1000); } catch (InterruptedException e){}
                    dealCard(dHand);
                    showPlayerHand(dealer, dHand, 0, true);
                }
                // Add a 1 second delay for dramatic effect
                try { Thread.sleep(1000); } catch (InterruptedException e){}
            }
            
            // Show results
            showResults();
            // Add a 1 second delay for dramatic effect
            try { Thread.sleep(1000); } catch (InterruptedException e){}
            if (insuranceBets) {
                showInsuranceResults();
            }
            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
            for (int ctr = 0; ctr < joined.size(); ctr++) {
                p = (BlackjackPlayer) joined.get(ctr);
                p.increment("bjrounds");

                if (p.has("cash")) {
                    if (p.has("quit")) {
                        removeJoined(p.getNick());
                        showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                        ctr--;
                    } else {
                        savePlayerData(p);
                    }
                } else {
                    if (p.has("bank")){
                        // Make a withdrawal if the player has a positive bankroll
                        int amount = Math.min(p.get("bank"), get("cash"));
                        p.bankTransfer(-amount);
                        savePlayerData(p);
                        informPlayer(p.getNick(), getMsg("auto_withdraw"), amount);
                        // Check if the player has quit
                        if (p.has("quit")){
                            removeJoined(p);
                            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                            ctr--;
                        }
                    } else {
                        // Give penalty to players with no cash in their bankroll
                        p.increment("bankrupts");
                        blacklist.add(p);
                        removeJoined(p);
                        showMsg(getMsg("unjoin_bankrupt"), p.getNickStr(), joined.size());
                        setRespawnTask(p);
                        ctr--;
                    }
                }
                
                resetPlayer(p);
            }
            saveGameStats();
        } else {
            showMsg(getMsg("no_players"));
        }
        
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        state = BlackjackState.NONE;
        
        // Check if any auto-starts remaining
        if (startCount > 0 && joined.size() > 0){
            startCount--;
            state = BlackjackState.PRE_START;
            showStartRound();
            setStartRoundTask();
        } else {
            startCount = 0;
            if (deck.getNumberDiscards() > 0) {
                setIdleShuffleTask();
            }
        }
    }
    
    @Override
    public void endGame() {
        cancelStartRoundTask();
        cancelIdleOutTask();
        cancelRespawnTasks();
        cancelIdleShuffleTask();
        gameTimer.cancel();
        deck = null;
        dealer = null;
        currentPlayer = null;
        houseStatsList.clear();
        house = null;
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        awayList.clear();
        notSimpleList.clear();
        joined.clear();
        waitlist.clear();
        blacklist.clear();
        cmdMap.clear();
        opCmdMap.clear();
        aliasMap.clear();
        msgMap.clear();
        settings.clear();
    }
    
    @Override
    public void resetGame() {
        insuranceBets = false;
        discardPlayerHand(dealer);
        currentPlayer = null;
    }
    
    @Override
    protected void resetPlayer(Player p) {
        discardPlayerHand((BlackjackPlayer) p);
        p.clear("currentindex");
        p.clear("initialbet");
        p.clear("quit");
        p.clear("surrender");
        p.clear("insurebet");
    }
    
    /**
     * Creates a new idle shuffle task.
     */
    public void setIdleShuffleTask() {
        idleShuffleTask = new IdleShuffleTask(this);
        gameTimer.schedule(idleShuffleTask, get("idleshuffle")*1000);
    }
    
    /**
     * Cancels the idle shuffle task if it exists.
     */
    public void cancelIdleShuffleTask() {
        if (idleShuffleTask != null){
            idleShuffleTask.cancel();
            gameTimer.purge();
        }
    }

    @Override
    public boolean isInProgress() {
        return !state.equals(BlackjackState.NONE);
    }
    
    ///////////////////////////////////////////////
    //// Card management methods for Blackjack ////
    ///////////////////////////////////////////////
    
    /**
     * Deals a card from the shoe to the specified hand.
     * @param h the hand
     */
    @Override
    public void dealCard(Hand h) {
        h.add(deck.takeCard());
        if (deck.getNumberCards() == get("shufflepoint")) {
            showMsg(getMsg("bj_deck_empty"));
            deck.refillDeck();
        }
    }
    
    /**
     * Merges the discards and shuffles the shoe.
     */
    public void shuffleShoe() {
        deck.refillDeck();
        showMsg(getMsg("bj_shuffle_shoe"));
    }
    
    /**
     * Deals two cards to the specified player.
     * @param p the player to be dealt to
     */
    private void dealHand(BlackjackPlayer p) {
        p.addHand();
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    
    /**
     * Deals hands (two cards) to everybody at the table.
     */
    public void dealTable() {
        BlackjackPlayer p;
        BlackjackHand h;
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            dealHand(p);
            h = p.getHand();
            h.setBet(p.get("initialbet"));
            // Send the player his hand in a hole game
            if (has("hole")) {
                informPlayer(p.getNick(), getMsg("bj_hand"), p.getHand(), p.getHand().getBet());
            }
        }
        dealHand(dealer);
        showTableHands(true);
    }
    
    /**
     * Discards a player's cards into the discard pile.
     * Loops through each hand that the player has.
     * @param p the player whose hands are to be discarded
     */
    private void discardPlayerHand(BlackjackPlayer p) {
        if (p.hasHands()) {
            for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
                deck.addToDiscard(p.getHand(ctr));
            }
            p.resetHands();
        }
    }

    ////////////////////////////////////
    //// Blackjack gameplay methods ////
    ////////////////////////////////////
    
    /**
     * Sets the initialize bet for the current player to see a hand.
     * The game then moves on to the next hand, player or phase.
     * 
     * @param amount the bet on the hand
     */
    private void bet(int amount) {
        cancelIdleOutTask();    
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        // Check if amount is greater than the player's stack
        if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_high"), p.get("cash"));
            setIdleOutTask();
        // Check if the amount is less than minimum bet
        } else if (amount < get("minbet") && amount < p.get("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_low"), get("minbet"));
            setIdleOutTask();
        } else {
            p.set("initialbet", amount);
            p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
            house.add("cash", amount);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null) {
                dealTable();
                currentPlayer = joined.get(0);
                quickEval();
            } else {
                showTurn(currentPlayer, 0);
                setIdleOutTask();
            }
        }
    }
    
    /**
     * Lets the current Player stand.
     * The game then moves on to the next hand, player or phase.
     */
    private void stay() {
        cancelIdleOutTask();
        continueRound();
    }
    
    /**
     * Gives the current Player's hand an additional card.
     * Checks if the hand is now bust.
     */
    private void hit() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        dealCard(h);
        showHitResult(p,h);
        if (h.isBust()) {
            continueRound();
        } else {
            setIdleOutTask();
        }
    }
    
    /**
     * Gives the current Player's hand an additional card and doubles the bet
     * on the hand. The game then moves on to the next hand, player or phase.
     */
    private void doubleDown() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_dd"));
            setIdleOutTask();
        } else if (p.get("initialbet") > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {			
            p.add("cash", -1 * h.getBet());
            p.add("bjwinnings", -1 * h.getBet());
            house.add("cash", h.getBet());
            h.addBet(h.getBet());
            showMsg(getMsg("bj_dd"), p.getNickStr(false), h.getBet(), p.get("cash"));
            dealCard(h);
            showHitResult(p,h);
            continueRound();
        }
    }
    
    /**
     * Lets the current Player surrender his hand and receive back half the 
     * bet on that hand. The game then moves on to the hand, player or phase.
     */
    private void surrender() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (p.hasSplit()){
            informPlayer(p.getNick(), getMsg("no_surr_split"));
            setIdleOutTask();
        } else if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_surr"));
            setIdleOutTask();
        } else {
            p.add("cash", calcHalf(p.get("initialbet")));
            p.add("bjwinnings", calcHalf(p.get("initialbet")));
            house.add("cash", -1 * calcHalf(p.get("initialbet")));
            p.set("surrender", 1);
            showMsg(getMsg("bj_surr"), p.getNickStr(false), p.get("cash"));
            continueRound();
        }
    }
    
    /**
     * Sets the insurance bet for the current Player.
     * 
     * @param amount the insurance bet
     */
    private void insure(int amount) {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (p.has("insurebet")) {
            informPlayer(p.getNick(), getMsg("already_insured"));
        } else if (!dealerUpcardAce()) {
            informPlayer(p.getNick(), getMsg("no_insure_no_ace"));
        } else if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_insure_has_hit"));
        } else if (p.hasSplit()){
            informPlayer(p.getNick(), getMsg("no_insure_has_split"));
        } else if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
        } else if (amount > calcHalf(p.get("initialbet"))) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_high"), calcHalf(p.get("initialbet")));
        } else if (amount <= 0) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_low"));
        } else {
            insuranceBets = true;
            p.set("insurebet", amount);
            p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
            house.add("cash", amount);
            showMsg(getMsg("bj_insure"), p.getNickStr(false), p.get("insurebet"), p.get("cash"));
        }
        setIdleOutTask();
    }
    
    /**
     * Lets the current Player split the current hand into two hands, each
     * with its own bet.
     */
    private void split() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand nHand, cHand = p.getHand();
        if (!cHand.isPair()) {
            informPlayer(p.getNick(), getMsg("no_pair"));
            setIdleOutTask();
        } else if (p.get("cash") < cHand.getBet()) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {
            p.add("cash", -1 * cHand.getBet());
            p.add("bjwinnings", -1 * cHand.getBet());
            house.add("cash", cHand.getBet());
            p.splitHand();
            dealCard(cHand);
            nHand = p.getHand(p.get("currentindex") + 1);
            dealCard(nHand);
            nHand.setBet(cHand.getBet());
            showSplitHands(p);
            showMsg(getMsg("separator"));
            quickEval();
        }
    }

    /////////////////////////////////////////////
    //// Blackjack behind-the-scenes methods ////
    /////////////////////////////////////////////
    
    /**
     * Determines what to do when the action falls to a new player/hand
     */
    private void quickEval() {
        state = BlackjackState.PLAYING;
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        
        if (p.hasSplit()) {
            showTurn(p, p.get("currentindex") + 1);
        } else {
            showTurn(p, 0);
        }
        
        if (p.has("quit")){
            stay();
        } else {
            setIdleOutTask();
        }
    }
    
    /**
     * Calculates half of an amount rounded up.
     * @param amount
     * @return half of the amount rounded up
     */
    private int calcHalf(int amount) {
        return (int) (Math.ceil((double) (amount) / 2.));
    }
    
    /**
     * Calculates the winnings for a Blackjack win.
     * @param h a hand with Blackjack
     * @return the payout
     */
    private int calcBlackjackPayout(BlackjackHand h){
        return (2 * h.getBet() + calcHalf(h.getBet()));
    }
    
    /**
     * Calculates the winnings for a regular win.
     * @param h a winning hand
     * @return the payout
     */
    private int calcWinPayout(BlackjackHand h){
        return 2 * h.getBet();
    }
    
    /**
     * Calculates the winnings for an insurance win.
     * @param p a player with an insurance bet
     * @return the payout
     */
    private int calcInsurancePayout(BlackjackPlayer p){
        return 3 * p.get("insurebet");
    }
    
    /**
     * Determines if the dealer's upcard is an Ace.
     * @return true if it is an Ace
     */
    private boolean dealerUpcardAce() {
        return dealer.getHand().get(1).isFace("A");
    }
    
    /**
     * Determines if the dealer needs to play his hand.
     * If all the players have busted, surrendered or Blackjack then the
     * dealer does not need to play his hand.
     * @return true if one player does not meet the requirements.
     */
    private boolean needDealerPlay() {
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            BlackjackPlayer p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
                BlackjackHand h = p.getHand(ctr2);
                if (!h.isBust() && !p.hasSurrendered() && !h.isBlackjack()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Pays winnings.
     * @param p the player
     * @param h the hand to calculate
     */
    private void payPlayer(BlackjackPlayer p, BlackjackHand h){
        int result = h.compareTo(dealer.getHand());
        int payout = 0;
        switch (result){
            case 2: payout = calcBlackjackPayout(h); break;
            case 1: payout = calcWinPayout(h); break;
            case 0: payout = h.getBet(); break;
            default:
        }
        p.add("cash", payout);
        p.add("bjwinnings", payout);
        house.add("cash", -1 * payout);
    }
    
    /**
     * Pays insurance winnings.
     * @param p the player with an insurance bet
     */
    private void payPlayerInsurance(BlackjackPlayer p){
        if (dealer.getHand().isBlackjack()) {
            p.add("cash", calcInsurancePayout(p));
            p.add("bjwinnings", calcInsurancePayout(p));
            house.add("cash", -1 * calcInsurancePayout(p));
        }
    }

    ///////////////////////////////
    //// Card-counting methods ////
    ///////////////////////////////
    
    /**
     * Calculates the Zen count.
     * Contributors: Yky, brrr 
     */
    private int getZen() {
        int zenCount = 0;
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23").countTokens() == 0) {
                zenCount++;
            } else if (new StringTokenizer(face, "456").countTokens() == 0) {
                zenCount += 2;
            } else if (face.equals("7")) {
                zenCount++;
            } else if (new StringTokenizer(face, "TJQK").countTokens() == 0) {
                zenCount -= 2;
            } else if (face.equals("A")) {
                zenCount--;
            }
        }
        return zenCount;
    }
    
    /**
     * Calculates the hi-lo count.
     * Contributors: Yky, brrr 
     */
    private int getHiLo() {
        int hiLo = 0;
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23456").countTokens() == 0) {
                hiLo++;
            } else if (new StringTokenizer(face, "TJQKA").countTokens() == 0) {
                hiLo--;
            }
        }
        return hiLo;
    }
    
    /**
     * Calculates the red 7 count.
     * Contributors: Yky, brrr
     */
    private double getRed7() {
        double red7 = -2 * get("decks");
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23456").countTokens() == 0) {
                red7++;
            } else if (new StringTokenizer(face, "TJQKA").countTokens() == 0) {
                red7--;
            } else if (face.equals("7")) {
                red7 += 0.5;
            }
        }
        return red7;
    }
    
    @Override
    public int getTotalPlayers(){
        try {
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int total = 0, numLines = records.size();
            
            for (PlayerRecord record : records){
                if (record.has("bjrounds")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }
    
    //////////////////////////////////////////////
    //// Message output methods for Blackjack ////
    //////////////////////////////////////////////
    
    /**
     * Shows house stats for a given shoe size.
     * @param n the number of decks in the shoe
     */
    public void showHouseStat(int n) {
        HouseStat hs = getHouseStat(n);
        if (hs != null) {
            showMsg(hs.toString());
        } else {
            showMsg(getMsg("bj_no_stats"), n);
        }
    }
    
    /**
     * Displays which player is currently required to act.
     * @param p the player required to act
     * @param index the index of the hand
     */
    public void showTurn(Player p, int index) {
        if (state.equals(BlackjackState.BETTING)) {
            showMsg(getMsg("bj_turn_betting"), p.getNickStr(), p.get("cash"), p.get("cash"));
        } else if (index == 0) {
            showMsg(getMsg("bj_turn"), p.getNickStr());
        } else {
            showMsg(getMsg("bj_turn_split"), p.getNickStr(), index);
        }
    }
    
    /**
     * Displays a player's hand.
     * @param p the player
     * @param h the player's hand
     * @param index the index of the hand
     * @param forceNoHole whether to force reveal hole card
     */
    private void showPlayerHand(BlackjackPlayer p, BlackjackHand h, int index, boolean forceNoHole) {
        if (index == 0) {
            if (forceNoHole){
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_hand_bj"), p.getNickStr(), h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h);
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h);
                }
            } else if (has("hole") || p == dealer) {
                if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h.toString(1));
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h.toString(1));
                }
            } else {
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_hand_bj"), p.getNickStr(), h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h);
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h);
                }
            }
        } else {
            if (has("hole")) {
                if (h.isBust()) {
                    showMsg(getMsg("bj_show_split_hand_bust"), p.getNickStr(), index, h.toString(1));
                } else {
                    showMsg(getMsg("bj_show_split_hand"), p.getNickStr(), index, h.toString(1));
                }
            } else {
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_split_hand_bj"), p.getNickStr(), index, h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_split_hand_bust"), p.getNickStr(), index, h);
                } else {
                    showMsg(getMsg("bj_show_split_hand"), p.getNickStr(), index, h);
                }
            }
        }
    }
    
    /**
     * Method to display split hands after a split.
     * @param p the player
     * @param h the hand
     * @param index the index of the hand
     */
    private void showPlayerHandWithBet(BlackjackPlayer p, BlackjackHand h, int index) {
        if (has("hole")) {
            showMsg(getMsg("bj_show_split_hand_bet"), p.getNickStr(), index, h.toString(1), h.getBet());
        } else {
            showMsg(getMsg("bj_show_split_hand_bet"), p.getNickStr(), index, h, h.getBet());
        }
    }
    
    /**
     * Method to display all of a player's split hands after a split.
     * @param p the player
     */
    private void showSplitHands(BlackjackPlayer p) {
        BlackjackHand h;
        showMsg(getMsg("bj_split"), p.getNickStr(false), p.getNickStr(false));
        for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
            h = p.getHand(ctr);
            showPlayerHandWithBet(p, h, ctr + 1);
        }
        showMsg(getMsg("bj_stack"), p.getNickStr(), p.get("cash"));
    }
    
    /**
     * Shows the result of a hit or double-down.
     * @param p the player
     * @param h the player's hand
     */
    private void showHitResult(BlackjackPlayer p, BlackjackHand h){
        if (p.hasSplit()) {
            showPlayerHand(p, h, p.get("currentindex") + 1, false);
        } else {
            showPlayerHand(p, h, 0, false);
        }
    }

    /**
     * Displays the dealt hands of the players and the dealer.
     * @param dealing
     */
    public void showTableHands(boolean dealing) {
        BlackjackPlayer p;
        if (dealing){
            showMsg(formatHeader(" Dealing Table... "));
        } else {
            showMsg(formatHeader(" Table: "));
        }
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++){
                if (p.hasSplit()) {
                    showPlayerHand(p, p.getHand(ctr2), ctr2+1, false);
                } else {
                    showPlayerHand(p, p.getHand(ctr2), 0, false);
                }
            }
        }
        showPlayerHand(dealer, dealer.getHand(), 0, false);
    }
    
    /**
     * Displays the final results of the round.
     */
    public void showResults() {
        BlackjackPlayer p;
        BlackjackHand h;
        showMsg(formatHeader(" Results: "));
        showDealerResult();
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
                h = p.getHand(ctr2);
                if (!p.hasSurrendered()){
                    payPlayer(p,h);
                }
                if (p.hasSplit()) {
                    showPlayerResult(p, h, ctr2+1);
                } else {
                    showPlayerResult(p, h, 0);
                }
            }
        }
    }
    
    /**
     * Displays the results of any insurance bets.
     */
    public void showInsuranceResults() {
        BlackjackPlayer p;
        showMsg(formatHeader(" Insurance Results: "));
        if (dealer.getHand().isBlackjack()) {
            showMsg(dealer.getNickStr() + " had blackjack.");
        } else {
            showMsg(dealer.getNickStr() + " did not have blackjack.");
        }

        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            if (p.has("insurebet")) {
                payPlayerInsurance(p);
                showPlayerInsuranceResult(p);
            }
        }
    }
    
    /**
     * Displays the result of the dealer's hand.
     */
    public void showDealerResult() {
        BlackjackHand dHand = dealer.getHand();
        if (dHand.isBlackjack()) {
            showMsg(getMsg("bj_dealer_result_bj"), dealer.getNickStr(), dHand);
        } else {
            showMsg(getMsg("bj_dealer_result"), dealer.getNickStr(), dHand.calcSum(), dHand);
        }
    }
    
    /**
     * Outputs the result of a player's hand to the game channel.
     * @param p the player to show
     * @param h the player's hand of which the results are to be shown
     * @param index the hand index if the player has split
     */
    private void showPlayerResult(BlackjackPlayer p, BlackjackHand h, int index) {
        String nickStr;
        if (index > 0){
            nickStr = p.getNickStr() + "-" + index;
        } else {
            nickStr = p.getNickStr();
        }
        int result = h.compareTo(dealer.getHand());
        if (p.hasSurrendered()) {
            showMsg(getMsg("bj_result_surr"), getSurrStr(), nickStr, h.calcSum(), h, p.get("cash"));
        } else {
            switch (result) {
                case 2: // Blackjack win
                    showMsg(getMsg("bj_result_bj"), getWinStr(), nickStr, h, calcBlackjackPayout(h), p.get("cash"));
                    break;
                case 1: // Regular win
                    showMsg(getMsg("bj_result_win"), getWinStr(), nickStr, h.calcSum(), h, calcWinPayout(h), p.get("cash"));
                    break;
                case 0: // Push
                    showMsg(getMsg("bj_result_push"), getPushStr(), nickStr, h.calcSum(), h, h.getBet(), p.get("cash"));
                    break;
                default: // Loss
                    showMsg(getMsg("bj_result_loss"), getLossStr(), nickStr, h.calcSum(), h, p.get("cash"));
            }
        }
    }
    
    /**
     * Displays the result of a player's insurance bet.
     * @param p a player who has made an insurance bet
     */
    private void showPlayerInsuranceResult(BlackjackPlayer p) {
        if (dealer.getHand().isBlackjack()) {
            showMsg(getMsg("bj_insure_win"), getWinStr(), p.getNickStr(), calcInsurancePayout(p), p.get("cash"));
        } else {
            showMsg(getMsg("bj_insure_loss"), getLossStr(), p.getNickStr(), p.get("cash"));
        }
    }
    
    @Override
    public void showPlayerWinnings(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            showMsg(getMsg("player_winnings"), p.getNick(false), p.get("bjwinnings"), getGameNameStr());
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            showMsg(getMsg("player_winnings"), p.getNick(false), p.get("bjwinnings"), getGameNameStr());
        } else {
            PlayerRecord record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else {
                showMsg(getMsg("player_winnings"), record.getNick(false), record.get("bjwinnings"), getGameNameStr());
            }
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            if (p.get("bjrounds") == 0) {
                showMsg(getMsg("player_no_rounds"), p.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_winrate"), p.getNick(false), (double) p.get("bjwinnings")/(double) p.get("bjrounds"), getGameNameStr());
            }
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            if (p.get("bjrounds") == 0) {
                showMsg(getMsg("player_no_rounds"), p.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_winrate"), p.getNick(false), (double) p.get("bjwinnings")/(double) p.get("bjrounds"), getGameNameStr());
            }
        } else {
            PlayerRecord record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else if (record.get("bjrounds") == 0){
                showMsg(getMsg("player_no_rounds"), record.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_winrate"), record.getNick(false), (double) record.get("bjwinnings")/(double) record.get("bjrounds"), getGameNameStr());
            }  
        }
    }
    
    @Override
    public void showPlayerRounds(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            if (p.get("bjrounds") == 0) {
                showMsg(getMsg("player_no_rounds"), p.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_rounds"), p.getNick(false), p.get("bjrounds"), getGameNameStr());
            }
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            if (p.get("bjrounds") == 0) {
                showMsg(getMsg("player_no_rounds"), p.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_rounds"), p.getNick(false), p.get("bjrounds"), getGameNameStr());
            }
        } else {
            PlayerRecord record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else if (record.get("bjrounds") == 0){
                showMsg(getMsg("player_no_rounds"), record.getNick(false), getGameNameStr());
            } else {
                showMsg(getMsg("player_rounds"), record.getNick(false), record.get("bjrounds"), getGameNameStr());
            }  
        }
    } 
    
    @Override
    public void showPlayerAllStats(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            showMsg(getMsg("player_all_stats"), p.getNick(false), p.get("cash"), p.get("bank"), p.get("netcash"), p.get("bankrupts"), p.get("bjwinnings"), p.get("bjrounds"));
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            showMsg(getMsg("player_all_stats"), p.getNick(false), p.get("cash"), p.get("bank"), p.get("netcash"), p.get("bankrupts"), p.get("bjwinnings"), p.get("bjrounds"));
        } else {
            PlayerRecord record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else {
                showMsg(getMsg("player_all_stats"), record.getNick(false), record.get("cash"), record.get("bank"), record.get("netcash"), record.get("bankrupts"), record.get("bjwinnings"), record.get("bjrounds"));
            }
        }
    }
    
    @Override
    public void showPlayerRank(String nick, String stat){
        if (getPlayerStat(nick, "exists") != 1){
            showMsg(getMsg("no_data"), formatNoPing(nick));
            return;
        }
        
        try {
            PlayerRecord aRecord;
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int length = records.size();
            String line = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex, rank = 0;
                ArrayList<String> nicks = new ArrayList<String>();
                ArrayList<Double> winrates = new ArrayList<Double>();
  
                for (int ctr = 0; ctr < length; ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.get("bjrounds") == 0){
                        winrates.add(0.);
                    } else {
                        winrates.add((double) aRecord.get("bjwinnings") / (double) aRecord.get("bjrounds"));
                    }
                }
                
                line += "Blackjack Win Rate: ";
                
                // Find the player with the highest value and check if it is 
                // the requested player. Repeat until found or end.
                for (int ctr = 0; ctr < length; ctr++){
                    highIndex = 0;
                    rank++;
                    for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                        if (winrates.get(ctr2) > winrates.get(highIndex)) {
                            highIndex = ctr2;
                        }
                    }
                    
                    if (nick.equalsIgnoreCase(nicks.get(highIndex))){
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatDecimal(winrates.get(highIndex)) + " ";
                        break;
                    } else {
                        nicks.remove(highIndex);
                        winrates.remove(highIndex);
                    }
                }
            } else {
                String statName = "";
                if (stat.equalsIgnoreCase("cash")) {
                    statName = "cash";
                    line += "Cash: ";
                } else if (stat.equalsIgnoreCase("bank")) {
                    statName = "bank";
                    line += "Bank: ";
                } else if (stat.equalsIgnoreCase("bankrupts")) {
                    statName = "bankrupts";
                    line += "Bankrupts: ";
                } else if (stat.equalsIgnoreCase("net") || stat.equals("netcash")) {
                    statName = "netcash";
                    line += "Net Cash: ";
                } else if (stat.equalsIgnoreCase("winnings")){
                    statName = "bjwinnings";
                    line += "Blackjack Winnings: ";
                } else if (stat.equalsIgnoreCase("rounds")) {
                    statName = "bjrounds";
                    line += "Blackjack Rounds: ";
                } else {
                    throw new IllegalArgumentException();
                }
                
                // Sort based on stat
                Collections.sort(records, PlayerRecord.getComparator(statName));
                
                // Find the rank of the player
                for (int ctr = 0; ctr < length; ctr++){
                    aRecord = records.get(ctr);
                    if (nick.equalsIgnoreCase(aRecord.getNick())){
                        if (stat.equalsIgnoreCase("rounds") || stat.equalsIgnoreCase("bankrupts")) {
                            line += "#" + (ctr+1) + " " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.get(statName)) + " ";
                        } else {
                            line += "#" + (ctr+1) + " " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " $" + formatNumber(aRecord.get(statName)) + " ";
                        }
                        break;
                    }
                }
            }
            
            // Show rank
            showMsg(line);
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
        
    @Override
    public void showTopPlayers(String stat, int n) {
        if (n < 1){
            throw new IllegalArgumentException();
        }
        
        try {
            PlayerRecord aRecord;
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int end = Math.min(n, records.size());
            int start = Math.max(end - 10, 0);
            String title = Colors.BOLD + Colors.BLACK + ",08 Top " + (start+1) + "-" + end;
            String list = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex;
                ArrayList<String> nicks = new ArrayList<String>();
                ArrayList<Double> winrates = new ArrayList<Double>();
                
                for (int ctr = 0; ctr < records.size(); ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.get("bjrounds") == 0){
                        winrates.add(0.);
                    } else {
                        winrates.add((double) aRecord.get("bjwinnings") / (double) aRecord.get("bjrounds"));
                    }
                }
                
                title += " Blackjack Win Rate ";
                
                // Find the player with the highest value, add to output string and remove.
                for (int ctr = 0; ctr < records.size(); ctr++){
                    highIndex = 0;
                    for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                        if (winrates.get(ctr2) > winrates.get(highIndex)) {
                            highIndex = ctr2;
                        }
                    }
                    
                    // Only add those in the required range.
                    if (ctr >= start) {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatDecimal(winrates.get(highIndex)) + " " + Colors.BLACK + ",08";
                    }
                    
                    nicks.remove(highIndex);
                    winrates.remove(highIndex);
                    
                    // Break when we've reached the end of required range
                    if (ctr + 1 == end) {
                        break;
                    }
                }
            } else {
                String statName = "";
                if (stat.equalsIgnoreCase("cash")) {
                    statName = "cash";
                    title += " Cash ";
                } else if (stat.equalsIgnoreCase("bank")) {
                    statName = "bank";
                    title += " Bank ";
                } else if (stat.equalsIgnoreCase("bankrupts")) {
                    statName = "bankrupts";
                    title += " Bankrupts ";
                } else if (stat.equalsIgnoreCase("net") || stat.equalsIgnoreCase("netcash")) {
                    statName = "netcash";
                    title += " Net Cash ";
                } else if (stat.equalsIgnoreCase("winnings")){
                    statName = "bjwinnings";
                    title += " Blackjack Winnings ";
                } else if (stat.equalsIgnoreCase("rounds")) {
                    statName = "bjrounds";
                    title += " Blackjack Rounds ";
                } else {
                    throw new IllegalArgumentException();
                }
                
                // Sort based on stat
                Collections.sort(records, PlayerRecord.getComparator(statName));

                // Add the players in the required range
                for (int ctr = start; ctr < end; ctr++){
                    aRecord = records.get(ctr);
                    if (stat.equalsIgnoreCase("rounds") || stat.equalsIgnoreCase("bankrupts")) {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.get(statName)) + " " + Colors.BLACK + ",08";
                    } else {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " $" + formatNumber(aRecord.get(statName)) + " " + Colors.BLACK + ",08";
                    }
                }
            }
            
            // Output title and the list
            showMsg(title);
            showMsg(list);
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    ///////////////////////////
    //// Formatted strings ////
    ///////////////////////////
    
    @Override
    public final String getGameNameStr(){
        return formatBold(getMsg("bj_game_name"));
    }
    
    @Override
    public final String getGameRulesStr() {
        if (has("soft17hit")){
            return String.format(getMsg("bj_rules_soft17hit"), deck.getNumberDecks(), get("shufflepoint"), get("minbet"));
        } else {
            return String.format(getMsg("bj_rules_soft17stand"), deck.getNumberDecks(), get("shufflepoint"), get("minbet"));
        }
    }
    
    @Override
    public final String getGameStatsStr(){
        return String.format(getMsg("bj_stats"), getTotalPlayers(), getGameNameStr(), getTotalRounds(), getTotalHouse());
    }
    
    private static String getWinStr(){
        return Colors.GREEN+",01"+" WIN "+Colors.NORMAL;
    }
    private static String getLossStr(){
        return Colors.RED+",01"+" LOSS "+Colors.NORMAL;
    }
    private static String getSurrStr(){
        return Colors.RED+",01"+" SURR "+Colors.NORMAL;
    }
    private static String getPushStr(){
        return Colors.WHITE+",01"+" PUSH "+Colors.NORMAL;
    }
}