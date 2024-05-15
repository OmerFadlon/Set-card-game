package omer.set.ex;

import omer.set.Env;

import java.util.concurrent.LinkedBlockingQueue;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public abstract class Player implements Runnable {

    /**
     * The game environment object.
     */
    protected final Env env;

    /**
     * Game entities.
     */
    protected final Table table;

    /**
     * Game entities.
     */
    protected final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * Notifications being passed between entities through interrupting threads.
     */
    protected volatile Thread playerThread;

    /**
     * True iff game should be terminated.
     * Volatile - safe reading for Dealer thread and Player thread.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Player's present state
     */
    public enum gameState {
        WAITING,
        PLAYING,
        PENALTY,
        POINT
    }
    /**
     * Player's present state.
     * Volatile - safe reading for InputManager thread and Player thread while dealer thread is the only writer.
     */
    protected volatile gameState state;

    /**
     * queue of the slot that was pressed by the input manager for the next play (in use for Human Player).
     * concurrency - Input Manager thread & Player thread
     */
    protected LinkedBlockingQueue<Integer> InputQueue;

    /**
     * the slot that was pressed by the AiPlayer for the next play (in use for Ai Player).
     * Volatile - Dealer thread and AiPlayer thread.
     */
    protected volatile Integer Input ;

    /**
     * The number of cards that was already picked by the player.
     * Volatile - player thread & dealer thread
     */
    protected volatile  Integer TokensPlaced;


    public Player(Env env, Dealer dealer, Table table, int id) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.dealer = dealer;
        state = gameState.WAITING;
        TokensPlaced = 0;
        InputQueue = new LinkedBlockingQueue<>(3);

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public abstract void run() ;

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public abstract void keyPressed(int slot);

    /**
     * This method places or removes a token according to the keyInput.
     */
    public abstract void executeAction();


    /**
     * The player asks the dealer to check his set by registering at the dealers requests queue
     */
    public void CheckMySet() {
        dealer.requests.add(this);
        dealer.dealerThread.interrupt();  //notify dealer to check the set
        //waiting for dealer to check the set
        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException setCheck) {}

        //following the dealer's response:
        if (state == gameState.POINT) {
            point();
        } else if (state == gameState.PENALTY) {
            penalty();
        }
        // else the set was claimed by some other player before.
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        //player freezes for a fixed time after winning a point
        Freeze(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player - freeze his play for a fixed time
     */
    public void penalty() {
        Freeze(env.config.penaltyFreezeMillis);
    }

    /**
     * The player is unable to play while he gets a penalty for wrong set or for a point
     */
    public void Freeze(long FreezeTime) {
        if (FreezeTime > 0) {
            env.ui.setFreeze(id, FreezeTime);
            while (FreezeTime != 0) {
                try {
                    Thread.sleep(1000);
                    FreezeTime = FreezeTime - 1000;
                }catch (InterruptedException ignored){}

                if (terminate) return;
                env.ui.setFreeze(id, FreezeTime);
            }
        }
    }

    public int score() {
        return score;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        this.terminate = true;
        playerThread.interrupt();
    }
}
