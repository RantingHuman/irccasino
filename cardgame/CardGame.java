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
package irccasino.cardgame;

import irccasino.GameManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Timer;
import org.pircbotx.Channel;
import org.pircbotx.Colors;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

/**
 * Generic class for card games written for irccasino.
 * @author Yizhe Shen
 */
public abstract class CardGame extends ListenerAdapter<PircBotX> {
    
    protected GameManager manager;
    protected Channel channel;
    protected char commandChar;
    protected ArrayList<Player> joined, blacklist, waitlist; //Player lists
    protected CardDeck deck; //the deck of cards
    protected Player currentPlayer; //stores the player whose turn it is
    protected Timer gameTimer; //All TimerTasks are scheduled on this Timer
    /** INI file settings **/
    protected HashMap<String,Integer> settings;
    // In-game properties
    protected boolean inProgress, roundEnded;
    protected int startCount;
    protected HashMap<String,String> cmdMap, opCmdMap, aliasMap, msgMap;
    protected String name, iniFile, helpFile, strFile;
    // TimerTasks
    protected IdleOutTask idleOutTask;
    protected IdleWarningTask idleWarningTask;
    protected StartRoundTask startRoundTask;
    protected ArrayList<RespawnTask> respawnTasks;

    public CardGame() {
        super();
    }
    
    /**
     * Creates a generic CardGame.
     * Not to be directly instantiated.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public CardGame(GameManager parent, char commChar, Channel gameChannel){
        manager = parent;
        commandChar = commChar;
        channel = gameChannel;
        joined = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        respawnTasks = new ArrayList<RespawnTask>();
        settings = new HashMap<String,Integer>();
        cmdMap = new HashMap<String,String>();
        opCmdMap = new HashMap<String,String>();
        aliasMap = new HashMap<String,String>();
        msgMap = new HashMap<String,String>();
        gameTimer = new Timer("Game Timer");
        startRoundTask = null;
        idleOutTask = null;
        idleWarningTask = null;
        currentPlayer = null;
        checkPlayerFile();
    }
    
    /**
     * Occurs when a message is sent to the game channel.
     * @param event message event
     */
    @Override
    public void onMessage(MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the message if it is a command
        if (msg.length() > 1 && msg.charAt(0) == commandChar && 
                msg.charAt(1) != ' ' && event.getChannel().equals(channel)){
            StringTokenizer st = new StringTokenizer(msg.substring(1));
            String command = st.nextToken();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            
            processCommand(event.getUser(), command, params);
        }
    }
    
    /**
     * Occurs when a user joins the game channel.
     * @param event join event
     */
    @Override
    public void onJoin(JoinEvent<PircBotX> event){
        if (event.getChannel().equals(channel)){
            processJoin(event.getUser());
        }
    }

    /**
     * Occurs when a user parts the game channel.
     * @param event part event
     */
    @Override
    public void onPart(PartEvent<PircBotX> event){
        if (event.getChannel().equals(channel)){
            processQuit(event.getUser());
        }
    }

    /**
     * Occurs when a user disconnects from the IRC network.
     * @param event quit event
     */
    @Override
    public void onQuit(QuitEvent<PircBotX> event){
        processQuit(event.getUser());
    }

    /**
     * Occurs when a user changes nicks.
     * @param event nick change event
     */
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> event){
        processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
    }
    
    /* Methods that process IRC events */
    /**
     * Processes commands in the channel where the game is running.
     * 
     * @param user IRC user who issued the command.
     * @param command The command that was issued.
     * @param params A list of parameters that were passed along.
     */
    protected void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        /* Common commands for all games that can be called at anytime or have 
         * the same permissions. */
        if (command.equalsIgnoreCase("join") || command.equalsIgnoreCase("j")){
            join(nick, host);
        } else if (command.equalsIgnoreCase("leave") || command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("l") || command.equalsIgnoreCase("q")){
            leave(nick);
        } else if (command.equalsIgnoreCase("stop")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!inProgress) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                cancelAutoStarts(nick);
            }
        } else if (command.equalsIgnoreCase("cash")) {
            if (params.length > 0){
                showPlayerCash(params[0]);
            } else {
                showPlayerCash(nick);
            }
        } else if (command.equalsIgnoreCase("netcash") || command.equalsIgnoreCase("net")) {
            if (params.length > 0){
                showPlayerNetCash(params[0]);
            } else {
                showPlayerNetCash(nick);
            }
        } else if (command.equalsIgnoreCase("bank")) {
            if (params.length > 0){
                showPlayerBank(params[0]);
            } else {
                showPlayerBank(nick);
            }
        } else if (command.equalsIgnoreCase("bankrupts")) {
            if (params.length > 0){
                showPlayerBankrupts(params[0]);
            } else {
                showPlayerBankrupts(nick);
            }
        } else if (command.equalsIgnoreCase("winnings")) {
            if (params.length > 0){
                showPlayerWinnings(params[0]);
            } else {
                showPlayerWinnings(nick);
            }
        } else if (command.equalsIgnoreCase("winrate")) {
            if (params.length > 0){
                showPlayerWinRate(params[0]);
            } else {
                showPlayerWinRate(nick);
            }
        } else if (command.equalsIgnoreCase("rounds")) {
            if (params.length > 0){
                showPlayerRounds(params[0]);
            } else {
                showPlayerRounds(nick);
            }
        } else if (command.equalsIgnoreCase("player") || command.equalsIgnoreCase("p")){
            if (params.length > 0){
                showPlayerAllStats(params[0]);
            } else {
                showPlayerAllStats(nick);
            }
        } else if (command.equalsIgnoreCase("deposit") || command.equalsIgnoreCase("withdraw")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 0){
                    try {
                        if (command.equalsIgnoreCase("deposit")) {
                            transfer(nick, Integer.parseInt(params[0]));
                        } else {
                            transfer(nick, -Integer.parseInt(params[0]));
                        }
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("waitlist")) {
            showMsg(getMsg("waitlist"), getPlayerListString(waitlist));
        } else if (command.equalsIgnoreCase("blacklist")) {
            showMsg(getMsg("blacklist"), getPlayerListString(blacklist));
        } else if (command.equalsIgnoreCase("rank")) {
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 1){
                    try {
                        showPlayerRank(params[1].toLowerCase(), params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else if (params.length == 1){
                    try {
                        showPlayerRank(nick, params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showPlayerRank(nick, "cash");
                }
            }
        } else if (command.equalsIgnoreCase("top")) {
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 1){
                    try {
                        showTopPlayers(params[1].toLowerCase(), Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else if (params.length == 1){
                    try {
                        showTopPlayers("cash", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showTopPlayers("cash", 5);
                }
            }
        } else if (command.equalsIgnoreCase("simple")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equalsIgnoreCase("stats")){
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                showMsg(getGameStatsStr());
            }
        } else if (command.equalsIgnoreCase("grules")) {
            informPlayer(nick, getGameRulesStr());
        } else if (command.equalsIgnoreCase("ghelp")) {
            if (params.length == 0){
                informPlayer(nick, getMsg("game_help"), commandChar, commandChar, commandChar);
            } else {
                informPlayer(nick, getCommandHelp(params[0].toLowerCase()));
            }
        } else if (command.equalsIgnoreCase("gcommands")) {
            informPlayer(nick, getGameNameStr() + " commands:");
            informPlayer(nick, getCommandsStr());
            if (channel.isOp(user)) {
                informPlayer(nick, getGameNameStr() + " Op commands:");
                informPlayer(nick, getOpCommandsStr());
            }
        } else if (command.equalsIgnoreCase("game")) {
            showMsg(getMsg("game_name"), getGameNameStr());
        // Op Commands
        } else if (command.equalsIgnoreCase("fj") || command.equalsIgnoreCase("fjoin")){
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 0){
                    String fNick = params[0];
                    Iterator<User> it = channel.getUsers().iterator();
                    while(it.hasNext()){
                        User u = it.next();
                        if (u.getNick().equalsIgnoreCase(fNick)){
                            fjoin(nick, u.getNick(), u.getHostmask());
                            return;
                        }
                    }
                    informPlayer(nick, getMsg("nick_not_found"), fNick);
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("fl") || command.equalsIgnoreCase("fq") || command.equalsIgnoreCase("fquit") || command.equalsIgnoreCase("fleave")){
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 0){
                    leave(params[0]);
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("fdeposit") || command.equalsIgnoreCase("fwithdraw")) {
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 1) {
                    if (!isJoined(params[0])) {
                        informPlayer(nick, params[0] + " is not currently joined!");
                    } else if (inProgress) {
                        informPlayer(nick, getMsg("wait_round_end"));
                    } else {
                        try {
                            if (command.equalsIgnoreCase("fdeposit")) {
                                transfer(params[0], Integer.parseInt(params[1]));
                            } else {
                                transfer(params[0], -Integer.parseInt(params[1]));
                            }
                        } catch (NumberFormatException e) {
                            informPlayer(nick, getMsg("bad_parameter"));
                        }
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("cards") || command.equalsIgnoreCase("discards")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int num = Integer.parseInt(params[0]);
                        if (command.equalsIgnoreCase("cards") && deck.getNumberCards() > 0) {
                            infoDeckCards(nick, 'c', num);
                        } else if (command.equalsIgnoreCase("discards") && deck.getNumberDiscards() > 0) {
                            infoDeckCards(nick, 'd', num);
                        } else {
                            informPlayer(nick, "Empty!");
                        }
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("settings")) {
            if (isOpCommandAllowed(user, nick)) {
                informPlayer(nick, getGameNameStr() + " settings:");
                informPlayer(nick, getSettingsStr());
            }
        } else if (command.equalsIgnoreCase("set")){
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        set(params[0].toLowerCase(), Integer.parseInt(params[1]));
                        saveIniFile();
                        showMsg(getMsg("setting_updated"), params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equalsIgnoreCase("get")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        showMsg(getMsg("setting"), params[0].toLowerCase(), get(params[0].toLowerCase()));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        }
    }
    
    /**
     * Processes a user join event in the game channel.
     * 
     * @param user The IRC user who has joined.
     */
    protected void processJoin(User user){
        String nick = user.getNick();
        if (loadPlayerStat(nick, "exists") != 1){
            // informPlayer(nick, getMsg("new_nick"), getGameNameStr(), commandChar);
        }
    }
    
    /**
     * Processes a user quit or part event in the game channel.
     * 
     * @param user The IRC user who has quit or parted.
     */
    protected void processQuit(User user){
        String nick = user.getNick();
        if (isJoined(nick) || isWaitlisted(nick)){
            leave(nick);
        }
    }
    
    /**
     * Process a user change nick event in the game channel.
     * 
     * @param user The IRC user who has changed nicks.
     * @param oldNick The old nick of the user.
     * @param newNick The new nick of the user.
     */
    protected void processNickChange(User user, String oldNick, String newNick){
        String hostmask = user.getHostmask();
        if (isJoined(oldNick) || isWaitlisted(oldNick)){
            informPlayer(newNick, getMsg("nick_change"));
            if (isJoined(oldNick)){
                manager.deVoice(channel, user);
                leave(oldNick);
            } else if(isWaitlisted(oldNick)){
                removeWaitlisted(oldNick);
            }
            join(newNick, hostmask);
        }
    }
    
    /* 
     * Accessor methods 
     * Returns or sets object properties.
     */
    /**
     * Returns the game channel.
     * @return the game channel
     */
    public Channel getChannel(){
        return channel;
    }
    
    /**
     * Public accessor required for GameManager.
     * @return true if a round is in progress
     */
    public boolean isInProgress() {
        return inProgress;
    }
    
    /* 
     * Game management methods
     * These methods control the game flow. 
     */
    /**
     * Starts a new round of the game.
     */
    abstract protected void startRound();
    
    /**
     * Progresses the game during a round.
     */
    abstract protected void continueRound();
    
    /**
     * Finishes a round of the game
     */
    abstract protected void endRound();
    
    /**
     * Terminates the game.
     */
    abstract public void endGame();
    
    /**
     * Resets the game, usually at the end of a round.
     */
    abstract protected void resetGame();
    
    /**
     * Processes a player's departure from the game.
     * @param nick the player's nick
     */
    abstract protected void leave(String nick);
    
    /**
     * Saves the INI file associated with the game instance.
     */
    abstract protected void saveIniFile();
    
    /**
     * Initializes game settings and properties.
     */
    protected void initialize() {
        // In-game properties
        inProgress = false;
        roundEnded = false;
        startCount = 0;
    }
    
    /**
     * Overwrites the value of a setting, if it exists.
     * @param setting setting name
     * @param value new value
     * @throws IllegalArgumentException bad setting
     */
    protected void set(String setting, int value) throws IllegalArgumentException {
        if (!settings.containsKey(setting)) {
            throw new IllegalArgumentException();
        }
        settings.put(setting, value);
    }
    
    /**
     * Retrieves the value of a setting, if it exists.
     * @param setting setting name
     * @return the setting value
     * @throws IllegalArgumentException bad setting
     */
    protected int get(String setting) throws IllegalArgumentException {
        if (!settings.containsKey(setting)){
            throw new IllegalArgumentException();
        }
        return settings.get(setting);
    }
    
    /**
     * Returns whether or not a setting has a value greater than 0.
     * @param setting setting name
     * @return true if value is greater than 0
     * @throws IllegalArgumentException bad setting
     */
    public boolean has(String setting) throws IllegalArgumentException {
        if (!settings.containsKey(setting)){
            throw new IllegalArgumentException();
        }
        return get(setting) > 0;
    }
    
    /**
     * Loads the settings from the INI file.
     */
    protected void loadIni() {
        try {
            BufferedReader in = new BufferedReader(new FileReader(iniFile));
            String str;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    set(st.nextToken(), Integer.parseInt(st.nextToken()));
                }
            }
            in.close();
        } catch (IOException e) {
            /* load defaults if INI file is not found */
            manager.log(iniFile + " not found! Creating new " + iniFile + "...");
            initialize();
            saveIniFile();
        }
    }
    
    /**
     * Loads data from strlib file.
     * @param file file path
     */
    protected void loadStrLib(String file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            StringTokenizer st;
            String str;
            while (in.ready()){
                // Replace all unicode
                str = in.readLine().replaceAll("u0002", Colors.BOLD);
                // Skips all lines that begin with #
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    msgMap.put(st.nextToken(), st.nextToken());
                }
            }
            in.close();
        } catch (IOException e) {
            manager.log("Error reading from " + file + "!");
        }
    }
    
    /**
     * Loads data from help file.
     * @param file file path
     */
    protected void loadHelp(String file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String[] st, st2;
            String str, cmd, params, alias, def, line;
            while (in.ready()){
                str = in.readLine();
                // Skips all lines that begin with #
                if (!str.startsWith("#") && str.contains("|")) {
                    st = str.split("\\|");
                    
                    // Command name
                    if (st[0].equals("")) {
                        cmd = "---";
                    } else {
                        cmd = st[0];
                    }
                    
                    // Parameters
                    if (st[1].equals("")) {
                        params = "---";
                    } else {
                        params = st[1];
                    }
                    
                    // Aliases
                    if (st[2].equals("")) {
                        alias = "---";
                    } else {
                        alias = st[2];
                        st2 = st[2].split(",");
                        for (String a : st2) {
                            aliasMap.put(a, cmd);
                        }
                    }
                    
                    // Definition
                    if (st[3].equals("")) {
                        def = "---";
                    } else {
                        def = st[3];
                    }
                    
                    line = String.format(getMsg("help_def"), cmd, params, alias, def);
                    if (def.contains("Op command")) {
                        opCmdMap.put(cmd, line);
                    } else {
                        cmdMap.put(cmd, line);
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            manager.log("Error reading from " + file + "!");
        }
    }
    
    /**
     * Attempts to add a player to the game.
     * @param nick the player's nick
     * @param host the player's host
     */
    protected void join(String nick, String host) {
        CardGame game = manager.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(nick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(nick, getMsg("is_joined"));
        } else if (isBlacklisted(nick) || manager.isBlacklisted(nick)) {
            informPlayer(nick, getMsg("on_blacklist"));
        } else if (game != null) {
            informPlayer(nick, getMsg("is_joined_other"), game.getGameNameStr(), game.getChannel().getName());
        } else if (inProgress) {
            if (isWaitlisted(nick)) {
                informPlayer(nick, getMsg("on_waitlist"));
            } else {
                addWaitlistPlayer(nick, host);
            }
        } else {
            addPlayer(nick, host);
        }
    }
    
    /**
     * Attempts to force add a player to the game.
     * @param OpNick the channel Op
     * @param nick the player to be force joined
     * @param host the player's host
     */
    protected void fjoin(String OpNick, String nick, String host) {
        CardGame game = manager.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(OpNick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(OpNick, getMsg("is_joined_nick"), nick);
        } else if (isBlacklisted(nick) || manager.isBlacklisted(nick)) {
            informPlayer(OpNick, getMsg("on_blacklist_nick"), nick);
        } else if (game != null) {
            informPlayer(OpNick, getMsg("is_joined_other_nick"), nick, game.getGameNameStr(), game.getChannel().getName());
        } else if (inProgress) {
            if (isWaitlisted(nick)) {
                informPlayer(OpNick, getMsg("on_waitlist_nick"), nick);
            } else {
                addWaitlistPlayer(nick, host);
            }
        } else {
            addPlayer(nick, host);
        }
    }
    
    /**
     * Attempts to remove a player from the game.
     * @param p the player
     */
    protected void leave(Player p){
        leave(p.getNick());
    }
    
    /**
     * Moves players on the waitlist to the joined list and clears the waitlist.
     */
    protected void mergeWaitlist(){
        for (Player p : waitlist) {
            addPlayer(p);
        }
        waitlist.clear();
    }
    
    protected void cancelAutoStarts(String nick){
        if (startCount != 0) {
            startCount = 0;
            showMsg(getMsg("stop"));
        } else {
            informPlayer(nick, getMsg("stop_no_autostarts"));
        }
    }
    
    /**
     * Sets a RespawnTask for a player who has gone bankrupt.
     * @param p the bankrupt player
     */
    protected void setRespawnTask(Player p) {
        // Calculate extra time penalty for players with debt
        int penalty = Math.max(-1 * p.get("bank") / 1000 * 60, 0);
        informPlayer(p.getNick(), getMsg("bankrupt_info"), (get("respawn") + penalty)/60.);
        RespawnTask task = new RespawnTask(p, this);
        gameTimer.schedule(task, (get("respawn")+penalty)*1000);
        respawnTasks.add(task);
    }
    
    /**
     * Cancels all RespawnTasks.
     */
    protected void cancelRespawnTasks() {
        for (RespawnTask task : respawnTasks) {
            task.cancel();
        }
        respawnTasks.clear();
        gameTimer.purge();
        // Fast-track loans
        for (Player p : blacklist) {
            p.set("cash", get("cash"));
            p.add("bank", -get("cash"));
            savePlayerData(p);
        }
    }
    
    /**
     * Accessor for the respawnTasks ArrayList.
     * @return respawnTasks ArrayList
     */
    protected ArrayList<RespawnTask> getRespawnTasks() {
        return respawnTasks;
    }
    
    /**
     * Schedules a new startRoundTask.
     */
    protected void setStartRoundTask(){
        startRoundTask = new StartRoundTask(this);
        gameTimer.schedule(startRoundTask, get("startwait") * 1000);
    }
    
    /**
     * Cancels the any scheduled startRoundTask.
     */
    protected void cancelStartRoundTask(){
        if (startRoundTask != null){
            startRoundTask.cancel();
            gameTimer.purge();
        }
    }
    
    /**
     * Schedules a new idleOutTask.
     */
    protected void setIdleOutTask() {
        if (get("idlewarning") < get("idle")) {
            idleWarningTask = new IdleWarningTask(currentPlayer, this);
            gameTimer.schedule(idleWarningTask, get("idlewarning")*1000);
        }
        idleOutTask = new IdleOutTask(currentPlayer, this);
        gameTimer.schedule(idleOutTask, get("idle")*1000);
    }
    
    /**
     * Cancels any scheduled idleOutTask.
     */
    protected void cancelIdleOutTask() {
        if (idleOutTask != null){
            idleWarningTask.cancel();
            idleOutTask.cancel();
            gameTimer.purge();
        }
    }
    
    /**
     * Checks if an Op command is allowed.
     * @param user command issuer
     * @param nick the user's nick
     * @return true if the User is allowed use Op commands
     */
    protected boolean isOpCommandAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            return true;
        }
        return false;
    }
    
    /* 
     * Player management methods 
     * Controls joining, leaving, waitlisting, and bankruptcies. Also includes
     * toggling of simple, paying debt.
     */
    
    /**
     * Adds a player to the joined list.
     * @param nick the player's nick
     * @param host the player's host
     */
    abstract protected void addPlayer(String nick, String host);
    
    /**
     * Adds a player to the waitlist.
     * @param nick the player's nick
     * @param host the player's host
     */
    abstract protected void addWaitlistPlayer(String nick, String host);
    
    /**
     * Adds a player to the joined list.
     * @param p the player
     */
    protected void addPlayer(Player p){
        User user = findUser(p.getNick());
        joined.add(p);
        loadPlayerData(p);
        if (user != null){
            manager.voice(channel, user);
        }
        showMsg(getMsg("join"), p.getNickStr(), joined.size());
    }
    
    /**
     * Checks if the player is on the joined list.
     * @param nick the player's nick
     * @return true if on the joined list
     */
    public boolean isJoined(String nick){
        return (findJoined(nick) != null);
    }
    
    /**
     * Checks if the player is on the waitlist.
     * @param nick the player's nick
     * @return true if on the waitlist
     */
    public boolean isWaitlisted(String nick){
        return (findWaitlisted(nick) != null);
    }
    
    /**
     * Checks if the player is on the blacklist.
     * @param nick the player's nick
     * @return true if on the blacklist
     */
    public boolean isBlacklisted(String nick){
        return (findBlacklisted(nick) != null);
    }
    
    /**
     * Removes the specified player from the joined list, based on nick.
     * @param nick the player's nick
     */
    protected void removeJoined(String nick){
        Player p = findJoined(nick);
        removeJoined(p);
    }
    
    /**
     * Removes the specified player from the joined list.
     * @param p the player
     */
    protected void removeJoined(Player p){
        User user = findUser(p.getNick());
        joined.remove(p);
        savePlayerData(p);
        if (user != null){
            manager.deVoice(channel, user);
        }
        if (!p.has("cash")) {
            showMsg(getMsg("unjoin_bankrupt"), p.getNickStr(), joined.size());
        } else {
            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
        }
    }
    
    /**
     * Removes the specified player from the waitlist based on nick.
     * @param nick the player's nick
     */
    protected void removeWaitlisted(String nick){
        Player p = findWaitlisted(nick);
        removeWaitlisted(p);
    }
    
    /**
     * Removes the specified player from the waitlist.
     * @param p the player
     */
    protected void removeWaitlisted(Player p){
        waitlist.remove(p);
    }

    /**
     * Removes the specified player from the blacklist.
     * @param p the player
     */
    protected void removeBlacklisted(Player p){
        blacklist.remove(p);
    }
    
    /**
     * Searches for a user in the game channel.
     * @param nick the user's nick
     * @return the User instance or null if not found
     */
    protected User findUser(String nick){
        for (User user : manager.getUsers(channel)) {
            if (user.getNick().equalsIgnoreCase(nick)){
                return user;
            }
        }
        return null;
    }
    
    /**
     * Searches for a player who has joined.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findJoined(String nick){
        for (Player p : joined) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Searches for a player on the waitlist.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findWaitlisted(String nick){
        for (Player p : waitlist) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Searches for a player on the blacklist.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findBlacklisted(String nick){
        for (Player p : blacklist) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Returns the player that comes next after currentPlayer.
     * Returns null if currentPlayer is the last player.
     * @return the next player or null
     */
    protected Player getNextPlayer(){
        int index = joined.indexOf(currentPlayer);
        if (index + 1 < joined.size()){
            return joined.get(index + 1);
        } else {
            return null;
        }
    }
    
    /**
     * Toggles a player's "simple" status.
     * Players with "simple" set to true will have game information sent via
     * notice. Players with "simple" set to false will have game information sent
     * via message.
     * 
     * @param nick the player's nick
     */
    protected void togglePlayerSimple(String nick){
        Player p = findJoined(nick);
        p.set("simple", (p.get("simple") + 1) % 2);
        if (p.isSimple()){
            manager.sendNotice(nick, "Game info will now be noticed to you.");
        } else {
            manager.sendMessage(nick, "Game info will now be messaged to you.");
        }
    }
    
    /**
     * Transfers the specified amount from a player's stack to his bankroll.
     * A negative amount indicates a withdrawal. A positive amount indicates
     * a deposit.
     * 
     * @param nick the player's nick
     * @param amount the amount to transfer
     */
    protected void transfer(String nick, int amount){
        Player p = findJoined(nick);
        // Ignore a transfer of $0
        if (amount == 0){
            informPlayer(nick, getMsg("no_transaction"));
        // Disallow withdrawals for bankrolls with insufficient funds
        } else if (amount < 0 && p.get("bank") < -amount){
            informPlayer(nick, getMsg("no_withdrawal"));
        // Disallow deposits of amounts larger than cash
        } else if (amount > 0 && amount > p.get("cash")){
            informPlayer(nick, getMsg("no_deposit_cash"));
        // Disallow deposits that leave the player with $0 cash
        } else if (amount > 0 && amount == p.get("cash")){
            informPlayer(nick, getMsg("no_deposit_bankrupt"));
        } else {
            p.bankTransfer(amount);
            savePlayerData(p);
            if (amount > 0){
                showMsg(getMsg("deposit"), p.getNickStr(), amount, p.get("cash"), p.get("bank"));
            } else {
                showMsg(getMsg("withdraw"), p.getNickStr(), -amount, p.get("cash"), p.get("bank"));
            }
        }
    }
    
    /**
     * Devoices all players joined in a game and saves their data.
     * This method only needs to be called when a game is shutdown.
     */
    protected void devoiceAll(){
        String modeSet = "";
        String nickStr = "";
        int count = 0;
        for (Player p : joined) {
            savePlayerData(p);
            modeSet += "v";
            nickStr += " " + p.getNick();
            count++;
            if (count % 4 == 0) {
                manager.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
                nickStr = "";
                modeSet = "";
                count = 0;
            }
        }
        if (count > 0) {
            manager.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
        }
    }
    
    /* Player file management methods
     * Methods for reading and saving player data.
     */
    
    /**
     * Returns the total number of players who have played this game.
     * Counts the number of players who have played more than one round of this
     * game.
     * 
     * @return the total counted from players.txt
     */
    abstract protected int getTotalPlayers();
    
    /**
     * Returns the specified statistic for a nick.
     * If a player is already joined or blacklisted, the value is read from the
     * Player object. Otherwise, a search is done in players.txt.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic
     */
    protected int getPlayerStat(String nick, String stat){
        if (isJoined(nick) || isBlacklisted(nick)){
            Player p = findJoined(nick);
            if (p == null){
                p = findBlacklisted(nick);
            }
            return p.get(stat);
        }
        return loadPlayerStat(nick, stat);
    }
    
    /**
     * Searches players.txt for a specific player's statistic.
     * If the player or statistic is not found, Integer.MIN_VALUE is returned.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic or Integer.MIN_VALUE if not found
     */
    protected int loadPlayerStat(String nick, String stat){
        try {
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            for (PlayerRecord record : records) {
                if (nick.equalsIgnoreCase(record.getNick())){
                    return record.get(stat);
                }
            }
            return Integer.MIN_VALUE;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return Integer.MIN_VALUE;
        }
    }
    
    /**
     * Loads a Player's data from players.txt.
     * If a matching nick is found then all statistics are loaded. Otherwise,
     * the player is loaded with default values.
     * 
     * @param p the Player to find
     */
    protected void loadPlayerData(Player p) {
        try {
            boolean found = false;
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);

            for (PlayerRecord record : records) {
                if (p.getNick().equalsIgnoreCase(record.getNick())) {
                    if (record.get("cash") <= 0) {
                        p.set("cash", get("cash"));
                    } else {
                        p.set("cash", record.get("cash"));
                    }
                    p.set("bank", record.get("bank"));
                    p.set("bankrupts", record.get("bankrupts"));
                    p.set("bjwinnings", record.get("bjwinnings"));
                    p.set("bjrounds", record.get("bjrounds"));
                    p.set("tpwinnings", record.get("tpwinnings"));
                    p.set("tprounds", record.get("tprounds"));
                    p.set("ttwins", record.get("ttwins"));
                    p.set("ttplayed", record.get("ttplayed"));
                    p.set("simple", record.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.set("cash", get("cash"));
                informPlayer(p.getNick(), getMsg("new_player"), getGameNameStr(), get("cash"));
            }
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    /**
     * Saves a Player's data into players.txt.
     * If a matching nick is found, the existing data is overwritten. Otherwise,
     * a new line is added for the Player.
     * 
     * @param p the Player to save
     */
    protected void savePlayerData(Player p){
        boolean found = false;
        ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
        
        try {
            loadPlayerFile(records);
            for (PlayerRecord record : records) {
                if (p.getNick().equalsIgnoreCase(record.getNick())) {
                    record.set("cash", p.get("cash"));
                    record.set("bank", p.get("bank"));
                    record.set("bankrupts", p.get("bankrupts"));
                    record.set("bjwinnings", p.get("bjwinnings"));
                    record.set("bjrounds", p.get("bjrounds"));
                    record.set("tpwinnings", p.get("tpwinnings"));
                    record.set("tprounds", p.get("tprounds"));
                    record.set("ttwins", p.get("ttwins"));
                    record.set("ttplayed", p.get("ttplayed"));
                    record.set("simple", p.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                records.add(new PlayerRecord(p.getNick(), p.get("cash"),
                                        p.get("bank"), p.get("bankrupts"),
                                        p.get("bjwinnings"), p.get("bjrounds"),
                                        p.get("tpwinnings"), p.get("tprounds"),
                                        p.get("ttwins"), p.get("ttplayed"),
                                        p.get("simple")));
            }
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }

        try {
            savePlayerFile(records);
        } catch (IOException e) {
            manager.log("Error writing to players.txt!");
        }
    }
    
    /**
     * Checks if players.txt exists and creates it if it doesn't exist.
     */
    protected final void checkPlayerFile(){
        try {
            BufferedReader out = new BufferedReader(new FileReader("players.txt"));
            out.close();
        } catch (IOException e){
            manager.log("players.txt not found! Creating new players.txt...");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
                out.close();
            } catch (IOException f){
                manager.log("Error creating players.txt.");
            }
        }
    }
    
    /**
     * Loads players.txt.
     * Reads the file's contents into an ArrayList of PlayerRecord.
     * 
     * @param records stores the records read from file
     * @throws IOException
     */
    protected void loadPlayerFile(ArrayList<PlayerRecord> records) throws IOException {
        String nick;
        int cash, bank, bankrupts, bjwinnings, bjrounds, tpwinnings, tprounds, ttwins, ttplayed, simple;
        
        BufferedReader in = new BufferedReader(new FileReader("players.txt"));
        StringTokenizer st;
        while (in.ready()){
            st = new StringTokenizer(in.readLine());
            nick = st.nextToken();
            cash = Integer.parseInt(st.nextToken());
            bank = Integer.parseInt(st.nextToken());
            bankrupts = Integer.parseInt(st.nextToken());
            bjwinnings = Integer.parseInt(st.nextToken());
            bjrounds = Integer.parseInt(st.nextToken());
            tpwinnings = Integer.parseInt(st.nextToken());
            tprounds = Integer.parseInt(st.nextToken());
            ttwins = Integer.parseInt(st.nextToken());
            ttplayed = Integer.parseInt(st.nextToken());
            simple = Integer.parseInt(st.nextToken());
            records.add(new PlayerRecord(nick, cash, bank, bankrupts, bjwinnings, 
                                        bjrounds, tpwinnings, tprounds, ttwins, ttplayed, simple));
        }
        in.close();
    }
    
    /**
     * Saves data to players.txt.
     * Saves an ArrayList of StatFileLine to the file.
     * 
     * @param records
     * @throws IOException
     */
    protected void savePlayerFile(ArrayList<PlayerRecord> records) throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
        
        for (PlayerRecord record : records) {
            out.println(record);
        }
        out.close();
    }
    
    /* Methods for managing game stats. */
    /**
     * Loads the stats for the game from a file.
     */
    abstract protected void loadGameStats();
    
    /**
     * Saves the stats for the game to a file.
     */
    abstract protected void saveGameStats();
    
    /* Generic card management methods */
    
    /**
     * Takes a card from the deck and adds it to the discard pile.
     */
    protected void burnCard() {
        deck.addToDiscard(deck.takeCard());
    }
    
    /**
     * Takes a card from the deck and adds it to the specified hand.
     * @param h
     */
    protected void dealCard(Hand h) {
        h.add(deck.takeCard());
    }
    
    /* 
     * Channel output methods to reduce clutter.
     * These methods will all send a specific message or set of
     * messages to the main channel.
     */
        
    /**
     * Outputs a player's winnings for the game to the game channel.
     * @param nick the player's nick
     */
    abstract protected void showPlayerWinnings(String nick);
    
    /**
     * Outputs a player's win rate for the game to the game channel.
     * @param nick the player's nick
     */
    abstract protected void showPlayerWinRate(String nick);
    
    /**
     * Outputs the number of rounds of the game a player has played to
     * the game channel.
     * @param nick the player's nick
     */
    abstract protected void showPlayerRounds(String nick); 
    
    /**
     * Outputs all of a player's stats relevant to this game.
     * Sends the list of statistics separated by '|' character to the game
     * channel.
     * 
     * @param nick the player's nick
     */
    abstract protected void showPlayerAllStats(String nick);
    
    /**
     * Outputs a player's rank for the given stat to the game channel.
     * 
     * @param nick the player's nick
     * @param stat the stat name
     */
    abstract protected void showPlayerRank(String nick, String stat);
    
    /**
     * Outputs the top N players for a given statistic.
     * Outputs the list of players sorted by ranking to the game channel.
     * 
     * @param stat the statistic used for ranking
     * @param n the length of the list up to n players
     */
    abstract protected void showTopPlayers(String stat, int n);
    
    /**
     * Sends a message to the game channel.
     * @param msg the message to send
     * @param args the parameters for the message
     */
    protected void showMsg(String msg, Object... args){
        manager.sendMessage(channel, String.format(msg, args));
    }
    
    /**
     * Outputs the start round message to the game channel.
     */
    protected void showStartRound(){
        if (startCount > 0){
            showMsg(getMsg("start_round_auto"), getGameNameStr(), get("startwait"), startCount);
        } else {
            showMsg(getMsg("start_round"), getGameNameStr(), get("startwait"));
        }
    }
    
    /**
     * Outputs the amount in a player's stack to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerCash(String nick){
        int cash = getPlayerStat(nick, "cash");
        if (cash != Integer.MIN_VALUE){
            showMsg(getMsg("player_cash"), formatNoPing(nick), cash);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Outputs a player's net cash to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerNetCash(String nick){
        int netcash = getPlayerStat(nick, "netcash");
        if (netcash != Integer.MIN_VALUE){
            showMsg(getMsg("player_net"), formatNoPing(nick), netcash);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Outputs the amount in a player's bank to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerBank(String nick){
        int bank = getPlayerStat(nick, "bank");
        if (bank != Integer.MIN_VALUE){
            showMsg(getMsg("player_bank"), formatNoPing(nick), bank);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Outputs the number of times a player has gone bankrupt to the game 
     * channel.
     * @param nick the player's nick
     */
    protected void showPlayerBankrupts(String nick){
        int bankrupts = getPlayerStat(nick, "bankrupts");
        if (bankrupts != Integer.MIN_VALUE){
            showMsg(getMsg("player_bankrupts"), formatNoPing(nick), bankrupts);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /* 
     * Private messages to players.
     * These methods will all send notices/messages to the specified nick.
     */
    
    /**
     * Sends a message to a player via PM or notice depending on simple status.
     * @param nick the player's nick
     * @param message the message
     * @param args parameters to be included in the message
     */
    protected void informPlayer(String nick, String message, Object... args){
        if (getPlayerStat(nick, "simple") > 0) {
            manager.sendNotice(nick, String.format(message, args));
        } else {
            manager.sendMessage(nick, String.format(message, args));
        }
    }
    
    /**
     * Reveals cards from the card deck.
     * @param nick the player
     * @param type undealt cards or discards
     * @param num the number of cards to reveal
     */
    protected void infoDeckCards(String nick, char type, int num){
        if (num < 1){
            throw new IllegalArgumentException();
        }
        int cardIndex=0, numOut, n;
        String cardStr;
        ArrayList<Card> tCards;
        if (type == 'c'){
            tCards = deck.getCards();
        } else {
            tCards = deck.getDiscards();
        }
        n = Math.min(num, tCards.size());
        while(n > 0){
            cardStr = cardIndex+"";
            numOut = Math.min(n, 25);
            cardStr += "-" + (cardIndex+numOut-1) + ":";
            n -= numOut;
            for (int ctr=cardIndex; ctr < cardIndex+numOut; ctr++){
                cardStr += " " + tCards.get(ctr);
            }
            cardIndex += numOut;
            informPlayer(nick, cardStr + Colors.NORMAL);
        }
    }
    
    /* Formatted strings */
    /**
     * Returns the name of the game formatted in bold.
     * @return the game name in bold
     */
    abstract public String getGameNameStr();
    
    /**
     * Returns the rules for the game.
     * @return the rules
     */
    abstract protected String getGameRulesStr();
    
    /**
     * Returns the stats for the game.
     * @return the stats
     */
    abstract protected String getGameStatsStr();
    
    /**
     * Returns a list of commands for this game based on its help file.
     * @return a list of commands
     */
    protected String getCommandsStr() {
        String commandList = "";
        String[] keys = cmdMap.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            commandList += key + ", ";
        }
        return commandList.substring(0, commandList.length() - 2);
    }
    
    /**
     * Returns a list of Op commands for this game based on its help file.
     * @return a list of Op commands
     */
    protected String getOpCommandsStr() {
        String commandList = "";
        String[] keys = opCmdMap.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            commandList += key + ", ";
        }
        return commandList.substring(0, commandList.length() - 2);
    }
    
    /**
     * Returns a list of settings for this game based on settings.
     * @return a list of settings
     */
    protected String getSettingsStr() {
        String settingsList = "";
        String[] keys = settings.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            settingsList += key + ", ";
        }
        return settingsList.substring(0, settingsList.length() - 2);
    }
    
    public static String formatDecimal(double n) {
        return String.format("%,.2f", n);
    }
    public static String formatNoDecimal(double n) {
        return String.format("%,.0f", n);
    }
    public static String formatNumber(int n){
        return String.format("%,d", n);
    }
    public static String formatHeader(String str){
        return Colors.BOLD + Colors.YELLOW + ",01" + str + Colors.NORMAL;
    }
    public static String formatBold(String str){
        return Colors.BOLD + str + Colors.BOLD;
    }
    public static String formatBold(int value){
        return formatBold(value + "");
    }
    public static String formatNoPing(String str) {
        return str.substring(0,1) + "\u200b" + str.substring(1);
    }
    
    /**
     * Generates a string from a list of players.
     * @param playerList the list of players
     * @return a string containing number of players and the players' nicks
     */
    protected static String getPlayerListString(ArrayList<? extends Player> playerList){
        int size = playerList.size();
        String outStr = formatBold(size);
        if (size == 0){
            outStr += " players";
        } else if (size == 1){
            outStr += " player: ";
        } else {
            outStr += " players: ";
        }
        for (int ctr=0; ctr < size; ctr++){
            if (ctr == size-1){
                outStr += playerList.get(ctr).getNick();
            } else {
                outStr += playerList.get(ctr).getNick()+", ";
            }
        }
        return outStr;
    }
    
    /**
     * Returns the help data on the specified command.
     * @param command
     * @return the help data for the command
     */
    protected String getCommandHelp(String command){
        if (cmdMap.containsKey(command)){
            return cmdMap.get(command);
        } else if (opCmdMap.containsKey(command)) {
            return opCmdMap.get(command);
        } else if (aliasMap.containsKey(command)) {
            if (cmdMap.containsKey(aliasMap.get(command))) {
                return cmdMap.get(aliasMap.get(command));
            } else {
                return opCmdMap.get(aliasMap.get(command));
            }
        } else {
            return "Error: Help for \'" + command + "\' not found!";
        }
    }
    
    /**
     * Returns the message based on the specified key.
     * @param msgKey the message key
     * @return the message
     */
    protected String getMsg(String msgKey) {
        if (msgMap.containsKey(msgKey)){
            return msgMap.get(msgKey);
        } else {
            return "Error: Message for \'" + msgKey + "\' not found!";
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof CardGame) {
            CardGame c = (CardGame) o;
            if (channel.equals(c.channel) && name.equals(c.name) &&
                hashCode() == c.hashCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + channel.hashCode();
        hash = 17 * hash + name.hashCode();
        return hash;
    }
}