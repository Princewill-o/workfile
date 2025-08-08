// IN2011 Computer Networks
// Coursework 2024/2025 Resit
//
// Submission by
// Wasey-Coding
// YOUR_STUDENT_ID_NUMBER_GOES_HERE
// YOUR_EMAIL_GOES_HERE

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// DO NOT EDIT starts
interface NameServerInterface {
    public void setNameServer(InetAddress ipAddress, int port) throws Exception;
    public void handleIncomingQueries(int port) throws Exception;
}
// DO NOT EDIT ends

public class NameServer implements NameServerInterface {
    private InetAddress upstreamIP;
    private int upstreamPort;
    private Resolver resolver;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private final Map<String, CachedRecord> cache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, CachedRecord> negativeCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<InetAddress, RequestCounter> requestCounters = new ConcurrentHashMap<>();

    static class CachedRecord {
        public final byte[] data;
        public final long expires;
        public final int type;
        public final int rcode;
        public final int originalTtl;

        public CachedRecord(byte[] data, int ttl, int type, int rcode) {
            this.data = data;
            this.originalTtl = ttl;
            this.expires = System.currentTimeMillis() + (ttl * 1000L);
            this.type = type;
            this.rcode = rcode;
        }

        public static CachedRecord negative(int ttl, int rcode) {
            return new CachedRecord(null, ttl, -1, rcode);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expires;
        }

        public int getRemainingTtl() {
            return (int) ((expires - System.currentTimeMillis()) / 1000);
        }
    }

    static class RequestCounter {
        private final long[] timestamps = new long[10];
        private int index = 0;

        public synchronized boolean isOverLimit() {
            long now = System.currentTimeMillis();
            timestamps[index] = now;
            index = (index + 1) % timestamps.length;

            if (timestamps[timestamps.length - 1] == 0) return false;
            return (now - timestamps[(index + 1) % timestamps.length]) < 1000;
        }
    }

    // DO NOT EDIT starts
    @Override
    public void setNameServer(InetAddress ipAddress, int port) throws Exception {
        this.upstreamIP = ipAddress;
        this.upstreamPort = port;
        resolver = new Resolver();
        resolver.setNameServer(ipAddress, port);
    }
    // DO NOT EDIT ends

    @Override
    public void handleIncomingQueries(int port) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("NameServer listening on UDP port " + port);
            byte[] buffer = new byte[512];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    RequestCounter counter = requestCounters.computeIfAbsent(
                            packet.getAddress(), k -> new RequestCounter());
                    if (counter.isOverLimit()) {
                        continue;
                    }

                    threadPool.submit(() -> {
                        try {
                            processRequest(socket, packet);
                        } catch (IOException e) {
                            System.err.println("Error processing request: " + e.getMessage());
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Error receiving packet: " + e.getMessage());
                }
            }
        }
    }

    private void processRequest(DatagramSocket socket, DatagramPacket packet) throws IOException {
        byte[] reqData = Arrays.copyOf(packet.getData(), packet.getLength());
        InetAddress clientAddr = packet.getAddress();
        int clientPort = packet.getPort();

        DNSQuery query;
        try {
            query = parseQuery(reqData);
        } catch (Exception e) {
            try {
                byte[] errResp = DNSResponse.buildErrorResponse(reqData, 1);
                socket.send(new DatagramPacket(errResp, errResp.length, clientAddr, clientPort));
            } catch (IOException ioe) {
                System.err.println("Failed to send error response: " + ioe.getMessage());
            }
            return;
        }

        String cacheKey = query.qname.toLowerCase() + "|" + query.qtype;
        byte[] responseData = null;

        try {
            responseData = getCachedResponse(reqData, query, cacheKey);
        } catch (IOException e) {
            System.err.println("Error getting cached response: " + e.getMessage());
        }

        if (responseData == null) {
            try {
                responseData = resolveAndRespond(query, reqData, cacheKey);
            } catch (Exception e) {
                try {
                    responseData = DNSResponse.buildErrorResponse(reqData, 2);
                } catch (IOException ioe) {
                    System.err.println("Failed to build error response: " + ioe.getMessage());
                    return;
                }
            }
        }

        try {
            socket.send(new DatagramPacket(responseData, responseData.length, clientAddr, clientPort));
        } catch (IOException e) {
            System.err.println("Failed to send response: " + e.getMessage());
        }
    }

    private DNSQuery parseQuery(byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(12);

        StringBuilder name = new StringBuilder();
        int len;
        while ((len = dis.readUnsignedByte()) != 0) {
            if ((len & 0xC0) == 0xC0) {
                dis.readUnsignedByte();
                break;
            }
            byte[] label = new byte[len];
            dis.readFully(label);
            name.append(new String(label, "UTF-8")).append(".");
        }

        int qtype = dis.readUnsignedShort();
        int qclass = dis.readUnsignedShort();
        return new DNSQuery(name.toString(), qtype, qclass);
    }

    private byte[] getCachedResponse(byte[] reqData, DNSQuery query, String cacheKey) throws IOException {
        synchronized (cache) {
            CachedRecord answer = cache.get(cacheKey);
            if (answer != null) {
                if (answer.isExpired()) {
                    cache.remove(cacheKey);
                } else {
                    return buildResponse(reqData, query, answer);
                }
            }

            CachedRecord neg = negativeCache.get(cacheKey);
            if (neg != null) {
                if (neg.isExpired()) {
                    negativeCache.remove(cacheKey);
                } else {
                    return DNSResponse.buildErrorResponse(reqData, neg.rcode);
                }
            }
        }
        return null;
    }

    private byte[] resolveAndRespond(DNSQuery query, byte[] reqData, String cacheKey) throws Exception {
        CachedRecord record = resolveRecord(query);
        byte[] response;

        if (record != null) {
            synchronized (cache) {
                cache.put(cacheKey, record);
            }
            response = buildResponse(reqData, query, record);
        } else {
            CachedRecord neg = CachedRecord.negative(30, 3);
            synchronized (negativeCache) {
                negativeCache.put(cacheKey, neg);
            }
            response = DNSResponse.buildErrorResponse(reqData, 3);
        }

        return response;
    }

    private CachedRecord resolveRecord(DNSQuery query) throws Exception {
        switch (query.qtype) {
            case 1:
                InetAddress addr = resolver.iterativeResolveAddress(query.qname);
                return addr != null ?
                        new CachedRecord(addr.getAddress(), getTtlFromResolver(), 1, 0) : null;
            case 16:
                String txt = resolver.iterativeResolveText(query.qname);
                return txt != null ?
                        new CachedRecord(txt.getBytes("UTF-8"), getTtlFromResolver(), 16, 0) : null;
            case 5:
                String cname = resolver.iterativeResolveName(query.qname, 5);
                return cname != null ?
                        new CachedRecord(cname.getBytes("UTF-8"), getTtlFromResolver(), 5, 0) : null;
            case 2:
                String ns = resolver.iterativeResolveName(query.qname, 2);
                return ns != null ?
                        new CachedRecord(ns.getBytes("UTF-8"), getTtlFromResolver(), 2, 0) : null;
            case 15:
                String mx = resolver.iterativeResolveName(query.qname, 15);
                return mx != null ?
                        new CachedRecord(mx.getBytes("UTF-8"), getTtlFromResolver(), 15, 0) : null;
            default:
                return null;
        }
    }

    private int getTtlFromResolver() {
        return 300;
    }

    private byte[] buildResponse(byte[] req, DNSQuery query, CachedRecord record) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeShort(((req[0] & 0xFF) << 8) | (req[1] & 0xFF));
        dos.writeShort(0x8180);
        dos.writeShort(1);
        dos.writeShort(1);
        dos.writeShort(0);
        dos.writeShort(0);

        int idx = 12;
        while (req[idx] != 0) {
            dos.writeByte(req[idx++]);
        }
        dos.writeByte(0);
        dos.writeShort(query.qtype);
        dos.writeShort(query.qclass);

        dos.writeShort(0xC00C);
        dos.writeShort(query.qtype);
        dos.writeShort(query.qclass);
        dos.writeInt(record.getRemainingTtl());

        if (query.qtype == 1) {
            dos.writeShort(4);
            dos.write(record.data);
        } else if (query.qtype == 16) {
            dos.writeShort(record.data.length + 1);
            dos.writeByte(record.data.length);
            dos.write(record.data);
        } else {
            byte[] labels = toDNSLabels(new String(record.data, "UTF-8"));
            if (query.qtype == 15) {
                dos.writeShort(labels.length + 2);
                dos.writeShort(10);
                dos.write(labels);
            } else {
                dos.writeShort(labels.length);
                dos.write(labels);
            }
        }

        return baos.toByteArray();
    }

    private byte[] toDNSLabels(String name) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String label : name.split("\\.")) {
            if (!label.isEmpty()) {
                byte[] bytes = label.getBytes("UTF-8");
                baos.write(bytes.length);
                baos.write(bytes);
            }
        }
        baos.write(0);
        return baos.toByteArray();
    }

    static class DNSQuery {
        public final String qname;
        public final int qtype;
        public final int qclass;

        public DNSQuery(String qname, int qtype, int qclass) {
            this.qname = qname;
            this.qtype = qtype;
            this.qclass = qclass;
        }
    }

    static class DNSResponse {
        public static byte[] buildErrorResponse(byte[] req, int rcode) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeShort(((req[0] & 0xFF) << 8) | (req[1] & 0xFF));
            dos.writeShort(0x8180 | rcode);
            dos.writeShort(1);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);

            int idx = 12;
            while (req[idx] != 0) {
                dos.writeByte(req[idx++]);
            }
            dos.writeByte(0);
            dos.writeShort(((req[idx+1] & 0xFF) << 8) | (req[idx+2] & 0xFF));
            dos.writeShort(((req[idx+3] & 0xFF) << 8) | (req[idx+4] & 0xFF));

            return baos.toByteArray();
        }
    }
}