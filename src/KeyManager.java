import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.*;

/**
 * Hanterar generering och lagring av kryptografiska nycklar.
 */
public class KeyManager {
    private KeyPair signingKeys;
    private SecretKey encryptionKey;
    
    /**
     * Skapar en ny KeyManager och genererar nycklar för signering och kryptering.
     *
     * @throws NoSuchAlgorithmException Om algoritmen för nyckelgenerering inte stöds.
     */
    public KeyManager() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        signingKeys = keyGen.generateKeyPair();
        
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        encryptionKey = keyGenerator.generateKey();
    }
    
    /**
     * Returnerar nyckelparet för signering.
     *
     * @return Nyckelparet för signering.
     */
    public KeyPair getSigningKeys() {
        return signingKeys;
    }
    
    /**
     * Returnerar hemliga nyckeln för kryptering.
     *
     * @return Hemliga nyckeln för kryptering.
     */
    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }
    
    public static PublicKey getPublicKeyFromBytes(byte[] keyBytes) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return keyFactory.generatePublic(keySpec);
    }
}