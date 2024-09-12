My Concurrent Implementation of the card game - "Set"

Introdaction:

Designed and implemented an online version of the card game "set", utilizing threads, synchronization methods, and concurrency to facilitate simultaneous gameplay for both human and AI players effectively. The project was part of the Systems Programming course at Ben Gurion University, The main goal of the project was to practice concurrent programming on Java 8 environment. My enthusiasm for the project led me to reimplement it, resulting in a faster, more efficient, and modular version in which CPU-controlled players compete against each other in real-time.

Objectives:

a. Ensuring Thread Safety and Correctness: Emphasis on critical sections and addressing edge cases to prevent concurrency issues.

b. CPU Optimization: Utilizing thread synchronization techniques, including Readers-Writers locks, to enhance CPU resource utilization.

c. Object-Oriented Programming: Utilizing inheritance to create an abstract Player class, extended by Human-Player class and Ai - player class, along with other essential entities like the Table, Dealer, and Timer.

Overview of the "Set" Card Game:

The "Set" card game involves a dealer distributing cards onto the table, players competing against each other aiming to collect valid sets of three cards by placing tokens on the table. Each card has four attributes: shape, color, number, and shading. A valid set includes three cards satisfying conditions like having either the same or different attributes for each characteristic. The dealer evaluates the players' sets, rewards or penalizes them based on set validity, retrieves their tokens. A background timer resets the table upon reaching zero.

Project Entities:

a. The Table: Acts as a singleton-like repository, stores shared resources such as current table cards and players' tokens. b. The Player: An abstract class serving as the foundation for both Human and Bot players, with the next card selection mechanism implemented in inheriting classes. c. The Dealer: Responsible for table resets and set evaluations. d. The Timer: An additional thread managing the countdown functionality of each round of the game. Prompting table resets when the countdown is over.

Challenges and Solutions:

a. Managing Shared Resources: Implemented a Readers-Writers lock to handle the dynamic resource allocation of the game's table. Allowing players to access the data simultaneously as readers, while giving priority to the dealer as the only writer. b. Optimizing CPU Resource Utilization: Ensuring threads to be waiting while no action is needed, established using an effective inter-entity communication based on notifications. c. Dealer Instructions to Players: Utilized a "state" data member for real-time updates from the dealer to players, influencing their actions based on their respective states: waiting, playing, point, penalty.

For running the game:

After you git clone this repo, simply run the main file of the project to start the game.
compete against your friend and the Ai players using the keyboard to pick cards and collect sets.
Key board bottuns for player 1: "o", "i", "u", "y", "l", "k", "j", "h", ".", ",", "m", "n"

Key board bottuns for player 2: "q", "w", "e", "r", "a", "s", "d", "f", "z", "x", "c", "v"

Commit Optional changes to the game settings through the config file data: a. Changing the number of human players or Ai players. b. Changing the timer settings c. Changing the player's reward/penelty system operation.
