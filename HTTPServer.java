import java.io.*;
import java.net.*;

public class HTTPServer {
    private static final int PORT = 8080;
    private static final String WEB_ROOT = "www"; // Directory for HTML files

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HTTP Server is listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start(); // Handle each client in a separate thread
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                // Read HTTP request line
                String requestLine = reader.readLine();
                if (requestLine == null) return;

                String[] tokens = requestLine.split(" ");
                if (tokens.length < 2 || !tokens[0].equals("GET")) {
                    sendResponse(out, 400, "Bad Request", "<h1>400 Bad Request</h1>");
                    return;
                }

                String requestedPath = tokens[1];
                if (requestedPath.equals("/")) {
                    requestedPath = "/index.html"; // Default page
                }

                // Prevent directory traversal attacks
                if (requestedPath.contains("..")) {
                    sendResponse(out, 403, "Forbidden", "<h1>403 Forbidden</h1>");
                    return;
                }

                File file = new File(WEB_ROOT + requestedPath);
                if (file.exists() && !file.isDirectory()) {
                    sendFileResponse(out, file);
                } else {
                    sendResponse(out, 404, "Not Found", "<h1>404 Not Found</h1>");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendResponse(OutputStream out, int statusCode, String statusText, String htmlContent) throws IOException {
            PrintWriter writer = new PrintWriter(out);
            writer.println("HTTP/1.1 " + statusCode + " " + statusText);
            writer.println("Content-Type: text/html");
            writer.println("Content-Length: " + htmlContent.length());
            writer.println();
            writer.println(htmlContent);
            writer.flush();
        }

        private void sendFileResponse(OutputStream out, File file) throws IOException {
            byte[] fileBytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(fileBytes);
            }
            PrintWriter writer = new PrintWriter(out);
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/html");
            writer.println("Content-Length: " + fileBytes.length);
            writer.println();
            writer.flush();
            out.write(fileBytes);
            out.flush();
        }
    }
}
