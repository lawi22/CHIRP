import java.security.*;
import javax.crypto.*;

public class KeyGen {
    private final KeyPair signingKeys;
    private final SecretKey encryptionKey;
    
    public KeyGen() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        signingKeys = keyGen.generateKeyPair();
        
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        encryptionKey = keyGenerator.generateKey();
    }
    
    public KeyPair getSigningKeys() {
        return signingKeys;
    }
    
    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }
}