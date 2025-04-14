import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

/**
 * ChatClientUI hanterar användargränssnittet för chattklienten.
 */
public class ChatClientUI extends JFrame {
    private ChatClient client;
    private String username;
    private boolean running = true;
    
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    
    /**
     * Skapar en ny ChatClientUI och initialiserar den.
    */
    public ChatClientUI() {
        super("CHIRP");
        initGUI();
    }
    
    /**
     * Initlialiserar UI klienten.
    */
    private void initGUI() {
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        
        messageField = new JTextField();
        sendButton = new JButton("Send");
        
        // input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(inputPanel, BorderLayout.SOUTH);
        
        ActionListener sendAction = e -> {
            try {
                String message = messageField.getText();
                if (!message.isEmpty()) {
                    client.sendChatMessage(message);
                    displayMessage("You: " + message);
                    messageField.setText("");
                }
            } catch (Exception ex) {
                displayMessage("Error sending message: " + ex.getMessage());
            }
        };
        
        sendButton.addActionListener(sendAction);
        messageField.addActionListener(sendAction);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
    }
    
    /**
     * Ansluter klienten till en chattserver.
     * 
     * @param host Serverns adress
     * @param port Serverns port
     * @param username Användarnamn för anslutningen
     */
    public void connect(String host, int port, String username) {
        try {
            this.username = username;
            setTitle("CHIRP - " + username);
            
            PipedOutputStream pipeOut = new PipedOutputStream();
            PipedInputStream pipeIn = new PipedInputStream(pipeOut);
            
            PrintStream uiPrintStream = new PrintStream(pipeOut, true);
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            
            System.setOut(uiPrintStream);
            System.setErr(uiPrintStream);
            
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pipeIn))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        displayMessage(line);
                    }
                } catch (IOException e) {
                    originalErr.println("Error reading console output: " + e.getMessage());
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            }).start();
            
            // skapa klienten
            client = new ChatClient(host, port, username) {
                @Override
                public void close() {
                    super.close();
                    SwingUtilities.invokeLater(() -> {
                        messageField.setEnabled(false);
                        sendButton.setEnabled(false);
                    });
                }
            };
            
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            
        } catch (Exception e) {
            displayMessage("Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visar ett meddelande i chatten.
     * 
     * @param message Meddelandet som ska visas
     */
    private void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    /**
     * Stänger anslutningen till servern.
     */
    public void close() {
        try {
            running = false;
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    /**
     * Startar chattklienten och visar startdialogen.
     * 
     * @param args Kommandoradsargument som inte används
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClientUI ui = new ChatClientUI();
            
            JPanel loginPanel = new JPanel(new GridLayout(3, 2, 5, 5));
            
            JTextField usernameField = new JTextField();
            JTextField hostField = new JTextField("localhost");
            JTextField portField = new JTextField("9494");
            
            loginPanel.add(new JLabel("Username:"));
            loginPanel.add(usernameField);
            loginPanel.add(new JLabel("Host:"));
            loginPanel.add(hostField);
            loginPanel.add(new JLabel("Port:"));
            loginPanel.add(portField);
            
            int result = JOptionPane.showConfirmDialog(
                ui, loginPanel, "Login to Chat", 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            
            if (result == JOptionPane.OK_OPTION && !usernameField.getText().trim().isEmpty()) {
                String username = usernameField.getText().trim();
                String host = hostField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                
                ui.setVisible(true);
                ui.connect(host, port, username);
            } else {
                System.exit(0);
            }
        });
    }
}