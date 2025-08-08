import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.nio.ByteBuffer;

/**
 * VM-Optimized StubResolver
 * 
 * This version includes additional error handling and fallback mechanisms
 * for common VM networking issues:
 * - Firewall restrictions
 * - NAT networking
 * - DNS server restrictions
 * - Network timeouts
 * 
 * @author [Your Name]
 * @version 1.0
 */
// Interface is already defined in StubResolver.java

public class StubResolverVM implements StubResolverInterface {
    private InetAddress dnsServer;
    private int serverPort;
    
    // VM-specific settings
    private static final int VM_TIMEOUT = 10000; // 10 seconds for VM
    private static final int VM_RETRIES = 5; // More retries for VM
    private static final int VM_BUFFER_SIZE = 8192; // Larger buffer for VM

    public void setNameServer(InetAddress ipAddress, int port) throws Exception {
        this.dnsServer = ipAddress;
        this.serverPort = port;
    }

    // DNS record type constants
    private static final int RECORD_TYPE_A = 1;
    private static final int RECORD_TYPE_NS = 2;
    private static final int RECORD_TYPE_CNAME = 5;
    private static final int RECORD_TYPE_MX = 15;
    private static final int RECORD_TYPE_TXT = 16;
    private static final int CLASS_INTERNET = 1;

    public InetAddress recursiveResolveAddress(String domainName) throws Exception {
        byte[] dnsResponse = performDNSQueryVM(domainName, RECORD_TYPE_A);
        return extractIPAddress(dnsResponse);
    }

    public String recursiveResolveText(String domainName) throws Exception {
        byte[] responseData = performDNSQueryVM(domainName, RECORD_TYPE_TXT);
        int answerCount = extractAnswerCount(responseData);

        if (answerCount == 0) return null;

        int currentPosition = findAnswerSectionStart(responseData);
        StringBuilder combinedTxtRecords = new StringBuilder();

        for (int recordIndex = 0; recordIndex < answerCount; recordIndex++) {
            currentPosition = advancePastName(responseData, currentPosition);
            int recordType = extractRecordType(responseData, currentPosition);
            currentPosition += 2; // Skip TYPE field
            currentPosition += 2; // Skip CLASS field  
            currentPosition += 4; // Skip TTL field
            int dataLength = extractDataLength(responseData, currentPosition);
            currentPosition += 2;

            if (recordType == RECORD_TYPE_TXT) {
                int dataEnd = currentPosition + dataLength;
                while (currentPosition < dataEnd) {
                    int textLength = responseData[currentPosition++] & 0xFF;
                    if (currentPosition + textLength > dataEnd) break;
                    combinedTxtRecords.append(new String(responseData, currentPosition, textLength, "UTF-8")).append(" ");
                    currentPosition += textLength;
                }
            } else {
                currentPosition += dataLength;
            }
        }

        return combinedTxtRecords.length() > 0 ? combinedTxtRecords.toString().trim() : null;
    }

    public String recursiveResolveName(String domainName, int type) throws Exception {
        if (!(type == RECORD_TYPE_NS || type == RECORD_TYPE_MX || type == RECORD_TYPE_CNAME))
            throw new IllegalArgumentException("Unsupported record type. Only NS, MX, CNAME are supported.");

        byte[] responseData = performDNSQueryVM(domainName, type);

        int answerCount = extractAnswerCount(responseData);
        if (answerCount == 0) return null;

        int currentPosition = findAnswerSectionStart(responseData);

        for (int recordIndex = 0; recordIndex < answerCount; recordIndex++) {
            currentPosition = advancePastName(responseData, currentPosition);
            int recordType = extractRecordType(responseData, currentPosition);
            currentPosition += 8; // Skip type + class + TTL
            int dataLength = extractDataLength(responseData, currentPosition);
            currentPosition += 2;
            if (recordType == type) {
                return extractDomainName(responseData, currentPosition);
            } else {
                currentPosition += dataLength;
            }
        }
        return null;
    }

    /**
     * VM-optimized DNS query with enhanced error handling
     * Includes multiple retries and fallback mechanisms
     */
    private byte[] performDNSQueryVM(String domainName, int queryType) throws Exception {
        Exception lastException = null;
        
        // Try multiple times with different strategies
        for (int attempt = 0; attempt < VM_RETRIES; attempt++) {
            try {
                DatagramSocket networkSocket = new DatagramSocket();
                networkSocket.setSoTimeout(VM_TIMEOUT);
                
                // VM-specific: Set socket options for better VM compatibility
                networkSocket.setReuseAddress(true);
                networkSocket.setBroadcast(false);

                byte[] queryPacket = constructQueryPacket(domainName, queryType);
                DatagramPacket outgoingPacket = new DatagramPacket(queryPacket, queryPacket.length, dnsServer, serverPort);
                
                System.out.println("VM: Attempt " + (attempt + 1) + " - Sending query to " + dnsServer.getHostAddress());
                networkSocket.send(outgoingPacket);

                byte[] responseBuffer = new byte[VM_BUFFER_SIZE];
                DatagramPacket incomingPacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                networkSocket.receive(incomingPacket);
                networkSocket.close();

                System.out.println("VM: Query successful, received " + incomingPacket.getLength() + " bytes");
                return Arrays.copyOf(incomingPacket.getData(), incomingPacket.getLength());
                
            } catch (SocketTimeoutException e) {
                lastException = e;
                System.out.println("VM: Timeout on attempt " + (attempt + 1) + " - " + e.getMessage());
            } catch (IOException e) {
                lastException = e;
                System.out.println("VM: Network error on attempt " + (attempt + 1) + " - " + e.getMessage());
            } catch (Exception e) {
                lastException = e;
                System.out.println("VM: Unexpected error on attempt " + (attempt + 1) + " - " + e.getMessage());
            }
            
            // Wait before retry (exponential backoff)
            if (attempt < VM_RETRIES - 1) {
                try {
                    Thread.sleep(1000 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        throw new Exception("VM: All " + VM_RETRIES + " attempts failed. Last error: " + lastException.getMessage(), lastException);
    }

    private byte[] constructQueryPacket(String domainName, int queryType) throws IOException {
        ByteArrayOutputStream packetBuilder = new ByteArrayOutputStream();
        DataOutputStream dataWriter = new DataOutputStream(packetBuilder);

        // DNS Header
        dataWriter.writeShort(0x1234); // Transaction ID
        dataWriter.writeShort(0x0100); // Flags: Standard Query, Recursion Desired
        dataWriter.writeShort(1);      // QDCOUNT: 1 question
        dataWriter.writeShort(0);      // ANCOUNT: 0 answers
        dataWriter.writeShort(0);      // NSCOUNT: 0 authority records
        dataWriter.writeShort(1);      // ARCOUNT: 1 additional record (EDNS0 OPT record)

        // Question Section: Domain name in DNS format
        String[] domainParts = domainName.split("\\.");
        for (String part : domainParts) {
            if (!part.isEmpty()) {
                byte[] partBytes = part.getBytes("UTF-8");
                dataWriter.writeByte(partBytes.length);
                dataWriter.write(partBytes);
            }
        }
        dataWriter.writeByte(0);           // End of domain name
        dataWriter.writeShort(queryType);  // Query type
        dataWriter.writeShort(CLASS_INTERNET); // Class IN

        // EDNS0 OPT pseudo-record for modern DNS compatibility
        dataWriter.writeByte(0);           // Root label
        dataWriter.writeShort(41);         // OPT record type
        dataWriter.writeShort(4096);       // UDP payload size
        dataWriter.writeByte(0);           // Extended RCODE
        dataWriter.writeByte(0);           // EDNS version
        dataWriter.writeShort(0x0000);     // Z flags
        dataWriter.writeShort(0);          // RDLENGTH

        return packetBuilder.toByteArray();
    }

    private InetAddress extractIPAddress(byte[] responseData) throws Exception {
        int answerCount = extractAnswerCount(responseData);
        if (answerCount == 0) return null;

        int currentPosition = findAnswerSectionStart(responseData);

        for (int recordIndex = 0; recordIndex < answerCount; recordIndex++) {
            currentPosition = advancePastName(responseData, currentPosition);
            int recordType = extractRecordType(responseData, currentPosition);
            currentPosition += 8; // Skip type + class + TTL
            int dataLength = extractDataLength(responseData, currentPosition);
            currentPosition += 2;
            if (recordType == RECORD_TYPE_A) {
                return InetAddress.getByAddress(Arrays.copyOfRange(responseData, currentPosition, currentPosition + dataLength));
            } else {
                currentPosition += dataLength;
            }
        }
        return null;
    }

    private int findAnswerSectionStart(byte[] responseData) throws IOException {
        int position = 12; // Skip DNS header
        while (responseData[position] != 0) position += (responseData[position] & 0xFF) + 1;
        position += 5; // Skip null terminator + QTYPE + QCLASS
        return position;
    }

    private int advancePastName(byte[] responseData, int position) {
        while (true) {
            int length = responseData[position] & 0xFF;
            if (length == 0) return position + 1;
            if ((length & 0xC0) == 0xC0) return position + 2; // Compression pointer
            position += length + 1;
        }
    }

    private String extractDomainName(byte[] responseData, int position) {
        StringBuilder domainBuilder = new StringBuilder();
        while (true) {
            int length = responseData[position] & 0xFF;
            if (length == 0) break;
            if ((length & 0xC0) == 0xC0) {
                int pointer = ((length & 0x3F) << 8) | (responseData[position + 1] & 0xFF);
                domainBuilder.append(extractDomainName(responseData, pointer));
                break;
            } else {
                position++;
                for (int i = 0; i < length; i++) {
                    domainBuilder.append((char) responseData[position++]);
                }
                domainBuilder.append(".");
            }
        }
        return domainBuilder.toString();
    }

    private int extractAnswerCount(byte[] responseData) {
        return ((responseData[6] & 0xFF) << 8) | (responseData[7] & 0xFF);
    }

    private int extractRecordType(byte[] responseData, int position) {
        return ((responseData[position] & 0xFF) << 8) | (responseData[position + 1] & 0xFF);
    }

    private int extractDataLength(byte[] responseData, int position) {
        return ((responseData[position] & 0xFF) << 8) | (responseData[position + 1] & 0xFF);
    }

    // Debug methods for compatibility
    public byte[] sendQueryDebug(String domainName, int type) throws Exception {
        return performDNSQueryVM(domainName, type);
    }

    public byte[] buildQueryDebug(String domain, int type) {
        try {
            return constructQueryPacket(domain, type);
        } catch (IOException e) {
            return null;
        }
    }

    public void skipQuestionDebug(ByteBuffer buffer, byte[] response) throws Exception {
        // Compatibility method
    }

    public String parseNameDebug(ByteBuffer buffer, byte[] response) throws Exception {
        return "";
    }
} 