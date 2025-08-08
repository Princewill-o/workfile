import java.net.InetAddress;

/**
 * VM-Specific Test for StubResolver
 * This version includes additional debugging and error handling for VM environments
 */
public class TestStubResolverVM {

    public static void main(String[] args) {
        System.out.println("=== VM StubResolver Test ===");
        
        try {
            // Use the VM-optimized version
            StubResolverVM r = new StubResolverVM();

            // Set the Cloudflare public DNS name server
            byte[] cloudflarePublic = new byte[]{1,1,1,1};
            r.setNameServer(InetAddress.getByAddress(cloudflarePublic), 53);

            System.out.println("VM: Testing A record resolution...");
            // Try to look up some records
            InetAddress i = r.recursiveResolveAddress("moodle4-vip.city.ac.uk.");
            if (i == null) {
                System.out.println("VM: moodle4-vip.city.ac.uk. does have an A record. That should work?");
                return;
            } else {
                System.out.println("VM: moodle4-vip.city.ac.uk.\tA\t" + i.toString() );
            }

            System.out.println("VM: Testing TXT record resolution...");
            String txt = r.recursiveResolveText("city.ac.uk.");
            if (txt == null) {
                System.out.println("VM: city.ac.uk. does have TXT records. That should work?");
                return;
            } else {
                System.out.println("VM: city.ac.uk.\tTXT\t" + txt );
            }

            System.out.println("VM: Testing CNAME record resolution...");
            String cn = r.recursiveResolveName("moodle4.city.ac.uk.", 5);
            if (cn == null) {
                System.out.println("VM: moodle4.city.ac.uk. should be a CNAME. That should work?");
                return;
            } else {
                System.out.println("VM: moodle4.city.ac.uk.\tCNAME\t" + cn);
            }

        } catch (Exception e) {
            System.out.println("VM: Exception caught");
            e.printStackTrace();
            return;
        }

        System.out.println("VM: Starting tests complete!");
        System.out.println("VM: You are on your way!");
        System.out.println("VM: You will need to write your own tests to make sure that everything works.");
        return;
    }
} 