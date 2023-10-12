/* Version 1.0 (watch for bugs), 2023-03-02: 





FEATURES:

1. Translation from Java Object to byte array, to datagram, over network, back to byte array, to Java Object.
2. UDP/Datagram listener.
3. UDP/Datagram sending.
4. Two threads: one listening for datagrams and one scanning for user console input.
5. UDP Listener loop fires off a worker to handle the incoming Java object.

Note that with three (or more) threads sharing one console file for output, console messages may
be in a scrambled order.

Java version 20 

Compile: javac GossipStarter.java


Thanks:
  -This code uses the provided starter code (Clark Elliott, GossipStarter.java copyright (c) 2023 with all rights reserved).
      

*/

import java.io.*;
import java.net.*;
import java.util.*;


// Must be serializable to send 1 bit after another over the network.
class GossipData implements Serializable{ 
  int nodeID;
  int average;
  int highValue;
  int highValueNode;
  int lowValue;
  int lowValueNode;
  int groupSize;
  String userString;
  int cycleCount;
  UUID cycleID;
  int cycleStarterNode;
  

  // Whatever else you might want to send.
}
/**********************************************************************************************/
class LocalData {
  int nodeID;
  int serverPort;
  int localDataValue;
  int average;
  int highValue;
  int highValueNode;
  int lowValue;
  int lowValueNode;
  int groupSize;
  int cycles;
  int N;
  //HashMap<UUID, Integer> cycleTracker;
}
/**********************************************************************************************/
// Gossip class called from the CLI with one argument
// birth of the individual gossip node.
public class Gossip {
 
  public static LocalData localData = new LocalData();
  
  public static void main(String[] args){
    initGossip(args);
    // loop to listen for external connections
    try{
      // create a UDP portal
      DatagramSocket UDPSocket = new DatagramSocket(localData.serverPort);
      //System.out.println("SERVER: Receive Buffer size: " + UDPSocket.getReceiveBufferSize() + "\n");  
      byte[] data = new byte[2048];
      //InetAddress IPAddress = InetAddress.getByName("localhost");
      while(true){
        // a DatagramSocket will receive a DatagramPacket as fixed size array of bytes.
        DatagramPacket dp = new DatagramPacket(data, data.length);
        UDPSocket.receive(dp);
         // hand off the data to a new array
        byte[] receivedData = dp.getData();
        // data as a byte array input stream
        ByteArrayInputStream in = new ByteArrayInputStream(receivedData);
        // data as an object in the obect input stream
        ObjectInputStream ois = new ObjectInputStream(in);
        try{
          // read the object of the input stream
          GossipData gd = (GossipData) ois.readObject();
          if (gd.userString.equals("stopserver")){
            System.out.println("SERVER: Stopping UDP listener now.\n");
            UDPSocket.close();
            break;
          }
          // respond to outside correspondence
          new GossipWorker(gd, dp).start();
        }
        catch (ClassNotFoundException cnf){
          cnf.printStackTrace();
        }
      }
    }
    catch(SocketException se){
      se.printStackTrace();
    }
    catch (IOException io){
      io.printStackTrace();
    }
  }
  public static void initGossip(String[] args){
    if(args.length > 0){
      localData.serverPort = 48100 + Integer.parseInt(args[0]);
      localData.nodeID = Integer.parseInt(args[0]);
    }
    localData.localDataValue = (int) (Math.random() * 100);
    localData.average = localData.lowValue = localData.highValue = localData.localDataValue;
    localData.cycles = localData.groupSize = 0;
    localData.N = 20;
    localData.highValueNode = localData.lowValueNode = localData.nodeID;
    System.out.printf("Node %d listening at port %d\n", localData.nodeID, localData.serverPort);

    // listening for input from the user console... aka client code
    ConsoleInputLooper consoleLoop = new ConsoleInputLooper();
    Thread t = new Thread(consoleLoop);
    t.start();
  }
}
/**********************************************************************************/
// process the communication from the outside world
class GossipWorker extends Thread{
  GossipData gd;
  // the datapacket sent from the outside world
  DatagramPacket dp;
  public GossipWorker (GossipData gd, DatagramPacket dp){
    this.gd = gd; 
    this.dp = dp;
  }

  public void run(){
    System.out.println("Communication from the outside world! It's from node: " + gd.nodeID);
    System.out.println("Command received: " + gd.userString + "\n");
    int nextNodePort = gd.nodeID > Gossip.localData.nodeID ?  Gossip.localData.serverPort-1 : Gossip.localData.serverPort+1;
    switch(this.gd.userString){
      
      // pong response 
      case "p":
      // this dp has the return address of the pinger
        Pinger.receive(this.dp, this.gd);
        break;
      case "v":
      // perform local action and forward onward
        RandomizeValue.randomize();
        PublishCommand.publish(gd, nextNodePort);
        break;
      case "l":
      // perform local action and forward onward
        DisplayLocals.displayLocals();
        PublishCommand.publish(gd, nextNodePort);
        break;
      // internal case for printing Max/Min
      case "lm":
      // perform local action and forward onward
        DisplayLocals.displayMaxMin();
        PublishCommand.publish(gd, nextNodePort);
        break;
      case "m":
        MCycle.compute(gd);
        //  handle the cases where the receiver is the sender
        // case: root node has sent 2 publishes, has received one back, and is still waiting for another response
        if(gd.cycleStarterNode == Gossip.localData.nodeID && gd.cycleCount == 2){
          System.out.println("Sender node has received 1 response");
          gd.cycleCount--;
          // case root node received back all expected comms, and is ready to publish results
        } else if (gd.cycleStarterNode == Gossip.localData.nodeID && gd.cycleCount == 1){
          System.out.println("Sender node has received all necessary responses. Disseminate info.");
          gd.cycleCount--;
          // root node will now contain the min and max. time to disseminate the info
          PublishCommand.publish(gd, Gossip.localData.serverPort+1);
          PublishCommand.publish(gd, Gossip.localData.serverPort-1);
          gd.userString = "lm";
          try{
          sleep(250);
          }catch (Exception e){}
          new ConsoleWorker(gd).start();
          break;
        }
        // if next node is running, forward the command onward
        gd.userString = "p";
        if(Pinger.ping(gd, nextNodePort)){
          gd.userString = "m";
          PublishCommand.publish(gd, nextNodePort);
        }
        // else, turn around and send the data back
        else if(gd.cycleCount > 0){
          gd.userString = "m";
          PublishCommand.publish(gd, nextNodePort > Gossip.localData.serverPort ? nextNodePort - 2 : nextNodePort +2);
        }
        break;
      default:
        System.out.println("Input not recognized"); 
    }
    System.out.print("\n>");
  }
}
/**************************************************************************************/
class ConsoleInputLooper implements Runnable {
  Scanner input = new Scanner(System.in);
  
  public void run(){
      String clientCommand;
      System.out.println("Enter t too see a list of functions.\n");
      while(true){
        System.out.print(">");
        clientCommand = input.nextLine();
        if(clientCommand.equals("quit")){
          System.out.println("Goodbye");
          System.exit(0);
        }
        GossipData gd = new GossipData();
        // pass along the command in serializable data object to be shared with the processes
        gd.userString = clientCommand;
        new ConsoleWorker(gd).start();
      }
  }
}
/***********************************************************************************************/
// process the input from the user console
// The Gossip Data object will have the user command and will be passed along to subsequent processes
class ConsoleWorker extends Thread{
  GossipData gd;
  public ConsoleWorker(GossipData gd){this.gd = gd;}

  public void run(){
    System.out.println("Console Worker entered");
    switch(gd.userString){
      case "t": 
        System.out.println("l - list local values\np - ping upper node and lower node\nm - retreive the current Max/Min\nv - randomize local bucket value\n");
        break;
      case "p":   
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID+1, Pinger.ping(gd, Gossip.localData.serverPort+1));
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID-1, Pinger.ping(gd, Gossip.localData.serverPort-1));
        break;
      case "v":
      // randomize the local node and send off in either direction.
      // we don't care about responses. Either a node will receive the call, or it is not running. 
        RandomizeValue.randomize();
        PublishCommand.publish(gd, Gossip.localData.serverPort+1);
        PublishCommand.publish(gd, Gossip.localData.serverPort-1);
        break;
      case "l":
        DisplayLocals.displayLocals();
        PublishCommand.publish(gd, Gossip.localData.serverPort+1);
        PublishCommand.publish(gd, Gossip.localData.serverPort-1);
        break;
      case "lm":
        DisplayLocals.displayMaxMin();
        PublishCommand.publish(gd, Gossip.localData.serverPort+1);
        PublishCommand.publish(gd, Gossip.localData.serverPort-1);
        break;
      case "m":
        MCycle.initM(gd);
        
        // messages go outbound from root, and then inbound to root
        // cycleCount will be equal to the number of branches / expected responses back
        // Gossip worker will use cycleCount to know when to turn around and go back, or stop
        // send the command to upper node if it is running 
        gd.userString = "p";
        if(Pinger.ping(gd, Gossip.localData.serverPort+1)){
          gd.userString = "m";
          gd.cycleCount++;
          PublishCommand.publish(gd, Gossip.localData.serverPort+1);
        }
        // send the command to lower node if it is running
        gd.userString = "p";
        if(Pinger.ping(gd, Gossip.localData.serverPort-1)){
          gd.userString = "m";
          gd.cycleCount++;
          PublishCommand.publish(gd, Gossip.localData.serverPort-1);
        }
        // no neighbors are present, so print local data and call it a day
        if (gd.cycleCount == 0){
          System.out.printf("Max: %d\nMin: %d\n", Gossip.localData.highValue, Gossip.localData.lowValue);
        }
        break;
      default:
        System.out.println("Input not recognized"); 

    }
    System.out.print("\n>");
  }  
}

/***********************************************************************************************/
// simple publish class does not need to know if neighbor node is running
// send off the command to the next node to receive, only if it is running
class PublishCommand{
  public static void publish(GossipData gd, int port){
    try{
        gd.nodeID = Gossip.localData.nodeID;
        DatagramSocket dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        // set the GossipData obj with the necassary info for a ping call. 
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, port);
        dg.send(dp);
        dg.close();
  }
  catch(UnknownHostException he ){
        he.printStackTrace();
      }
  catch(IOException io){
        io.printStackTrace();
      }
}
}
/***********************************************************************************************/
class Pinger extends Thread {
  public static boolean pingSuccess = false;

  public static boolean ping(GossipData gd, int port){
    if(port < 48100 || port > 48109){
      return false;
    }
    try{
        DatagramSocket dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        // set the GossipData obj with the necassary info for a ping call. 
        gd.nodeID = Gossip.localData.nodeID;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, port);
        dg.send(dp);
        pingSuccess = listen(dg, gd);
      }
      catch(UnknownHostException he ){
        he.printStackTrace();
      }
      catch(IOException io){
        io.printStackTrace();
      }
      
      return pingSuccess;
    }

    // listen for response from the neighbor on the same socket as the outgoing ping
    // Socket.setSoTimeout set to continue if no response from neighbor
    // only if data is received will pingSuccess set to true. Else returns false. 
    public static boolean listen(DatagramSocket dg, GossipData gd){
      try{
        byte[] data = new byte[2048];
        DatagramPacket dp = new DatagramPacket(data, data.length);
        dg.setSoTimeout(500);
        dg.receive(dp);
        byte[] receivedData = dp.getData();
        ByteArrayInputStream in = new ByteArrayInputStream(receivedData);
        ObjectInputStream ois = new ObjectInputStream(in);
        try{
          gd = (GossipData) ois.readObject();
          return true;
        }
        catch (ClassNotFoundException cnf){
          cnf.printStackTrace();
        }
      }
      catch(SocketTimeoutException ste){
      }
      catch (IOException io){}
      return false;
    }

    // ping receiever receives the ping and pongs back
     public static void receive(DatagramPacket dp, GossipData gd){
      try{
          DatagramSocket dg = new DatagramSocket();
          InetAddress IPAddress = InetAddress.getByName("localhost");
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(outputStream);
          oos.writeObject(gd);
          byte[] data = outputStream.toByteArray();
          DatagramPacket responsePacket = new DatagramPacket(data, data.length, IPAddress, dp.getPort());
          dg.send(responsePacket);
          dg.close(); 
        }
        catch(UnknownHostException he ){
          he.printStackTrace();
        }
        catch(IOException io){
          io.printStackTrace();
        }
    }
}
/***********************************************************************************************/
class MCycle{

public static void initM(GossipData gd){
    //reset values as high/low could've changed with randomizer
    gd.highValue = gd.lowValue = Gossip.localData.localDataValue;
    gd.highValueNode = gd.lowValueNode = Gossip.localData.nodeID;
    gd.cycleStarterNode = Gossip.localData.nodeID;
    gd.cycleCount = 0;
  }

  public static void compute(GossipData gd){
    if(Gossip.localData.highValue > gd.highValue){
      gd.highValue = Gossip.localData.highValue;
      gd.highValueNode = Gossip.localData.highValueNode;
    }
    else {
      Gossip.localData.highValue = gd.highValue;
      Gossip.localData.highValueNode = gd.highValueNode;
    }
    if(Gossip.localData.lowValue < gd.lowValue){
      gd.lowValue = Gossip.localData.lowValue;
      gd.lowValueNode = Gossip.localData.lowValueNode;
    }
    else {
      Gossip.localData.lowValue = gd.lowValue;
      Gossip.localData.lowValueNode = gd.lowValueNode;
    }
  }
 
}
/***********************************************************************************************/
// randomizer will reset stored data (max/min, etc.) as they are no longer accurate. 
class RandomizeValue{
  public static void randomize(){
    // locally increase the cycle count
    Gossip.localData.cycles++;
    System.out.println("Current local value: " + Gossip.localData.localDataValue );
    Gossip.localData.localDataValue = (int) (Math.random() * 100);
    System.out.println("New local value: " + Gossip.localData.localDataValue );
    Gossip.localData.highValue = Gossip.localData.lowValue = Gossip.localData.localDataValue;
    Gossip.localData.highValueNode = Gossip.localData.lowValueNode = Gossip.localData.nodeID;
  }
}
/***********************************************************************************************/
// "l" and "m" will call these functions once locally, with respect to the input
// use this opportunity to increase the LOCAL cycle count
class DisplayLocals {
  public static void displayLocals(){
    Gossip.localData.cycles++;
    System.out.printf("Locally stored data for node %d\nPublic Port: %d\nInteger Value: %d\nCycles: %d\n", Gossip.localData.nodeID, Gossip.localData.serverPort, Gossip.localData.localDataValue, Gossip.localData.cycles);
  }
  public static void displayMaxMin(){
    Gossip.localData.cycles++;
    System.out.println("Max value: " + Gossip.localData.highValue + " at node: " + Gossip.localData.highValueNode );
    System.out.println("Min value: " + Gossip.localData.lowValue + " at node: " + Gossip.localData.lowValueNode);
  }
}

/*********************************************************************************/
/* Discussion Posts:  */

/*        OUTBOUND UDP UTILITY CODE
          DatagramSocket dg = new DatagramSocket();
          InetAddress IPAddress = InetAddress.getByName("localhost");
          GossipData gd = new GossipData();
          gd.userString = clientCommand;
          gd.isFromConsole = true;
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(outputStream);
          oos.writeObject(gd);
          byte[] data = outputStream.toByteArray();
          DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, Gossip.serverPort);
          dg.send(dp);
          dg.close(); */
