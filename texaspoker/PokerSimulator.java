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

package irccasino.texaspoker;

import irccasino.cardgame.Card;
import irccasino.cardgame.CardDeck;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Game simulator for calculating winning percentages.
 * @author Yizhe Shen
 */
public class PokerSimulator {
    private CardDeck simDeck;
    private ArrayList<PokerPlayer> simList;
    private Hand simComm;
    private int rounds;
    private Random randGen;

    public PokerSimulator(ArrayList<PokerPlayer> list, Hand comm) {
        simDeck = new CardDeck(1);
        simList = new ArrayList<PokerPlayer>();
        simComm = new Hand();
        randGen = new Random();
        rounds = 0;

        PokerPlayer simP;

        // Give simulated community cards matching the real community.
        for (Card aCard : comm) {
            simComm.add(simDeck.takeCard(aCard));
        }

        // Give simulated players simulated cards matching the real hands.
        for (PokerPlayer p : list) {
            if (!p.has("fold")) {
                simP = new PokerPlayer(p.getNick(), p.getHost());
                for (Card aCard : p.getHand()){
                   simP.getHand().add(simDeck.takeCard(aCard));
                   simP.set("wins", 0);
                   simP.set("ties", 0);
                }
                simList.add(simP);
            }
        }

        // Run simulation
        if (simComm.size() == 0) {
            monteSim();
        } else {
            bruteSim(0);
        }
    }

    /**
     * Simulates games by sequentially adding cards to simComm until full
     * and determining winners and ties. Uses a recursive algorithm.
     * The number of rounds generated will be nCr, where n is the number of
     * cards remaining in the simDeck and r is the number of cards required
     * to fill simComm.
     * @param index the index in simDeck from which to start adding cards
     *              to simComm
     */
    private void bruteSim(int index) {
        Card c;
        // Add another card if the simulated community isn't full
        if (simComm.size() < 5) {
            for (int ctr = index; ctr < simDeck.getNumberCards(); ctr++) {
                c = simDeck.peekCard(ctr);
                simComm.add(c);
                bruteSim(ctr + 1);
                simComm.remove(c);
            }
        } else {
            findWinners();
        }
    }
    
    /**
     * Runs a Monte Carlo simulation to determine win and tie percentages. The
     * number of trials is hard-coded to produce results with a good degree
     * of accuracy (+/-1%) while running reasonably fast. 
     */
    private void monteSim() {
        ArrayList<Integer> indices = new ArrayList<Integer>();
        int origSize = simComm.size();
        int newIndex;
        
        for (int trial = 0; trial < 100000; trial++) {
            // Pick random cards to complete the simulated community
            for (int ctr = origSize; ctr < 5; ctr++) {
                newIndex = randGen.nextInt(simDeck.getNumberCards());
                while(indices.contains(newIndex)) {
                    newIndex = randGen.nextInt(simDeck.getNumberCards());
                }
                indices.add(newIndex);
                simComm.add(simDeck.peekCard(newIndex));
            }
            
            findWinners();
            
            // Remove the randomly picked cards
            indices.clear();
            for (int ctr = 4; ctr >= origSize; ctr--) {
                simComm.remove(ctr);
            }
        }
    }
    
    /**
     * Determines the winners for a full simulated community.
     */
    private void findWinners() {
        rounds++;
        int winners = 1;
        
        // Create PokerHands
        for (PokerPlayer p : simList) {
            p.getPokerHand().addAll(p.getHand());
            p.getPokerHand().addAll(simComm);
            Collections.sort(p.getPokerHand());
            Collections.reverse(p.getPokerHand());
        }

        // Sort players by PokerHand
        Collections.sort(simList);
        Collections.reverse(simList);

        // Determine number of winners
        for (int ctr = 1; ctr < simList.size(); ctr++){
            if (simList.get(0).compareTo(simList.get(ctr)) == 0){
                winners++;
            }
        }

        // Increment win count for winners
        if (winners == 1) {
            simList.get(0).increment("wins");
        } else {
            for (int ctr = 0; ctr < winners; ctr++){
                simList.get(ctr).increment("ties");
            }
        }

        // Clear PokerHands
        for (PokerPlayer p : simList) {
            p.getPokerHand().clear();
            p.getPokerHand().resetValue();
        }
    }

    /**
     * Returns the win percentage for the specified player.
     * @param p the player
     * @return the win percentage or -1 if the player is not found
     */
    public double getWinPct(PokerPlayer p) {
        for (PokerPlayer simP : simList) {
            if (simP.equals(p)) {
                return (double) simP.get("wins") / (double) rounds * 100;
            }
        }
        return -1.0;
    }

    /**
     * Returns the tie percentage for the specified player.
     * @param p the player
     * @return the tie percentage or -1 if the player is not found
     */
    public double getTiePct(PokerPlayer p) {
        for (PokerPlayer simP : simList) {
            if (simP.equals(p)) {
                return (double) simP.get("ties") / (double) rounds * 100;
            }
        }
        return -1.0;
    }

    /**
     * Produces a String that contains every player's cards and their
     * percentages for wins and splits.
     * @return a ready-to-display String
     */
    @Override
    public String toString() {
        Collections.sort(simList, Player.getComparator("wins"));
        String out = "Showdown: ";
        for (PokerPlayer p : simList) {
            out += p.getNick() + " (" + p.getHand() + ", " + Math.round(getWinPct(p)) + "%%, " + Math.round(getTiePct(p)) + "%%), ";
        }
        return out.substring(0, out.length() - 2);
    }
}