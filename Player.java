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

import java.util.Objects;
import org.pircbotx.*;

/**
 * A player class with common methods and members for all types of players.
 * It serves as a template and should not be directly instantiated.
 * @author Yizhe Shen
 */
public abstract class Player extends Stats{
    /** Stores the player's dealer status. */
    protected boolean dealer;
    /** Stores the player's nick. */
    protected String nick;
    /** Stores the player's hostmask. */
    protected String hostmask;
    
    /**
     * Creates a new Player.
     * Not to be instantiated directly. Serves as the template for specific types
     * of players.
     * 
     * @param nick IRC user nick
     * @param hostmask IRC user hostmask
     * @param dealer Whether or not this player is dealer
     */
    public Player(String nick, String hostmask, boolean dealer){
        super();
        this.nick = nick;
        this.dealer = dealer;
        this.hostmask = hostmask;
        set("cash", 0);
        set("bank", 0);
        set("bankrupts", 0);
        set("bjrounds", 0);
        set("bjwinnings", 0);
        set("tprounds", 0);
        set("tpwinnings", 0);
        set("simple", 1);
        set("quit", 0);
    }
    
    /* Player info methods */
    /**
     * Returns the Player's nick or "Dealer" if the player is in the dealer role.
     * 
     * @return the Player's name
     */
    public String getNick(){
        if (dealer){
            return "Dealer";
        }
        return nick;
    }
    
    /**
     * Returns the Player's hostmask.
     * 
     * @return the Player's hostmask
     */
    public String getHostmask() {
        return hostmask;
    }
    
    /**
     * Sets the Player's dealer status.
     * 
     * @param b the new status
     */
    public void setDealer(boolean b){
        dealer = b;
    }
    
    /**
     * Whether or not the Player is the dealer.
     * 
     * @return true if the Player is the dealer
     */
    public boolean isDealer(){
        return dealer;
    }
    
    /**
     * Returns the simple status of the Player.
     * If simple is true, then game information is sent via notices. If simple
     * is false, then game information is sent via private messages.
     * 
     * @return true if simple is turned on
     */
    public boolean isSimple(){
        return get("simple") == 1;
    }
    
    @Override
    public int get(String stat){
        if (stat.equals("exists")){
            return 1;
        } else if (stat.equals("netcash")){
            return get("cash") + get("bank");
        }
        return super.get(stat);
    }
    
    /**
     * Transfers the specified amount from cash into bank.
     * 
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
        add("bank", amount);
        add("cash", -1 * amount);
    }
    
    /**
     * Returns the player's nick formatted in IRC bold.
     * 
     * @return the bold-formatted nick
     */
    public String getNickStr(){
        return Colors.BOLD + nick + Colors.BOLD;
    }
    
    /**
     * String representation includes the Player's nick and hostmask.
     * 
     * @return a String containing the Players nick and hostmask
     */
    @Override
    public String toString(){
        return nick + " " + hostmask;
    }
    
    /**
     * Comparison of Player objects.
     * @param o the Object to compare
     * @return true if the properties are the same
     */
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Player) {
            Player p = (Player) o;
            if (nick.equals(p.nick) && hostmask.equals(p.hostmask) &&
                dealer == p.dealer && hashCode() == p.hashCode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated hashCode method.
     * @return the Player's hashCode
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + (this.dealer ? 1 : 0);
        hash = 29 * hash + Objects.hashCode(this.nick);
        hash = 29 * hash + Objects.hashCode(this.hostmask);
        return hash;
    }
}