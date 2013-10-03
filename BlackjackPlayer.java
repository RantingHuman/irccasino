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

/**
 * Extends the Player class for players playing Blackjack.
 * @author Yizhe Shen
 */
public class BlackjackPlayer extends Player{
    /** The index of the player's current BlackjackHand. */
	private int currentIndex;
    /** ArrayList containing the player's BlackjackHands. */
	private ArrayList<BlackjackHand> hands;
    /** The player's surrender status. */
	private boolean surrender;
	
	/**
	 * Creates a new BlackjackPlayer.
     * Creates the new player with the specified parameters.
	 * 
	 * @param nick IRC user's nick.
     * @param hostmask IRC user's hostmask.
	 * @param dealer Whether or not player is dealer.
	 */
    public BlackjackPlayer(String nick, String hostmask, boolean dealer){
        super(nick, hostmask, dealer);
        hands = new ArrayList<BlackjackHand>();
        statsMap.put("initialbet", 0);
        statsMap.put("insurebet", 0);
        currentIndex = 0;
        surrender = false;
    }
    
    /* Blackjack-specific card/hand manipulation methods */
    /**
     * Adds a new hand to the ArrayList of BlackjackHands.
     */
    public void addHand(){
    	hands.add(new BlackjackHand());
    }
    
    /**
     * Gets the BlackjackHand at the specified index.
     * @param num the specified index
     * @return the BlackjackHand at the index
     */
    public BlackjackHand getHand(int num){
    	return hands.get(num);
    }
    
    /**
     * Gets the player's current BlackjackHand.
     * @return the BlackjackHand at the current index
     */
    public BlackjackHand getHand(){
    	return hands.get(currentIndex);
    }
    
    /**
     * Increments the current index in order to get the next hand.
     * @return the BlackjackHand at the incremented index
     */
    public BlackjackHand getNextHand(){
    	return getHand(++currentIndex);
    }
    
    /**
     * The index of the player's current BlackjackHand.
     * @return the current index
     */
    public int getCurrentIndex(){
    	return currentIndex;
    }
    
    /**
     * Resets the current index back to the player's first hand.
     */
    public void resetCurrentIndex(){
    	currentIndex = 0;
    }
    
    /**
     * Returns the number BlackjackHands the player has.
     * @return the number of BlackjackHands
     */
    public int getNumberHands(){
    	return hands.size();
    }
    
    /**
     * Whether or not the player has any BlackjackHands.
     * @return true if the player has any BlackjackHands
     */
    public boolean hasHands(){
        return hands.size() > 0;
    }
    
    /**
     * Clears all of the player's hands.
     */
    public void resetHands(){
    	hands.clear();
    }
    
    /**
     * Sets the player's surrender status to the specified status.
     * @param b the new status
     */
	public void setSurrender(boolean b){
		surrender = b;
	}
    
    /**
     * Whether or not the player has surrendered.
     * @return true if the player has surrendered.
     */
	public boolean hasSurrendered(){
		return surrender;
	}
    
    /* Methods related to splitting hands */
    /**
     * Whether or not the player has split his initial hand.
     * @return true if hands contains more than one BlackjackHand
     */
    public boolean hasSplit(){
    	return hands.size() > 1;
    }
    
    /**
     * Splits a BlackjackHand into two BlackjackHands.
     * Creates a new BlackjackHand and gives it the second card of the original.
     * The card is then removed from the original BlackjackHand. The new
     * BlackjackHand is added to the end of the ArrayList of BlackjackHands.
     */
    public void splitHand(){
    	BlackjackHand tHand = new BlackjackHand();
    	BlackjackHand cHand = getHand();
    	tHand.add(cHand.get(1));
    	cHand.remove(1);
    	
    	hands.add(currentIndex+1, tHand);
    }
}
