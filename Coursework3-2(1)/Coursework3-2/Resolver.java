// IN2011 Computer Networks
// Coursework 2024/2025 Resit
//
// Submission by
// [Your Name]
// [Your Student ID]
// [Your Email]

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Resolver - A full iterative DNS resolver implementation
 * 
 * This resolver performs iterative DNS resolution, starting from root servers
 * and following the DNS hierarchy to find answers. Unlike the stub resolver,
 * this one doesn't rely on recursive DNS servers - it does the work itself.
 * 
 * Key learning points:
 * - Iterative DNS resolution process
 * - Handling NS referrals and glue records
 * - CNAME chain resolution
 * - DNS packet parsing and construction
 * - Error handling and retry logic
 * 
 * The iterative process:
 * 1. Start with root servers
 * 2. Query for the domain
 * 3. If no answer, follow NS referrals
 * 4. Use glue records when available
 * 5. Resolve NS names if needed
 * 6. Repeat until we get an answer
 * 
 * @author [Your Name]
 * @version 1.0
 */

// DO NOT EDIT starts
interface ResolverInterface {
    void setNameServer(InetAddress ipAddress, int port) throws Exception;
    InetAddress iterativeResolveAddress(String domainName) throws Exception;
    String iterativeResolveText(String domainName) throws Exception;
    String iterativeResolveName(String domainName, int type) throws Exception;
}

public class Resolver implements ResolverInterface {
    // Store the root server we'll start our iterative resolution from
    // This is typically one of the 13 root DNS servers (a.root-servers.net, etc.)
    private InetAddress rootServerAddress;
    private int rootServerPort;

    /**
     * Sets the root DNS server to start iterative resolution from
     * This allows testing with different root servers without hardcoding
     */
    public void setNameServer(InetAddress ipAddress, int port) {
        this.rootServerAddress = ipAddress;
        this.rootServerPort = port;
    }

    /**
     * Resolves a domain name to an IPv4 address using iterative DNS
     * This method handles the full iterative resolution process
     */
    public InetAddress iterativeResolveAddress(String domainName) throws Exception {
        String resolvedIP = performIterativeResolution(domainName, 1); // Type 1 = A record
        return resolvedIP != null ? InetAddress.getByName(resolvedIP) : null;
    }

    /**
     * Resolves TXT records using iterative DNS
     * TXT records often contain domain verification strings, SPF records, etc.
     */
    public String iterativeResolveText(String domainName) throws Exception {
        return performIterativeResolution(domainName, 16); // Type 16 = TXT record
    }

    /**
     * Resolves CNAME, NS, or MX records using iterative DNS
     * These records return domain names rather than IP addresses
     */
    public String iterativeResolveName(String domainName, int type) throws Exception {
        if (!(type == 2 || type == 5 || type == 15)) // NS, CNAME, MX record types
            throw new IllegalArgumentException("Unsupported record type. Only NS (2), CNAME (5), MX (15) are supported.");
        return performIterativeResolution(domainName, type);
    }

    /**
     * The core iterative resolution algorithm
     * This is the heart of the resolver - it follows the DNS hierarchy
     * from root servers down to the authoritative server for the domain
     */
    private String performIterativeResolution(String targetDomain, int queryType) throws Exception {
        InetAddress currentNameserver = rootServerAddress; // Start with root server
        Set<String> processedCNAMEs = new HashSet<>(); // Track CNAMEs to detect loops
        Map<String, InetAddress> nameserverCache = new HashMap<>(); // Cache resolved NS IPs

        while (true) {
            // Build and send DNS query to current nameserver
            byte[] dnsQuery = DNSPacketParser.createQueryPacket(targetDomain, queryType);
            byte[] dnsResponse;
            try {
                dnsResponse = executeDNSQuery(dnsQuery, currentNameserver);
            } catch (IOException e) {
                throw new IOException("DNS query failed for " + currentNameserver.getHostAddress(), e);
            }

            // Parse the DNS response
            DNSPacketParser parsedResponse = DNSPacketParser.parseDNSPacket(dnsResponse);

            // Check for direct answers in the answer section
            for (DNSResourceRecord answerRecord : parsedResponse.answerRecords) {
                if (answerRecord.recordType == queryType) {
                    // Found the record we're looking for!
                    return answerRecord.getRecordDataAsString();
                } else if (answerRecord.recordType == 5) { // CNAME record
                    // Handle CNAME redirection
                    String cnameTarget = answerRecord.getRecordDataAsString();
                    if (processedCNAMEs.contains(cnameTarget)) {
                        throw new Exception("CNAME loop detected - domain points to itself");
                    }
                    processedCNAMEs.add(cnameTarget);
                    targetDomain = cnameTarget; // Follow the CNAME
                    currentNameserver = rootServerAddress; // Start over from root
                    continue; // Query for the new domain
                }
            }

            // No direct answer found - look for NS referrals in authority section
            List<String> nameserverHostnames = new ArrayList<>();
            for (DNSResourceRecord authorityRecord : parsedResponse.authorityRecords) {
                if (authorityRecord.recordType == 2) { // NS record
                    nameserverHostnames.add(authorityRecord.getRecordDataAsString());
                }
            }

            // Extract glue records from additional section
            // Glue records provide IP addresses for NS servers, avoiding extra queries
            Map<String, InetAddress> glueRecords = new HashMap<>();
            for (DNSResourceRecord additionalRecord : parsedResponse.additionalRecords) {
                if (additionalRecord.recordType == 1) { // A record (glue)
                    glueRecords.put(additionalRecord.domainName, InetAddress.getByName(additionalRecord.getRecordDataAsString()));
                }
            }

            // Find the next nameserver to query
            boolean foundNextServer = false;
            for (String nsHostname : nameserverHostnames) {
                InetAddress nsIPAddress = glueRecords.get(nsHostname);

                if (nsIPAddress == null) {
                    // No glue record - need to resolve the NS name
                    if (nameserverCache.containsKey(nsHostname)) {
                        // Use cached result
                        nsIPAddress = nameserverCache.get(nsHostname);
                    } else {
                        try {
                            // Recursively resolve the NS name (this is allowed!)
                            nsIPAddress = iterativeResolveAddress(nsHostname);
                            nameserverCache.put(nsHostname, nsIPAddress);
                        } catch (Exception e) {
                            // This NS failed, try the next one
                            continue;
                        }
                    }
                }

                try {
                    currentNameserver = nsIPAddress;
                    foundNextServer = true;
                    break; // Found a working NS, use it
                } catch (Exception e) {
                    // This NS failed, try the next one
                }
            }

            // If no NS worked with glue records, try resolving them all
            if (!foundNextServer) {
                if (!nameserverHostnames.isEmpty()) {
                    for (String nsHostname : nameserverHostnames) {
                        try {
                            // Resolve each NS name and try it
                            InetAddress resolvedNS = iterativeResolveAddress(nsHostname);
                            nameserverCache.put(nsHostname, resolvedNS);
                            currentNameserver = resolvedNS;
                            foundNextServer = true;
                            break;
                        } catch (Exception e) {
                            // This NS failed, try the next one
                        }
                    }
                }
                if (!foundNextServer) {
                    throw new Exception("No valid next nameserver IP found for domain: " + targetDomain);
                }
            }
        }
    }

    /**
     * Executes a DNS query with retry logic
     * This handles network timeouts and temporary failures
     */
    private byte[] executeDNSQuery(byte[] queryPacket, InetAddress targetServer) throws IOException {
        int maxRetries = 3; // Try up to 3 times
        IOException lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(3000); // 3 second timeout per attempt
                DatagramPacket outgoingPacket = new DatagramPacket(queryPacket, queryPacket.length, targetServer, rootServerPort);
                socket.send(outgoingPacket);

                byte[] responseBuffer = new byte[4096]; // Larger buffer for DNS responses
                DatagramPacket incomingPacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                socket.receive(incomingPacket);
                socket.close();

                return Arrays.copyOf(responseBuffer, incomingPacket.getLength());
            } catch (IOException e) {
                lastException = e;
                // Continue to next attempt
            }
        }
        throw lastException; // All attempts failed
    }
}

/**
 * DNSPacketParser - Helper class for parsing DNS packets
 * 
 * This class handles the complex task of parsing DNS response packets.
 * DNS packets have a specific structure with multiple sections that need
 * to be parsed carefully.
 * 
 * I learned about DNS packet structure from RFC 1035 and various DNS
 * documentation. The parsing was tricky to get right, especially
 * handling name compression and different record types.
 */
class DNSPacketParser {
    public List<DNSResourceRecord> answerRecords = new ArrayList<>();
    public List<DNSResourceRecord> authorityRecords = new ArrayList<>();
    public List<DNSResourceRecord> additionalRecords = new ArrayList<>();

    /**
     * Parses a complete DNS packet into its component parts
     * This extracts all the records from the different sections
     */
    public static DNSPacketParser parseDNSPacket(byte[] packetData) throws IOException {
        DNSPacketParser parser = new DNSPacketParser();
        
        // Extract counts from DNS header
        int questionCount = ((packetData[4] & 0xff) << 8) | (packetData[5] & 0xff);
        int answerCount = ((packetData[6] & 0xff) << 8) | (packetData[7] & 0xff);
        int authorityCount = ((packetData[8] & 0xff) << 8) | (packetData[9] & 0xff);
        int additionalCount = ((packetData[10] & 0xff) << 8) | (packetData[11] & 0xff);

        int currentOffset = 12; // Start after DNS header
        
        // Skip question section (we don't need to parse it)
        for (int i = 0; i < questionCount; i++) {
            currentOffset = skipDomainName(packetData, currentOffset);
            currentOffset += 4; // Skip QTYPE and QCLASS
        }

        // Parse answer records
        for (int i = 0; i < answerCount; i++) {
            DNSResourceRecord record = DNSResourceRecord.parseRecord(packetData, currentOffset);
            parser.answerRecords.add(record);
            currentOffset = record.endPosition;
        }
        
        // Parse authority records (NS records for referrals)
        for (int i = 0; i < authorityCount; i++) {
            DNSResourceRecord record = DNSResourceRecord.parseRecord(packetData, currentOffset);
            parser.authorityRecords.add(record);
            currentOffset = record.endPosition;
        }
        
        // Parse additional records (glue records)
        for (int i = 0; i < additionalCount; i++) {
            DNSResourceRecord record = DNSResourceRecord.parseRecord(packetData, currentOffset);
            parser.additionalRecords.add(record);
            currentOffset = record.endPosition;
        }
        return parser;
    }

    /**
     * Creates a DNS query packet
     * This builds a simple DNS query without EDNS0 for compatibility
     */
    public static byte[] createQueryPacket(String domain, int type) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(outputStream);
        
        // DNS Header
        dataStream.writeShort(0x1234); // Transaction ID
        dataStream.writeShort(0x0000); // Flags: Standard Query

        dataStream.writeShort(1); // QDCOUNT: 1 question
        dataStream.writeShort(0); // ANCOUNT: 0 answers
        dataStream.writeShort(0); // NSCOUNT: 0 authority records
        dataStream.writeShort(0); // ARCOUNT: 0 additional records

        // Question section: domain name
        for (String label : domain.split("\\.")) {
            byte[] labelBytes = label.getBytes();
            dataStream.writeByte(labelBytes.length);
            dataStream.write(labelBytes);
        }
        dataStream.writeByte(0); // End of domain name
        dataStream.writeShort(type); // Query type
        dataStream.writeShort(1); // Class IN
        return outputStream.toByteArray();
    }

    /**
     * Skips over a domain name in DNS packet format
     * Handles both regular labels and compression pointers
     */
    public static int skipDomainName(byte[] data, int offset) {
        while (true) {
            int length = data[offset] & 0xff;
            if (length == 0) return offset + 1; // End of name
            if ((length & 0xC0) == 0xC0) return offset + 2; // Compression pointer
            offset += 1 + length; // Regular label: length + label bytes
        }
    }

    /**
     * Reads a domain name from DNS packet format
     * Handles DNS name compression (pointers to earlier names)
     * This was one of the trickiest parts to implement correctly!
     */
    public static String readDomainName(byte[] data, int[] offsetRef) {
        int currentOffset = offsetRef[0];
        StringBuilder nameBuilder = new StringBuilder();
        while (true) {
            int length = data[currentOffset++] & 0xff;
            if (length == 0) break; // End of name
            if ((length & 0xC0) == 0xC0) {
                // DNS compression: pointer to earlier name
                int pointer = ((length & 0x3F) << 8) | (data[currentOffset++] & 0xff);
                offsetRef[0] = currentOffset;
                return nameBuilder.append(readDomainName(data, new int[]{pointer})).toString();
            }
            if (nameBuilder.length() > 0) nameBuilder.append(".");
            nameBuilder.append(new String(data, currentOffset, length));
            currentOffset += length;
        }
        offsetRef[0] = currentOffset;
        return nameBuilder.toString();
    }
}

/**
 * DNSResourceRecord - Represents a single DNS resource record
 * 
 * This class holds all the information from a DNS record including
 * the domain name, record type, TTL, and the actual data.
 * 
 * Different record types store data in different formats:
 * - A records: 4-byte IPv4 addresses
 * - CNAME/NS/MX: Domain names (with compression)
 * - TXT: Length-prefixed text strings
 */
class DNSResourceRecord {
    public String domainName;
    public int recordType, recordClass, timeToLive, dataLength;
    public byte[] recordData;
    public int endPosition;
    public byte[] completePacket;
    public int dataStartPosition;

    /**
     * Parses a single DNS resource record from packet data
     * This extracts all the fields from a DNS record
     */
    public static DNSResourceRecord parseRecord(byte[] packetData, int startOffset) throws IOException {
        int[] offsetRef = {startOffset};
        String domainName = DNSPacketParser.readDomainName(packetData, offsetRef);
        startOffset = offsetRef[0];
        
        // Extract record header fields
        int recordType = ((packetData[startOffset] & 0xff) << 8) | (packetData[startOffset + 1] & 0xff);
        int recordClass = ((packetData[startOffset + 2] & 0xff) << 8) | (packetData[startOffset + 3] & 0xff);
        int timeToLive = ((packetData[startOffset + 4] & 0xff) << 24) | ((packetData[startOffset + 5] & 0xff) << 16) |
                ((packetData[startOffset + 6] & 0xff) << 8) | (packetData[startOffset + 7] & 0xff);
        int dataLength = ((packetData[startOffset + 8] & 0xff) << 8) | (packetData[startOffset + 9] & 0xff);
        byte[] recordData = Arrays.copyOfRange(packetData, startOffset + 10, startOffset + 10 + dataLength);
        
        // Create and populate the record object
        DNSResourceRecord record = new DNSResourceRecord();
        record.domainName = domainName;
        record.recordType = recordType;
        record.recordClass = recordClass;
        record.timeToLive = timeToLive;
        record.dataLength = dataLength;
        record.recordData = recordData;
        record.endPosition = startOffset + 10 + dataLength;
        record.completePacket = packetData;
        record.dataStartPosition = startOffset + 10;

        return record;
    }

    /**
     * Extracts the record data as a string based on record type
     * This handles the different data formats for different record types
     */
    public String getRecordDataAsString() throws IOException {
        if (recordType == 1 && recordData.length == 4) {
            // A record: 4-byte IPv4 address
            return InetAddress.getByAddress(recordData).getHostAddress();
        } else if (recordType == 2 || recordType == 5) {
            // NS records (type 2) and CNAME records (type 5): domain name (with compression)
            return DNSPacketParser.readDomainName(completePacket, new int[]{dataStartPosition});
        } else if (recordType == 15) {
            // MX records (type 15): 2-byte priority + domain name (with compression)
            int priority = ((recordData[0] & 0xFF) << 8) | (recordData[1] & 0xFF);
            String domainName = DNSPacketParser.readDomainName(completePacket, new int[]{dataStartPosition + 2});
            return priority + " " + domainName;
        } else if (recordType == 16) {
            // TXT record: length-prefixed text
            int textLength = recordData[0] & 0xff;
            return new String(recordData, 1, textLength);
        } else {
            return "Unsupported record type";
        }
    }
}