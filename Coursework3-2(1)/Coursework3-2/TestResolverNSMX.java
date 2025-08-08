import java.net.InetAddress;

/**
 * Dedicated test for NS and MX record resolution with Resolver
 * This tests the specific record types that were added to meet coursework requirements
 */
public class TestResolverNSMX {

    public static void main(String[] args) {
        System.out.println("=== Resolver NS and MX Record Tests ===\n");

        try {
            Resolver r = new Resolver();
            
            // Set the root server
            byte[] rootServer = new byte[]{-58,41,0,4}; // a.root-servers.net
            r.setNameServer(InetAddress.getByAddress(rootServer), 53);

            System.out.println("Testing with root server (a.root-servers.net)");
            System.out.println("============================================\n");

            // Test NS Records
            System.out.println("--- NS Record Tests ---");
            testNSRecords(r);

            System.out.println("\n--- MX Record Tests ---");
            testMXRecords(r);

            System.out.println("\n=== All NS and MX Tests Complete ===");

        } catch (Exception e) {
            System.out.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testNSRecords(Resolver r) {
        String[] domains = {
            "example.com",
            "google.com", 
            "microsoft.com",
            "city.ac.uk",
            "amazon.com"
        };

        for (String domain : domains) {
            try {
                System.out.println("Testing NS record for: " + domain);
                String nsResult = r.iterativeResolveName(domain, 2); // Type 2 = NS
                
                if (nsResult != null) {
                    System.out.println("✓ NS record found: " + nsResult);
                } else {
                    System.out.println("✗ No NS record found");
                }
                System.out.println();
                
            } catch (Exception e) {
                System.out.println("✗ Error resolving NS for " + domain + ": " + e.getMessage());
                System.out.println();
            }
        }
    }

    private static void testMXRecords(Resolver r) {
        String[] domains = {
            "example.com",
            "google.com",
            "microsoft.com", 
            "city.ac.uk",
            "amazon.com"
        };

        for (String domain : domains) {
            try {
                System.out.println("Testing MX record for: " + domain);
                String mxResult = r.iterativeResolveName(domain, 15); // Type 15 = MX
                
                if (mxResult != null && !mxResult.trim().isEmpty()) {
                    System.out.println("✓ MX record found: " + mxResult);
                } else {
                    System.out.println("✗ No MX record found (or empty)");
                }
                System.out.println();
                
            } catch (Exception e) {
                System.out.println("✗ Error resolving MX for " + domain + ": " + e.getMessage());
                System.out.println();
            }
        }
    }
} 