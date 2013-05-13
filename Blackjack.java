/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
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

package irccasino;

import java.util.*;
import java.io.*;

import org.pircbotx.*;
import org.pircbotx.hooks.events.*;

public class Blackjack extends CardGame {
	public static class IdleOutTask extends TimerTask {
		BlackjackPlayer player;
		Blackjack game;

		public IdleOutTask(BlackjackPlayer p, Blackjack g) {
			player = p;
			game = g;
		}

		@Override
		public void run() {
			if (player == game.getCurrentPlayer()) {
				player.setQuit(true);
				game.bot.sendMessage(game.channel, player.getNickStr()
						+ " has wasted precious time and idled out.");
				if (!game.isBetting()) {
					game.stay();
				} else {
					game.leave(player.getNick());
				}
			}
		}
	}

	public static class IdleShuffleTask extends TimerTask {
		Blackjack game;

		public IdleShuffleTask(Blackjack g) {
			game = g;
		}

		@Override
		public void run() {
			game.shuffleShoe();
		}
	}

	public static class HouseStat {
		int decks, rounds, cash;

		public HouseStat() {
			this(0, 0, 0);
		}
		public HouseStat(int a, int b, int c) {
			decks = a;
			rounds = b;
			cash = c;
		}
		
		public int getNumDecks() {
			return decks;
		}
		public int getNumRounds() {
			return rounds;
		}
		public void incrementNumRounds() {
			rounds++;
		}
		public int getCash() {
			return cash;
		}
		public void addCash(int amount) {
			cash += amount;
		}
		public String toString() {
			return formatNumber(rounds) + " round(s) have been played using " 
					+ formatNumber(decks) + " deck shoes. The house has won $"
					+ formatNumber(cash) + " during those round(s).";
		}
	}

	private BlackjackPlayer dealer;
	private boolean insuranceBets, countEnabled, holeEnabled;
	private int shoeDecks, idleShuffleTime;
	private Timer idleShuffleTimer;	
	private ArrayList<HouseStat> stats;
	private HouseStat house;

	/**
	 * Class constructor for Blackjack, a subclass of CardGame.
	 * 
	 * @param parent
	 *            the bot that creates an instance of this ListenerAdapter
	 * @param gameChannel
	 *            the IRC channel in which the game is to be run.
	 */
	public Blackjack(PircBotX parent, Channel gameChannel) {
		super(parent, gameChannel);
		gameName = "Blackjack";
		dealer = new BlackjackPlayer(bot.getNick(),"",true);
		stats = new ArrayList<HouseStat>();
		loadHouseStats();
		loadSettings();
		insuranceBets = false;
	}

	@Override
	public void onPart(PartEvent<PircBotX> event) {
		String nick = event.getUser().getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
	}

	@Override
	public void onQuit(QuitEvent<PircBotX> event) {
		String nick = event.getUser().getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
		
	}
	
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> e){
    	String oldNick = e.getOldNick();
    	String newNick = e.getNewNick();
    	String hostmask = e.getUser().getHostmask();
    	if (isJoined(oldNick) || isWaitlisted(oldNick)){
    		infoNickChange(newNick);
    		if (isJoined(oldNick)){
		    	leave(oldNick);
	    	} else if(isWaitlisted(oldNick)){
				removeWaitlisted(oldNick);
	    	}
    		join(newNick, hostmask);
    	}
    }

	@Override
	public void onMessage(MessageEvent<PircBotX> event) {
		String origMsg = event.getMessage();
		String msg = origMsg.toLowerCase();

		if (msg.charAt(0) == '.') {
			User user = event.getUser();
			String nick = user.getNick();
			String hostmask = user.getHostmask();

			/* Parsing commands from the channel */
			if (msg.equals(".join") || msg.equals(".j")) {
				join(nick, hostmask);
			} else if (msg.equals(".leave") || msg.equals(".quit")
					|| msg.equals(".l") || msg.equals(".q")) {
				leave(nick);
			} else if (msg.equals(".start") || msg.equals(".go")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (isInProgress()) {
					infoRoundStarted(nick);
				} else if (getNumberJoined() > 0) {
					cancelIdleShuffleTimer();
					showStartRound();
					showPlayers();
					setInProgress(true);
					setBetting(true);
					setStartRoundTimer();
				} else {
					showNoPlayers();
				}
			} else if (msg.startsWith(".bet ") || msg.startsWith(".b ")
					|| msg.equals(".bet") || msg.equals(".b")) {
				if (isStage1PlayerTurn(nick)){
					try {
						try {
							int amount = parseNumberParam(msg);
							bet(amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals(".hit") || msg.equals(".h")) {
				if (isStage2PlayerTurn(nick)){
					hit();
				}
			} else if (msg.equals(".stay") || msg.equals(".stand")
					|| msg.equals(".sit")) {
				if (isStage2PlayerTurn(nick)){
					stay();
				}
			} else if (msg.equals(".doubledown") || msg.equals(".dd")) {
				if (isStage2PlayerTurn(nick)){
					doubleDown();
				}
			} else if (msg.equals(".surrender") || msg.equals(".surr")) {
				if (isStage2PlayerTurn(nick)){
					surrender();
				}
			} else if (msg.startsWith(".insure ") || msg.startsWith(".insure")) {
				if (isStage2PlayerTurn(nick)){
					try {
						try {
							int amount = parseNumberParam(msg);
							insure(amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals(".split")) {
				if (isStage2PlayerTurn(nick)){
					split();
				}
			} else if (msg.equals(".table")) {
				if (isStage2(nick)){
					showTableHands();
				}
			} else if (msg.equals(".sum")) {
				if (isStage2(nick)){
					BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
					infoPlayerSum(p, p.getCurrentHand());
				}
			} else if (msg.equals(".hand")) {
				if (isStage2(nick)){
					BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
					infoPlayerHand(p, p.getCurrentHand());
				}
			} else if (msg.equals(".allhands")) {
				bot.sendNotice(nick, "This command is not implemented.");
			} else if (msg.equals(".turn")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else {
					BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
					if (p.hasSplit()){
						showTurn(p, p.getCurrentIndex()+1);
					} else {
						showTurn(p);
					}
				}
				/* Contributed by Yky */
			} else if (msg.equals(".zc") || (msg.equals(".zen"))) {
				if (isCountAllowed(nick)){
					bot.sendMessage(channel, "Zen count = " + getZenCount());
				}
				/* Contributed by Yky */
			} else if (msg.equals(".hc") || (msg.equals(".hilo"))) {
				if (isCountAllowed(nick)){
					bot.sendMessage(channel, "Hi-Lo count = " + getHiLo());
				}
				/* Contributed by Yky */
			} else if (msg.equals(".rc") || (msg.equals(".red7"))) {
				if (isCountAllowed(nick)){
					bot.sendMessage(channel, "Red7 count = " + getRed7());
				}
			} else if (msg.equals(".numcards") || msg.equals(".ncards")) {
				if (isCountAllowed(nick)){
					showNumCards();
				}
			} else if (msg.equals(".numdiscards") || msg.equals(".ndiscards")) {
				if (isCountAllowed(nick)){
					showNumDiscards();
				}
			} else if (msg.equals(".simple")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else {
					togglePlayerSimple(nick);
				}
			} else if (msg.equals(".stats")){
				showGameStats();
			} else if (msg.startsWith(".cash ") || msg.equals(".cash")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerCash(param);
				} catch (NoSuchElementException e) {
					showPlayerCash(nick);
				}
			} else if (msg.startsWith(".netcash ") || msg.equals(".netcash")
					|| msg.startsWith(".net ") || msg.equals(".net")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerNetCash(param);
				} catch (NoSuchElementException e) {
					showPlayerNetCash(nick);
				}
			} else if (msg.startsWith(".debt ") || msg.equals(".debt")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerDebt(param);
				} catch (NoSuchElementException e) {
					showPlayerDebt(nick);
				}
			} else if (msg.startsWith(".bankrupts ")
					|| msg.equals(".bankrupts")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerBankrupts(param);
				} catch (NoSuchElementException e) {
					showPlayerBankrupts(nick);
				}
			} else if (msg.startsWith(".rounds ") || msg.equals(".rounds")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerRounds(param);
				} catch (NoSuchElementException e) {
					showPlayerRounds(nick);
				}
			} else if (msg.startsWith(".paydebt ") || msg.equals(".paydebt") ) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							payPlayerDebt(nick, amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals(".players")) {
				showPlayers();
			} else if (msg.equals(".waitlist")) {
				showWaitlist();
			} else if (msg.equals(".blacklist")) {
				showBlacklist();
			} else if (msg.startsWith(".house ") || msg.equals(".house")) {
				if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							int ndecks = parseNumberParam(msg);
							showHouseStat(ndecks);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						showHouseStat(shoeDecks);
					}
				}
			} else if (msg.startsWith(".top5 ") || msg.equals(".top5")) {
				if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							String param = parseStringParam(msg).toLowerCase();
							showTopPlayers(param, 5);
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						showTopPlayers("cash", 5);
					}
				}
			} else if (msg.startsWith(".top10 ") || msg.equals(".top10")) {
				if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							String param = parseStringParam(msg).toLowerCase();
							showTopPlayers(param, 10);
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						showTopPlayers("cash", 10);
					}
				}
			} else if (msg.equals(".gamerules") || msg.equals(".grules")) {
				infoGameRules(nick);
			} else if (msg.equals(".gamehelp") || msg.equals(".ghelp")) {
				infoGameHelp(nick);
			} else if (msg.equals(".gamecommands") || msg.equals(".gcommands")) {
				infoGameCommands(nick);
			} else if (msg.equals(".currentgame") || msg.equals(".game")) {
				showGameName();
			} else if (msg.equals(".numdecks") || msg.equals(".ndecks")) {
				if (isCountAllowed(nick)){
					showNumDecks();
				}
			/* Op commands */
			} else if (msg.startsWith(".cards ") || msg.startsWith(".discards ") || 
					msg.equals(".cards") || msg.equals(".discards")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							int num = parseNumberParam(msg);
							if (msg.startsWith(".cards ")
									&& deck.getNumberCards() > 0) {
								infoDeckCards(nick, 'c', num);
							} else if (msg.startsWith(".discards ")
									&& deck.getNumberDiscards() > 0) {
								infoDeckCards(nick, 'd', num);
							} else {
								bot.sendNotice(nick, "Empty!");
							}
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals(".shuffle")) {
				if (isOpCommandAllowed(user, nick)){
					cancelIdleShuffleTimer();
					shuffleShoe();
				}
			} else if (msg.equals(".reload")) {
				if (isOpCommandAllowed(user, nick)){
					cancelIdleShuffleTimer();
					loadSettings();
					showReloadSettings();
				}
			} else if (msg.startsWith(".set ") || msg.equals(".set")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							String[] iniParams = parseIniParams(msg);
							setSetting(iniParams);
							showUpdateSetting(iniParams[0]);
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.startsWith(".get ") || msg.equals(".get")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							String param = parseStringParam(msg);
							showSetting(param,getSetting(param));
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			}
		}
	}

	/* Accessor methods */
	public void setShoeDecks(int value) {
		shoeDecks = value;
	}
	public int getShoeDecks() {
		return shoeDecks;
	}
	public void setCountEnabled(boolean value) {
		countEnabled = value;
	}
	public boolean isCountEnabled() {
		return countEnabled;
	}
	public void setHoleEnabled(boolean value){
		holeEnabled = value;
	}
	public boolean isHoleEnabled(){
		return holeEnabled;
	}
	public void setIdleShuffleTime(int value) {
		idleShuffleTime = value;
	}
	public int getIdleShuffleTime() {
		return idleShuffleTime;
	}

	/* Game settings management */
	@Override
	public void setSetting(String[] params) {
		String setting = params[0];
		String value = params[1];
		if (setting.equals("decks")) {
			setShoeDecks(Integer.parseInt(value));
			deck = new CardDeck(shoeDecks);
			deck.shuffleCards();
			house = getHouseStat(shoeDecks);
			if (house == null) {
				house = new HouseStat(shoeDecks, 0, 0);
				stats.add(house);
			}
		} else if (setting.equals("idle")) {
			setIdleOutTime(Integer.parseInt(value));
		} else if (setting.equals("idleshuffle")) {
			setIdleShuffleTime(Integer.parseInt(value));
		} else if (setting.equals("cash")) {
			setNewCash(Integer.parseInt(value));
		} else if (setting.equals("respawn")) {
			setRespawnTime(Integer.parseInt(value));
		} else if (setting.equals("count")) {
			setCountEnabled(Boolean.parseBoolean(value));
		} else if (setting.equals("hole")) {
			setHoleEnabled(Boolean.parseBoolean(value));
		} else {
			throw new IllegalArgumentException();
		}
		saveSettings();
	}
	@Override
	public String getSetting(String param){
		if (param.equals("decks")) {
			return getShoeDecks()+"";
		} else if (param.equals("idle")) {
			return getIdleOutTime()+"";
		} else if (param.equals("idleshuffle")) {
			return getIdleShuffleTime()+"";
		} else if (param.equals("cash")) {
			return getNewCash()+"";
		} else if (param.equals("respawn")) {
			return getRespawnTime()+"";
		} else if (param.equals("count")) {
			return isCountEnabled()+"";
		} else if (param.equals("hole")) {
			return isHoleEnabled()+"";
		} else {
			throw new IllegalArgumentException();
		}
	}
	@Override
	public void loadSettings() {
		try {
			BufferedReader f = new BufferedReader(new FileReader("blackjack.ini"));
			String str, name, value;
			StringTokenizer st;
			while (f.ready()) {
				str = f.readLine();
				if (str.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(str, "=");
				name = st.nextToken();
				value = st.nextToken();
				if (name.equals("decks")) {
					shoeDecks = Integer.parseInt(value);
				} else if (name.equals("idle")) {
					idleOutTime = Integer.parseInt(value);
				} else if (name.equals("idleshuffle")) {
					idleShuffleTime = Integer.parseInt(value);
				} else if (name.equals("cash")) {
					newcash = Integer.parseInt(value);
				} else if (name.equals("respawn")) {
					respawnTime = Integer.parseInt(value);
				} else if (name.equals("count")) {
					countEnabled = Boolean.parseBoolean(value);
				} else if (name.equals("hole")) {
					holeEnabled = Boolean.parseBoolean(value);
				}
			}
			f.close();
		} catch (IOException e) {
			/* load defaults if blackjack.ini is not found */
			System.out.println("blackjack.ini not found! Creating new blackjack.ini...");
			shoeDecks = 1;
			newcash = 1000;
			idleOutTime = 60;
			respawnTime = 600;
			idleShuffleTime = 300;
			countEnabled = true;
			holeEnabled = false;
			saveSettings();
		}
		deck = new CardDeck(shoeDecks);
		deck.shuffleCards();
		house = getHouseStat(shoeDecks);
		if (house == null) {
			house = new HouseStat(shoeDecks, 0, 0);
			stats.add(house);
		}
	}
	@Override
	public void saveSettings() {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter("blackjack.ini")));
			out.println("#Settings");
			out.println("#Number of decks in the dealer's shoe");
			out.println("decks=" + shoeDecks);
			out.println("#Number of seconds before a player idles out");
			out.println("idle=" + idleOutTime);
			out.println("#Number of seconds of idleness after a round ends before the deck is shuffled");
			out.println("idleshuffle=" + idleShuffleTime);
			out.println("#Initial amount given to new and bankrupt players");
			out.println("cash=" + newcash);
			out.println("#Number of seconds before a bankrupt player is allowed to join again");
			out.println("respawn=" + respawnTime);
			out.println("#Whether card counting functions are enabled");
			out.println("count=" + countEnabled);
			out.println("#Whether player hands are shown with a hole card in the main channel");
			out.println("hole=" + holeEnabled);
			out.close();
		} catch (IOException f) {
			System.out.println("Error creating blackjack.ini!");
		}
	}

	/* House stats management */
	public void loadHouseStats() {
		try {
			BufferedReader f = new BufferedReader(new FileReader(
					"housestats.txt"));
			String str;
			int ndecks, nrounds, winnings;
			StringTokenizer st;
			while (f.ready()) {
				str = f.readLine();
				if (str.startsWith("#")) {
					if (str.contains("blackjack")) {
						while (f.ready()) {
							str = f.readLine();
							if (str.startsWith("#")) {
								break;
							}
							st = new StringTokenizer(str);
							ndecks = Integer.parseInt(st.nextToken());
							nrounds = Integer.parseInt(st.nextToken());
							winnings = Integer.parseInt(st.nextToken());
							stats.add(new HouseStat(ndecks, nrounds, winnings));
						}
						break;
					}
				}
			}
			f.close();
		} catch (IOException e) {
			System.out
					.println("housestats.txt not found! Creating new housestats.txt...");
			try {
				PrintWriter out = new PrintWriter(new BufferedWriter(
						new FileWriter("housestats.txt")));
				out.close();
			} catch (IOException f) {
				System.out.println("Error creating housestats.txt!");
			}
		}
	}
	public void saveHouseStats() {
		boolean found = false;
		int index = 0;
		ArrayList<String> lines = new ArrayList<String>();
		try {
			BufferedReader f = new BufferedReader(new FileReader(
					"housestats.txt"));
			String str;
			while (f.ready()) {
				str = f.readLine();
				lines.add(str);
				if (str.startsWith("#blackjack")) {
					found = true;
					index = lines.size();
					while (f.ready()) {
						str = f.readLine();
						if (str.startsWith("#")) {
							lines.add(str);
							break;
						}
					}
				}
			}
			f.close();
		} catch (IOException e) {
			/* housestats.txt is not found */
			System.out.println("Error reading housestats.txt!");
		}
		if (!found) {
			lines.add("#blackjack");
			index = lines.size();
		}
		for (int ctr = 0; ctr < stats.size(); ctr++) {
			lines.add(index, stats.get(ctr).getNumDecks() + " "
					+ stats.get(ctr).getNumRounds() + " "
					+ stats.get(ctr).getCash());
		}
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter("housestats.txt")));
			for (int ctr = 0; ctr < lines.size(); ctr++) {
				out.println(lines.get(ctr));
			}
			out.close();
		} catch (IOException f) {
			System.out.println("Error writing to housestats.txt!");
		}
	}
	public HouseStat getHouseStat(int numDecks) {
		HouseStat hs;
		for (int ctr = 0; ctr < stats.size(); ctr++) {
			hs = stats.get(ctr);
			if (hs.getNumDecks() == numDecks) {
				return hs;
			}
		}
		return null;
	}
	public int getTotalRounds(){
		int total=0;
		for (int ctr=0; ctr<stats.size(); ctr++){
			total += stats.get(ctr).getNumRounds();
		}
		return total;
	}
	public int getTotalHouse(){
		int total=0;
		for (int ctr=0; ctr<stats.size(); ctr++){
			total += stats.get(ctr).getCash();
		}
		return total;
	}

	
	/* Game management methods */
	@Override
	public void join(String nick, String hostmask){
		if (isJoined(nick)) {
			infoAlreadyJoined(nick);
		} else if (isBlacklisted(nick)) {
			infoBlacklisted(nick);
		} else if (isInProgress()) {
			if (isWaitlisted(nick)) {
				infoAlreadyWaitlisted(nick);
			} else {
				addWaitlistPlayer(nick, hostmask);
			}
		} else {
			addPlayer(nick, hostmask);
		}
	}
	@Override
	public void leave(Player p){
		leave(p.getNick());
	}
	@Override
	public void leave(String nick) {
		BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
		if (isJoined(nick)){
			if (isInProgress()) {
				if (isBetting()){
					if (p == currentPlayer){
						currentPlayer = getNextPlayer();
						savePlayerData(p);
						removeJoined(p);
						showLeave(p);
						if (currentPlayer == null) {
							setBetting(false);
							if (getNumberJoined() == 0) {
								endRound();
							} else {
								dealTable();
								currentPlayer = getJoined(0);
								quickEval();
							}
						} else {
							showTurn(currentPlayer);
						}
					} else {
						savePlayerData(p);
						removeJoined(p);
						showLeave(p);
					}
				} else {
					p.setQuit(true);
					if (p == currentPlayer){
						stay();
					}
				}
			} else {
				savePlayerData(p);
				removeJoined(p);
				showLeave(p);
			}
		} else if (isWaitlisted(nick)) {
			infoLeaveWaitlist(nick);
			removeWaitlisted(nick);
		} else {
			infoNotJoined(nick);
		}
	}
	@Override
	public void startRound() {
		if (getNumberJoined() > 0) {
			currentPlayer = getJoined(0);
			showTurn(currentPlayer);
			setIdleOutTimer();
		} else {
			endRound();
		}
	}
	@Override
	public void continueRound(){
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand nextHand;
		if (p.getCurrentIndex() < p.getNumberHands() - 1) {
			nextHand = p.getNextHand();
			showPlayerHand(p, nextHand, p.getCurrentIndex() + 1);
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
		BlackjackPlayer p;
		BlackjackHand dHand;
		setInProgress(false);
		if (getNumberJoined() > 0) {
			house.incrementNumRounds();
			// Make dealer decisions
			showTurn(dealer);
			dHand = dealer.getCurrentHand();
			showPlayerHand(dealer, dHand, true);
			if (needDealerHit()) {
				while (getCardSum(dHand) < 17) {
					dealOne(dHand);
					showPlayerHand(dealer, dHand, true);
				}
			}
			if (isHandBlackjack(dHand)) {
				showBlackjack(dealer);
			} else if (isHandBusted(dHand)) {
				showBusted(dealer);
			}
			// Show results
			showResults();
			if (hasInsuranceBets()) {
				showInsuranceResults();
			}
			// Clean-up tasks
			for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
				p = (BlackjackPlayer) getJoined(ctr);
				p.incrementRounds();
				if (p.getCash() == 0) {
					p.incrementBankrupts();
					blacklist.add(p);
					infoPlayerBankrupt(p.getNick());
					bot.sendMessage(channel, p.getNickStr()	+ " has gone bankrupt. " +
							"S/He has been kicked to the curb.");
					removeJoined(p.getNick());
					showLeave(p);
					setRespawnTimer(p);
					ctr--;
				}
				if (p.hasQuit() && isJoined(p)) {
					removeJoined(p.getNick());
					showLeave(p);
					ctr--;
				}
				savePlayerData(p);
				resetPlayer(p);
			}
		} else {
			showNoPlayers();
		}
		resetGame();
		showEndRound();
		showSeparator();
		mergeWaitlist();
		if (deck.getNumberDiscards() > 0) {
			setIdleShuffleTimer();
		}
	}
	@Override
	public void endGame() {
		cancelStartRoundTimer();
		cancelIdleOutTimer();
		cancelIdleShuffleTimer();
		cancelRespawnTimers();
		saveAllPlayers();
		saveHouseStats();
		saveSettings();
		joined.clear();
		waitlist.clear();
		blacklist.clear();
		deck = null;
		dealer = null;
		currentPlayer = null;
	}
	@Override
	public void resetGame() {
		discardPlayerHand(dealer);
		setInsuranceBets(false);
	}
	public void resetPlayer(BlackjackPlayer p) {
		discardPlayerHand(p);
		p.resetCurrentIndex();
		p.clearInitialBet();
		p.setQuit(false);
		p.setSurrender(false);
		p.clearInsureBet();
	}
	@Override
	public void setIdleOutTimer() {
		idleOutTimer = new Timer();
		idleOutTimer.schedule(new IdleOutTask((BlackjackPlayer) currentPlayer,
				this), idleOutTime*1000);
	}
	@Override
	public void cancelIdleOutTimer() {
		if (idleOutTimer != null) {
			idleOutTimer.cancel();
			idleOutTimer = null;
		}
	}
	public void setIdleShuffleTimer() {
		idleShuffleTimer = new Timer();
		idleShuffleTimer.schedule(new IdleShuffleTask(this), idleShuffleTime*1000);
	}
	public void cancelIdleShuffleTimer() {
		if (idleShuffleTimer != null) {
			idleShuffleTimer.cancel();
			idleShuffleTimer = null;
		}
	}
	public boolean isStage1PlayerTurn(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
			return false;
		} else if (!isInProgress()) {
			infoNotStarted(nick);
			return false;
		} else if (!isBetting()) {
			infoNotBetting(nick);
			return false;
		} else if (!(currentPlayer == findJoined(nick))) {
			infoNotTurn(nick);
			return false;
		}
		return true;
	}
	public boolean isStage2(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
			return false;
		} else if (!isInProgress()) {
			infoNotStarted(nick);
			return false;
		} else if (isBetting()) {
			infoNoCards(nick);
			return false;
		}
		return true;
	}
	public boolean isStage2PlayerTurn(String nick){
		if (!isStage2(nick)){
			return false;
		} else if (!(currentPlayer == findJoined(nick))) {
			infoNotTurn(nick);
			return false;
		}
		return true;
	}
	public boolean isOpCommandAllowed(User user, String nick){
		if (isInProgress()) {
			infoWaitRoundEnd(nick);
			return false;
		} else if (!channel.isOp(user)) {
			infoOpsOnly(nick);
			return false;
		}
		return true;
	}
	public boolean isCountAllowed(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
			return false;
		} else if (isInProgress()) {
			infoWaitRoundEnd(nick);
			return false;
		} else if (!isCountEnabled()) {
			infoCountDisabled(nick);
			return false;
		}
		return true;
	}

	/* Player management methods */
	@Override
	public void loadPlayerData(Player p) {
		try {
			boolean found = false;
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
			int numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					if (stacks.get(ctr) <= 0) {
						p.setCash(getNewCash());
					} else {
						p.setCash(stacks.get(ctr));
					}
					p.setDebt(debts.get(ctr));
					p.setBankrupts(bankrupts.get(ctr));
					p.setRounds(bjrounds.get(ctr));
					p.setSimple(simples.get(ctr));
					found = true;
					break;
				}
			}
			if (!found) {
				p.setCash(getNewCash());
				p.setDebt(0);
				p.setBankrupts(0);
				p.setRounds(0);
				p.setSimple(true);
				infoNewPlayer(p.getNick());
			}
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}
	}
	@Override
	public void savePlayerData(Player p) {
		boolean found = false;
		ArrayList<String> nicks = new ArrayList<String>();
		ArrayList<Integer> stacks = new ArrayList<Integer>();
		ArrayList<Integer> debts = new ArrayList<Integer>();
		ArrayList<Integer> bankrupts = new ArrayList<Integer>();
		ArrayList<Integer> bjrounds = new ArrayList<Integer>();
		ArrayList<Boolean> simples = new ArrayList<Boolean>();
		int numLines;
		try {
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
			numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					stacks.set(ctr, p.getCash());
					debts.set(ctr, p.getDebt());
					bankrupts.set(ctr, p.getBankrupts());
					bjrounds.set(ctr, p.getRounds());
					simples.set(ctr, p.isSimple());
					found = true;
				}
			}
			if (!found) {
				nicks.add(p.getNick());
				stacks.add(p.getCash());
				debts.add(p.getDebt());
				bankrupts.add(p.getBankrupts());
				bjrounds.add(p.getRounds());
				simples.add(p.isSimple());
			}
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}

		try {
			savePlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
		} catch (IOException e) {
			System.out.println("Error writing to players.txt!");
		}
	}
	@Override
	public int getPlayerRounds(String nick) {
		return loadPlayerStat(nick, "bjrounds");
	}
	@Override
    public int getTotalPlayers(){
    	try {
	    	ArrayList<String> nicks = new ArrayList<String>();
	        ArrayList<Boolean> simples = new ArrayList<Boolean>();
	        ArrayList<Integer> stacks = new ArrayList<Integer>();
	        ArrayList<Integer> bankrupts = new ArrayList<Integer>();
	        ArrayList<Integer> debts = new ArrayList<Integer>();
	        ArrayList<Integer> bjrounds = new ArrayList<Integer>();
	    	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
	    	int total = 0, numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (bjrounds.get(ctr) > 0){
        			total++;
        		}
        	}
        	return total;
    	} catch (IOException e){
		 	System.out.println("Error reading players.txt!");
		 	return -1;
    	}
    }
	@Override
	public void addPlayer(String nick, String hostmask) {
		addPlayer(new BlackjackPlayer(nick, hostmask, false));
	}
	@Override
	public void addPlayer(Player p){
		joined.add(p);
		loadPlayerData(p);
		showJoin(p);
	}
	@Override
	public void addWaitlistPlayer(String nick, String hostmask) {
		Player p = new BlackjackPlayer(nick, hostmask, false);
		waitlist.add(p);
		infoJoinWaitlist(p.getNick());
	}

	/* Card management methods for Blackjack */
	public void shuffleShoe() {
		deck.refillDeck();
		showShuffleShoe();
	}
	public void dealOne(Hand h) {
		h.add(deck.takeCard());
		if (deck.getNumberCards() == 0) {
			showDeckEmpty();
			deck.refillDeck();
		}
	}
	public void dealHand(BlackjackPlayer p) {
		p.addHand();
		Hand h = p.getCurrentHand();
		for (int ctr2 = 0; ctr2 < 2; ctr2++) {
			h.add(deck.takeCard());
			if (deck.getNumberCards() == 0) {
				showDeckEmpty();
				deck.refillDeck();
			}
		}
	}
	public void dealTable() {
		BlackjackPlayer p;
		BlackjackHand h;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
			dealHand(p);
			h = p.getCurrentHand();
			h.setBet(p.getInitialBet());
			if (isHoleEnabled()) {
				infoPlayerHand(p, h);
			}
		}
		dealHand(dealer);
		showTableHands();
	}
	@Override
	public void discardPlayerHand(Player p) {
		BlackjackPlayer BJp = (BlackjackPlayer) p;
		if (BJp.hasHands()) {
			for (int ctr = 0; ctr < BJp.getNumberHands(); ctr++) {
				deck.addToDiscard(BJp.getHand(ctr).getAllCards());
			}
			BJp.resetHands();
		}
	}

	/* Primary Blackjack command methods */
	public void bet(int amount) {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		if (amount > p.getCash()) {
			infoBetTooHigh(p.getNick(), p.getCash());
			setIdleOutTimer();
		} else if (amount <= 0) {
			infoBetTooLow(p.getNick());
			setIdleOutTimer();
		} else {
			p.setInitialBet(amount);
			p.addCash(-1 * amount);
			house.addCash(amount);
			showProperBet(p);
			currentPlayer = getNextPlayer();
			if (currentPlayer == null) {
				setBetting(false);
				showDealingTable();
				dealTable();
				currentPlayer = getJoined(0);
				quickEval();
			} else {
				showTurn(currentPlayer);
				setIdleOutTimer();
			}
		}
	}

	public void stay() {
		cancelIdleOutTimer();
		continueRound();
	}

	public void hit() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getCurrentHand();
		dealOne(h);
		showHitResult(p,h);
		if (isHandBusted(h)) {
			continueRound();
		} else {
			setIdleOutTimer();
		}
	}

	public void doubleDown() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getCurrentHand();
		if (h.hasHit()) {
			infoNotDoubleDown(p.getNick());
			setIdleOutTimer();
		} else if (p.getInitialBet() > p.getCash()) {
			infoInsufficientFunds(p.getNick());
			setIdleOutTimer();
		} else {			
			p.addCash(-1 * h.getBet());
			house.addCash(h.getBet());
			h.addBet(h.getBet());
			showDoubleDown(p, h);
			dealOne(h);
			showHitResult(p,h);
			continueRound();
		}
	}

	public void surrender() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getCurrentHand();
		if (p.hasSplit()){
			infoNotSurrenderSplit(p.getNick());
			setIdleOutTimer();
		} else if (h.hasHit()) {
			infoNotSurrender(p.getNick());
			setIdleOutTimer();
		} else {
			p.addCash(calcHalf(p.getInitialBet()));
			house.addCash(-1 * calcHalf(p.getInitialBet()));
			p.setSurrender(true);
			showSurrender(p);
			continueRound();
		}
	}

	public void insure(int amount) {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getCurrentHand();
		if (p.hasInsured()) {
			infoAlreadyInsured(p.getNick());
		} else if (!dealerUpcardAce()) {
			infoNotInsureNoAce(p.getNick());
		} else if (h.hasHit()) {
			infoNotInsureHasHit(p.getNick());
		} else if (p.hasSplit()){
			infoNotInsureHasSplit(p.getNick());
		} else if (p.getCash() == 0) {
			infoInsufficientFunds(p.getNick());
		} else if (amount > calcHalf(p.getInitialBet())) {
			infoInsureBetTooHigh(p.getNick(), calcHalf(p.getInitialBet()));
		} else if (amount <= 0) {
			infoInsureBetTooLow(p.getNick());
		} else {
			setInsuranceBets(true);
			p.setInsureBet(amount);
			p.addCash(-1 * amount);
			house.addCash(amount);
			showInsure(p);
		}
		setIdleOutTimer();
	}

	public void split() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand nHand, cHand = p.getCurrentHand();
		if (!isHandPair(cHand)) {
			infoNotPair(p.getNick());
			setIdleOutTimer();
		} else if (p.getCash() < cHand.getBet()) {
			infoInsufficientFunds(p.getNick());
			setIdleOutTimer();
		} else {
			p.addCash(-1 * cHand.getBet());
			house.addCash(cHand.getBet());
			p.splitHand();
			dealOne(cHand);
			nHand = p.getHand(p.getCurrentIndex() + 1);
			dealOne(nHand);
			nHand.setBet(cHand.getBet());
			showSplitHands(p);
			showSeparator();
			showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
			quickEval();
		}
	}

	/* Blackjack behind-the-scenes methods */
	public void quickEval() {
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getCurrentHand();
		if (p.hasSplit()) {
			showTurn(p, p.getCurrentIndex() + 1);
		} else {
			showTurn(p);
		}
		if (isHandBlackjack(h)) {
			if (p.hasSplit()){
				showBlackjack(p, p.getCurrentIndex() + 1);
			} else {
				showBlackjack(p);
			}
		}
		if (p.hasQuit()){
			stay();
		} else {
			setIdleOutTimer();
		}
	}

	public int getCardValue(Card c) {
		int num;
		try {
			num = Integer.parseInt(c.getFace());
		} catch (NumberFormatException e) {
			return 10;
		}
		return num;
	}

	public int getCardSum(BlackjackHand h) {
		int sum = 0, numAces = 0;
		int numCards = h.getSize();
		Card card;
		// sum the non-aces first and store number of aces in the hand
		for (int ctr = 0; ctr < numCards; ctr++) {
			card = h.get(ctr);
			if (card.getFace().equals("A")) {
				numAces++;
			} else {
				sum += getCardValue(card);
			}
		}
		// find biggest non-busting sum with aces
		if (numAces > 0) {
			if ((numAces - 1) + 11 + sum > 21) {
				sum += numAces;
			} else {
				sum += (numAces - 1) + 11;
			}
		}
		return sum;
	}

	public int calcHalf(int amount) {
		return (int) (Math.ceil((double) (amount) / 2.));
	}
	
	public int getBlackjackPayout(BlackjackHand h){
		return (2 * h.getBet() + calcHalf(h.getBet()));
	}
	public int getWinPayout(BlackjackHand h){
		return 2 * h.getBet();
	}
	public int getInsurancePayout(BlackjackPlayer p){
		return 3 * p.getInsureBet();
	}

	/* contributed by Yky */
	private int getZenCount() {
		int zenCount = 0;
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3")) {
				zenCount++;
			} else if (face.equals("4") || face.equals("5") || face.equals("6")) {
				zenCount += 2;
			} else if (face.equals("7")) {
				zenCount += 1;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K")) {
				zenCount -= 2;
			} else if (face.equals("A")) {
				zenCount -= 1;
			}
		}
		return zenCount;
	}

	/* contributed by Yky */
	private int getHiLo() {
		int hiLo = 0;
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3") || face.equals("4")
					|| face.equals("5") || face.equals("6")) {
				hiLo++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K") || face.equals("A")) {
				hiLo--;
			}
		}
		return hiLo;
	}

	/* contributed by Yky */
	private double getRed7() {
		double red7 = -2 * getShoeDecks();
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3") || face.equals("4")
					|| face.equals("5") || face.equals("6")) {
				red7++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K") || face.equals("A")) {
				red7--;
			} else if (face.equals("7")) {
				red7 += 0.5;
			}
		}
		return red7;
	}
	public boolean isHandPair(Hand h) {
		if (h.getSize() > 2) {
			return false;
		}
		return h.get(0).getFace().equals(h.get(1).getFace());
	}

	public boolean isHandBlackjack(BlackjackHand h) {
		int sum = getCardSum(h);
		if (sum == 21 && h.getSize() == 2) {
			return true;
		}
		return false;
	}

	public boolean isHandBusted(BlackjackHand h) {
		int sum = getCardSum(h);
		if (sum > 21) {
			return true;
		}
		return false;
	}

	public int evaluateHand(BlackjackHand h) {
		BlackjackHand dHand = dealer.getCurrentHand();
		int sum = getCardSum(h), dsum = getCardSum(dHand);
		boolean pBlackjack = isHandBlackjack(h);
		boolean dBlackjack = isHandBlackjack(dHand);
		if (sum > 21) {
			return -1;
		} else if (sum == 21) {
			/* Different cases at 21 */
			if (pBlackjack && !dBlackjack) {
				return 2;
			} else if (pBlackjack && dBlackjack) {
				return 0;
			} else if (!pBlackjack && dBlackjack) {
				return -1;
			} else {
				if (dsum == 21) {
					return 0;
				} else {
					return 1;
				}
			}
		} else {
			/* Any case other than 21 */
			if (dsum > 21 || dsum < sum) {
				return 1;
			} else if (dsum == sum) {
				return 0;
			} else {
				return -1;
			}
		}
	}

	public int evaluateInsurance() {
		BlackjackHand dHand = dealer.getCurrentHand();
		if (isHandBlackjack(dHand)) {
			return 1;
		} else {
			return -1;
		}
	}
	public boolean dealerUpcardAce() {
		Hand dHand = dealer.getCurrentHand();
		if (dHand.get(1).getFace().equals("A")) {
			return true;
		}
		return false;
	}
	public boolean hasInsuranceBets() {
		return insuranceBets;
	}
	public void setInsuranceBets(boolean b) {
		insuranceBets = b;
	}

	public boolean needDealerHit() {
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			BlackjackPlayer p = (BlackjackPlayer) getJoined(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
				BlackjackHand h = p.getHand(ctr2);
				if (!isHandBusted(h) && !p.hasSurrendered()
						&& !isHandBlackjack(h)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public void payPlayer(BlackjackPlayer p, BlackjackHand h){
		int result = evaluateHand(h);
		if (result == 2) {
			p.addCash(getBlackjackPayout(h));
			house.addCash(-1*getBlackjackPayout(h));
		} else if (result == 1) {
			p.addCash(getWinPayout(h));
			house.addCash(-1*getWinPayout(h));
		} else if (result == 0) {
			p.addCash(h.getBet());
			house.addCash(-1*h.getBet());
		}
	}
	public void payPlayerInsurance(BlackjackPlayer p){
		int result = evaluateInsurance();
		if (result == 1) {
			p.addCash(getInsurancePayout(p));
			house.addCash(-1*getInsurancePayout(p));
		}
	}

	/* Channel output methods for Blackjack */
	@Override
	public void showGameStats(){
		int totalPlayers, totalRounds, totalHouse;
		saveAllPlayers();
		saveHouseStats();
		totalPlayers = getTotalPlayers();
		totalRounds = getTotalRounds();
		totalHouse = getTotalHouse();
		bot.sendMessage(channel, formatNumber(totalPlayers)+" player(s) have played " +
					getGameNameStr()+". They have played a total of " +
					formatNumber(totalRounds) + " rounds. The house has won $" +
					formatNumber(totalHouse) + " in those rounds.");
	}
	@Override
	public void showTopPlayers(String param, int n) {
		int highIndex;
		saveAllPlayers();
		try {
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
			ArrayList<Integer> test = new ArrayList<Integer>();
			String title = Colors.BLACK + ",08Top " + n;
			String list;
			if (param.equals("cash")) {
				test = stacks;
				title += " Cash:";
			} else if (param.equals("debt")) {
				test = debts;
				title += " Debt:";
			} else if (param.equals("bankrupts")) {
				test = bankrupts;
				title += " Bankrupts:";
			} else if (param.equals("net")) {
				for (int ctr = 0; ctr < nicks.size(); ctr++) {
					test.add(stacks.get(ctr) - debts.get(ctr));
				}
				title += " Net Cash:";
			} else if (param.equals("rounds")) {
				test = bjrounds;
				title += " Blackjack Rounds:";
			} else {
				throw new IllegalArgumentException();
			}
			list = title;
			for (int ctr = 1; ctr <= n; ctr++){
				highIndex = 0;
				for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
					if (test.get(ctr2) > test.get(highIndex)) {
						highIndex = ctr2;
					}
				}
				if (param.equals("rounds") || param.equals("bankrupts")) {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " " 
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				} else {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " $"
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				}
				nicks.remove(highIndex);
				test.remove(highIndex);
				if (nicks.isEmpty()) {
					break;
				}
			}
			bot.sendMessage(channel, list);
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}
	}

	@Override
	public void showTurn(Player p) {
		if (isBetting()) {
			bot.sendMessage(channel, p.getNickStr() + "'s turn. Stack: $"
							+ formatNumber(p.getCash())
							+ ". Enter an initial bet up to $"
							+ formatNumber(p.getCash()) + ".");
		} else {
			bot.sendMessage(channel, "It's now " + p.getNickStr() + "'s turn.");
		}
	}

	public void showTurn(Player p, int index) {
		String nickStr = p.getNickStr() + "-" + index;
		bot.sendMessage(channel, "It's now " + nickStr + "'s turn.");
	}
	
	public void showPlayerHand(BlackjackPlayer p, BlackjackHand h, boolean forceNoHole) {
		if (forceNoHole){
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(0));
		} else if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(0));
		}
	}
	public void showPlayerHand(BlackjackPlayer p, BlackjackHand h, int handIndex) {
		if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(0));
		}
	}

	public void showPlayerHandWithBet(BlackjackPlayer p, BlackjackHand h, int handIndex) {
		if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel,
					p.getNickStr() + "-" + handIndex + ": " + h.toString(1)
							+ ", bet: $" + formatNumber(h.getBet()));
		} else {
			bot.sendMessage(channel,
					p.getNickStr() + "-" + handIndex + ": " + h.toString(0)
							+ ", bet: $" + formatNumber(h.getBet()));
		}
	}

	@Override
	public void showPlayerRounds(String nick) {
		int rounds = getPlayerRounds(nick);
		if (rounds != Integer.MIN_VALUE) {
			bot.sendMessage(channel, nick + " has played " + rounds
					+ " round(s) of " + getGameNameStr() + ".");
		} else {
			bot.sendMessage(channel, "No data found for " + nick + ".");
		}
	}

	public void showDeckEmpty() {
		bot.sendMessage(channel,
				"The dealer's shoe is empty. Refilling the dealer's shoe...");
	}

	public void showProperBet(BlackjackPlayer p) {
		bot.sendMessage(channel,
				p.getNickStr() + " bets $"
						+ String.format("%,d", p.getInitialBet())
						+ ". Stack: $" + formatNumber(p.getCash()));
	}

	public void showDoubleDown(BlackjackPlayer p, Hand h) {
		bot.sendMessage(channel,
				p.getNickStr() + " has doubled down! The bet is now $"
						+ formatNumber(h.getBet()) + ". Stack: $"
						+ formatNumber(p.getCash()));
	}

	public void showSurrender(BlackjackPlayer p) {
		bot.sendMessage(channel,
				p.getNickStr()
						+ " has surrendered! Half the bet is returned. Stack: $"
						+ formatNumber(p.getCash()));
	}

	public void showInsure(BlackjackPlayer p) {
		bot.sendMessage(channel,
				p.getNickStr() + " has made an insurance bet of $"
						+ formatNumber(p.getInsureBet()) + ". Stack: $"
						+ formatNumber(p.getCash()));
	}

	public void showSplitHands(BlackjackPlayer p) {
		BlackjackHand h;
		bot.sendMessage(channel,
				p.getNickStr() + " has split the hand! " + p.getNickStr()
						+ "'s hands are now:");
		for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
			h = p.getHand(ctr);
			showPlayerHandWithBet(p, h, ctr + 1);
		}
		bot.sendMessage(channel,
				p.getNickStr() + "'s stack: $"
						+ formatNumber(p.getCash()));
	}

	public void showShuffleShoe() {
		bot.sendMessage(channel, "The dealer's shoe has been shuffled.");
	}

	public void showReloadSettings() {
		bot.sendMessage(channel, "blackjack.ini has been reloaded.");
	}

	public void showHouseStat(int n) {
		HouseStat hs = getHouseStat(n);
		if (hs != null) {
			bot.sendMessage(channel, hs.toString());
		} else {
			bot.sendMessage(channel, "No statistics found for " + n
					+ " deck(s).");
		}
	}

	public void showSeparator() {
		bot.sendMessage(channel, Colors.BOLD
						+ "------------------------------------------------------------------");
	}
	@Override
	public void showNumCards() {
		bot.sendMessage(channel, formatNumber(deck.getNumberCards())
				+ " cards left in the dealer's shoe.");
	}
	public void showDealingTable() {
		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Dealing..."
				+ Colors.NORMAL);
	}
	public void showHitResult(BlackjackPlayer p, BlackjackHand h){
		if (p.hasSplit()) {
			showPlayerHand(p, h, p.getCurrentIndex() + 1);
		} else {
			showPlayerHand(p, h, false);
		}
		if (isHandBusted(h)) {
			if (p.hasSplit()){
				showBusted(p, p.getCurrentIndex() + 1);
			} else {
				showBusted(p);
			}
		}
	}
	public void showBusted(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " has busted!");
	}
	
	public void showBusted(BlackjackPlayer p, int index) {
		bot.sendMessage(channel, p.getNickStr()+"-" + index + " has busted!");
	}

	public void showBlackjack(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " has blackjack!");
	}
	
	public void showBlackjack(BlackjackPlayer p, int index) {
		bot.sendMessage(channel, p.getNickStr() + "-" + index + " has blackjack!");
	}

	public void showTableHands() {
		BlackjackPlayer p;
		BlackjackHand h;
		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Table:"	+ Colors.NORMAL);
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++){
				h = p.getHand(ctr2);
				if (p.hasSplit()) {
					showPlayerHand(p, h, ctr2+1);
				} else {
					showPlayerHand(p, h, false);
				}
			}
		}
		showPlayerHand(dealer, dealer.getCurrentHand(), false);
	}

	public void showResults() {
		BlackjackPlayer p;
		BlackjackHand h;
		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Results:" + Colors.NORMAL);
		showDealerResult();
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
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
	public void showInsuranceResults() {
		BlackjackPlayer p;
		BlackjackHand dHand = dealer.getCurrentHand();

		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Insurance Results:" + Colors.NORMAL);
		if (isHandBlackjack(dHand)) {
			bot.sendMessage(channel, dealer.getNickStr() + " had blackjack.");
		} else {
			bot.sendMessage(channel, dealer.getNickStr() + " did not have blackjack.");
		}

		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
			if (p.hasInsured()) {
				payPlayerInsurance(p);
				showPlayerInsuranceResult(p);
			}
		}
	}
	public void showDealerResult() {
		BlackjackHand dHand = dealer.getCurrentHand();
		int sum = getCardSum(dHand);
		String outStr = dealer.getNickStr();
		if (isHandBlackjack(dHand)) {
			outStr += " has blackjack (";
		} else {
			outStr += " has " + sum + " (";
		}
		outStr += dHand.toString(0) + ").";
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerResult(BlackjackPlayer p, BlackjackHand h, int index) {
		String outStr, nickStr;
		if (index > 0){
			nickStr = p.getNickStr() + "-" + index;
		} else {
			nickStr = p.getNickStr();
		}
		int result = evaluateHand(h);
		int sum = getCardSum(h);
		if (p.hasSurrendered()) {
			outStr = getSurrenderStr()+": ";
			outStr += nickStr+" has "+sum+" ("+h.toString(0)+").";
		} else if (result == 2) {
			outStr = getWinStr()+": ";
			outStr += nickStr+" has blackjack ("+h.toString(0)+") and wins $";
			outStr += formatNumber(getBlackjackPayout(h)) + ".";
		} else if (result == 1) {
			outStr = getWinStr()+": ";
			outStr += nickStr+" has "+sum+" ("+h.toString(0)+") and wins $";
			outStr += formatNumber(getWinPayout(h))+".";
		} else if (result == 0) {
			outStr = getPushStr()+": "+nickStr+" has "+sum+" ("+h.toString(0)+") ";
			outStr += "and the $" + formatNumber(h.getBet()) + " bet is returned.";
		} else {
			outStr = getLossStr()+": ";
			outStr += nickStr+" has "+sum+" ("+h.toString(0)+").";
		}
		outStr += " Stack: $" + formatNumber(p.getCash());
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerInsuranceResult(BlackjackPlayer p) {
		String outStr;
		int result = evaluateInsurance();
		
		if (result == 1) {
			outStr = getWinStr()+": " + p.getNickStr() + " wins $"
					+ formatNumber(getInsurancePayout(p)) + ".";
		} else {
			outStr = getLossStr()+": " + p.getNickStr() + " loses.";
		}
		outStr += " Stack: $" + formatNumber(p.getCash());
		bot.sendMessage(channel, outStr);
	}

	/* Player/nick output methods to simplify messaging/noticing */
	public void infoCountDisabled(String nick){
		bot.sendNotice(nick, "Counting functions are disabled.");
	}

	public void infoNotPair(String nick) {
		bot.sendNotice(nick, "Your hand cannot be split. It is not a pair.");
	}

	public void infoNotDoubleDown(String nick) {
		bot.sendNotice(nick, "You can only double down before hitting!");
	}
	
	public void infoNotSurrender(String nick) {
		bot.sendNotice(nick, "You cannot surrender after hitting!");
	}
	public void infoNotSurrenderSplit(String nick) {
		bot.sendNotice(nick, "You cannot surrender a split hand!");
	}

	public void infoInsureBetTooHigh(String nick, int max) {
		bot.sendNotice(nick, "Maximum insurance bet is $" + formatNumber(max) + ". Try again.");
	}
	public void infoInsureBetTooLow(String nick){
        bot.sendNotice(nick, "Minimum insurance bet is $1. Try again.");
    }
	public void infoNotInsureNoAce(String nick) {
		bot.sendNotice(nick, "The dealer's upcard is not an ace. You cannot make an insurance bet.");
	}
	public void infoNotInsureHasHit(String nick) {
		bot.sendNotice(nick, "You cannot make an insurance bet after hitting.");
	}
	public void infoNotInsureHasSplit(String nick) {
		bot.sendNotice(nick, "You cannot make an insurance bet after splitting.");
	}
	public void infoAlreadyInsured(String nick) {
		bot.sendNotice(nick, "You have already made an insurance bet.");
	}

	public void infoPlayerHand(BlackjackPlayer p, BlackjackHand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), 
					"Your current hand is " + h.toString(0) + ".");
		} else {
			bot.sendMessage(p.getNick(),
					"Your current hand is " + h.toString(0) + ".");
		}
	}
	public void infoPlayerSum(BlackjackPlayer p, BlackjackHand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), "Hand sum is " + getCardSum(h) + ".");
		} else {
			bot.sendMessage(p.getNick(), "Hand sum is " + getCardSum(h) + ".");
		}
	}
	public void infoPlayerBet(BlackjackPlayer p, BlackjackHand h) {
		String outStr = "You have bet $"+ formatNumber(h.getBet())+ " on this hand.";
		outStr += ".";
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), outStr);
		} else {
			bot.sendMessage(p.getNick(), outStr);
		}
	}

	public void infoPlayerBankrupt(String nick) {
		Player p = findJoined(nick);
		if (p.isSimple()) {
			bot.sendNotice(nick, "You've lost all your money. Please wait " 
						+ respawnTime/60 + " minute(s) for a loan.");
		} else {
			bot.sendMessage(nick, "You've lost all your money. Please wait "
						+ respawnTime/60 + " minute(s) for a loan.");
		}
	}

	/* Formatted strings */
	@Override
	public String getGameNameStr() {
		return Colors.BOLD + gameName + Colors.NORMAL;
	}

	@Override
	public String getGameHelpStr() {
		return "For help on how to play "
				+ getGameNameStr()
				+ ", please visit an online resource. "
				+ "For game commands, type .gcommands. For house rules, type .grules.";
	}

	@Override
	public String getGameRulesStr() {
		return "Dealer stands on soft 17. The dealer's shoe has "
				+ deck.getNumberDecks()
				+ " deck(s) of cards. Cards are reshuffled when the shoe is depleted. "
				+ "Regular wins are paid out at 1:1 and blackjacks are paid out at 3:2. "
				+ "Insurance wins are paid out at 2:1";
	}

	@Override
	public String getGameCommandStr() {
		return "start (go), join (j), leave (quit, l, q), bet (b), hit (h), stay (sit, stand), doubledown (dd), "
				+ "surrender (surr), insure, split, table, turn, sum, hand, allhands, cash, netcash (net), "
				+ "debt, bankrupts, rounds, numdecks (ndecks), numcards (ncards), numdiscards (ndiscards), "
				+ "hilo (hc), zen (zc), red7 (rc), simple, players, waitlist, blacklist, top5, "
				+ "gamehelp (ghelp), gamerules (grules), gamecommands (gcommands)";
	}
	
	public String getSurrenderStr(){
		return Colors.RED+",01"+" SURR "+Colors.NORMAL;
	}
	public String getPushStr(){
		return Colors.WHITE+",01"+" PUSH "+Colors.NORMAL;
	}
}