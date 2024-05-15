package omer.set.ex;

import omer.set.Env;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.ArrayBlockingQueue;



/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Game entities.
     */
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * True if time is up for the current round.
     * States that dealer must prepare the new round.
     */
    protected volatile boolean EndOfRound;

    /**
     * A queue of players that waits for their set to be checked by the dealer.
     * Used by players and dealer:
     * safety is maintained using Java's concurrent Blocking queue.
     * fairness is maintained between all player's threads in insertion(first one to call a set will be noticed by the dealer first)
     */
    protected ArrayBlockingQueue<Player> requests;

    /**
     * @param dealerThread the main thread running the dealer loop
     */
    protected Thread dealerThread;

    /**
     * @param timer the timer of the game.
     */
    protected Timer timer;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        requests = new ArrayBlockingQueue<>(4, true);

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        dealerThread = Thread.currentThread();

        initializeGameThreads();

        //main loop of the Dealer:
        while (!shouldFinish()) {

            //End of round - stop players from playing,clear the table and reset some data:
            notifyPlayers(Player.gameState.WAITING);
            table.rwLock.writeLock().lock();        //locking while reaching the shared data of the table
            requests.clear();
            removeAllCardsFromTable();

            //Prepare new round - shuffle the deck, place cards, start the timer and resume the players:
            Collections.shuffle(deck);
            placeCardsOnTable();
            table.rwLock.writeLock().unlock(); //unlocking the shared data of the table
            EndOfRound = false;
            timer.timerThread.interrupt();
            notifyPlayers(Player.gameState.PLAYING);

            //dealers inner loop:
            RoundLoop();
        }

        //if game ended properly and not due to an external event
        if (!terminate) {
            terminate();
            announceWinners();
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Initialize player's threads and timer thread
     */
    private void initializeGameThreads() {
        //Starting player's threads:
        for (int i = 0; i < players.length; i++) {
            new Thread(players[i]).start();
            try {
                Thread.currentThread().sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        //Starting timer's thread:
        timer = new Timer(env, this);
        new Thread(timer).start();
        try {
            Thread.currentThread().sleep(10);
        } catch (InterruptedException ignored) {
        }

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void RoundLoop() {
        while (!terminate && !EndOfRound) {

            //test all the sets that were submitted so far by the players:
            while (!requests.isEmpty() && !EndOfRound && !terminate) {
                SetTesting();
            }
            //wait until A set is submitted by a player or TimeOut or Termination of the game:
            try {synchronized (this) { this.wait(); }
            } catch (InterruptedException dealerNotified) {}
        }
    }

    /**
     * resuming\stopping player by Changing the players game-state and notifying him.
     *
     * @param state - player's game state
     */
    public void notifyPlayers(Player.gameState state) {
        for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            //synchronization with player for shared resources of the player
            synchronized (player) {
                player.state = state;
                player.InputQueue.clear();    //restarting player's entities
                player.Input = null;
                players[i].playerThread.interrupt();
            }
        }
    }

    /**
     * Tests a set that was claimed by one of the players
     */
    public void SetTesting() {
        Player player = requests.poll();

        int[][] slotsAndCards = findTheSet(player);

        //checking if the set is valid:
        Boolean isSet = env.util.testSet(slotsAndCards[1]);

        //Lock for the access of the shared data of the table
        table.rwLock.writeLock().lock();

        //If the set is valid->reward the player and replace the cards:
        if (isSet) {
            player.state = Player.gameState.POINT;
            removeSetFromTable(slotsAndCards[0]);
        }
        //If the set is not valid-> penalize the player and remove his tokens:
        else {
            player.state = Player.gameState.PENALTY;
            for (int slot : slotsAndCards[0]) {
                table.removeToken(player.id, slot);
            }
        }
        table.rwLock.writeLock().unlock();
        player.playerThread.interrupt();
    }

    /**
     * returns the slots and cards of the player's set pending for testing
     */
    public int[][] findTheSet(Player player) {
        int[][] ans = new int[2][3];
        int counter = 0;
        //No lock is needed for the shared data because the Dealer is the only writer
        for (int slot = 0; slot < env.config.tableSize && counter < 3; slot++) {
            if (table.tokens[player.id][slot] == true) {
                ans[0][counter] = slot;
                ans[1][counter] = table.slotToCard[slot];
                counter++;
            }
        }
        return ans;
    }

    /**
     * Called when the game should be terminated.
     * 1st option : Due to an external event (exit button pressed),
     * 2nd option : The game was finished properly
     * //terminating threads
     */
    public void terminate() {
        //terminating players threads:
        for (Player player : players) {
            player.terminate();
        }
        //terminating timer:
        timer.terminate();

        //terminate dealer thread
        terminate = true;
        dealerThread.interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Removing all player's tokens from the specific slots & replacing the cards in those slots.
     */
    private void removeSetFromTable(int[] slots) {
        for (int slot : slots) {
            for (Player player : players) {
                if (table.tokens[player.id][slot] == true) {
                    table.removeToken(player.id, slot);
                    requests.remove(player);
                    //if a token of another player who waits for his set to be checked was removed -> resume his play:
                    if (player.state == Player.gameState.WAITING) {
                        player.state = Player.gameState.PLAYING;
                        player.playerThread.interrupt();
                    }
                }
            }
            table.removeCard(slot);
        }
        placeCardsOnTable();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for (int slot =0 ; slot < env.config.tableSize && !deck.isEmpty() && !terminate ; slot++){
            if (table.slotToCard[slot] == null)
                table.placeCard(deck.remove(0) , slot);
        }
        //Giving a hint for the players if necessary
        if (env.config.hints == true && !terminate) table.hints();
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (Integer slot=0 ; slot < env.config.tableSize && !terminate ; slot++){
            Integer card = table.slotToCard[slot];
            if (card != null) {
                table.removeCard(slot);
                deck.add(card);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxPoints = -1;
        for (Player player : players) {
            if (player.score() > maxPoints) {
                maxPoints = player.score();
            }
        }
        List winnersList = new LinkedList<>();
        for (Player player : players) {
            if (player.score() == maxPoints)
                winnersList.add(player.id);
        }
        int[] winners = new int[winnersList.size()];
        for (int i = 0; i < winners.length; i++)
            winners[i] = (int) (winnersList.get(i));
        env.ui.announceWinner(winners);
    }
}



