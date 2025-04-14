import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hanterar anslutningar från flera klienter och skickar vidare meddelanden mellan dem.
 */
public class ChatServer {
    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, String> userPublicKeys = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    
    /**
     * Skapar en ny ChatServer på angiven port.
     *
     * @param port Portnummer för servern.
     * @throws IOException Om ett fel uppstår vid skapandet av serverns socket.
     */
    public ChatServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
    }
    
    /**
     * Startar servern och accepterar klientanslutningar.
     */
    public void start() {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                pool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            stop();
        }
    }
    
    /**
     * Stoppar servern och stänger alla anslutningar.
     */
    public void stop() {
        try {
            for (ClientHandler client : clients.values()) {
                client.close();
            }
            clients.clear();
            
            pool.shutdown();
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            System.out.println("Server shut down");
        } catch (IOException e) {
            System.err.println("Error shutting down server: " + e.getMessage());
        }
    }
    
    /**
     * Registrerar en klient med användarnamn och publik nyckel.
     *
     * @param username Användarnamn för klienten som registreras.
     * @param handler ClientHandler för klienten.
     * @param publicKey Publik nyckel för klienten.
     */
    public void registerClient(String username, ClientHandler handler, String publicKey) {
        clients.put(username, handler);
        
        userPublicKeys.put(username, publicKey);
        
        broadcastMessage("JOIN " + username);
        
        // Send the new user's public key to all clients
        broadcastPublicKey(username, publicKey);
        
        System.out.println("User registered: " + username);
    }
    
    /**
     * Avregistrerar en klient med användarnamn.
     *
     * @param username Klienten om avregistrerats.
     */
    public void unregisterClient(String username) {
        clients.remove(username);
        broadcastMessage("LEAVE " + username);
        
        System.out.println("User unregistered: " + username);
    }
    
    /**
     * Skickar ett meddelande till alla anslutna klienter.
     *
     * @param message Meddelandet som ska skickas.
     */
    public void broadcastMessage(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }
    
    public void broadcastPublicKey(String username, String publicKey) {
        String keyMessage = "KEY " + username + " " + publicKey;
        for (ClientHandler client : clients.values()) {
            client.sendMessage(keyMessage);
        }
    }
    
    public void relayMessage(String sender, String encryptedMessage) {
        String fullMessage = "MSG " + sender + " " + encryptedMessage;
        
        for (ClientHandler client : clients.values()) {
            client.sendMessage(fullMessage);
        }
    }
    
    /**
     * Skickar alla registrerade publika nycklar till en specifik klient.
     *
     * @param handler ClientHandler för klienten som ska ta emot nycklarna.
     */
    public void sendAllPublicKeys(ClientHandler handler) {
        for (Map.Entry<String, String> entry : userPublicKeys.entrySet()) {
            handler.sendMessage("KEY " + entry.getKey() + " " + entry.getValue());
        }
    }
    
    /**
     * Huvudmetoden för att starta servern.
     *
     * @param args Argument för portnummer.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9494;
        
        try {
            ChatServer server = new ChatServer(port);
            
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            
            server.start();
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }
    
    /**
     * Hanterar kommunikationen med en enskild klient.
     */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final ChatServer server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = null;
        private boolean running = true;
        
        /**
         * Skapar en ny ClientHandler för en given socket och server.
         *
         * @param socket Klientens socket.
         * @param server Referens till ChatServer.
         */
        public ClientHandler(Socket socket, ChatServer server) {
            this.socket = socket;
            this.server = server;
        }
        
        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                String inputLine;
                while (running && (inputLine = in.readLine()) != null) {
                    processCommand(inputLine);
                }
            } catch (SocketException e) {
                System.out.println("Client disconnected: " + (username != null ? username : "unknown"));
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                close();
            }
        }
        
        /**
         * Håller koll på inkommande kommandon från klienten.
         *
         * @param input Inkommande kommando.
         */
        private void processCommand(String input) {
            try {
                StringTokenizer tokenizer = new StringTokenizer(input);
                if (!tokenizer.hasMoreTokens()) return;
                
                String command = tokenizer.nextToken();
                
                switch (command) {
                    case "REGISTER":
                        if (tokenizer.countTokens() >= 2) {
                            String name = tokenizer.nextToken();
                            
                            // ta public keyen
                            StringBuilder publicKey = new StringBuilder();
                            while (tokenizer.hasMoreTokens()) {
                                publicKey.append(tokenizer.nextToken());
                                if (tokenizer.hasMoreTokens()) {
                                    publicKey.append(" ");
                                }
                            }
                            
                            this.username = name;
                            server.registerClient(name, this, publicKey.toString());
                        }
                        break;
                        
                    case "JOIN":
                        if (tokenizer.hasMoreTokens()) {
                            String name = tokenizer.nextToken();
                            System.out.println("User joined: " + name);
                        }
                        break;
                        
                    case "LEAVE":
                        if (tokenizer.hasMoreTokens()) {
                            String name = tokenizer.nextToken();
                            server.unregisterClient(name);
                            running = false;
                        }
                        break;
                        
                    case "MSG":
                        if (tokenizer.countTokens() >= 2) {
                            String sender = tokenizer.nextToken();
                            
                            StringBuilder msgContent = new StringBuilder();
                            while (tokenizer.hasMoreTokens()) {
                                msgContent.append(tokenizer.nextToken());
                                if (tokenizer.hasMoreTokens()) {
                                    msgContent.append(" ");
                                }
                            }
                            
                            server.relayMessage(sender, msgContent.toString());
                        }
                        break;
                        
                    case "REQUEST_KEYS":
                        server.sendAllPublicKeys(this);
                        break;
                        
                    default:
                        System.out.println("Sorry what command was that? No no no!: " + command);
                }
            } catch (Exception e) {
                System.err.println("Error processing command: " + e.getMessage());
            }
        }
        
        /**
         * Skickar ett meddelande en klienten.
         *
         * @param message Meddelandet som ska skickas.
         */
        public void sendMessage(String message) {
            if (out != null && !socket.isClosed()) {
                out.println(message);
            }
        }
        
        /**
         * Stänger anslutningen till klienten.
         */
        public void close() {
            try {
                if (username != null) {
                    server.unregisterClient(username);
                }
                
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                
                running = false;
            } catch (IOException e) {
                System.err.println("Error closing client handler: " + e.getMessage());
            }
        }
    }
}