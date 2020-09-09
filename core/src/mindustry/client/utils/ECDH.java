package mindustry.client.utils;

import org.whispersystems.curve25519.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

public class ECDH{
    Curve25519 cipher = Curve25519.getInstance(Curve25519.JAVA, new SecureRandomProvider());
    Curve25519KeyPair pair = cipher.generateKeyPair();
    private SecretKeySpec aesKey;
    private IvParameterSpec iv;
    private Cipher aesCipher;
    public boolean isReady = false;

    public byte[] getAgreement(byte[] otherKey){
        return cipher.calculateAgreement(otherKey, pair.getPrivateKey());
    }

    public byte[] getkey(){
        return pair.getPublicKey();
    }

    public void initializeAes(byte[] otherKey){
        try{
            byte[] sharedSecret = getAgreement(otherKey);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sharedSecret);
            byte[] keyBytes = new byte[16];
            System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
            byte[] ivBytes = new byte[16];
            System.arraycopy(digest.digest(), 16, ivBytes, 0, keyBytes.length);
            aesKey = new SecretKeySpec(keyBytes, "AES");
            iv = new IvParameterSpec(ivBytes);

            aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            isReady = true;
        }catch(NoSuchAlgorithmException | NoSuchPaddingException error){
            error.printStackTrace();
        }
    }

    public byte[] encrypt(byte[] input) {
        try{
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
            return aesCipher.doFinal(input);
        } catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException error) {
            error.printStackTrace();
            return null;
        }
    }

    public byte[] decrypt(byte[] input) {
        try{
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
            return aesCipher.doFinal(input);
        } catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException error) {
            error.printStackTrace();
            return null;
        }
    }

    public String encryptString(byte[] input) {
        return Base256Coder.encode(encrypt(input));
    }

    public String encryptString(String input) {
        return Base256Coder.encode(encrypt(input.getBytes(StandardCharsets.UTF_8)));
    }

    public byte[] decrypt(String input) {
        return decrypt(Base256Coder.decode(input));
    }

    public static void main(String[] args){
        ECDH client0 = new ECDH();
        ECDH client1 = new ECDH();

        client1.initializeAes(client0.getkey());
        System.out.println(client0.getkey().length);
        client0.initializeAes(client1.getkey());

        System.out.println(Arrays.toString(client1.decrypt(client0.encrypt(new byte[]{1, 2, 3}))));
    }
}

class SecureRandomProvider implements org.whispersystems.curve25519.SecureRandomProvider{
    SecureRandom random = new SecureRandom();

    @Override
    public void nextBytes(byte[] output){
        random.nextBytes(output);
    }

    @Override
    public int nextInt(int maxValue){
        return random.nextInt(maxValue);
    }
}
