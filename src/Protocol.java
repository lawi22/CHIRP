import java.io.Serializable;

/**
 * Protokollklass för att definiera meddelandetyp och struktur för kommunikation.
 */
public class Protocol {
    // olika meddelanden
    public static final int MESSAGE_TYPE_CHAT = 1;
    public static final int MESSAGE_TYPE_KEY_EXCHANGE = 2;
    public static final int MESSAGE_TYPE_JOIN = 3;
    public static final int MESSAGE_TYPE_LEAVE = 4;
    
    /**
     * Meddelandeobjekt som används för att skicka olika typer av meddelanden.
     */
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int type;
        private String sender;
        private Object payload;
        
        /**
         * Skapar ett meddelande med den angivna typen, avsändaren och payload.
         *
         * @param type Typ av meddelande (exempelvis chat, nyckelbyte).
         * @param sender Avsändarens användarnamn.
         * @param payload Meddelandets data (kan vara text, nyckel, etc).
         */
        public Message(int type, String sender, Object payload) {
            this.type = type;
            this.sender = sender;
            this.payload = payload;
        }
        
        //Gets och sets
        public int getType() {
            return type;
        }
        
        public String getSender() {
            return sender;
        }
        
        public Object getPayload() {
            return payload;
        }
    }
    
    /**
     * Nyckelbytespayload som används för att överföra public keys.
     */
    public static class KeyExchangePayload implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private byte[] publicKey;
        
        /**
         * Skapar en en payload för key exchanges.
         *
         * @param publicKey Public keyen som ska skickas.
         */
        public KeyExchangePayload(byte[] publicKey) {
            this.publicKey = publicKey;
        }
        
        public byte[] getPublicKey() {
            return publicKey;
        }
    }
}