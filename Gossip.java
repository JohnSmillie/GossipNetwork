
/*  

1. Name: John W. Smillie
 
2. Date: 2023-10-25
 
3. Java version:  "20.0.2" 2023-07-18
   Java(TM) SE Runtime Environment (build 20.0.2+9-78)

 
4. Precise command-line compilation examples / instructions:
 
    > javac *.java
    > javac *.java
    
 
5. Precise examples / instructions to run this program:
 
    In separate shell windows (in any order):
    
    > java Gossip <option>

    Option will be an interger 0-9 corresponding to a node in the network
  

 
6. Full list of files needed for running the program:
 
    a. Gossip.java
 
7. Notes:
    - Client and ClientAdmin are permitted to swith servers even of secondary server is not running,
      however, if they try and take any action with this server they will receive a message 
      indicating the server is not available.
    - I chose to use implementation one from the provided Joke State document.
    - Comments are placed ABOVE the line or section of which they describe.
    - My code utilizes the resources provided with the assignment. Specifically, asynchronus threads, and mode changer
      were rewritten with my own constructs or logic.
    - Closing down a server with active clients and/or admin can blow up the system. Clients and Admins can come and go with no ill effects.
    - I only one machine, therefore I have only tested this system with local IP addresses (i.e. java JokeClient localhost localhost and java JokeClient 10.0.0.177 10.0.0.177) 

8. Thanks:
   -This code uses the provided starter code (Clark Elliott, GossipStarter.java copyright (c) 2023 with all rights reserved).
   
*/

import java.io.*;
import java.net.*;
import java.util.*;


// Must be serializable to send 1 bit after another over the network.
class GossipData implements Serializable{ 
  int nodeID;
  int maxValue;
  int maxValueNode;
  int minValue;
  int minValueNode;
  double average;
  double groupSize;
  String userString;
  int cycleStarterNode;
  int currentRoundCount;
  boolean pingSuccess;
  int newNValue;
}
/**********************************************************************************************/
class LocalData {
  int nodeID;
  int localPort;
  int upperNodePort;
  int lowerNodePort;
  int localDataValue;
  int maxValue;
  int maxValueNode;
  int minValue;
  int minValueNode;
  double average;
  double groupSize;
  int cycles;
  int currentRoundCount;
  int N;
  boolean firstVisit;
  boolean hasUpper;
  boolean hasLower;
  
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
      DatagramSocket UDPSocket = new DatagramSocket(localData.localPort);
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
          new GossipWorker(gd, dp, UDPSocket).start();
          
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
      localData.localPort = 48100 + Integer.parseInt(args[0]);
      localData.nodeID = Integer.parseInt(args[0]);
    }
    localData.localDataValue = (int) (Math.random() * 100);
    localData.average = localData.minValue = localData.maxValue = localData.localDataValue;
    localData.cycles = 0;
    localData.N = 20;
    localData.currentRoundCount = localData.N;
    localData.groupSize = 0;
    localData.maxValueNode = localData.minValueNode = localData.nodeID;
    localData.upperNodePort = localData.localPort+1;
    localData.lowerNodePort = localData.localPort-1;
    localData.firstVisit = true;
    localData.hasUpper = false;
    localData.hasLower = false;
    
    System.out.printf("Node %d listening at port %d\n", localData.nodeID, localData.localPort);

    // listening for input from the user console... aka client code
    ConsoleInputLooper consoleLoop = new ConsoleInputLooper();
    Thread t = new Thread(consoleLoop);
    t.start();
  }

  public static void testNeighbors(GossipData gd){
    gd.userString = "p";
    gd = Pinger.send(gd, Gossip.localData.upperNodePort);
    localData.hasUpper = gd.pingSuccess;
    gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
    localData.hasLower = gd.pingSuccess;
  }
}
/**********************************************************************************/
// process the communication from the outside world
class GossipWorker extends Thread{
  GossipData gd;
  // the datapacket sent from the outside world
  DatagramPacket dp;
  DatagramSocket dg;
  public GossipWorker (GossipData gd, DatagramPacket dp, DatagramSocket dg){
    this.gd = gd; 
    this.dp = dp;
    this.dg = dg;
  }
  
  public void run(){
   
    //System.out.println("Communication from the outside world! It's from node: " + gd.nodeID);
    //System.out.println("Command received: " + gd.userString + "\n");
    // if sender is lower, next port is upper and vice versa
    int nextNodePort = gd.nodeID > Gossip.localData.nodeID ?  Gossip.localData.localPort-1 : Gossip.localData.localPort+1;
    switch(this.gd.userString){
      
      // pong response 
      case "p":
      // this dp has the return address of the pinger
        Publisher.publish(gd, this.dp.getPort());
        break;
      case "v":
      // perform local action and forward onward
        RandomizeValue.randomize();
        Publisher.publish(gd, nextNodePort);
        break;
      case "l":
      // perform local action and forward onward
        DisplayLocals.displayLocals();
        Publisher.publish(gd, nextNodePort);
        break;
      case "la":
        DisplayLocals.displayAverage();
        Publisher.publish(gd, nextNodePort);
        break;
      case "lz":
        DisplayLocals.displaySize(gd);
        Publisher.publish(gd, nextNodePort);
        break;
      case "m":
        Publisher.publish(gd, nextNodePort);
        MCycle.gossip(gd);
        break;
      case "calcM":
        MCycle.calcM(gd);
        break;
      case "aTest":
        Gossip.testNeighbors(gd);
        gd.userString = "aTest";
        Publisher.publish(gd, nextNodePort);
        break;
      case "a":
        System.out.println("Node receiving trade: " + Gossip.localData.nodeID);
        // calculate average when contacted by neighbor
        gd.average = AverageCycle.calcAvg(gd.average);
        Publisher.publish(gd, this.dp.getPort());
         
        // check if this node is the initiator
        if(Gossip.localData.nodeID == gd.cycleStarterNode){
          gd.currentRoundCount--;
          //System.out.println("cycles remaining: " + gd.currentRoundCount);
        }
        if (gd.currentRoundCount < 1){
           //System.out.println("cycle complete");
           gd.userString = "la";
           gd.currentRoundCount = 0;
           new ConsoleWorker(gd).start();
           break;
        }
        // determine which node to trade with next
        if(nextNodePort == Gossip.localData.upperNodePort){ 
          if( Gossip.localData.hasUpper){
            //System.out.printf("Sending from node %d to %d ", Gossip.localData.nodeID, nextNodePort);
            AverageCycle.trade(gd, nextNodePort);
         }
          else {
            //System.out.printf("Sending from node %d to %d\n", Gossip.localData.nodeID, nextNodePort-2);
            AverageCycle.trade(gd, nextNodePort - 2);
          }
        }
         else{
          if(Gossip.localData.hasLower){
            //System.out.printf("Sending from node %d to %d ", Gossip.localData.nodeID, nextNodePort);
            AverageCycle.trade(gd, nextNodePort);
          }
          else{
            //System.out.printf("Sending from node %d to %d\n", Gossip.localData.nodeID, nextNodePort+2);
            AverageCycle.trade(gd, nextNodePort + 2);
          }
         
        }
        break;

      case "z":
        gd = SizeCycle.compute(gd);
        Publisher.publish(gd, dp.getPort());
        if(Gossip.localData.nodeID == gd.cycleStarterNode){
          gd.currentRoundCount--;
          //System.out.println("cycles remaining: " + gd.currentRoundCount);
        }
        if (gd.currentRoundCount < 1){
           System.out.println("cycle complete");
           gd.userString = "lz";
           gd.currentRoundCount = 0;
           new ConsoleWorker(gd).start();
           break;
        }
        SizeCycle.gossip(gd, nextNodePort);
        break;


      case "y":
        DisplayLocals.displayCycles();
        Publisher.publish(gd, nextNodePort);
        break;
      case "N":
        Gossip.localData.N = gd.newNValue;
        Gossip.localData.cycles++;
        Publisher.publish(gd, nextNodePort);
        break;
      case "k":
        System.out.println("Goodbye!");
        Publisher.publish(gd, nextNodePort);
        System.exit(0);
        break;
      default:
        System.out.println("Input not recognized"); 
    }
    //System.out.println("");
  }
}
/**************************************************************************************/
class ConsoleInputLooper implements Runnable {
  Scanner input = new Scanner(System.in);
  
  public void run(){
      String clientCommand;
      System.out.println("Enter t too see a list of functions.\n");
      while(true){
        System.out.print("");
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
    boolean isNumeric = testIsNumeric(gd);
    if(isNumeric){
      Gossip.localData.N = gd.newNValue = Integer.parseInt(gd.userString);
      gd.userString = "N";
    }
    switch(gd.userString){
      case "t": 
        System.out.println("l - list local values\np - ping upper node and lower node\nm - retreive the current Max/Min\nv - randomize local bucket value\na - calculate network average\nz - calculate network size\ny - list completed cycles for the each node\nN - set message count for a cycle\nd - delete the current node\nk - shut down the network\n\n");
        break;
      case "p":   
        gd = Pinger.send(gd, Gossip.localData.upperNodePort);
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID+1, gd.pingSuccess);
        gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
        System.out.printf("Ping to node %d successful: %b\n\n", Gossip.localData.nodeID-1, gd.pingSuccess );
        break;
      case "v":
      // randomize the local node and send off in either direction to be performed locally
      // we don't care about responses. Either a node will receive the call, or it is not running. 
        RandomizeValue.randomize();
        //Publisher.publish(gd, Gossip.localData.upperNodePort);
        //Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      case "l":
      // display local values, then send in either direction
      // we don't care about responses. Either a node will receive the call, or it is not running.
        DisplayLocals.displayLocals();
        break;
      case "la":
      // display local average, then send in either direction
      // we don't care about responses. Either a node will receive the call, or it is not running.
        DisplayLocals.displayAverage();
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      case "lz":
        DisplayLocals.displaySize(gd);
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      case "m":
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        MCycle.gossip(gd);
        break;
      case "a": 

        AverageCycle.initAvg(gd);
        Gossip.testNeighbors(gd);
        if(!Gossip.localData.hasLower && !Gossip.localData.hasUpper){
          DisplayLocals.displayAverage();
          break;
        }
        gd.userString = "aTest";
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        try{
          Thread.sleep(1000);
        }catch(Exception e){}
        gd.userString = "a";
        if (Gossip.localData.hasUpper){
          AverageCycle.trade(gd, Gossip.localData.upperNodePort);
        }
        else {
          AverageCycle.trade(gd, Gossip.localData.lowerNodePort);
        }
        break;
      case "z":
        SizeCycle.initSize(gd);
        SizeCycle.gossip(gd, Gossip.localData.upperNodePort);
        break;
      case "y":
        DisplayLocals.displayCycles();
        break;
      case "N":
        Gossip.localData.cycles++;
        break;
      case "d":
        System.out.println("Goodbye!");
        System.exit(0);
        break;
      case "k":
        System.out.println("Goodbye!");
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        System.exit(0);
        break;
      default:
        System.out.println("Input not recognized"); 

    }
    if (gd.userString.equals("v") || gd.userString.equals("l")  || gd.userString.equals("y") || gd.userString.equals("N")){
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
    }
    
  } 

  public boolean testIsNumeric(GossipData gd){
    char[] userInput = gd.userString.toCharArray();
    if(userInput.length < 1){return false;}
    for(int i = 0; i < userInput.length; i++){
      if (userInput[i] > 57 || userInput[0] < 48){
        return false;
      }
    }
    return true;
  }
}
/***********************************************************************************************/

class AverageCycle extends Thread{
  

  public static void initAvg(GossipData gd){
    //System.out.println("Initializer called.");
    gd.average = Gossip.localData.average;
    gd.currentRoundCount = Gossip.localData.N;
    gd.cycleStarterNode = Gossip.localData.nodeID;
    
  }

  public static void trade(GossipData gd, int nextNodePort){
    //System.out.printf("Node %d has received back trade value\n", Gossip.localData.nodeID);
      gd = Pinger.send(gd, nextNodePort);
      Gossip.localData.average = gd.average;
      //System.out.printf("new value %f stored at node %d\n", Gossip.localData.average, Gossip.localData.nodeID);
   
      return;  
  }

  public static double calcAvg(double avg){
      //System.out.println("local average: " + Gossip.localData.average + " gd.average: " + avg);
      
      Gossip.localData.average = avg = (Gossip.localData.average + avg)/2;
      //System.out.printf("new value %f stored at node %d\n", Gossip.localData.average, Gossip.localData.nodeID);
    return avg;
  }
  
  

  
}
/***********************************************************************************************/

class SizeCycle{
  public static GossipData initSize(GossipData gd){
    Gossip.localData.groupSize = gd.groupSize = 1;
    gd.currentRoundCount = Gossip.localData.N;
    gd.cycleStarterNode = Gossip.localData.nodeID;
    return gd;
  }

  public static void gossip(GossipData gd, int nextNodePort){
    gd = Pinger.send(gd, nextNodePort);
      if(gd.pingSuccess){
        Gossip.localData.groupSize = gd.groupSize;
       return;
      }
    gd = Pinger.send(gd, nextNodePort > Gossip.localData.localPort ? nextNodePort - 2 : nextNodePort + 2);
      if(gd.pingSuccess){
        Gossip.localData.groupSize = gd.groupSize;
       return;
      }
    System.out.println("There are no neighbor nodes. Size: 1");
  }

  public static GossipData compute(GossipData gd){
    if(Gossip.localData.groupSize == gd.groupSize){return gd;}
    gd.groupSize = Gossip.localData.groupSize = (Gossip.localData.groupSize + gd.groupSize)/2;
    return gd;
  }
}


/***********************************************************************************************/
class MCycle{

  public static void gossip (GossipData gd){
    gd.userString = "calcM";
    try{
    Thread.sleep(100);
    }catch (Exception e){}
    do{
      //System.out.println("Cycles remaining: " + Gossip.localData.currentRoundCount);
      gd.maxValue = Gossip.localData.maxValue;
      gd.maxValueNode = Gossip.localData.maxValueNode;
      gd.minValue = Gossip.localData.minValue;
      gd.minValueNode = Gossip.localData.minValueNode;
      Publisher.publish(gd, Gossip.localData.upperNodePort);
      Publisher.publish(gd, Gossip.localData.lowerNodePort);
      --Gossip.localData.currentRoundCount;
      try{
        Thread.sleep(25);
      }catch (Exception e){}
    }while(Gossip.localData.currentRoundCount > 1);
    try{
    Thread.sleep(100);
    }catch (Exception e){}
    DisplayLocals.displayMaxMin();
  }

  public static void calcM(GossipData gd){
    if(gd.maxValue > Gossip.localData.maxValue ){
      Gossip.localData.maxValue = gd.maxValue;
      Gossip.localData.maxValueNode = gd.maxValueNode;
    }
    if(gd.minValue < Gossip.localData.minValue ){
      Gossip.localData.minValue = gd.minValue;
      Gossip.localData.minValueNode = gd.minValueNode;
    }
  }
}

/***********************************************************************************************/
class Pinger extends Thread {
  
  public static GossipData send(GossipData gd, int port){
    if(port < 48100 || port > 48109){
      return gd;
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
        gd = listen(dg, gd);
        //System.out.println("Pinger success: " + gd.pingSuccess);
      }
      catch(UnknownHostException he ){
        he.printStackTrace();
      }
      catch(IOException io){
        io.printStackTrace();
      }
      return gd;
    }

    // listen for response from the neighbor on the same socket as the outgoing ping
    // Socket.setSoTimeout set to continue if no response from neighbor
    // only if data is received will pingSuccess set to true. Else returns false. 
    public static GossipData listen(DatagramSocket dg, GossipData gd){
      try{
        byte[] data = new byte[2048];
        DatagramPacket dp = new DatagramPacket(data, data.length);
        // wait 
        dg.setSoTimeout(150);
        dg.receive(dp);
        byte[] receivedData = dp.getData();
        //System.out.println("Pinger listen has received info");
        ByteArrayInputStream in = new ByteArrayInputStream(receivedData);
        ObjectInputStream ois = new ObjectInputStream(in);
        try{
          gd = (GossipData) ois.readObject();
          gd.pingSuccess = true;
          return gd;
        }
        catch (ClassNotFoundException cnf){
          cnf.printStackTrace();
        }
      }
      catch(SocketTimeoutException ste){
       // System.out.println("Socket time out");
      }
      catch (IOException io){}
      gd.pingSuccess = false;
      return gd;
    }
}
/***********************************************************************************************/
// simple publish class does not need to know if neighbor node is running
// send off the command to the next node to receive, only if it is running
class Publisher{
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
// randomizer will reset stored data (max/min, etc.) as they are no longer accurate. 
class RandomizeValue{
  public static void randomize(){
    // locally increase the cycle count
    Gossip.localData.cycles++;
    System.out.println("Current local value: " + Gossip.localData.localDataValue );
    Gossip.localData.localDataValue = (int) (Math.random() * 100);
    System.out.println("New local value: " + Gossip.localData.localDataValue );
    Gossip.localData.maxValue = Gossip.localData.minValue  = Gossip.localData.localDataValue;
    Gossip.localData.maxValueNode = Gossip.localData.minValueNode = Gossip.localData.nodeID;
    Gossip.localData.average = (double) Gossip.localData.localDataValue; 
  }
}
/***********************************************************************************************/
// "l" and "m" will call these functions once locally, with respect to the input
// use this opportunity to increase the LOCAL cycle count
class DisplayLocals {
  public static void displayLocals(){
    Gossip.localData.cycles++;
    System.out.printf("Locally stored data for node %d\nPublic Port: %d\nInteger Value: %d\nCycles: %d\nN value: %d\n", Gossip.localData.nodeID, Gossip.localData.localPort, Gossip.localData.localDataValue, Gossip.localData.cycles, Gossip.localData.N);
    System.out.println("\n");
  }
  public static void displayMaxMin(){
    Gossip.localData.cycles++;
    Gossip.localData.currentRoundCount = Gossip.localData.N;
    System.out.println("Max value: " + Gossip.localData.maxValue + " at node: " + Gossip.localData.maxValueNode );
    System.out.println("Min value: " + Gossip.localData.minValue + " at node: " + Gossip.localData.minValueNode);
    System.out.println("\n");
  }
  public static void displayCycles(){
    Gossip.localData.cycles++;
    System.out.printf("Number of cycles for node %d: %d\n", Gossip.localData.nodeID, Gossip.localData.cycles);
    System.out.println("\n");
  }
  public static void displayAverage(){
    Gossip.localData.cycles++;
    Gossip.localData.currentRoundCount = Gossip.localData.N;
    System.out.printf("Local value: %d, network average: %.2f\n", Gossip.localData.localDataValue, Gossip.localData.average);
    Gossip.localData.average = Gossip.localData.localDataValue;
    Gossip.localData.firstVisit = true;
    Gossip.localData.hasUpper = false;
    Gossip.localData.hasLower = false;
    
    System.out.println("\n");
  }
  public static void displaySize(GossipData gd){
    Gossip.localData.cycles++;
    double testD = 1/gd.groupSize;
    int testI = (int) (1/gd.groupSize);
    double testDiv = testD/testI;
    System.out.printf("Group size: %d\n",  testDiv > 1 ? testI+1 : testI );
    Gossip.localData.groupSize = 0;
    System.out.println("\n");
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
          DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, Gossip.localPort);
          dg.send(dp);
          dg.close(); */
