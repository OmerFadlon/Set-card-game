package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    public volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The number of tokens placed on the table.
     */
    public int usedTokens;

    /**
     * The current set to claim - slots & ids of the cards with token placed upon them.
     */
    public int[][] setArray;
    /**
     * The action queue for the player's thread.
     */
    public Queue<Integer> incomingActions;

    /**
     * The dealer.
     */
    private Dealer dealer;

    /**
     * Penalty time in case of trying to claim a wrong set.
     */
    public volatile long penalizedTime;

    /**
     * Is a set being claimed at the moment
     */
    public volatile boolean checkingSet;
    /**



    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        usedTokens = 0;
        setArray = new int[3][3];   //row 0 = card id, row 1 = card slot
                                    // cell [2][0] = player's Id
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                setArray[i][j] = -1;
            }
        }
        setArray[2][0] = id;
        incomingActions = new LinkedList<>();
        penalizedTime = 0;
        checkingSet = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */

    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        //for an Ai player: create a bot for generating random key-preses:
        if (!human) createArtificialIntelligence();

        //player's thread main loop:
        while (!terminate) {
            if (penalizedTime > 0)
                penalize();
            synchronized (this) {     //synchronization between dealer thread & player's(ith) thread
                while (!terminate && penalizedTime == 0 && (incomingActions.isEmpty() || !dealer.cardsOnTable || checkingSet)) {
                    try {
                        this.wait();  //to increase efficiency: player's thread waits as long as there is no actions waiting, or while the dealer reshuffle or the dealer checks his set
                    } catch (InterruptedException ignored) {
                    }
                }
                //the thread execute the next action from the queue:
                if (!incomingActions.isEmpty() && !terminate && incomingActions.peek() != null && penalizedTime == 0 && dealer.cardsOnTable && !checkingSet) {
                    actionFromQueue(incomingActions.remove());
                    notifyAll();
                }
            }
        }

        //terminating the Ai thread if Ai:
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        //terminating player's thread:
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses.
     * for more efficiency: If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                synchronized (this){
                    while (incomingActions.size()==3) {
                        try {
                            this.wait();
                        } catch (InterruptedException ignored) {
                        }

                    }
                }
                keyPressed((int) (Math.random() * env.config.tableSize));
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) { //sync between player's thread and input manager thread
        if (incomingActions.size() < 3 && penalizedTime == 0 && table.slotToCard[slot] != null && table.slotToCard[slot] != -1 && dealer.cardsOnTable && !checkingSet) {
            incomingActions.add(slot);              //adding action to the player's action queue.
            notifyAll();                            // notifying player's thread
        }
    }

    /**
     * This method is called when the player's thread acts.
     * Player's thread removing or placing a token according to the slot that is popped from the queue:
     */
    public synchronized void actionFromQueue(int slot) { //sync between dealer and player thread's
        if (table.slotToCard[slot] != null && table.slotToCard[slot] != -1) {
            boolean removed = false;

            //Check if the player already placed a token on this slot, if so remove it:
            for (int i = 0; !removed && i < 3; i++) {
                if (setArray[1][i] == slot) {
                    synchronized (dealer) {
                        removed = table.removeToken(this.id, slot);
                    }
                    //update setArray
                    setArray[0][i] = -1;
                    setArray[1][i] = -1;
                    //update tokens
                    usedTokens--;
                }
            }
            //check if the slot is empty,if so place a token:
            if (!removed && usedTokens < 3) {
                boolean placed = false;
                synchronized (dealer) {
                    table.placeToken(this.id, slot);
                }
                int cardId = table.slotToCard[slot];
                for (int i = 0; !placed && i < 3; i++) {
                    if (setArray[0][i] == -1) {
                        //update set array
                        setArray[0][i] = cardId;
                        setArray[1][i] = slot;
                        //update tokens
                        usedTokens++;
                        placed = true;
                    }
                }
                if (usedTokens == 3) {              //if third token was placed.
                    checkingSet=true;
                    dealer.addSetToQueue(setArray); //claim a set

                }

            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        //penalizedTime = env.config.pointFreezeMillis;
        synchronized (this) {
            penalizedTime = env.config.pointFreezeMillis;
            notifyAll();
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {    //sync is necessary between dealer to player thread's
        penalizedTime = env.config.penaltyFreezeMillis;
        notifyAll();
    }
    /**
     * returns a player's score
     */
    public int score() {
        return score;
    }

    public void join() {
        try {
            playerThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * The player is unable to play while he gets a penalty
     */
    public void penalize() {
        if (penalizedTime > 0) { //player penalized
            env.ui.setFreeze(id, penalizedTime);
            long time = penalizedTime;
            while (time != 0) {
                try {
                    playerThread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                time = time - 1000;
                env.ui.setFreeze(id, time);
            }
            penalizedTime = 0;
        }
    }

    /**
     * Dealer wakes up the players threads after placing cards on the table
     */
    public synchronized void wakeUpPlayer() {
        notifyAll();
    }
}
