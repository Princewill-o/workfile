import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class HTTPServer {

    public static void main(String[] args) throws IOException {
        int port = 18080;  // Listen on port 8080

        System.out.println("Opening the HTTP server socket on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server waiting for client...");
        while (true) {
            // Wait for a connection and hand it off to a new thread
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected!");

            // Create a new thread to handle the client
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            Writer writer = new OutputStreamWriter(clientSocket.getOutputStream());

            // Read the first line of the HTTP request (method, path, HTTP version)
            String requestLine = reader.readLine();
            System.out.println("Received request: " + requestLine);

            // Split the request line by spaces
            String[] requestParts = requestLine.split(" ");

            // Read the headers (finish when we encounter an empty line)
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println("Header: " + headerLine);  // Print the headers
            }

            // Process the request based on the method and path
            if (requestParts.length >= 3 && requestParts[0].equalsIgnoreCase("GET")) {
                String path = requestParts[1];  // The requested path (e.g., /index.html)

                // Sanitize the path to prevent directory traversal attacks
                path = sanitizePath(path);

                // If the path is root or index.html, serve the file
                if (path.equals("/") || path.equals("/index.html")) {
                    serveFile(writer, "index.html", 200);
                } else {
                    serveFile(writer, path.substring(1), 400);  // Remove the leading '/'
                }
            } else {
                // If the method is not GET
                System.out.println("Method is not GET, responding with 405 Method Not Allowed");

                // Respond with 405 Method Not Allowed
                writer.write("HTTP/1.1 405 Method Not Allowed\n");
                writer.write("Content-Type: text/html\n");
                writer.write("\n");
                writer.write("<html><body><h1>Method Not Allowed</h1></body></html>");
                writer.flush();
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to sanitize the path and prevent directory traversal
    private String sanitizePath(String path) {
        // Remove any '..' and normalize the path to avoid directory traversal
        Path sanitizedPath = Paths.get(path).normalize();

        // Ensure the path is within the "webroot" directory (e.g., current working directory)
        Path webRoot = Paths.get("webroot");  // Use a specific directory for the server files
        Path absolutePath = webRoot.resolve(sanitizedPath).toAbsolutePath();

        // Check if the absolute path is within the "webroot" directory
        if (!absolutePath.startsWith(webRoot.toAbsolutePath())) {
            return null;  // Path is outside the allowed directory, reject it
        }

        return sanitizedPath.toString();  // Return the sanitized and valid path
    }

    // Method to serve files or return an error
    private void serveFile(Writer writer, String filePath, int statusCode) {
        Path path = Paths.get(filePath);
        File file = path.toFile();

        // Check if the file exists
        if (file.exists() && file.isFile()) {
            try {
                // Read the file content and send it in the response
                byte[] fileContent = Files.readAllBytes(path);

                // Determine the MIME type based on the file extension (basic handling)
                String contentType = "text/html";
                if (filePath.endsWith(".html")) {
                    contentType = "text/html";
                } else if (filePath.endsWith(".css")) {
                    contentType = "text/css";
                } else if (filePath.endsWith(".js")) {
                    contentType = "application/javascript";
                } else if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (filePath.endsWith(".png")) {
                    contentType = "image/png";
                }

                // Respond with 200 OK and the file content
                writer.write("HTTP/1.1 " + statusCode + " OK\n");
                writer.write("Content-Type: " + contentType + "\n");
                writer.write("Content-Length: " + fileContent.length + "\n");
                writer.write("\n");
                writer.write(new String(fileContent)); // Send the file content
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // If the file does not exist, respond with 404 Not Found
            try {
                writer.write("HTTP/1.1 404 Not Found\n");
                writer.write("Content-Type: text/html\n");
                writer.write("\n");
                writer.write("<html><body><h1>404 Not Found</h1></body></html>");
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
