package mindustry.client.utils;
/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import arc.math.*;
import arc.struct.*;
import org.bouncycastle.jce.provider.*;

import java.nio.charset.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;

public class Crypto{
    private KeyPair keyPair;
    public State state;
    private KeyAgreement agreement;
    private SecretKeySpec aesKey;
    private IvParameterSpec iv;
    private Cipher cipher;
    public Array<String> keyAccumulator;
    public boolean isInitializing;

    public Crypto(boolean initializing) {
        this.isInitializing = initializing;
        if(initializing) {
            state = State.WAITING_FOR_RESPONSE;
            try{
                KeyPairGenerator dhKeyPairGen = KeyPairGenerator.getInstance("DH");
                dhKeyPairGen.initialize(512);
                keyPair = dhKeyPairGen.generateKeyPair();

                agreement = KeyAgreement.getInstance("DH");
                agreement.init(keyPair.getPrivate());
            } catch(NoSuchAlgorithmException | InvalidKeyException error) {
                error.printStackTrace();
            }
        }else{
            state = State.WAITING_FOR_KEY;
        }
    }

    public byte[] getDhKey() {
        return keyPair.getPublic().getEncoded();
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public byte[] respondToKey(byte[] key){
        try{
            KeyFactory keyFactory = KeyFactory.getInstance("DH");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(key);

            PublicKey incomingKey = keyFactory.generatePublic(x509KeySpec);

            /*
             * Bob gets the DH parameters associated with Alice's public key.
             * He must use the same parameters when he generates his own key
             * pair.
             */
            DHParameterSpec dhParamFromIncomingKey = ((DHPublicKey)incomingKey).getParams();

            // Bob creates his own DH key pair
            KeyPairGenerator outgoingKeyGen = KeyPairGenerator.getInstance("DH");
            outgoingKeyGen.initialize(dhParamFromIncomingKey);
            KeyPair outgoingKeyPair = outgoingKeyGen.generateKeyPair();

            // Bob creates and initializes his DH KeyAgreement object
            agreement = KeyAgreement.getInstance("DH");

            agreement.init(outgoingKeyPair.getPrivate());
            agreement.doPhase(incomingKey, true);

            return outgoingKeyPair.getPublic().getEncoded();
        } catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidAlgorithmParameterException | InvalidKeyException error) {
            error.printStackTrace();
            return null;
        }
    }

    public void receiveDhKey(byte[] key) {
        try{
            KeyFactory keyFactory = KeyFactory.getInstance("DH");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(key);
            PublicKey incomingKey = keyFactory.generatePublic(x509KeySpec);
            agreement.doPhase(incomingKey, true);
        } catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException error) {
            error.printStackTrace();
        }
    }

    public void generateAesKey() {
        try{
            byte[] sharedSecret = agreement.generateSecret();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sharedSecret);
            byte[] keyBytes = new byte[16];
            System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
            byte[] ivBytes = new byte[16];
            System.arraycopy(digest.digest(), 16, ivBytes, 0, keyBytes.length);
            aesKey = new SecretKeySpec(keyBytes, "AES");
            iv = new IvParameterSpec(ivBytes);

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            state = State.READY;
        } catch(NoSuchAlgorithmException | NoSuchPaddingException error) {
            error.printStackTrace();
        }
    }

    public static void main(String[] argv) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

//        /*
//         * Alice creates her own DH key pair with 2048-bit key size
//         */
//        System.out.println("ALICE: Generate DH keypair ...");
//        KeyPairGenerator aliceKpairGen = KeyPairGenerator.getInstance("DH");
//        aliceKpairGen.initialize(2048);
//        KeyPair aliceKpair = aliceKpairGen.generateKeyPair();
//
//        // Alice creates and initializes her DH KeyAgreement object
//        System.out.println("ALICE: Initialization ...");
//        KeyAgreement aliceKeyAgree = KeyAgreement.getInstance("DH");
//        aliceKeyAgree.init(aliceKpair.getPrivate());
//
//        // Alice encodes her public key, and sends it over to Bob.
//        byte[] alicePubKeyEnc = aliceKpair.getPublic().getEncoded();
//
//        /*
//         * Let's turn over to Bob. Bob has received Alice's public key
//         * in encoded format.
//         * He instantiates a DH public key from the encoded key material.
//         */
//        KeyFactory bobKeyFac = KeyFactory.getInstance("DH");
//        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(alicePubKeyEnc);
//
//        PublicKey alicePubKey = bobKeyFac.generatePublic(x509KeySpec);
//
//        /*
//         * Bob gets the DH parameters associated with Alice's public key.
//         * He must use the same parameters when he generates his own key
//         * pair.
//         */
//        DHParameterSpec dhParamFromAlicePubKey = ((DHPublicKey)alicePubKey).getParams();
//
//        // Bob creates his own DH key pair
//        System.out.println("BOB: Generate DH keypair ...");
//        KeyPairGenerator bobKpairGen = KeyPairGenerator.getInstance("DH");
//        bobKpairGen.initialize(dhParamFromAlicePubKey);
//        KeyPair bobKpair = bobKpairGen.generateKeyPair();
//
//        // Bob creates and initializes his DH KeyAgreement object
//        System.out.println("BOB: Initialization ...");
//        KeyAgreement bobKeyAgree = KeyAgreement.getInstance("DH");
//        bobKeyAgree.init(bobKpair.getPrivate());
//
//        // Bob encodes his public key, and sends it over to Alice.
//        byte[] bobPubKeyEnc = bobKpair.getPublic().getEncoded();
//
//        /*
//         * Alice uses Bob's public key for the first (and only) phase
//         * of her version of the DH
//         * protocol.
//         * Before she can do so, she has to instantiate a DH public key
//         * from Bob's encoded key material.
//         */
//        KeyFactory aliceKeyFac = KeyFactory.getInstance("DH");
//        x509KeySpec = new X509EncodedKeySpec(bobPubKeyEnc);
//        PublicKey bobPubKey = aliceKeyFac.generatePublic(x509KeySpec);
//        System.out.println("ALICE: Execute PHASE1 ...");
//        aliceKeyAgree.doPhase(bobPubKey, true);
//
//        /*
//         * Bob uses Alice's public key for the first (and only) phase
//         * of his version of the DH
//         * protocol.
//         */
//        System.out.println("BOB: Execute PHASE1 ...");
//        bobKeyAgree.doPhase(alicePubKey, true);
//
//        /*
//         * At this stage, both Alice and Bob have completed the DH key
//         * agreement protocol.
//         * Both generate the (same) shared secret.
//         */
//        byte[] aliceSharedSecret = aliceKeyAgree.generateSecret();
//        int aliceLen = aliceSharedSecret.length;
//        byte[] bobSharedSecret = new byte[aliceLen];
//        int bobLen = bobKeyAgree.generateSecret(bobSharedSecret, 0);
//        System.out.println("Alice secret: " +
//        toHexString(aliceSharedSecret));
//        System.out.println("Bob secret: " +
//        toHexString(bobSharedSecret));
//        if (!java.util.Arrays.equals(aliceSharedSecret, bobSharedSecret))
//            throw new Exception("Shared secrets differ");
//        System.out.println("Shared secrets are the same");
//
//        /*
//         * Now let's create a SecretKey object using the shared secret
//         * and use it for encryption. First, we generate SecretKeys for the
//         * "AES" algorithm (based on the raw shared secret data) and
//         * Then we use AES in CBC mode, which requires an initialization
//         * vector (IV) parameter. Note that you have to use the same IV
//         * for encryption and decryption: If you use a different IV for
//         * decryption than you used for encryption, decryption will fail.
//         *
//         * If you do not specify an IV when you initialize the Cipher
//         * object for encryption, the underlying implementation will generate
//         * a random one, which you have to retrieve using the
//         * javax.crypto.Cipher.getParameters() method, which returns an
//         * instance of java.security.AlgorithmParameters. You need to transfer
//         * the contents of that object (e.g., in encoded format, obtained via
//         * the AlgorithmParameters.getEncoded() method) to the party who will
//         * do the decryption. When initializing the Cipher for decryption,
//         * the (reinstantiated) AlgorithmParameters object must be explicitly
//         * passed to the Cipher.init() method.
//         */
//        System.out.println("Use shared secret as SecretKey object ...");
//        SecretKeySpec bobAesKey = new SecretKeySpec(bobSharedSecret, 0, 16, "AES");
//        SecretKeySpec aliceAesKey = new SecretKeySpec(aliceSharedSecret, 0, 16, "AES");
//
//        /*
//         * Bob encrypts, using AES in CBC mode
//         */
//        Cipher bobCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        bobCipher.init(Cipher.ENCRYPT_MODE, bobAesKey);
//        byte[] cleartext = "This is just an example".getBytes();
//        byte[] ciphertext = bobCipher.doFinal(cleartext);
//
//        // Retrieve the parameter that was used, and transfer it to Alice in
//        // encoded format
//        byte[] encodedParams = bobCipher.getParameters().getEncoded();
//
//        /*
//         * Alice decrypts, using AES in CBC mode
//         */
//
//        // Instantiate AlgorithmParameters object from parameter encoding
//        // obtained from Bob
//        AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
//        aesParams.init(encodedParams);
//        Cipher aliceCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        aliceCipher.init(Cipher.DECRYPT_MODE, aliceAesKey, aesParams);
//        byte[] recovered = aliceCipher.doFinal(ciphertext);
//        if (!java.util.Arrays.equals(cleartext, recovered))
//            throw new Exception("AES in CBC mode recovered text is " +
//            "different from cleartext");
//        System.out.println("AES in CBC mode recovered text is same as cleartext");
//        Crypto client0 = new Crypto(true);
//        Crypto client1 = new Crypto(false);
//        byte[] firstKey = client0.getDhKey();
//        byte[] secondKey = client1.respondToKey(firstKey);
//        client0.receiveDhKey(secondKey);
//
//        client0.generateAesKey();
//        client1.generateAesKey();
//
//        System.out.println(client0.state);
//        System.out.println(client1.state);
//
//        byte[] input = new byte[4096];
//        new Random().nextBytes(input);
//
//        byte[] encrypted = client0.encrypt(input);
//        System.out.println(Arrays.toString(encrypted));
//        System.out.println(Arrays.toString(input));
//        byte[] decrypted = client1.decrypt(encrypted);
//        System.out.println(Arrays.toString(decrypted));
//        System.out.println(Arrays.equals(input, decrypted));
//
//        String encryptedText = client0.encryptString("Hello!");
//        System.out.println(new String(client1.decrypt(encryptedText), StandardCharsets.UTF_8));


        Crypto client0 = new Crypto(true);
        Crypto client1 = new Crypto(false);

        Array<String> message = client0.getKey();
        Array<String> messages2 = client1.fromMessages(message);
        client0.fromMessages(messages2);
        client0.generateAesKey();
        client1.generateAesKey();
        System.out.println(client0.encryptString("AAAAAAAAA"));
        System.out.println(new String(client1.decrypt(client0.encryptString("AAAAAAAAA")), StandardCharsets.UTF_8));
    }

    public Array<String> getKey(){
        String key = Base256Coder.encode(getDhKey());
        Array<String> parts = new Array<>();
        for (int i = 0; i < key.length(); i += 100) {
            parts.add(key.substring(i, Math.min(i + 100, key.length())));
        }
        return parts;
    }

    public Array<String> fromMessages(Array<String> inp){
        StringBuilder builder = new StringBuilder();
        for(String s : inp){
            builder.append(s);
        }

        if(state == State.WAITING_FOR_KEY){
            Array<String> parts = new Array<>();
            String key = Base256Coder.encode(respondToKey(Base256Coder.decode(builder.toString())));
            for (int i = 0; i < key.length(); i += 100) {
                parts.add(key.substring(i, Math.min(i + 100, key.length())));
            }
            return parts;
        }else{
            receiveDhKey(Base256Coder.decode(builder.toString()));
            return null;
        }
    }

    public byte[] encrypt(byte[] input) {
        try{
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
            return cipher.doFinal(input);
        } catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException error) {
            error.printStackTrace();
            return null;
        }
    }

    public byte[] decrypt(byte[] input) {
        try{
            cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
            return cipher.doFinal(input);
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

    /*
     * Converts a byte to hex digit and writes to the supplied buffer
     */
    private static void byte2hex(byte b, StringBuffer buf) {
        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
        '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]);
        buf.append(hexChars[low]);
    }

    /*
     * Converts a byte array to hex string
     */
    private static String toHexString(byte[] block) {
        StringBuffer buf = new StringBuffer();
        int len = block.length;
        for (int i = 0; i < len; i++) {
            byte2hex(block[i], buf);
            if (i < len-1) {
                buf.append(":");
            }
        }
        return buf.toString();
    }

    @Override
    public String toString(){
        return "Client state: " + state + " ready: " + isReady();
    }
}

enum State {
    WAITING_FOR_KEY, WAITING_FOR_RESPONSE, READY;
}
