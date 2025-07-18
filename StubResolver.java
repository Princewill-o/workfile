// IN2011 Computer Networks
// Coursework 2024/2025 Resit
//
// Submission by
// YOUR_NAME_GOES_HERE
// YOUR_STUDENT_ID_NUMBER_GOES_HERE
// YOUR_EMAIL_GOES_HERE

import java.net.*;
import java.io.*;
import java.util.Random;

interface StubResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port) throws Exception;
    public InetAddress recursiveResolveAddress(String domainName) throws Exception;
    public String recursiveResolveText(String domainName) throws Exception;
    public String recursiveResolveName(String domainName, int type) throws Exception;
}

public class StubResolver implements StubResolverInterface {

    private InetAddress nameServerIP;
    private int nameServerPort;

    @Override
    public void setNameServer(InetAddress ipAddress, int port) throws Exception {
        this.nameServerIP = ipAddress;
        this.nameServerPort = port;
    }

    @Override
    public InetAddress recursiveResolveAddress(String domainName) throws Exception {
        byte[] query = buildQuery(domainName, 1); // A
        byte[] response = sendQuery(query);
        return parseAddressResponse(response);
    }

    @Override
    public String recursiveResolveText(String domainName) throws Exception {
        byte[] query = buildQuery(domainName, 16); // TXT
        byte[] response = sendQuery(query);
        return parseTxtResponse(response);
    }

    @Override
    public String recursiveResolveName(String domainName, int type) throws Exception {
        byte[] query = buildQuery(domainName, type);
        byte[] response = sendQuery(query);

        switch (type) {
            case 5:  return parseNameResponse(response, 5);   // CNAME
            case 2:  return parseNameResponse(response, 2);   // NS
            case 15: return parseMXResponse(response);        // MX
            case 16: return parseTxtResponse(response);       // TXT
            default: return null;
        }
    }

    private byte[] buildQuery(String domain, int type) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        Random random = new Random();
        dos.writeShort(random.nextInt(65536)); // Random ID
        dos.writeShort(0x0100); // Standard query, recursion desired
        dos.writeShort(1); // QDCOUNT
        dos.writeShort(0); // ANCOUNT
        dos.writeShort(0); // NSCOUNT
        dos.writeShort(0); // ARCOUNT

        for (String label : domain.split("\\.")) {
            byte[] bytes = label.getBytes("UTF-8");
            dos.writeByte(bytes.length);
            dos.write(bytes);
        }
        dos.writeByte(0); // End of QNAME
        dos.writeShort(type); // QTYPE
        dos.writeShort(1); // QCLASS (IN)

        return baos.toByteArray();
    }

    private byte[] sendQuery(byte[] query) throws Exception {
        if (nameServerIP == null || nameServerPort == 0) {
            throw new Exception("Name server not set.");
        }

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000); // 5 seconds timeout
        DatagramPacket request = new DatagramPacket(query, query.length, nameServerIP, nameServerPort);

        int retries = 3;
        while (retries > 0) {
            try {
                socket.send(request);
                byte[] buffer = new byte[1024]; // Increased buffer size
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                socket.close();
                return response.getData();
            } catch (SocketTimeoutException e) {
                retries--;
                if (retries == 0) {
                    socket.close();
                    throw new Exception("DNS query timed out after retries.");
                }
            }
        }
        socket.close();
        throw new Exception("DNS query failed.");
    }

    private InetAddress parseAddressResponse(byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(2); // Skip ID
        int flags = dis.readUnsignedShort();
        int rcode = flags & 0x000F;
        if (rcode != 0) {
            if (rcode == 3) return null; // NXDOMAIN
            throw new Exception("DNS query failed with RCODE: " + rcode);
        }
        dis.skipBytes(2); // QDCOUNT
        int anCount = dis.readUnsignedShort();
        dis.skipBytes(4); // NSCOUNT + ARCOUNT

        skipQuestions(dis);

        for (int i = 0; i < anCount; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2); // class
            dis.skipBytes(4); // TTL
            int rdLength = dis.readUnsignedShort();
            if (type == 1 && rdLength == 4) {
                byte[] addr = new byte[4];
                dis.readFully(addr);
                return InetAddress.getByAddress(addr);
            } else {
                dis.skipBytes(rdLength);
            }
        }
        return null;
    }

    private String parseTxtResponse(byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(2); // Skip ID
        int flags = dis.readUnsignedShort();
        int rcode = flags & 0x000F;
        if (rcode != 0) {
            if (rcode == 3) return null; // NXDOMAIN
            throw new Exception("DNS query failed with RCODE: " + rcode);
        }
        dis.skipBytes(2); // QDCOUNT
        int anCount = dis.readUnsignedShort();
        dis.skipBytes(4); // NSCOUNT + ARCOUNT

        skipQuestions(dis);

        for (int i = 0; i < anCount; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2); // class
            dis.skipBytes(4); // TTL
            int rdLength = dis.readUnsignedShort();
            if (type == 16) {
                int txtLen = dis.readUnsignedByte();
                byte[] txt = new byte[txtLen];
                dis.readFully(txt);
                return new String(txt, "UTF-8");
            } else {
                dis.skipBytes(rdLength);
            }
        }
        return null;
    }

    private String parseNameResponse(byte[] data, int expectedType) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(2); // Skip ID
        int flags = dis.readUnsignedShort();
        int rcode = flags & 0x000F;
        if (rcode != 0) {
            if (rcode == 3) return null; // NXDOMAIN
            throw new Exception("DNS query failed with RCODE: " + rcode);
        }
        dis.skipBytes(2); // QDCOUNT
        int anCount = dis.readUnsignedShort();
        dis.skipBytes(4); // NSCOUNT + ARCOUNT

        skipQuestions(dis);

        for (int i = 0; i < anCount; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2); // class
            dis.skipBytes(4); // TTL
            int rdLength = dis.readUnsignedShort();
            if (type == expectedType) {
                return readName(dis, data);
            } else {
                dis.skipBytes(rdLength);
            }
        }
        return null;
    }

    private String parseMXResponse(byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(2); // Skip ID
        int flags = dis.readUnsignedShort();
        int rcode = flags & 0x000F;
        if (rcode != 0) {
            if (rcode == 3) return null; // NXDOMAIN
            throw new Exception("DNS query failed with RCODE: " + rcode);
        }
        dis.skipBytes(2); // QDCOUNT
        int anCount = dis.readUnsignedShort();
        dis.skipBytes(4); // NSCOUNT + ARCOUNT

        skipQuestions(dis);

        for (int i = 0; i < anCount; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2); // class
            dis.skipBytes(4); // TTL
            int rdLength = dis.readUnsignedShort();
            if (type == 15) {
                dis.readUnsignedShort(); // preference
                return readName(dis, data);
            } else {
                dis.skipBytes(rdLength);
            }
        }
        return null;
    }

    private void skipQuestions(DataInputStream dis) throws IOException {
        skipName(dis);
        dis.skipBytes(4); // QTYPE + QCLASS
    }

    private void skipName(DataInputStream dis) throws IOException {
        int len;
        while ((len = dis.readUnsignedByte()) != 0) {
            if ((len & 0xC0) == 0xC0) {
                dis.readUnsignedByte(); // pointer
                break;
            } else {
                dis.skipBytes(len);
            }
        }
    }

    private String readName(DataInputStream dis, byte[] fullMessage) throws IOException {
        StringBuilder name = new StringBuilder();
        int len = dis.readUnsignedByte();

        while (len != 0) {
            if ((len & 0xC0) == 0xC0) {
                int b2 = dis.readUnsignedByte();
                int pointer = ((len & 0x3F) << 8) | b2;
                name.append(readNameFromPointer(fullMessage, pointer));
                break;
            } else {
                byte[] label = new byte[len];
                dis.readFully(label);
                name.append(new String(label, "UTF-8")).append(".");
                len = dis.readUnsignedByte();
            }
        }

        if (name.length() > 0 && name.charAt(name.length() - 1) == '.') {
            name.setLength(name.length() - 1);
        }
        return name.toString();
    }

    private String readNameFromPointer(byte[] message, int pointer) throws IOException {
        StringBuilder name = new StringBuilder();
        int offset = pointer;
        int len = message[offset++] & 0xFF;

        while (len != 0) {
            if ((len & 0xC0) == 0xC0) {
                int b2 = message[offset++] & 0xFF;
                int newPointer = ((len & 0x3F) << 8) | b2;
                name.append(readNameFromPointer(message, newPointer));
                break;
            } else {
                byte[] label = new byte[len];
                System.arraycopy(message, offset, label, 0, len);
                name.append(new String(label, "UTF-8")).append(".");
                offset += len;
                len = message[offset++] & 0xFF;
            }
        }

        if (name.length() > 0 && name.charAt(name.length() - 1) == '.') {
            name.setLength(name.length() - 1);
        }
        return name.toString();
    }
}