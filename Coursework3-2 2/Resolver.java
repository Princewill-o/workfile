// IN2011 Computer Networks
// Coursework 2024/2025 Resit
//
// Submission by
// YOUR_NAME_GOES_HERE
// YOUR_STUDENT_ID_NUMBER_GOES_HERE
// YOUR_EMAIL_GOES_HERE

import java.net.*;
import java.io.*;
import java.util.*;

// DO NOT EDIT starts
interface ResolverInterface {
    public void setNameServer(InetAddress ipAddress, int port) throws Exception;

    public InetAddress iterativeResolveAddress(String domainName) throws Exception;
    public String iterativeResolveText(String domainName) throws Exception;
    public String iterativeResolveName(String domainName, int type) throws Exception;
}
// DO NOT EDIT ends

public class Resolver implements ResolverInterface {

    private InetAddress nameServerIP;
    private int nameServerPort;

    @Override
    public void setNameServer(InetAddress ipAddress, int port) {
        this.nameServerIP = ipAddress;
        this.nameServerPort = port;
    }

    @Override
    public InetAddress iterativeResolveAddress(String domainName) throws Exception {
        DNSResult result = iterativeResolve(domainName, 1, new HashSet<>());
        return result != null ? result.address : null;
    }

    @Override
    public String iterativeResolveText(String domainName) throws Exception {
        DNSResult result = iterativeResolve(domainName, 16, new HashSet<>());
        return result != null ? result.txt : null;
    }

    @Override
    public String iterativeResolveName(String domainName, int type) throws Exception {
        DNSResult result = iterativeResolve(domainName, type, new HashSet<>());
        if (result == null) return null;
        switch (type) {
            case 5: return result.cname;
            case 2: return result.ns;
            case 15: return result.mx;
            default: return null;
        }
    }

    private DNSResult iterativeResolve(String domainName, int type, Set<String> visited) throws Exception {
        if (visited.contains(domainName.toLowerCase())) return null; // CNAME loop
        visited.add(domainName.toLowerCase());

        List<InetSocketAddress> servers = new ArrayList<>();
        servers.add(new InetSocketAddress(nameServerIP, nameServerPort));

        for (int depth = 0; depth < 20; depth++) {
            for (InetSocketAddress ns : servers) {
                try {
                    byte[] query = buildQuery(domainName, type);
                    byte[] response = sendQuery(query, ns.getAddress(), ns.getPort());
                    DNSResult result = parseResponse(response, domainName, type);
                    if (result.found) return result;
                    if (result.cname != null && !result.cname.equalsIgnoreCase(domainName))
                        return iterativeResolve(result.cname, type, visited);
                    if (!result.referralServers.isEmpty()) {
                        servers = result.referralServers;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private byte[] buildQuery(String domain, int type) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeShort(0x1234);
        dos.writeShort(0x0000);
        dos.writeShort(1);
        dos.writeShort(0);
        dos.writeShort(0);
        dos.writeShort(0);

        for (String label : domain.split("\\.")) {
            byte[] bytes = label.getBytes("UTF-8");
            dos.writeByte(bytes.length);
            dos.write(bytes);
        }
        dos.writeByte(0);
        dos.writeShort(type);
        dos.writeShort(1);

        return baos.toByteArray();
    }

    private byte[] sendQuery(byte[] query, InetAddress server, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(3000);
        DatagramPacket packet = new DatagramPacket(query, query.length, server, port);
        socket.send(packet);

        byte[] buffer = new byte[512];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        socket.close();
        return Arrays.copyOf(response.getData(), response.getLength());
    }

    private DNSResult parseResponse(byte[] data, String domain, int queryType) throws Exception {
        DNSResult result = new DNSResult();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        dis.skipBytes(4);
        int qd = dis.readUnsignedShort();
        int an = dis.readUnsignedShort();
        int ns = dis.readUnsignedShort();
        int ar = dis.readUnsignedShort();

        for (int i = 0; i < qd; i++) {
            skipName(dis);
            dis.skipBytes(4);
        }

        for (int i = 0; i < an; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2);
            dis.skipBytes(4);
            int rdLen = dis.readUnsignedShort();
            if (type == 1 && rdLen == 4) {
                byte[] addr = new byte[4];
                dis.readFully(addr);
                result.address = InetAddress.getByAddress(addr);
                result.found = true;
            } else if (type == 16) {
                int txtLen = dis.readUnsignedByte();
                byte[] txt = new byte[txtLen];
                dis.readFully(txt);
                result.txt = new String(txt, "UTF-8");
                result.found = true;
            } else if (type == 5) {
                result.cname = readName(dis, data);
                result.found = true;
            } else if (type == 2) {
                result.ns = readName(dis, data);
                result.found = true;
            } else if (type == 15) {
                dis.readUnsignedShort();
                result.mx = readName(dis, data);
                result.found = true;
            } else {
                dis.skipBytes(rdLen);
            }
        }

        Map<String, InetAddress> glue = new HashMap<>();
        for (int i = 0; i < ar; i++) {
            String name = readName(dis, data);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2);
            dis.skipBytes(4);
            int rdLen = dis.readUnsignedShort();
            if (type == 1 && rdLen == 4) {
                byte[] addr = new byte[4];
                dis.readFully(addr);
                glue.put(name.toLowerCase(), InetAddress.getByAddress(addr));
            } else {
                dis.skipBytes(rdLen);
            }
        }

        dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(12);
        for (int i = 0; i < qd; i++) {
            skipName(dis);
            dis.skipBytes(4);
        }
        for (int i = 0; i < ns; i++) {
            skipName(dis);
            int type = dis.readUnsignedShort();
            dis.skipBytes(2);
            dis.skipBytes(4);
            int rdLen = dis.readUnsignedShort();
            if (type == 2) {
                String nsHost = readName(dis, data);
                InetAddress ip = glue.get(nsHost.toLowerCase());
                if (ip != null) result.referralServers.add(new InetSocketAddress(ip, 53));
            } else {
                dis.skipBytes(rdLen);
            }
        }

        return result;
    }

    private void skipName(DataInputStream dis) throws IOException {
        int len;
        while ((len = dis.readUnsignedByte()) != 0) {
            if ((len & 0xC0) == 0xC0) {
                dis.readUnsignedByte();
                break;
            } else {
                dis.skipBytes(len);
            }
        }
    }

    private String readName(DataInputStream dis, byte[] full) throws IOException {
        StringBuilder name = new StringBuilder();
        int len = dis.readUnsignedByte();
        while (len != 0) {
            if ((len & 0xC0) == 0xC0) {
                int b2 = dis.readUnsignedByte();
                int ptr = ((len & 0x3F) << 8) | b2;
                name.append(readNameFromPointer(full, ptr));
                break;
            } else {
                byte[] label = new byte[len];
                dis.readFully(label);
                name.append(new String(label, "UTF-8")).append(".");
                len = dis.readUnsignedByte();
            }
        }
        if (name.length() > 0 && name.charAt(name.length() - 1) == '.')
            name.setLength(name.length() - 1);
        return name.toString();
    }

    private String readNameFromPointer(byte[] data, int offset) throws IOException {
        StringBuilder name = new StringBuilder();
        int len = data[offset++] & 0xFF;
        while (len != 0) {
            if ((len & 0xC0) == 0xC0) {
                int b2 = data[offset++] & 0xFF;
                int ptr = ((len & 0x3F) << 8) | b2;
                name.append(readNameFromPointer(data, ptr));
                break;
            } else {
                byte[] label = Arrays.copyOfRange(data, offset, offset + len);
                name.append(new String(label, "UTF-8")).append(".");
                offset += len;
                len = data[offset++] & 0xFF;
            }
        }
        if (name.length() > 0 && name.charAt(name.length() - 1) == '.')
            name.setLength(name.length() - 1);
        return name.toString();
    }

    private static class DNSResult {
        boolean found = false;
        InetAddress address;
        String txt, cname, ns, mx;
        List<InetSocketAddress> referralServers = new ArrayList<>();
    }
}
