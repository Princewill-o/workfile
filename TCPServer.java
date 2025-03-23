import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {

    public TCPServer() {}

    public static void main(String[] args) throws IOException {

        // Change port to 18080 as per the coursework challenge
        int port = 18080;

        // The server side is slightly more complex
        // First, create a ServerSocket to listen on port 18080
        System.out.println("Opening the server socket on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

        // The ServerSocket listens and creates a Socket object
        // for each incoming connection.
        System.out.println("Server waiting for client...");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected!");

        // Set up readers and writers to communicate with the client
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        Writer writer = new OutputStreamWriter(clientSocket.getOutputStream());

        // Read the message sent by the client
        String message = reader.readLine();
        System.out.println("The client said: " + message);

        // Sending a response to the client
        System.out.println("Sending a message to the client");
        writer.write("Nice to meet you\n");
        writer.flush();  // Flush the writer to send the message

        // Close the client connection
        clientSocket.close();

        // Optionally, close the server socket when the server is done
        serverSocket.close();
    }
}
