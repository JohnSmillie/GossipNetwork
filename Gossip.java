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
import java.time.Duration;
import java.util.*;

class GossipData implements Serializable{ // Must be serializable to send 1 bit after another over the network.
  int nodeID;
  int nodeLocalDataValue;
  int average;
  int highValue;
  int lowValue;
  int groupSize;
  int cycles;
  String userString;
  UUID cycleID;
  boolean isFromConsole;
  InetAddress IPAddress;
  int portSentFrom;
  // Whatever else you might want to send.
}
/********************************************************************************/
// Gossip class called from the CLI with one argument
//  birth of the individual gossip node.
public class Gossip{
  public static int serverPort;
  public static int nodeID;
  
  
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
          if (gd.userString.indexOf("stopserver") > -1){
            System.out.println("SERVER: Stopping UDP listener now.\n");
            break;
          }
          
          // look to see the message is from 
          // if it's the our port number, than spawn a client worker
          // if it's not our port number, spawn a server worker
          if (gd.isFromConsole){
            // respond to client (console) commands
            new ConsoleWorker(gd).start();
          }
          else{
            // respond to outside correspondence
            new GossipWorker(gd).start();
          }
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
/**************************************************************************************/
class ConsoleInputLooper implements Runnable {
  Scanner input = new Scanner(System.in);
  
  public void run(){
    //BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    try{
      String clientCommand;
      System.out.println("Enter t too see a list of functions.");
      while(true){
        clientCommand = input.nextLine();
        if(clientCommand.equals("quit")){
          System.out.println("Goodbye");
          System.exit(0);
        }
        try{
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
          dg.close();
        }
        catch(UnknownHostException he ){
          he.printStackTrace();
        }
      }
    }catch(IOException io){
      io.printStackTrace();
    }
  }
}

/**********************************************************************************/
class ConsoleWorker extends Thread{
  GossipData gd;
  public ConsoleWorker(GossipData gd){ this.gd = gd;}

  public void run(){
    switch(gd.userString){
      case "t": 
        System.out.println("Press \"u\" to ping your upper neighbor");
        break;
      case "u": // this can become ping
        pingUpperNode();
        break;
      case "d":
        pingLowerNode();
        break;

    }
  }
  public void pingUpperNode(){
    try{
        DatagramSocket dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        GossipData gd = new GossipData();
        gd.userString = "Hello outside world!!";
        gd.isFromConsole = false;
        gd.nodeID = Gossip.nodeID;
        gd.portSentFrom = Gossip.serverPort;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, Gossip.serverPort+1);
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
  public void pingLowerNode(){
    try{
        DatagramSocket dg = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        GossipData gd = new GossipData();
        gd.userString = "Hello outside world!!";
        gd.isFromConsole = false;
        gd.nodeID = Gossip.nodeID;
        gd.portSentFrom = Gossip.serverPort;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(gd);
        byte[] data = outputStream.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, IPAddress, Gossip.serverPort-1);
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
class GossipWorker extends Thread{
  GossipData gd;
  public GossipWorker (GossipData gd){
    this.gd = gd; 
  }

  public void run(){
    System.out.println("Communication from the outside world!");
    System.out.println("Sender Node ID: " + gd.nodeID + " port number: " + gd.portSentFrom);
    System.out.println(gd.userString);
    System.out.println("Worker is continuing to work...");
    
    try{
      sleep(60000);
      System.out.println("Gossip Worker is awake.");
    }
    catch(Exception e){
      e.printStackTrace();
    }
    // respond back
  }
}
/*********************************************************************************/
/* Discussion Posts:  */