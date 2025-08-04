import java.net.*;
import java.io.*;
import java.util.*;

// DO NOT EDIT starts
interface NameServerInterface {
    public void setNameServer(InetAddress ipAddress, int port) throws Exception;
    public void handleIncomingQueries(int port) throws Exception;
}
// DO NOT EDIT ends

public class NameServer implements NameServerInterface {

    // Upstream DNS server for iterative resolution
    private InetAddress upstreamIP;
    private int upstreamPort;
    private Resolver resolver;

    // Simple cache (name|type -> CachedRecord)
    private final Map<String, CachedRecord> cache = new HashMap<>();
    private final Map<String, CachedRecord> negativeCache = new HashMap<>();

    @Override
    public void setNameServer(InetAddress ipAddress, int port) throws Exception {
        this.upstreamIP = ipAddress;
        this.upstreamPort = port;
        resolver = new Resolver();
        try {
            resolver.setNameServer(ipAddress, port);
            System.out.println("Upstream DNS set to " + ipAddress + ":" + port);
        } catch (Exception e) {
            System.err.println("Failed to set upstream DNS server: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void handleIncomingQueries(int port) throws Exception {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            System.out.println("NameServer successfully bound to UDP port " + port);
        } catch (SocketException e) {
            System.err.println("Failed to bind to port " + port + ": " + e.getMessage());
            if (e.getMessage().contains("Address already in use")) {
                System.err.println("Port " + port + " is already in use. Check with 'sudo netstat -tulnp | grep " + port + "' and terminate conflicting processes, or try a different port.");
            } else if (e.getMessage().contains("Permission denied")) {
                System.err.println("Permission denied on port " + port + ". Try running with sudo: 'sudo java NameServer " + port + "'");
            }
            throw e;
        }

        byte[] buffer = new byte[512];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                System.out.println("Received query from " + packet.getAddress() + ":" + packet.getPort());

                byte[] reqData = Arrays.copyOf(packet.getData(), packet.getLength());
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                DNSQuery query;
                try {
                    query = DNSQuery.parse(reqData);
                    System.out.println("Parsed query: " + query.qname + " type=" + query.qtype);
                } catch (Exception e) {
                    System.err.println("Malformed query from " + clientAddr + ": " + e.getMessage());
                    byte[] errResp = DNSResponse.buildErrorResponse(reqData, 1); // RCODE=1 (FORMERR)
                    socket.send(new DatagramPacket(errResp, errResp.length, clientAddr, clientPort));
                    continue;
                }

                String cacheKey = query.qname + "|" + query.qtype;
                CachedRecord answer = cache.get(cacheKey);
                if (answer != null && answer.isExpired()) {
                    cache.remove(cacheKey);
                    answer = null;
                    System.out.println("Removed expired cache entry: " + cacheKey);
                }

                CachedRecord neg = negativeCache.get(cacheKey);
                if (neg != null && neg.isExpired()) {
                    negativeCache.remove(cacheKey);
                    neg = null;
                    System.out.println("Removed expired negative cache entry: " + cacheKey);
                }

                byte[] responseData = null;

                if (answer != null) {
                    System.out.println("Serving from cache: " + cacheKey);
                    responseData = DNSResponse.buildAnswerResponse(reqData, query, answer);
                } else if (neg != null) {
                    System.out.println("Serving negative cache (NXDOMAIN): " + cacheKey);
                    responseData = DNSResponse.buildErrorResponse(reqData, 3); // RCODE=3 (NXDOMAIN)
                } else {
                    System.out.println("Resolving query: " + cacheKey);
                    try {
                        CachedRecord resolved = resolveAndCache(query);
                        if (resolved != null) {
                            responseData = DNSResponse.buildAnswerResponse(reqData, query, resolved);
                        } else {
                            negativeCache.put(cacheKey, CachedRecord.negative(System.currentTimeMillis() + 30_000, 3));
                            responseData = DNSResponse.buildErrorResponse(reqData, 3); // RCODE=3 (NXDOMAIN)
                            System.out.println("Cached NXDOMAIN for: " + cacheKey);
                        }
                    } catch (Exception e) {
                        System.err.println("Resolution failed for " + cacheKey + ": " + e.getMessage());
                        responseData = DNSResponse.buildErrorResponse(reqData, 2); // RCODE=2 (SERVFAIL)
                    }
                }

                try {
                    socket.send(new DatagramPacket(responseData, responseData.length, clientAddr, clientPort));
                    System.out.println("Sent response to " + clientAddr + ":" + clientPort);
                } catch (IOException e) {
                    System.err.println("Failed to send response to " + clientAddr + ":" + clientPort + ": " + e.getMessage());
                }

            } catch (IOException e) {
                System.err.println("IOException while handling query: " + e.getMessage());
                // Continue to keep the server running
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                // Defensive: keep running
            }
        }
    }

    /**
     * Uses Resolver to answer a query, stores in cache.
     */
    private CachedRecord resolveAndCache(DNSQuery query) throws Exception {
        String key = query.qname + "|" + query.qtype;
        CachedRecord record = null;
        long now = System.currentTimeMillis();

        try {
            if (query.qtype == 1) { // A
                InetAddress addr = resolver.iterativeResolveAddress(query.qname);
                if (addr != null) {
                    record = new CachedRecord(addr.getAddress(), now + 60_000, 1);
                    cache.put(key, record);
                    System.out.println("Cached A record for " + query.qname);
                }
            } else if (query.qtype == 16) { // TXT
                String txt = resolver.iterativeResolveText(query.qname);
                if (txt != null) {
                    record = new CachedRecord(txt.getBytes("UTF-8"), now + 60_000, 16);
                    cache.put(key, record);
                    System.out.println("Cached TXT record for " + query.qname);
                }
            } else if (query.qtype == 5) { // CNAME
                String cname = resolver.iterativeResolveName(query.qname, 5);
                if (cname != null) {
                    record = new CachedRecord(cname.getBytes("UTF-8"), now + 60_000, 5);
                    cache.put(key, record);
                    System.out.println("Cached CNAME record for " + query.qname);
                }
            } else if (query.qtype == 2) { // NS
                String ns = resolver.iterativeResolveName(query.qname, 2);
                if (ns != null) {
                    record = new CachedRecord(ns.getBytes("UTF-8"), now + 60_000, 2);
                    cache.put(key, record);
                    System.out.println("Cached NS record for " + query.qname);
                }
            } else if (query.qtype == 15) { // MX
                String mx = resolver.iterativeResolveName(query.qname, 15);
                if (mx != null) {
                    record = new CachedRecord(mx.getBytes("UTF-8"), now + 60_000, 15);
                    cache.put(key, record);
                    System.out.println("Cached MX record for " + query.qname);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve " + query.qname + " type=" + query.qtype + ": " + e.getMessage());
            throw e;
        }
        return record;
    }

    // Helper class for caching
    static class CachedRecord {
        public byte[] data;
        public long expires;
        public int type; // DNS TYPE
        public int rcode; // for negative cache (NXDOMAIN), 0 for normal

        public CachedRecord(byte[] data, long expires, int type) {
            this.data = data;
            this.expires = expires;
            this.type = type;
            this.rcode = 0;
        }

        // Static factory for negative cache
        public static CachedRecord negative(long expires, int rcode) {
            CachedRecord rec = new CachedRecord(null, expires, -1);
            rec.rcode = rcode;
            return rec;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expires;
        }
    }

    // Minimal DNS query parser
    static class DNSQuery {
        public String qname;
        public int qtype;
        public int qclass;

        public static DNSQuery parse(byte[] data) throws Exception {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            dis.skipBytes(12); // DNS header
            StringBuilder name = new StringBuilder();
            int len;
            while ((len = dis.readUnsignedByte()) != 0) {
                if ((len & 0xC0) == 0xC0) {
                    // Pointer, compression not expected for queries
                    throw new Exception("Malformed QNAME");
                }
                byte[] label = new byte[len];
                dis.readFully(label);
                name.append(new String(label, "UTF-8")).append(".");
            }
            String qname = name.toString();
            int qtype = dis.readUnsignedShort();
            int qclass = dis.readUnsignedShort();
            return new DNSQuery(qname, qtype, qclass);
        }

        public DNSQuery(String qname, int qtype, int qclass) {
            this.qname = qname;
            this.qtype = qtype;
            this.qclass = qclass;
        }
    }

    // DNS response builder
    static class DNSResponse {
        public static byte[] buildAnswerResponse(byte[] req, DNSQuery query, CachedRecord record) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Header
            dos.writeShort(((req[0] & 0xFF) << 8) | (req[1] & 0xFF)); // ID
            dos.writeShort(0x8180); // QR=1, AA=0, TC=0, RD=1, RA=1, RCODE=0
            dos.writeShort(1); // QDCOUNT
            dos.writeShort(1); // ANCOUNT
            dos.writeShort(0); // NSCOUNT
            dos.writeShort(0); // ARCOUNT

            // Question (copy from request)
            int idx = 12;
            while (req[idx] != 0) {
                dos.writeByte(req[idx]);
                idx++;
            }
            dos.writeByte(0);
            dos.writeShort(query.qtype);
            dos.writeShort(query.qclass);

            // Answer
            dos.writeShort(0xC00C); // pointer to offset 12
            dos.writeShort(query.qtype);
            dos.writeShort(query.qclass);
            dos.writeInt(60); // TTL: 60s

            if (query.qtype == 1 && record.data.length == 4) { // A
                dos.writeShort(4); // RDLENGTH
                dos.write(record.data);
            } else if (query.qtype == 16) { // TXT
                dos.writeShort(record.data.length + 1);
                dos.writeByte(record.data.length);
                dos.write(record.data);
            } else if (query.qtype == 5 || query.qtype == 2 || query.qtype == 15) { // CNAME, NS, MX
                byte[] labels = toDNSLabels(new String(record.data, "UTF-8"));
                if (query.qtype == 15) { // MX
                    dos.writeShort(labels.length + 2);
                    dos.writeShort(10); // preference
                    dos.write(labels);
                } else {
                    dos.writeShort(labels.length);
                    dos.write(labels);
                }
            } else {
                dos.writeShort(record.data.length);
                dos.write(record.data);
            }

            return baos.toByteArray();
        }

        public static byte[] buildErrorResponse(byte[] req, int rcode) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeShort(((req[0] & 0xFF) << 8) | (req[1] & 0xFF)); // ID
            dos.writeShort(0x8180 | rcode); // QR=1, RA=1, RCODE
            dos.writeShort(1); // QDCOUNT
            dos.writeShort(0); // ANCOUNT
            dos.writeShort(0); // NSCOUNT
            dos.writeShort(0); // ARCOUNT

            // Question (copy from request)
            int idx = 12;
            while (req[idx] != 0) {
                dos.writeByte(req[idx]);
                idx++;
            }
            dos.writeByte(0);
            dos.writeShort(((req[idx + 1] & 0xFF) << 8) | (req[idx + 2] & 0xFF));
            dos.writeShort(((req[idx + 3] & 0xFF) << 8) | (req[idx + 4] & 0xFF));

            return baos.toByteArray();
        }

        private static byte[] toDNSLabels(String name) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String[] labels = name.split("\\.");
            for (String label : labels) {
                if (!label.isEmpty()) {
                    byte[] lab = label.getBytes("UTF-8");
                    baos.write(lab.length);
                    baos.write(lab);
                }
            }
            baos.write(0);
            return baos.toByteArray();
        }
    }
}