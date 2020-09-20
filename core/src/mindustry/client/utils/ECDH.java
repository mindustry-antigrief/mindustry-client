package mindustry.client.utils;

import arc.util.Timer;
import mindustry.*;
import mindustry.client.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import org.whispersystems.curve25519.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

import static mindustry.Vars.*;
import static mindustry.client.Client.cachedKeys;

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
            System.out.println("READY!!!!");
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

    public static void handleMessage(Player playerSender, String message) {
        if (playerSender.name.equals(player.name)) {
            return;
        }

        if(message.contains("%K%")){
            boolean valid = message.split("%K%").length == 2;
            if(valid){
                String destination = Base256Coder.decodeString(message.split("%K%")[0]);
                String content = message.split("%K%")[1];
                if(destination.equals(player.name)){
                    if(!cachedKeys.containsKey(playerSender)){
                        cachedKeys.put(playerSender, new ECDH());
                        Timer.schedule(() -> {
                            MessageSystem.writeMessage(Base256Coder.encode(playerSender.name) + "%K%" + Base256Coder.encode(cachedKeys.get(playerSender).getkey()));
                        }, 2f);
//                        Call.sendChatMessage(Base256Coder.encode(playerSender.name) + "%K%" + Base256Coder.encode(cachedKeys.get(playerSender).getkey()));
                        cachedKeys.get(playerSender).initializeAes(Base256Coder.decode(content));
                    }else if(!cachedKeys.get(playerSender).isReady){
                        cachedKeys.get(playerSender).initializeAes(Base256Coder.decode(content));
                    }
                }
            }
        }

        if(message.contains("%ENC%") && message.split("%ENC%").length == 2){
            String destination = Base256Coder.decodeString(message.split("%ENC%")[0]);
            String ciphertext = message.split("%ENC%")[1];
            if(destination.equals(player.name)){
                if(cachedKeys.containsKey(playerSender)){
                    if(cachedKeys.get(playerSender).isReady){
                        ui.chatfrag.addMessage(new String(cachedKeys.get(playerSender).decrypt(ciphertext), StandardCharsets.UTF_8), playerSender.name, true);
                    }
                }
            }
        }
    }

    public static void main(String[] args){
        ECDH client0 = new ECDH();
        ECDH client1 = new ECDH();
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
