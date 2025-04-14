import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *  Hanterar anslutningen till en chattserver, skickar och tar emot meddelanden.
 */
public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private KeyManager keyManager;
    private String username;
    private boolean running = true;
    private Map<String, PublicKey> userPublicKeys = new ConcurrentHashMap<>();
    private static final int GCM_TAG_LENGTH = 16;
    
    /**
     * Skapar en ny ChatClient och ansluter till servern.
     *
     * @param host Serverns värdnamn eller IP-adress.
     * @param port Serverns portnummer.
     * @param username Användarnamn för klienten.
     * @throws Exception Om ett fel uppstår vid anslutning eller nyckelhantering.
     */
    public ChatClient(String host, int port, String username) throws Exception {
        this.username = username;
        
        keyManager = new KeyManager();
        
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        registerWithServer();
        
        new Thread(this::receiveMessages).start();
    }
    /**
     * Registrerar klienten med servern.
     *
     * @throws Exception Om ett fel uppstår vid registrering.
     */
    private void registerWithServer() throws Exception {
        // konvertera publik nyckel till b64-sträng
        byte[] publicKeyBytes = keyManager.getSigningKeys().getPublic().getEncoded();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        
        // skciak registreringsmeddelande med användarnamn och publik nyckel
        requestPublicKeys();
        out.println("REGISTER " + username + " " + publicKeyBase64 );
        System.out.println("Registered with server as " + username);
        
        // Anslut till chatten
        sendJoinMessage();
    }
    
    private void sendJoinMessage() {
        out.println("JOIN " + username);
        System.out.println("Joined chat as " + username);
    }

    /**
     * Skickar ett meddelande till chatten.
     *
     * @param messageText Meddelandetexten som ska skickas.
     * @throws Exception Om ett fel uppstår vid kryptering eller sändning.
     */
    public void sendChatMessage(String messageText) throws Exception {
        Protocol.Message message = new Protocol.Message(Protocol.MESSAGE_TYPE_CHAT, username, messageText);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(message);
        oos.close();
        
        // signera + kryptera
        String encryptedMessage = encryptAndSignMessage(bos.toByteArray());
        
        out.println("MSG " + username + " " + encryptedMessage);
    }
    
    /**
     * Krypterar och signerar ett meddelande.
     *
     * @param messageBytes Meddelandets bytearray.
     * @return Det krypterade och signerade meddelandet som Base64-sträng.
     * @throws Exception Om ett fel uppstår vid kryptering eller signering.
     */
    private String encryptAndSignMessage(byte[] messageBytes) throws Exception {
        // signera med användarens privata key
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyManager.getSigningKeys().getPrivate());
        signature.update(messageBytes);
        byte[] signatureBytes = signature.sign();
        
        // kombinera
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        
        dos.writeInt(messageBytes.length);
        dos.write(messageBytes);
        dos.writeInt(signatureBytes.length);
        dos.write(signatureBytes);
        dos.close();
        
        byte[] combinedData = bos.toByteArray();
        
        // shared secret mellan klienter
        SecretKeySpec sharedKey = new SecretKeySpec("ThisIsASharedSecretKeyFor256Bits".getBytes(), "AES");
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, parameterSpec);
        
        byte[] encryptedData = cipher.doFinal(combinedData);
        
        ByteArrayOutputStream encryptedOutput = new ByteArrayOutputStream();
        encryptedOutput.write(iv);
        encryptedOutput.write(encryptedData);
        
        return Base64.getEncoder().encodeToString(encryptedOutput.toByteArray());
    }
    
    /**
     * Avkrypterar och verifierar ett meddelande från en avsändare.
     *
     * @param sender Den användare som skickade meddelandet.
     * @param encryptedBase64 Det krypterade meddelandet som Base64-sträng.
     * @return Det avkrypterade meddelandet som bytearray.
     * @throws Exception Om ett fel uppstår vid dekryptering eller verifiering.
     */
    private byte[] decryptAndVerifyMessage(String sender, String encryptedBase64) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
    
        byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, 12);
        byte[] encryptedData = Arrays.copyOfRange(encryptedBytes, 12, encryptedBytes.length);
    
        SecretKeySpec sharedKey = new SecretKeySpec("ThisIsASharedSecretKeyFor256Bits".getBytes(), "AES");
    
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, parameterSpec);
        byte[] decryptedBytes = cipher.doFinal(encryptedData);
        
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decryptedBytes));
        
        int messageLength = dis.readInt();
        byte[] messageBytes = new byte[messageLength];
        dis.readFully(messageBytes);
        
        int signatureLength = dis.readInt();
        byte[] signatureBytes = new byte[signatureLength];
        dis.readFully(signatureBytes);
        
        PublicKey senderPublicKey = userPublicKeys.get(sender);
        if (senderPublicKey != null) 
        {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(senderPublicKey);
            signature.update(messageBytes);
            
            boolean verified = signature.verify(signatureBytes);
            if (!verified) 
            {
                throw new SecurityException("Message signature verification failed!");
            }
        } else 
        {
            System.out.println("Warning: Cannot verify message from " + sender + " - public key not available");
        }
        
        return messageBytes;
    }
    
    /**
     * Lyssnar på och hanterar inkommande meddelanden från servern.
     */
    private void receiveMessages() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                processMessage(line);
            }
        } catch (SocketTimeoutException e) {
            if (running) {
                receiveMessages();
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Error receiving messages: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                    reconnect();
                } catch (Exception reconnectError) {
                    System.err.println("Failed to reconnect: " + reconnectError.getMessage());
                }
            }
        }
    }
    
    /**
     * Försöker att återansluta till servern om anslutningen förloras.
     *
     * @throws Exception Om ett fel uppstår vid återanslutning.
     */
    private void reconnect() throws Exception {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            
            socket = new Socket(socket.getInetAddress().getHostName(), socket.getPort());
            socket.setSoTimeout(30000); //test med 30 sec timeout
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            registerWithServer();
            
            System.out.println("Reconnected to server");
        } catch (Exception e) {
            System.err.println("Reconnection failed: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Bearbetar inkommande meddelanden.
     *
     * @param message Inkommande meddelande från servern.
     */
    private void processMessage(String message) {
        try {
            StringTokenizer tokenizer = new StringTokenizer(message);
            if (!tokenizer.hasMoreTokens()) return;
            
            String command = tokenizer.nextToken();
            
            switch (command) {
                case "MSG":
                    if (tokenizer.hasMoreTokens()) {
                        String sender = tokenizer.nextToken();
                        
                        StringBuilder msgContent = new StringBuilder();
                        while (tokenizer.hasMoreTokens()) {
                            msgContent.append(tokenizer.nextToken());
                            if (tokenizer.hasMoreTokens()) msgContent.append(" ");
                        }
                        
                        try {
                            byte[] decryptedBytes = decryptAndVerifyMessage(sender, msgContent.toString());
                            
                            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(decryptedBytes));
                            Protocol.Message msgObj = (Protocol.Message) ois.readObject();
                            ois.close();
                            
                            if (msgObj.getType() == Protocol.MESSAGE_TYPE_CHAT) {
                                String decryptedMsg = (String) msgObj.getPayload();
                                System.out.println(sender + " (verifierad): " + decryptedMsg);
                            }
                        } catch (Exception e) {
                            System.out.println(sender + " (ej verifierad): " + msgContent.toString());
                            System.err.println("Decryption error: " + e.getMessage());
                        }
                    }
                    break;
                
                case "JOIN":
                    if (tokenizer.hasMoreTokens()) {
                        String joinedUser = tokenizer.nextToken();
                        System.out.println(joinedUser + " has joined the chat");
                    }
                    break;
                
                case "LEAVE":
                    if (tokenizer.hasMoreTokens()) {
                        String leftUser = tokenizer.nextToken();
                        System.out.println(leftUser + " has left the chat");
                        // radera nyckeln när user lämnar
                        userPublicKeys.remove(leftUser);
                    }
                    break;
                
                case "KEY":
                    if (tokenizer.countTokens() >= 2) {
                        String keyUser = tokenizer.nextToken();
                        String publicKeyBase64 = tokenizer.nextToken();

                        // konvertera
                        try {
                            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
                            PublicKey publicKey = keyFactory.generatePublic(keySpec);
                            
                            // spar
                            userPublicKeys.put(keyUser, publicKey);
                            System.out.println("Received public key from " + keyUser + ": welcome in");
                        } catch (Exception e) {
                            System.err.println("Error processing public key from " + keyUser + ": " + e.getMessage());
                        }
                    }
                    break;
                
                default:
                    System.out.println("Server: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }

    /**
     * Stänger anslutningen till servern.
     */
    public void close() {
        try {
            running = false;
            
            out.println("LEAVE " + username);
            
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    

    /**
     * Startar klienten, begär användarnamn och ansluter till servern.
     *
     * @param args Kommandoradsargument (host, port).
     */
    public static void main(String[] args) {
        ChatClient client = null;
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            
            // Försök ansluta till servern, standardvärden om inga argument ges
            String host = args.length > 0 ? args[0] : "atlas.dsv.su.se";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 9494;
            
            client = new ChatClient(host, port, username);
            
            System.out.println("Connected to chat server. Type messages below:");
            System.out.println("Type '/quit' to exit");
            
            while (scanner.hasNextLine()) {
                String messageText = scanner.nextLine();
                
                if (messageText.equals("/quit")) {
                    break;
                }
                
                client.sendChatMessage(messageText);
            }
            
            scanner.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * Begär serverns offentliga nycklar.
     */
    private void requestPublicKeys() {
        out.println("REQUEST_KEYS");
    }
}