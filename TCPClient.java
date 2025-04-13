import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class TCPClient {

    public TCPClient() {
    }

    // Computes SHA-256 hash and returns as hex string
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

        boolean looking = true;

        while (looking) {
            try (
                Socket clientSocket = new Socket(host, port);
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                Writer writer = new OutputStreamWriter(clientSocket.getOutputStream())
            ) {
                // Send request
                writer.write("GET 127.0.0.1:8080\n");
                writer.flush();

                // Read response
                String response = reader.readLine();
                String status = reader.readLine();

                if (response == null || status == null) {
                    System.out.println("Connection closed by server or incomplete response.");
                    continue;
                }

                response = response.trim();
                status = status.trim();

                // Check hash
                if (status.endsWith(computeHashID(response))) {
                    System.out.println("Flag: " + response);
                    looking = false;
                }

                // Small delay to avoid hammering the server
                Thread.sleep(500);

            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                Thread.sleep(1000); // Wait before retry
            }
        }

        System.out.println("TCPClient finished.");
    }
}
