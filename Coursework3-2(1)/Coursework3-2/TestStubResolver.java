// IN2011 Computer Networks
// Coursework 2024/2025 Resit
//
// This is an example of how the StubResolver can be used.
// It should work with your submission without any changes (as long as
// the name server is accessible from your network).
// This should help you start testing.
// You will need to do more testing than just this.

//
// NOTE: On Azure Lab machines, outbound DNS over UDP is blocked
//       except to the internal Azure DNS server: 168.63.129.16
//
//       If you're using Azure for testing, you MUST use this address.
//       Cloudflare (1.1.1.1) and other public servers will not work.
//
//       This file sets that IP for demonstration purposes.
//       You may use different IPs for local/home testing.
//       Do NOT hardcode IPs in your final submission.
//
//       Be sure to explain your testing setup in your README.txt.
//

import java.net.InetAddress;
import java.util.List;

public class TestStubResolver {

    public static void main(String[] args) {
        try {
            StubResolver r = new StubResolver();

            // Set the DNS name server
            // For Azure Lab machines: use byte[] azureInternalDNS = new byte[]{(byte)168, 63, (byte)129, 16};
            // For local testing: use Cloudflare public DNS
            byte[] azureInternalDNS = new byte[]{(byte)168, 63, (byte)129, 16};
            r.setNameServer(InetAddress.getByAddress(azureInternalDNS), 53);

            // Try to look up some records
            InetAddress i = r.recursiveResolveAddress("moodle4-vip.city.ac.uk.");
            if (i == null) {
                System.out.println("moodle4-vip.city.ac.uk. does have an A record. That should work?");
                return;
            } else {
                System.out.println("moodle4-vip.city.ac.uk.\tA\t" + i.toString());
            }

            String txt = r.recursiveResolveText("city.ac.uk.");
            if (txt == null) {
                System.out.println("city.ac.uk. does have TXT records. That should work?");
                return;
            } else {
                System.out.println("city.ac.uk.\tTXT\t" + txt);
            }

            String cn = r.recursiveResolveName("moodle4.city.ac.uk.", 5);
            if (cn == null) {
                System.out.println("moodle4.city.ac.uk. should be a CNAME. That should work?");
                return;
            } else {
                System.out.println("moodle4.city.ac.uk.\tCNAME\t" + cn);
            }

            // ✅ TEMPORARY TESTS FOR NS & MX — REMOVE BEFORE SUBMISSION
            System.out.println("\n=== EXTRA TESTS: NS and MX ===");

            System.out.println("\nNS records for city.ac.uk:");
            for (StubResolver.ResourceRecord rr : r.getAnswers("city.ac.uk", StubResolver.RecordType.NS)) {
                System.out.println(rr);
            }

            System.out.println("\nMX records for city.ac.uk:");
            for (StubResolver.ResourceRecord rr : r.getAnswers("city.ac.uk", StubResolver.RecordType.MX)) {
                System.out.println(rr);
            }
            // ✅ END EXTRA TESTS

        } catch (Exception e) {
            System.out.println("Exception caught");
            e.printStackTrace();
            return;
        }

        System.out.println("Starting tests complete!");
        System.out.println("You are on your way!");
        System.out.println("You will need to write your own tests to make sure that everything works.");
        return;
    }
}
