import java.net.InetAddress;

/**
 * Dedicated test for NS and MX record resolution with StubResolver
 * This tests the specific record types that were added to meet coursework requirements
 */
public class TestStubResolverNSMX {

    public static void main(String[] args) {
        System.out.println("=== StubResolver NS and MX Record Tests ===\n");

        try {
            StubResolver r = new StubResolver();
            
            // Set the Cloudflare public DNS name server
            byte[] cloudflarePublic = new byte[]{1,1,1,1};
            r.setNameServer(InetAddress.getByAddress(cloudflarePublic), 53);

            System.out.println("Testing with Cloudflare DNS (1.1.1.1)");
            System.out.println("=====================================\n");

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

    private static void testNSRecords(StubResolver r) {
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
                String nsResult = r.recursiveResolveName(domain, 2); // Type 2 = NS
                
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

    private static void testMXRecords(StubResolver r) {
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
                String mxResult = r.recursiveResolveName(domain, 15); // Type 15 = MX
                
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