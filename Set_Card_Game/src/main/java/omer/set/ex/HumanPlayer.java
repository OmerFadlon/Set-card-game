package omer.set.ex;

import omer.set.Env;

public class HumanPlayer extends Player {

    public HumanPlayer(Env env, Dealer dealer, Table table, int id) {
        super(env, dealer, table, id);
    }

    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        playerThread = Thread.currentThread();

        while (!terminate) {
            //player waits for Key presses:
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException KeyPress) {
                //checking if got interrupted by a key press of the InputManager or by the dealer
                if (!InputQueue.isEmpty() && !terminate)
                    executeAction();
            }
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Synchronization : input manager thread, player's thread, dealer thread.
     * (Sync preventing key Presses to happen while the player executes an Action and reaches the shared resource - Input,
     * also while dealer resets the player's data between rounds).
     */
    @Override
    public synchronized void keyPressed(int slot) {
        //Processing the Input if the player's state allows him to play and if he placed less than 3 tokens
        if (state == gameState.PLAYING && TokensPlaced < 3) {
            InputQueue.offer(slot);
            playerThread.interrupt();  //maybe sync and wait is needed between input manager thread and player
        }
    }

    /**
     * This method places or removes a token according to the keyInput.
     */
    public void executeAction() {
        int KeyInput = InputQueue.poll();

        table.rwLock.readLock().lock();
        // if no card is on the chosen slot->Ignore.
        if (table.slotToCard[KeyInput] != null) {
            //place a token on the chosen slot or remove the existing token:
            if (table.tokens[id][KeyInput] == false) {
                table.placeToken(id, KeyInput);
            } else if (table.tokens[id][KeyInput] == true) {
                table.removeToken(id, KeyInput);
            }
        }
        table.rwLock.readLock().unlock();

        //claim a set if 3 cards were picked:
        if (TokensPlaced == 3) {
            state = gameState.WAITING;
            InputQueue.clear();
            CheckMySet();
            if (dealer.EndOfRound == true)
                state = gameState.WAITING;
            else
                state = gameState.PLAYING;
        }
    }
}

