// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  YOUR_NAME_GOES_HERE
//  YOUR_STUDENT_ID_NUMBER_GOES_HERE
//  YOUR_EMAIL_GOES_HERE


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

interface NodeInterface {


    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */

    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;


    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;

    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.

    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;


    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;

    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {
    private String nodeName;
    private DatagramSocket socket;  // Declared socket field
    private int portNumber; // Declared portNumber field
    private Stack<String> relayStack = new Stack<>();
    private Map<String, String> dataStore = new HashMap<>();

    @Override
    public void setNodeName(String nodeName) throws Exception {
        if (nodeName == null || nodeName.isEmpty()) {
            throw new Exception("Not implemented");
        }
        this.nodeName = nodeName;
    }
    @Override
    public void openPort(int portNumber) throws Exception {
        if (portNumber < 1024 || portNumber > 65535) {
            throw new Exception("Not implemented");
        }
        try {
            this.socket = new DatagramSocket(portNumber);
            this.portNumber = portNumber;
        } catch (SocketException e) {
            throw new Exception("Failed to open port: " + e.getMessage());
        }
    }

    private String handleNearestRequest(String transactionId, String hashId) {
        String nearestAddresses = findNearestAddresses(hashId); // Get nearest addresses
        return transactionId + " O " + nearestAddresses; // Return the response string
    }

    private String findNearestAddresses(String hashId) {
        // Logic to find the nearest addresses goes here
        // For now, we'll return some example addresses
        // In a real CRN, this would involve complex logic
        // to find the closest nodes based on hash distance.

        // Example addresses (replace with your actual logic)
        String exampleAddresses = "N:node1 127.0.0.1:20110 N:node2 127.0.0.1:20111";
        return exampleAddresses;
    }


    @Override
    public void handleIncomingMessages(int delay) throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received message: " + message);

            if (message.startsWith("POEM")) { // Assuming poem messages start with "POEM"
                System.out.println("Getting the poem ...");
                String poem = message.substring(5); // Extract the poem
                System.out.println(poem); // Print the poem
                // add additional logic here to handle the rest of the expected output.
            }
            else if (message.startsWith("NODE")){
                System.out.println("Handling incoming connections");
                System.out.println(message);
            }
            else {
                // ... (Your other message handling logic)
            }

            if (delay > 0) {
                Thread.sleep(delay);
            }
        }
    }
    public boolean isActive(String nodeName) throws Exception {
        return nodeName.equals(this.nodeName);
    }
    @Override
    public void pushRelay(String nodeName) throws Exception {
        relayStack.push(nodeName);
    }
    @Override
    public void popRelay() throws Exception {
        if (relayStack.isEmpty()) {
            relayStack.pop();
        }
    }

    @Override
    public boolean exists(String key) throws Exception {
        return dataStore.containsKey(key);
    }

    @Override
    public String read(String key) throws Exception {
        return dataStore.get(key);
    }

    @Override
    public boolean write(String key, String value) throws Exception {
        dataStore.put(key, value);
        System.out.println("Written: " + key + " -> " + value);
        return true;

    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        if (dataStore.containsKey(key) && dataStore.get(key).equals(currentValue)) {
            dataStore.put(key, newValue);
            return true;
        }
        return false;
    }
}