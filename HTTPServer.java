import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPServer {

    public static void main(String[] args) throws IOException {
        // Port 18080 as per the coursework challenge
        int port = 18080;

        // The server listens on port 18080
        System.out.println("Opening the server socket on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

        // Server waits for client connections
        System.out.println("Server waiting for client...");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected!");

        // Create readers and writers to communicate with the client
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        Writer writer = new OutputStreamWriter(clientSocket.getOutputStream());

        // Read the first line of the request (method, path, and HTTP version)
        String requestLine = reader.readLine();
        System.out.println("Request: " + requestLine);

        // Split the request line into components (method, path, and HTTP version)
        String[] requestParts = requestLine.split(" ");
        String method = requestParts[0];
        String path = requestParts[1];

        // Read the rest of the headers
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            System.out.println("Header: " + header);
        }

        // Check if the method is GET
        if (!method.equals("GET")) {
            // If method is not GET, return a 405 Method Not Allowed response
            writer.write("HTTP/1.1 405 Method Not Allowed\r\n");
            writer.write("Content-Type: text/html\r\n");
            writer.write("\r\n");
            writer.write("<html><body><h1>405 Method Not Allowed</h1></body></html>");
            writer.flush();
        } else {
            // Handle the requested path
            if (path.equals("/") || path.equals("/index.html")) {
                // Return HTTP 200 with a simple HTML page
                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: text/html\r\n");
                writer.write("\r\n");
                writer.write("<html><body><h1>Welcome to the Home Page</h1></body></html>");
                writer.flush();
            } else {
                // Return HTTP 400 for invalid paths
                writer.write("HTTP/1.1 400 Bad Request\r\n");
                writer.write("Content-Type: text/html\r\n");
                writer.write("\r\n");
                writer.write("<html><body><h1>400 Bad Request</h1></body></html>");
                writer.flush();
            }
        }

        // Close the client connection
        clientSocket.close();
        serverSocket.close();
    }
}
