import java.net.*;
import java.io.*;

/**
 * VM Diagnostic Test for DNS Resolver
 * This helps identify VM-specific network and DNS issues
 */
public class VMTest {
    
    public static void main(String[] args) {
        System.out.println("=== VM DNS Diagnostic Test ===");
        
        // Test 1: Basic network connectivity
        System.out.println("\n1. Testing basic network connectivity...");
        try {
            InetAddress testAddr = InetAddress.getByName("1.1.1.1");
            System.out.println("✓ Can resolve 1.1.1.1: " + testAddr.getHostAddress());
        } catch (Exception e) {
            System.out.println("✗ Network connectivity issue: " + e.getMessage());
        }
        
        // Test 2: UDP socket creation
        System.out.println("\n2. Testing UDP socket creation...");
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(5000);
            System.out.println("✓ UDP socket created successfully");
            socket.close();
        } catch (Exception e) {
            System.out.println("✗ UDP socket issue: " + e.getMessage());
        }
        
        // Test 3: DNS port accessibility
        System.out.println("\n3. Testing DNS port accessibility...");
        try {
            Socket dnsTest = new Socket("1.1.1.1", 53);
            System.out.println("✓ Can connect to DNS port 53");
            dnsTest.close();
        } catch (Exception e) {
            System.out.println("✗ DNS port issue: " + e.getMessage());
        }
        
        // Test 4: Simple DNS query test
        System.out.println("\n4. Testing simple DNS query...");
        try {
            StubResolver r = new StubResolver();
            r.setNameServer(InetAddress.getByName("1.1.1.1"), 53);
            
            // Try a simple A record query
            InetAddress result = r.recursiveResolveAddress("example.com");
            if (result != null) {
                System.out.println("✓ DNS query successful: example.com -> " + result.getHostAddress());
            } else {
                System.out.println("✗ DNS query returned null");
            }
        } catch (Exception e) {
            System.out.println("✗ DNS query failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Test 5: VM-specific network info
        System.out.println("\n5. VM Network Information...");
        try {
            System.out.println("Local hostname: " + InetAddress.getLocalHost().getHostName());
            System.out.println("Local IP: " + InetAddress.getLocalHost().getHostAddress());
            
            // Check available network interfaces
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    System.out.println("Active interface: " + ni.getDisplayName());
                }
            }
        } catch (Exception e) {
            System.out.println("✗ Network info error: " + e.getMessage());
        }
        
        System.out.println("\n=== Diagnostic Complete ===");
    }
} 