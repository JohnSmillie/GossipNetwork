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
