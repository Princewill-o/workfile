import java.io.*;
import java.net.*;

public class HTTPServer {
    public static void main(String[] args) throws IOException {
        int port = 18080;
        System.out.println("Opening the server socket on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {  // Keep the server running to handle multiple requests
            System.out.println("Server waiting for client...");
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected!");

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            // Read the request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                continue;
            }
            System.out.println("Request: " + requestLine);

            // Parse HTTP method and path
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2 || !requestParts[0].equals("GET")) {
                sendResponse(writer, 400, "Bad Request", "<h1>400 Bad Request</h1>");
                clientSocket.close();
                continue;
            }
            String path = requestParts[1];

            // Read and discard the remaining headers (until an empty line is found)
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println("Header: " + line);
            }

            // Handle response based on path
            if (path.equals("/") || path.equals("/index.html")) {
                sendResponse(writer, 200, "OK", "<html><body><h1>Welcome to my server</h1></body></html>");
            } else {
                sendResponse(writer, 400, "Bad Request", "<html><body><h1>400 Bad Request</h1></body></html>");
            }

            clientSocket.close();
        }
    }

    private static void sendResponse(BufferedWriter writer, int statusCode, String statusText, String body) throws IOException {
        writer.write("HTTP/1.1 " + statusCode + " " + statusText + "\r\n");
        writer.write("Content-Type: text/html\r\n");
        writer.write("Content-Length: " + body.length() + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n"); // End of headers
        writer.write(body);
        writer.flush();
    }
}
