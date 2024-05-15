package omer.set.ex;

import omer.set.Env;

/**
 * This class manages the Timer thread
 */
public class Timer implements Runnable{

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    protected final Dealer dealer;

    /**
     * Notifications are passed between dealer and timer thread through interrupting threads.
     * Volatile - timer thread reads from this shared resource and dealer thread writes
     */
    protected volatile Thread timerThread;

    /**
     * True iff game should be terminated.
     * Volatile - timer thread reads from this shared resource and dealer thread writes
     */
    protected volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to round timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Timer (Env env, Dealer dealer){
        this.env = env;
        this.dealer = dealer;
    }

    /**
     *The timer of the game starts here:
     */
    public void run(){
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        timerThread = Thread.currentThread();

        //main loop of the timer:
        while (!terminate){
            //Waiting for the dealer to start the timer of the new round
            try{synchronized (this){this.wait();}}
            catch (InterruptedException TimerStarting){}

            //reset the timer for the new round:
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(env.config.turnTimeoutMillis);

            //The current round's timer:
            TimerInnerLoop();

            //notifying the dealer that this is the end of the current round
            dealer.EndOfRound = true;
            dealer.dealerThread.interrupt();
            }

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Inner loop of the timer, runs as long as the current round of the game is on
     */
    private void TimerInnerLoop(){
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            updateTimerDisplay(timeLeft);

            //Normally the timer is updated every second, for the last few seconds of a round it is updated every millisecond
            if (timeLeft > env.config.turnTimeoutWarningMillis & timeLeft > 1000) {
                try {timerThread.sleep(1000);} catch (InterruptedException Ignored){}
            }
            else {
                try {timerThread.sleep(1);} catch (InterruptedException Ignored){}
            }
        }
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(long timeLeft) {
        if (timeLeft > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(timeLeft, false);

        //count down for the last few seconds, displayed in red on the User's screen
        else
            env.ui.setCountdown(timeLeft, true);
    }

    /**
     *Terminates the timer if the game Ends
     */
    public void terminate(){
        terminate = true;
        timerThread.interrupt();
    }

}
