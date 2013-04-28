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
import org.pircbotx.*;

public class Player {
    protected User user;
    protected boolean simple, dealer;
    protected ArrayList<Card> hand;
    protected int cash, bet;
    
    /* Constructor requires User and player type */
    public Player(User u, boolean d){
        user = u;
        dealer = d;
        hand = new ArrayList<Card>();
        cash = 0;
        bet = 0;
        simple = true;
    }
    
    /* Player info methods */
    public String getNick(){
        if (isDealer()){
            return "Dealer";
        } else {
            return user.getNick();
        }
    }
    public String getHostMask(){
        return user.getHostmask();
    }
    public User getUser(){
        return user;
    }
    public void setDealer(boolean b){
    	dealer = b;
    }
    public boolean isDealer(){
        return dealer;
    }
    
    /* Info via notice or message */
    public boolean isSimple(){
        return simple;
    }
    public void setSimple(boolean s){
        simple = s;
    }
    
    /* Betting related methods */
    public void setBet(int amount){
    	bet = amount;
        cash -= amount;
    }
    public void addBet(int amount){
        bet += amount;
        cash -= amount;
    }
    public int getBet(){
        return bet;
    }
    public void clearBet(){
        bet = 0;
    }
    public boolean hasBet(){
    	return (bet > 0);
    }
    public void setCash(int amount){
        cash = amount;
    }
    public void addCash(int amount){
        cash += amount;
    }
    public int getCash(){
        return cash;
    }

    /* Player card manipulation methods */
    public int getHandSize(){
        return hand.size();
    }
    public boolean hasHand(){
        return hand.size() > 0;
    }
    public void addCard(Card newCard){
    	hand.add(newCard);
    }
    public ArrayList<Card> getHand(){
        return hand;
    }
    public void resetHand(){
        if (hasHand()){
        	hand.clear();
        }
    }
    
    /* Formatted string representations */
    public String getCardStr(int numHidden){
    	String hiddenBlock = Colors.DARK_BLUE+",00\uFFFD";
        String outStr="";
        for (int ctr=0; ctr<numHidden; ctr++){
            outStr += hiddenBlock+" ";
        }
        for (int ctr=numHidden; ctr<hand.size(); ctr++){
            outStr += hand.get(ctr)+" ";
        }
        return outStr.substring(0, outStr.length()-1)+Colors.NORMAL;
    }
    public String getNickStr(){
    	return Colors.BOLD+getNick()+Colors.NORMAL;
    }
}