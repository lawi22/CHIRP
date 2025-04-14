## CHIRP

Welcome to CHIRP, an encrypted chat app.
Messages are encrypted using AES (Advanced Encryption Standard) for secure communication.
Messages are signed using RSA to ensure authenticity and prevent tampering.
Multiple users can join the chat server and exchange messages in real time.
Public and private keys are generated and managed securely.


## Get started

1. Make sure to run the ChatServer.java file before launching or connecting to separate instance.
2. Run CHIRP.jar 

### Note on Key Management

Currently, CHIRP uses a shared key for encryption. While this is functional, the approach is not optimal for scalability and security in multi-user environments. A more robust Diffie-Hellman solution will be implemented in future updates.
