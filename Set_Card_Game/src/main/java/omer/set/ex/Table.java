package omer.set.ex;

import omer.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * A grid that holds for each player the slots on which he placed a token.
     */
    protected final boolean[][] tokens;

    /**
     * Game entities.
     */
    private final Player[] players;

    /**
     * Accessed to the table's shared resources through a Read-Write lock.
     * Allowing all players to access the table simultaneously, except when the dealer is accessing the table.
     * Giving priority to the dealer as the only writer.
     */
    protected final ReadWriteLock rwLock;


    public Table(Env env, Player[] players) {

        this.env = env;
        slotToCard = new Integer[env.config.tableSize];
        cardToSlot = new Integer[env.config.deckSize];
        this.players = players;
        tokens = new boolean[env.config.players][env.config.tableSize];
        rwLock = new ReentrantReadWriteLock(true);

    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }


    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        // delaying the actions of placing cards for ui
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table & removes all tokens from the grid slot
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        // ux-ui delaying the actions of the dealer
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        //removing the players' tokens from the grid slot
        for (Player player : players){
            if(tokens[player.id][slot] == true)
                removeToken(player.id, slot);
        }
        //removing the card
        int card = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        env.ui.removeCard(slot);

    }



    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
            tokens[player][slot] = true;
            players[player].TokensPlaced++;
            env.ui.placeToken(player, slot);
    }


    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     */
    public void removeToken(int player, int slot) {
        tokens[player][slot] = false;
        players[player].TokensPlaced--;
        env.ui.removeToken(player, slot);
    }
}
