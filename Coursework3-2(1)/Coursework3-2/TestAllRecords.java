import java.net.InetAddress;

/**
 * Comprehensive test for all DNS record types
 * Tests A, TXT, CNAME, NS, and MX records in both StubResolver and Resolver
 */
public class TestAllRecords {

    public static void main(String[] args) {
        System.out.println("=== Testing All DNS Record Types ===\n");

        // Test StubResolver
        System.out.println("--- StubResolver Tests ---");
        testStubResolver();

        System.out.println("\n--- Resolver Tests ---");
        testResolver();

        System.out.println("\n=== All Tests Complete ===");
    }

    private static void testStubResolver() {
        try {
            StubResolver r = new StubResolver();
            byte[] cloudflarePublic = new byte[]{1,1,1,1};
            r.setNameServer(InetAddress.getByAddress(cloudflarePublic), 53);

            // Test A record
            System.out.println("Testing A record...");
            InetAddress aResult = r.recursiveResolveAddress("example.com");
            System.out.println("A record result: " + (aResult != null ? aResult.getHostAddress() : "null"));

            // Test TXT record
            System.out.println("Testing TXT record...");
            String txtResult = r.recursiveResolveText("example.com");
            System.out.println("TXT record result: " + (txtResult != null ? txtResult.substring(0, Math.min(50, txtResult.length())) + "..." : "null"));

            // Test CNAME record
            System.out.println("Testing CNAME record...");
            String cnameResult = r.recursiveResolveName("www.example.com", 5);
            System.out.println("CNAME record result: " + (cnameResult != null ? cnameResult : "null"));

            // Test NS record
            System.out.println("Testing NS record...");
            String nsResult = r.recursiveResolveName("example.com", 2);
            System.out.println("NS record result: " + (nsResult != null ? nsResult : "null"));

            // Test MX record
            System.out.println("Testing MX record...");
            String mxResult = r.recursiveResolveName("example.com", 15);
            System.out.println("MX record result: " + (mxResult != null ? mxResult : "null"));

        } catch (Exception e) {
            System.out.println("StubResolver test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testResolver() {
        try {
            Resolver r = new Resolver();
            byte[] rootServer = new byte[]{-58,41,0,4}; // a.root-servers.net
            r.setNameServer(InetAddress.getByAddress(rootServer), 53);

            // Test A record
            System.out.println("Testing A record...");
            InetAddress aResult = r.iterativeResolveAddress("example.com");
            System.out.println("A record result: " + (aResult != null ? aResult.getHostAddress() : "null"));

            // Test TXT record
            System.out.println("Testing TXT record...");
            String txtResult = r.iterativeResolveText("example.com");
            System.out.println("TXT record result: " + (txtResult != null ? txtResult.substring(0, Math.min(50, txtResult.length())) + "..." : "null"));

            // Test CNAME record
            System.out.println("Testing CNAME record...");
            String cnameResult = r.iterativeResolveName("www.example.com", 5);
            System.out.println("CNAME record result: " + (cnameResult != null ? cnameResult : "null"));

            // Test NS record
            System.out.println("Testing NS record...");
            String nsResult = r.iterativeResolveName("example.com", 2);
            System.out.println("NS record result: " + (nsResult != null ? nsResult : "null"));

            // Test MX record
            System.out.println("Testing MX record...");
            String mxResult = r.iterativeResolveName("example.com", 15);
            System.out.println("MX record result: " + (mxResult != null ? mxResult : "null"));

        } catch (Exception e) {
            System.out.println("Resolver test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 