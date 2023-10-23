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
  int maxValue;
  int maxValueNode;
  int minValue;
  int minValueNode;
  double average;
  double groupSize;
  String userString;
  int cycleStarterNode;
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
  int currentCycleCount;
  int N;
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
      localData.localPort = 48100 + Integer.parseInt(args[0]);
      localData.nodeID = Integer.parseInt(args[0]);
    }
    localData.localDataValue = (int) (Math.random() * 100);
    localData.average = localData.minValue = localData.maxValue = localData.localDataValue;
    localData.cycles = 0;
    localData.N = 20;
    localData.currentCycleCount = localData.N;
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
      // internal case for printing average
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
      case "a":
        Publisher.publish(gd, nextNodePort);
        AverageCycle.gossip(gd);
        break;
      case "calcA":
        gd.average = AverageCycle.calcAvg(gd.average);
        Publisher.publish(gd, this.dp.getPort());
        break;
     /*  case "a":
      // calculate average when contacted by neighbor
        gd.average = AverageCycle.calcAvg(gd.average);
        // send back the results
        Publisher.publish(gd, this.dp.getPort());
        // check if this node is the initiator
        if(Gossip.localData.nodeID == gd.cycleStarterNode){
          gd.currentCycleCount--;
          System.out.println("cycles remaining: " + gd.currentCycleCount);
        }
        if (gd.currentCycleCount < 1){
           System.out.println("cycle complete");
           gd.userString = "la";
           gd.currentCycleCount = 0;
           new ConsoleWorker(gd).start();
           break;
        }
        AverageCycle.cycle(gd, nextNodePort);
        break;
      case "z":
        gd = SizeCycle.compute(gd);
        Publisher.publish(gd, dp.getPort());
        if(Gossip.localData.nodeID == gd.cycleStarterNode){
          gd.currentCycleCount--;
          System.out.println("cycles remaining: " + gd.currentCycleCount);
        }
        if (gd.currentCycleCount < 1){
           System.out.println("cycle complete");
           gd.userString = "lz";
           gd.currentCycleCount = 0;
           new ConsoleWorker(gd).start();
           break;
        }
        SizeCycle.cycle(gd, nextNodePort);
        break; */
      case "y":
        DisplayLocals.displayCycles();
        Publisher.publish(gd, nextNodePort);
        break;
      case "N":
        Gossip.localData.N = gd.newNValue;
        Publisher.publish(gd, nextNodePort);
        break;
      default:
        System.out.println("Input not recognized"); 
    }
    
    //System.out.print("\n>");
    //Gossip.localData.working = false;
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
    System.out.println("Console Worker entered");
    boolean isNumeric = testIsNumeric(gd);
    if(isNumeric){
      Gossip.localData.N = gd.newNValue = Integer.parseInt(gd.userString);
      gd.userString = "N";
    }
    switch(gd.userString){
      case "t": 
        System.out.println("l - list local values\np - ping upper node and lower node\nm - retreive the current Max/Min\nv - randomize local bucket value\n");
        break;
      case "p":   
        gd = Pinger.send(gd, Gossip.localData.upperNodePort);
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID+1, gd.pingSuccess);
        gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
        System.out.printf("Ping to node %d successful: %b\n", Gossip.localData.nodeID-1, gd.pingSuccess );
        break;
      case "v":
      // randomize the local node and send off in either direction to be performed locally
      // we don't care about responses. Either a node will receive the call, or it is not running. 
        RandomizeValue.randomize();
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      case "l":
      // display local values, then send in either direction
      // we don't care about responses. Either a node will receive the call, or it is not running.
        DisplayLocals.displayLocals();
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
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
        //AverageCycle.initAvg(gd);
        //AverageCycle.cycle(gd, Gossip.localData.upperNodePort);
        //AverageCycle.cycle(gd, Gossip.localData.lowerNodePort);
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        AverageCycle.gossip(gd);
        break;
      case "z":
        SizeCycle.initSize(gd);
        SizeCycle.cycle(gd, Gossip.localData.upperNodePort);
        break;
      case "y":
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        DisplayLocals.displayCycles();
        break;
      case "N":
        Publisher.publish(gd, Gossip.localData.upperNodePort);
        Publisher.publish(gd, Gossip.localData.lowerNodePort);
        break;
      default:
        System.out.println("Input not recognized"); 

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

class SizeCycle{
  public static GossipData initSize(GossipData gd){
    Gossip.localData.groupSize = gd.groupSize = 1;
    gd.cycleStarterNode = Gossip.localData.nodeID;
    return gd;
  }

  public static void cycle(GossipData gd, int nextNodePort){
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
      //System.out.println("Cycles remaining: " + Gossip.localData.currentCycleCount);
      gd.maxValue = Gossip.localData.maxValue;
      gd.maxValueNode = Gossip.localData.maxValueNode;
      gd.minValue = Gossip.localData.minValue;
      gd.minValueNode = Gossip.localData.minValueNode;
      Publisher.publish(gd, Gossip.localData.upperNodePort);
      Publisher.publish(gd, Gossip.localData.lowerNodePort);
      --Gossip.localData.currentCycleCount;
      try{
        Thread.sleep(25);
      }catch (Exception e){}
    }while(Gossip.localData.currentCycleCount > 1);
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

class AverageCycle extends Thread{
  
  public static void gossip(GossipData gd){
    gd.userString = "p";
    try{
      Thread.sleep(250);
    }catch (Exception e){} 
    gd = Pinger.send(gd, Gossip.localData.upperNodePort);
    boolean hasUpper = gd.pingSuccess;
    gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
    boolean hasLower = gd.pingSuccess;
    if(!hasLower && !hasUpper){
      System.out.println("There are no neighbors present.");
      return;
    }
    gd.average = Gossip.localData.localDataValue;
    gd.userString = "calcA";
    try{
      Thread.sleep(1500);
    }catch (Exception e){}
    do{
      gd.average = Gossip.localData.average;
      if(hasUpper){
        gd = Pinger.send(gd, Gossip.localData.upperNodePort);
        Gossip.localData.average = gd.average;
      }
      if(hasLower){
        gd = Pinger.send(gd, Gossip.localData.lowerNodePort);
        Gossip.localData.average = gd.average;
      }
      --Gossip.localData.currentCycleCount;
      try{
        Thread.sleep(500);
      }catch (Exception e){}
    }while(Gossip.localData.currentCycleCount > 1);
    try{
    Thread.sleep(250);
    }catch (Exception e){}
    DisplayLocals.displayAverage();
  }

  public static double calcAvg(double avg){
      System.out.println("local average: " + Gossip.localData.average + " gd.average: " + avg);
      Gossip.localData.average = avg = (Gossip.localData.average + avg)/2;
    return avg;
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
        dg.setSoTimeout(250);
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
    System.out.println("Group size: " + Gossip.localData.groupSize);
    System.out.println("\n");
  }
  public static void displayMaxMin(){
    Gossip.localData.cycles++;
    Gossip.localData.currentCycleCount = Gossip.localData.N;
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
    Gossip.localData.currentCycleCount = Gossip.localData.N;
    System.out.printf("Local value: %d, network average: %.2f\n", Gossip.localData.localDataValue, Gossip.localData.average);
    Gossip.localData.average = Gossip.localData.localDataValue;
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
