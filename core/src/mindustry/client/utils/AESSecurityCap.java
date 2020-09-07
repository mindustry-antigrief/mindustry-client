package mindustry.client.utils;

import sun.security.ec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.util.*;

/** Shamelessly stolen (and modified) from @Jegan Babu on stackoverflow (https://stackoverflow.com/questions/21081713/diffie-hellman-key-exchange-in-java) */
public class AESSecurityCap {

    private PublicKey publickey;
    KeyAgreement keyAgreement;
    byte[] sharedsecret;
    public boolean hasOtherKey = false;

    String ALGO = "AES/CBC/PKCS5Padding";

    public AESSecurityCap() {
        makeKeyExchangeParams();
    }

    private void makeKeyExchangeParams() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            publickey = kp.getPublic();
            keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(kp.getPrivate());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void setReceiverPublicKey(PublicKey publickey) {
        try {
            keyAgreement.doPhase(publickey, true);
            sharedsecret = keyAgreement.generateSecret();
            hasOtherKey = true;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void setReceiverPublicKey(String publickey) {
        try{
            setReceiverPublicKey(new ECPublicKeyImpl(Base256Coder.decode(publickey)));
        }catch(InvalidKeyException e){
            e.printStackTrace();
        }
    }

    public String encrypt(String msg) {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, key, generateIv());
            byte[] encVal = c.doFinal(msg.getBytes());
            return Base256Coder.encode(encVal);
        } catch (BadPaddingException | InvalidKeyException | NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return msg;
    }

    public String decrypt(String encryptedData) {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key, generateIv());
            byte[] decordedValue = Base256Coder.decode(encryptedData);
            byte[] decValue = c.doFinal(decordedValue);
            return new String(decValue);
        } catch (BadPaddingException | InvalidKeyException | NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return encryptedData;
    }

    public PublicKey getPublickey() {
        return publickey;
    }

    protected Key generateKey() {
        return new SecretKeySpec(sharedsecret, "AES");
    }

    protected IvParameterSpec generateIv() {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new IvParameterSpec(Arrays.copyOfRange(digest.digest(), 0, 16));
        }catch(NoSuchAlgorithmException error){
            error.printStackTrace();
            return new IvParameterSpec(new byte[]{0});
        }
    }

    public static void main(String[] args){
        AESSecurityCap client1 = new AESSecurityCap();
        AESSecurityCap client2 = new AESSecurityCap();

        client1.setReceiverPublicKey(client2.getPublicKeyEncoded());
        System.out.println(client2.getPublicKeyEncoded());
        System.out.println(client2.getPublicKeyEncoded().length());
        client2.setReceiverPublicKey(client1.getPublicKeyEncoded());

        StringBuilder b = new StringBuilder();
        for(int i = 0; i < 100; i++){
            b.append("a");
//            System.out.println(client1.encrypt(b.toString()));
            System.out.print(client1.encrypt(b.toString()).length());
            System.out.println("  " + i);
        }
    }

    public String getPublicKeyEncoded(){
        return Base256Coder.encode(publickey.getEncoded());
    }
}
