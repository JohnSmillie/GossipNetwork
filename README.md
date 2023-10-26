# GossipNetwork
- Peer-2-peer Gossip Network for Distributed Systems
- DePaul University CSC435 Dr. Clark Elliot
- All code written exclusively by John Smillie except where indicated

# How it works
- Run in multiple terminals - each terminal is a symmetric node
- Designed for 10 nodes -> 0-9.
- Ex: java Gossip 0 -> Starts up node 0
- Designed to be run on localhost only


# Basic information
- All communication is UDP
- Multi-threaded, multi-process, asynchronous
- Local node can accept console input while listening and calculating incoming gossip requests
- Console input initiates gossip calculations


# Notes from the program header

1. Name: John W. Smillie
 
2. Date: 2023-10-25
 
3. Java version:  "20.0.2" 2023-07-18
   - Java(TM) SE Runtime Environment (build 20.0.2+9-78)

 
4. Precise command-line compilation examples / instructions:
 
    > javac *.java
    > javac *.java
    
5. Precise examples / instructions to run this program:
 
    - In separate shell windows (in any order):
    
    > java Gossip <option>

    - Option will be an interger 0-9 corresponding to a node in the network
 
6. Full list of files needed for running the program:
 
    a. Gossip.java
 
7. Notes:

    - The system is not set up to handle starting multiple nodes with the same integer. Behavior is undefined.
    - Calculations of average and size will be more precise as N increases in value.
    - Default value for N is 20.
    - N is the maximum number of messages sent between neighbors in 1 cycle. 
    - Definition of a cycle: any input that prints on all nodes in the [sub-]network.
      Thus, one input of "l", "y", "m", "a", "z", "v", and also N (which does not print on the consoles) are one cycle.
    - From the assignment page: "We will test your system by running one cycle at a time" and also
      "Once a Gossip Cycle ends, a new one can be triggered from the console of any network node"
      My system is undefined beyond these statements. 
    - Commands "l", "y", "v", "N (and integer)", and "k" are broadcasted to the network using the Publisher class.
      In this sense, they are "send only" commands.
    - "m" performs N trades with neighbors, at which point the min and max are (theoretically) saved locally in all nodes.
    - It was my goal to perfom "a" and "z" in the same manner as "m", but I wasn't getting the correct results -
      performance was very inconsistent. Instead, "a" and "z" move linearly through the network in N "rounds". Because of this 
      nature, I did not implement a critical section. In this small network the values are calculated efficiently, 
      but this would not remain true for a large "real world" network. 

8. Thanks:
   -This code uses the provided starter code (Clark Elliott, GossipStarter.java copyright (c) 2023 with all rights reserved).
   
