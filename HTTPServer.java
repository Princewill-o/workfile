import java.io.*;
import java.net.*;

public class HTTPServer {
    private static final int PORT = 18080; // Change to 18080 for coursework challenge

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HTTP Server is listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start(); // Handle each request in a separate thread
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
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()
            ) {
                String requestLine = reader.readLine();
                if (requestLine == null) return;

                System.out.println("Received request: " + requestLine);
                String[] tokens = requestLine.split(" ");
                if (tokens.length < 2 || !tokens[0].equals("GET")) {
                    sendResponse(out, 400, "Bad Request", "<h1>400 Bad Request</h1>");
                    return;
                }

                String path = tokens[1];
                if (path.equals("/") || path.equals("/index.html")) {
                    sendResponse(out, 200, "OK", "<h1>Welcome to HTTP Server</h1>");
                } else {
                    sendResponse(out, 400, "Bad Request", "<h1>400 Bad Request</h1>");
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
    }
}
