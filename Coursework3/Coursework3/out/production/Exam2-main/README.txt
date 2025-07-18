IN2011 Computer Networks – Coursework 2024/2025 Resit

Submission by:
Name: Princewill Okube
Student ID: 
Email: Princewill.okube@city.ac.uk

---

Submitted Files:
- StubResolver.java
- Resolver.java
- NameServer.java
- TestStubResolver.java
- stubresolver.pcap
- nameserver.pcap
- README.txt

---

How to Compile:

All Java files are in the same directory and can be compiled using:

 javac *.java

---

How to Run:

To run the test program for StubResolver:
 java TestStubResolver

To run the NameServer:
 java NameServer 5300

To test the NameServer with dig:
 dig @127.0.0.1 -p 5300 example.com A

---

What Has Been Implemented:

 StubResolver.java
- Performs recursive resolution using a specified name server
- Supports A, TXT, and CNAME records
- Constructed and parsed DNS packets manually (UDP port 53)
- Tested with Cloudflare DNS (1.1.1.1)

 Resolver.java
- Performs full iterative resolution (root → TLD → authoritative)
- Supports record types: A, NS, CNAME, MX, TXT
- Handles CNAME chains, glue records, and resolution errors
- Avoids loops and handles multiple referral paths

 NameServer.java
- Accepts DNS queries from clients over UDP
- Uses Resolver.java to resolve unknown queries
- Implements caching of positive and negative responses
- TTLs are respected and expired entries are removed
- Can handle multiple clients and repeated requests
- Gracefully handles malformed or unsupported queries

---

Testing:

📁 Packet Captures Submitted:
- stubresolver.pcap – shows StubResolver querying 1.1.1.1 and receiving correct responses
- nameserver.pcap – shows client queries to NameServer and valid DNS responses being sent

 Additional testing was done with:
- dig
- tcpdump
- TestStubResolver.java
- Wireshark (on local machine)

---

Notes:
- All code was implemented without using Java’s built-in DNS resolution (`InetAddress`, etc.).
- All DNS messages were manually built and parsed as per protocol.
- No libraries or external dependencies used.
- NameServer listens on high port (5300) due to port 53 restriction.
- The test file was not edited and works directly with my implementation.

---

Thank you!
