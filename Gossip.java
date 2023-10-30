
/*  

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
   
*/

import java.io.*;
import java.net.*;
import java.util.*;


// Must be serializable to send 1 bit after another over the network
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
  boolean isOdd;
}
/**********************************************************************************************/
// Local data object
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
}
/***********************************************************************************************************/
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

  // set the opening values for the local data object
  public static void initGossip(String[] args){
    if(args.length > 0){
      localData.localPort = 48100 + Integer.parseInt(args[0]);
      localData.nodeID = Integer.parseInt(args[0]);
    }
    localData.localDataValue = (int) (Math.random() * 100);
    localData.average = localData.minValue = localData.maxValue = localData.localDataValue;
    localData.cycles = 0;
    localData.N = 20;
    localData.currentRoundCount = 0;
    localData.groupSize = 0;
    localData.maxValueNode = localData.minValueNode = localData.nodeID;
    localData.upperNodePort = localData.localPort+1;
    localData.lowerNodePort = localData.localPort-1;
  
    
    System.out.printf("Node %d listening at port %d\n", localData.nodeID, localData.localPort);

    // listening for input from the user console... aka client code
    ConsoleInputLooper consoleLoop = new ConsoleInputLooper();
    Thread t = new Thread(consoleLoop);
    t.start();
  }
}
/**************************************************************************************************************/
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
      case "aTwo":
        Publisher.publish(gd, nextNodePort);
        if (gd.isOdd && Gossip.localData.nodeID%2 == 1 || !gd.isOdd && Gossip.localData.nodeID%2 == 0){
          AverageCycle2.gossip(gd);
        }
        break;
      case "calcATwo":
        //System.out.println("calc value received: " + gd.average);
        gd.average = AverageCycle2.calcAvg(gd.average);
        //System.out.println("calc returning value: " + Gossip.localData.average);
        Publisher.publish(gd, this.dp.getPort());
        break;
      case "zTwo":
        Gossip.localData.groupSize = 0;
        Gossip.localData.currentRoundCount = 0;
        Publisher.publish(gd, nextNodePort);
        if (gd.isOdd && Gossip.localData.nodeID%2 == 1 || !gd.isOdd && Gossip.localData.nodeID%2 == 0){
          SizeCycle2.gossip(gd);
        }
        break;
      case "calcZTwo":
        //System.out.println("calc value received: " + gd.groupSize);
        gd = SizeCycle2.calcSize(gd);
        //System.out.println("calc returning value: " + Gossip.localData.groupSize);
        Publisher.publish(gd, this.dp.getPort());
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
   
  }
}
/************************************************************************************************************/
class ConsoleInputLooper implements Runnable {
  Scanner input = new Scanner(System.in);
  
  public void run(){
      String clientCommand;
      System.out.println("Enter t to see a list of functions.\n");
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
/*************************************************************************************************************/
// process the input from the user console
// The Gossip Data object will have the user command and will be passed along to subsequent processes
class ConsoleWorker extends Thread{
  GossipData gd;
  public ConsoleWorker(GossipData gd){this.gd = gd;}

  public void run(){
    // handle numeric inputs indicated the setting of N value
    boolean isNumeric = testIsNumeric(gd);
    if(isNumeric){
      Gossip.localData.N = gd.newNValue = Integer.parseInt(gd.userString);
      gd.userString = "N";
    }
    
    switch(gd.userString){
      case "t": 
        System.out.println("l - list local values\np - ping upper node and lower node\nm - retreive the current Max/Min\nv - randomize local bucket value\naTwo - calculate network average\nzTwo - calculate network size\ny - list completed cycles for the each node\nN - set message count for a cycle\nd - delete the current node\nk - shut down the network\n\n");
        break;
      case "p":   
        gd = Pinger.send(gd, Gossip.localData.upperNodePort);
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID+1, gd.pingSuccess);
        gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
        System.out.printf("Ping to node %d successful: %b\n\n", Gossip.localData.nodeID-1, gd.pingSuccess );
        break;
      case "v":
        RandomizeValue.randomize();
        break;
      case "l":
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
      // we don't care about responses. Either a node will receive the call, or it is not running.
        DisplayLocals.displaySize(gd);
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      case "m":
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        MCycle.gossip(gd);
        break;
      case "aTwo":
        System.out.println("Please wait while we process your request. This may take a few seconds.\n");
        if (Gossip.localData.nodeID%2 == 1){
          gd.isOdd = true;
        }
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        AverageCycle2.gossip(gd);
        try{
          Thread.sleep(1000);
        }catch(Exception e) {}
        gd.userString = "la";
        new ConsoleWorker(gd).start();
        break;
      case "zTwo":
        System.out.println("Please wait while we process your request. This may take a few seconds.\n");
        gd.currentRoundCount = Gossip.localData.N;
        if (Gossip.localData.nodeID%2 == 1){
          gd.isOdd = true;
        }
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        Gossip.localData.groupSize = 1;
        SizeCycle2.gossip(gd);
        try{
          Thread.sleep(1000);
        }catch(Exception e) {}
        gd.userString = "lz";
        new ConsoleWorker(gd).start();
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
    // display local values, then send in either direction
    // we don't care about responses. Either a node will receive the call, or it is not running.
    if (gd.userString.equals("v") || gd.userString.equals("l")  || gd.userString.equals("y") || gd.userString.equals("N")){
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
    }
  } 

  // see if the user input are digits 
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
// Basic self-explanatory functions here
// Timing and directional logic is within the GossipWorker case for 
class AverageCycle2 extends Thread{
  
  public static void gossip (GossipData gd){
    try{
        Thread.sleep(100);
    }catch(Exception e) {}
    while (Gossip.localData.currentRoundCount < Gossip.localData.N){
      //System.out.println("Cycle Number: " + Gossip.localData.currentRoundCount);
      gd.pingSuccess = false;
      gd.userString = "p";
      gd = Pinger.send(gd, Gossip.localData.upperNodePort);
      //System.out.println("Ping success: " + gd.pingSuccess);
      if (gd.pingSuccess){
        trade(gd, Gossip.localData.upperNodePort);
       
      }
      gd.pingSuccess = false;
      gd.userString = "p";
      gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
      //System.out.println("Ping success: " + gd.pingSuccess);
      if (gd.pingSuccess){
        trade(gd, Gossip.localData.lowerNodePort);
      }
      try{
        Thread.sleep(400);
    }catch(Exception e) {}
      Gossip.localData.currentRoundCount++;
    }
  }

  public static synchronized void trade(GossipData gd, int nextNodePort){

      //System.out.println("trade value sent " + Gossip.localData.average);
      gd.userString = "calcATwo";
      gd.average = Gossip.localData.average;
      DatagramSocket listener = Publisher.publish(gd, nextNodePort);
      gd = Publisher.listen(listener, gd);
      Gossip.localData.average = gd.average;
      //System.out.println("trade value received " + Gossip.localData.average);
      return;  
  }

  public static double calcAvg(double avg){
      Gossip.localData.average = avg = (Gossip.localData.average + avg)/2;
      return avg;
  }
}
/***********************************************************************************************/

class SizeCycle2{

  public static void gossip (GossipData gd){
    
    try{
        Thread.sleep(100);
    }catch(Exception e) {}
    while (Gossip.localData.currentRoundCount < Gossip.localData.N){
      //System.out.println("Cycle Number: " + Gossip.localData.currentRoundCount);
      gd.pingSuccess = false;
      gd.userString = "p";
      gd = Pinger.send(gd, Gossip.localData.upperNodePort);
      if (gd.pingSuccess){
        trade(gd, Gossip.localData.upperNodePort);
       
      }
      gd.pingSuccess = false;
      gd.userString = "p";
      gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
      if (gd.pingSuccess){
        trade(gd, Gossip.localData.lowerNodePort);
      }
      try{
        Thread.sleep(400);
    }catch(Exception e) {}
      Gossip.localData.currentRoundCount++;
    }
  }

  public static synchronized void trade(GossipData gd, int nextNodePort){
      //System.out.println("trade value sent " + Gossip.localData.groupSize);
      gd.userString = "calcZTwo";
      gd.groupSize = Gossip.localData.groupSize;
      DatagramSocket listener = Publisher.publish(gd, nextNodePort);
      gd = Publisher.listen(listener, gd);
      Gossip.localData.groupSize = gd.groupSize;
      //System.out.println("trade value received " + Gossip.localData.groupSize);
      
    return;
  }

  public static GossipData calcSize(GossipData gd){
    if(Gossip.localData.groupSize == gd.groupSize){return gd;}
    gd.groupSize = Gossip.localData.groupSize = (Gossip.localData.groupSize + gd.groupSize)/2;
    return gd;
  }
}


/***********************************************************************************************/
// timing is used to allow messages enough time to travel between nodes
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
// Send and listen class. uses setSoTimeout() to move forward if no response is received. Returns false if timeout is detected
// This class is also used to trade values in calculating average and size. 
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
    // Socket.setSoTimeout set to continue if no response from neighbor after a given period of time
    // only if data is received will pingSuccess set to true. Else returns false. 
    public static GossipData listen(DatagramSocket dg, GossipData gd){
      try{
        byte[] data = new byte[2048];
        DatagramPacket dp = new DatagramPacket(data, data.length);
        // wait 
        dg.setSoTimeout(75);
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
  public static DatagramSocket publish(GossipData gd, int port){
    DatagramSocket dg = null;
    try{
        gd.nodeID = Gossip.localData.nodeID;
        dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        // set the GossipData obj with the necassary info for a ping call. 
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, port);
        dg.send(dp);
        //dg.close();
  }
  catch(UnknownHostException he ){
        he.printStackTrace();
      }
  catch(IOException io){
        io.printStackTrace();
      }
      return dg;
  }

  public static GossipData listen(DatagramSocket dg, GossipData gd){
      try{
        byte[] data = new byte[2048];
        DatagramPacket dp = new DatagramPacket(data, data.length);
        // wait 
        dg.receive(dp);
        byte[] receivedData = dp.getData();
        //System.out.println("Pinger listen has received info");
        ByteArrayInputStream in = new ByteArrayInputStream(receivedData);
        ObjectInputStream ois = new ObjectInputStream(in);
        try{
          gd = (GossipData) ois.readObject();
          
          //return gd;
        }
        catch (ClassNotFoundException cnf){
          cnf.printStackTrace();
        }
      }
      catch (IOException io){}
      
      return gd;
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
    System.out.println("New local value: " + Gossip.localData.localDataValue + "\n");
    Gossip.localData.maxValue = Gossip.localData.minValue  = Gossip.localData.localDataValue;
    Gossip.localData.maxValueNode = Gossip.localData.minValueNode = Gossip.localData.nodeID;
    Gossip.localData.average = (double) Gossip.localData.localDataValue; 
  }
}
/***********************************************************************************************/
// Display functions will be used to reset flags and round values as these are the last stop in cycles
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
    Gossip.localData.currentRoundCount = 0;
    System.out.printf("Local value: %d, network average: %.2f\n", Gossip.localData.localDataValue, Gossip.localData.average);
    Gossip.localData.average = Gossip.localData.localDataValue;
    System.out.println("\n");
  }
  public static void displaySize(GossipData gd){
    System.out.println("Value received at display" + Gossip.localData.groupSize);
    int temp = (int) (Gossip.localData.groupSize * 1000);
    Gossip.localData.groupSize = (double)temp/1000;
    System.out.println("Group size: " + (int) (1/Gossip.localData.groupSize));
    Gossip.localData.currentRoundCount = 0;
    Gossip.localData.cycles++;
    Gossip.localData.groupSize = 0;
    System.out.println("\n");
  }
}

/*********************************************************************************/
/* Discussion Posts: 

 * My discussion post regarding UDP connection:
 
    Dealing with UDP transport places a lot of responsibility on the application developer to organize communication between processes. 
    Here are some resources I've found particularly useful for navigating these issues as it relates to the Gossip program:

    From Computer Networking: A Top-Down Approach (Kurose/Ross):

    Chapter 2.7.1 Socket Programming with UDP
    Chapter 3.3 Connectionless Transport: UDP
    Chapter 3.4 Principles of Reliable Data Transfer
    Chapter 3.8 Evolution of Transport-Layer Functionality (which introduces QUIC protocols)
    From Distributed Systems (van Steen/Tanenbaum):

    Chapter 4.3 Message-Oriented Communication
    In particular, Note 4.9 - the request-reply pattern
    And, Note 4.10 - the publisher-subscriber pattern
    Additionally, I'm taking inspiration from the QUIC (Quick UDP Internet Connection) protocols, using the following image - the right-hand side - 
    from Wikipedia as a template for a "trade" contract to implement when calculating the average and/or size of the [sub-]network. 
    
    [Image used here]

    Whereas "QUIC combines the handshakes needed to establish connection state with those needed for authentication and encryption" (Kurose/Ross), 
    my idea is to combine establishing connection with performing computations. 

    Lastly, if you're still reading, Java's DatagramSocket class includes some very useful methods. I used one specific method in the implementation of Ping. 
    Taking inspiration from TCP's timeout/retransmit mechanism, the setSoTimeout method is one of many ways to establish a timeout mechanism to detect if a neighbor node is up and running.   

    As always, all feedback is welcomed. Thanks!

  * A response to a fellow student:

    Imagine that you and a few friends download the same application. You will all be running the same program on your individual machines (with their relative IP/port numbers). 
    Each instance of the program will be listening for console input from the user, and simultaneously be responding to incoming requests from peers. 
    This is a peer-to-peer system like those we've studied this term. So yes, "each gossip instance acts as its own client and server", and yes, "each node [will] have its own 
    implementation of console looper".  

    The assignment page includes a suggestion for a full development process. I recommend that section as a tip for getting started. 

    Keep the questions coming and good luck!
/*********************************************************************************

* Sample Output:

    Node 6 listening at port 48106
    Enter t to see a list of functions.

    Locally stored data for node 6
    Public Port: 48106
    Integer Value: 81
    Cycles: 1
    N value: 20


    Locally stored data for node 6
    Public Port: 48106
    Integer Value: 81
    Cycles: 3
    N value: 17


    Local value: 81, network average: 29.76


    v
    Current local value: 81
    New local value: 51
    a
    Local value: 51, network average: 41.75


    z
    new gd.groupSize: 0.25
    Group size: 4


    y
    Number of cycles for node 6: 8


    Max value: 79 at node: 4
    Min value: 7 at node: 3


    d
    Goodbye!

/*********************************************************************************


    Node 5 listening at port 48105
    Enter t to see a list of functions.

    Locally stored data for node 5
    Public Port: 48105
    Integer Value: 2
    Cycles: 1
    N value: 20


    17
    l
    Locally stored data for node 5
    Public Port: 48105
    Integer Value: 2
    Cycles: 3
    N value: 17


    a
    Local value: 2, network average: 29.76


    Current local value: 2
    New local value: 30
    Local value: 30, network average: 41.75


    new gd.groupSize: 0.25
    Group size: 4


    Number of cycles for node 5: 8


    t
    l - list local values
    p - ping upper node and lower node
    m - retreive the current Max/Min
    v - randomize local bucket value
    a - calculate network average
    z - calculate network size
    y - list completed cycles for the each node
    N - set message count for a cycle
    d - delete the current node
    k - shut down the network


    m
    Max value: 79 at node: 4
    Min value: 7 at node: 3


    k
    Goodbye!

/*********************************************************************************

    Node 4 listening at port 48104
    Enter t to see a list of functions.

    l
    Locally stored data for node 4
    Public Port: 48104
    Integer Value: 20
    Cycles: 1
    N value: 20


    Locally stored data for node 4
    Public Port: 48104
    Integer Value: 20
    Cycles: 3
    N value: 17


    Local value: 20, network average: 29.75


    Current local value: 20
    New local value: 79
    Local value: 79, network average: 41.75


    new gd.groupSize: 0.25
    Group size: 4


    Number of cycles for node 4: 8


    Max value: 79 at node: 4
    Min value: 7 at node: 3


    Goodbye!

/*********************************************************************************


    Node 3 listening at port 48103
    Enter t to see a list of functions.

    p
    Ping to node 4 successful: true
    Ping to node 2 successful: false

    Locally stored data for node 3
    Public Port: 48103
    Integer Value: 16
    Cycles: 1
    N value: 20


    Locally stored data for node 3
    Public Port: 48103
    Integer Value: 16
    Cycles: 3
    N value: 17


    Local value: 16, network average: 29.74


    Current local value: 16
    New local value: 7
    Local value: 7, network average: 41.75


    new gd.groupSize: 0.25
    Group size: 4


    Number of cycles for node 3: 8


    Max value: 79 at node: 4
    Min value: 7 at node: 3


    Goodbye!
 */

