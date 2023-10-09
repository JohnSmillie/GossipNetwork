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
  int nodeLocalDataValue;
  int average;
  int highValue;
  int lowValue;
  int groupSize;
  int cycles;
  String userString;
  UUID cycleID;

  // Whatever else you might want to send.
}
/**********************************************************************************************/
class LocalData {
  int nodeID;
  int nodeLocalDataValue;
  int average;
  int highValue;
  int lowValue;
  int groupSize;
  int cycles;



}
/**********************************************************************************************/
// Gossip class called from the CLI with one argument
// birth of the individual gossip node.
public class Gossip{
  public static int serverPort;
  public static int nodeID;
  public int nodeLocalDataValue;
  public int average;
  public int highValue;
  public int lowValue;
  public int groupSize;
  
  public static void main(String[] args){
    if(args.length > 0){
      serverPort = 48100 + Integer.parseInt(args[0]);
      nodeID = Integer.parseInt(args[0]);
    }
    System.out.printf("Node %d listening at port %d\n", nodeID, serverPort);

    // listening for input from the user console... aka client code
    ConsoleInputLooper consoleLoop = new ConsoleInputLooper();
    Thread t = new Thread(consoleLoop);
    t.start();
    // loop to listen for external connections
    try{
      // create a UDP portal
      DatagramSocket UDPSocket = new DatagramSocket(serverPort);
      //System.out.println("SERVER: Receive Buffer size: " + UDPSocket.getReceiveBufferSize() + "\n");  
      byte[] data = new byte[2048];
      InetAddress IPAddress = InetAddress.getByName("localhost");
      while(true){
        // define the type of input received
        DatagramPacket dp = new DatagramPacket(data, data.length);
        UDPSocket.receive(dp);
        byte[] receivedData = dp.getData();
        ByteArrayInputStream in = new ByteArrayInputStream(receivedData);
        ObjectInputStream ois = new ObjectInputStream(in);
        try{
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
}
/**********************************************************************************/
class GossipWorker extends Thread{
  GossipData gd;
  DatagramPacket dp;
  public GossipWorker (GossipData gd, DatagramPacket dp){
    this.gd = gd; 
    this.dp = dp;
  }

  public void run(){
    System.out.println("Communication from the outside world! It's from node: " + gd.nodeID);
    System.out.println("Command received: " + gd.userString);
    switch(gd.userString){
      // pong response 
      case "p":
        try{
          DatagramSocket dg = new DatagramSocket();
          InetAddress IPAddress = InetAddress.getByName("localhost");
          GossipData gd = new GossipData();
          gd.userString = "Hello from node " + Gossip.nodeID;
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(outputStream);
          oos.writeObject(gd);
          byte[] data = outputStream.toByteArray();
          DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, this.dp.getPort());
          dg.send(dp);
          dg.close(); 
        }
        catch(UnknownHostException he ){
          he.printStackTrace();
        }
        catch(IOException io){
          io.printStackTrace();
        }
        break;
      case "m":

    }
  }
}
/**************************************************************************************/
class ConsoleInputLooper implements Runnable {
  Scanner input = new Scanner(System.in);
  
  public void run(){
      String clientCommand;
      System.out.println("Enter t too see a list of functions.");
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
// The Gossip Data object will have the user command and will be passed along to subsequent processes
class ConsoleWorker extends Thread{
  GossipData gd;
  public ConsoleWorker(GossipData gd){ this.gd = gd;}

  public void run(){
    System.out.println("Console Worker entered");
    switch(gd.userString){
      case "t": 
        System.out.println("Enter \"p\" to ping upper node and lower node");
        break;
      case "p": // this can become ping
        new Pinger().ping(gd, Gossip.serverPort+1);
        new Pinger().ping(gd, Gossip.serverPort-1);
        break;
        
       default:
        System.out.println("Input not recognized"); 

    }
     System.out.print(">");
  }
}

/***********************************************************************************************/
class Pinger extends Thread {
  public boolean pingSuccess = false;

  public boolean ping(GossipData gd, int port){
    if(port < 48100 || port > 48109){
      return false;
    }
    try{
        DatagramSocket dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        // set the GossipData obj with the necassary info for a ping call. 
        gd.nodeID = Gossip.nodeID;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, port);
        dg.send(dp);
       
        // neighbor node has logic to pong
        // c
        pingSuccess = listen(dg, gd);
      }
      catch(UnknownHostException he ){
        he.printStackTrace();
      }
      catch(IOException io){
        io.printStackTrace();
      }
      System.out.printf("Ping to node %d successful: %b\n", port-48100, pingSuccess);
      return pingSuccess;
    }

    // listen for response from the neighbor on the same socket as the outgoing ping
    public boolean listen(DatagramSocket dg, GossipData gd){
      try{
        byte[] data = new byte[2048];
        DatagramPacket dp = new DatagramPacket(data, data.length);
        dg.setSoTimeout(1000);
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
