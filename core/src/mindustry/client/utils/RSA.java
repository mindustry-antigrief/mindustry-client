package mindustry.client.utils;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.security.*;
import java.util.Base64;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RSA {
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(512, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        return pair;
    }

    public static String encrypt(String plainText, PublicKey publicKey) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] cipherText = encryptCipher.doFinal(plainText.getBytes(UTF_8));

        return Base64.getEncoder().encodeToString(cipherText);
    }

    public static String decrypt(String cipherText, PrivateKey privateKey) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(cipherText);

        Cipher decriptCipher = Cipher.getInstance("RSA");
        decriptCipher.init(Cipher.DECRYPT_MODE, privateKey);

        return new String(decriptCipher.doFinal(bytes), UTF_8);
    }

    public static String sign(String plainText, PrivateKey privateKey) throws Exception {
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes(UTF_8));

        byte[] signature = privateSignature.sign();

        return Base64.getEncoder().encodeToString(signature);
    }

    public static boolean verify(String plainText, String signature, PublicKey publicKey) throws Exception {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(plainText.getBytes(UTF_8));

        byte[] signatureBytes = Base64.getDecoder().decode(signature);

        return publicSignature.verify(signatureBytes);
    }

    public static void main(String... argv) throws Exception {
        //First generate a public/private key pair
        KeyPair pair = generateKeyPair();
        //KeyPair pair = getKeyPairFromKeyStore();

        //Our secret message
        String message = "the answer to life the universe and everything";

        //Encrypt the message
        String cipherText = encrypt(message, pair.getPublic());

        //Now decrypt it
        String decipheredMessage = decrypt(cipherText, pair.getPrivate());

        System.out.println(decipheredMessage);

        //Let's sign our message
        String signature = sign("foobar", pair.getPrivate());

        //Let's check the signature
        boolean isCorrect = verify("foobar", signature, pair.getPublic());
        System.out.println("Signature correct: " + isCorrect);
    }
}
