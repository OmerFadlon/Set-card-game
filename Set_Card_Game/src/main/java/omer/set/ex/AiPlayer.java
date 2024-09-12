package omer.set.ex;

import omer.set.Env;


public class AiPlayer extends Player{
    public AiPlayer(Env env, Dealer dealer, Table table, int id){
        super(env,dealer,table, id);
    }

    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        playerThread = Thread.currentThread();

        while (!terminate) {
            if (state == gameState.WAITING){
                    try { synchronized (this) {this.wait();}}
                    catch (InterruptedException Ignored ){}
                }
            else{
                //synchronization with dealer before generating a move so moves won't be done between rounds
                //(dealer has access to the shared resource-"Input" while he is restarting a round.)
                synchronized (this) {
                    if(state==gameState.PLAYING) {
                        Input = (int) (Math.random() * env.config.tableSize);//next action
                        executeAction();
                    }
                }
            }
            //optional for slowing down the Ai Player:
            try{this.playerThread.sleep(env.config.AiDelaySeconds);} catch (InterruptedException Ignored){}
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    //Function Not in use for Ai Player because he generates key presses and process them without the use of the Input manager thread
    @Override
    public void keyPressed(int slot) {
        System.out.println("Function not supported by Ai-Player");
    }
    /**
     * This method places or removes a token according to the keyInput.
     */
    public void executeAction() {
        table.rwLock.readLock().lock();
        // if no card is on the chosen slot->Ignore.
        if (table.slotToCard[Input] != null  ) {
            //place a token on the chosen slot or remove the existing token:
            if (table.tokens[id][Input] == false) {
                table.placeToken(id, Input);
            } else if (table.tokens[id][Input] == true) {
                table.removeToken(id, Input);
            }
        }
        table.rwLock.readLock().unlock();

        if (TokensPlaced == 3) {    //claim a set
            state = gameState.WAITING;
            CheckMySet();
            if (dealer.EndOfRound == true)
                state = gameState.WAITING;
            else
                state = gameState.PLAYING;
        }
    }


}
