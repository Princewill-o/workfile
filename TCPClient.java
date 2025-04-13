import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class TCPClient {

    public TCPClient() {
    }

    private static String computeHashID(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(s.getBytes(StandardCharsets.UTF_8));
        byte[] hash = md.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public static void main(String[] args) throws Exception {
        String IPAddressString = "10.200.51.18";
        InetAddress host = InetAddress.getByName(IPAddressString);

        int port = 4022;

        System.out.println("TCPClient connecting to " + host.toString() + ":" + port);
        Socket clientSocket = new Socket(host, port);

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        Writer writer = new OutputStreamWriter(clientSocket.getOutputStream());

        boolean looking = true;

        while (looking) {
            writer.write("GET 127.0.0.1:8080\n");
            writer.flush();

            String response = reader.readLine().trim();
            String status = reader.readLine().trim();

            if(status.endsWith(computeHashID(response))) {
                System.out.println("Flag: " + response);
                looking = false;
            }
        }
        clientSocket.close();
    }
}