package de.androidcrypto.talktoyourdesfirecard;

import static de.androidcrypto.talktoyourdesfirecard.Utils.printData;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;

public class DesfireAuthentication {

    /**
     * authentication codes taken from DESFireEv1.java (NFCJLIB)
     * Note: some minor modifications has been done to run the code without the rest of the library
     */

    private static final String TAG = DesfireAuthentication.class.getName();

    private IsoDep isoDep;

    // vars
    private boolean printToLog = true; // print data to log
    private int errorCode; // takes the result code
    private KeyType keyType;
    private Byte keyUsedForAuthentication;
    private byte[] initializationVector;
    private byte[] sessionKey;

    private String logData;

    public DesfireAuthentication(IsoDep isoDep, boolean printToLog) {
        this.isoDep = isoDep;
        this.printToLog = printToLog;
    }

    public boolean authenticateWithNfcjlibDes(byte[] key, byte keyNo) {
        clearData();
        log("authenticateWithNfcjlibDes", printData("key", key) + " keyNo: " + keyNo, true);
        //Log.d(TAG, "authenticateWithNfcjlibDes " + printData("key", key) + " keyNo: " + keyNo);
        try {
            return authenticate(key, keyNo, KeyType.DES);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticateWithNfcjlibTDes(byte[] key, byte keyNo) {
        log("authenticateWithNfcjlibTDes", printData("key", key) + " keyNo: " + keyNo, true);
        //Log.d(TAG, "authenticateWithNfcjlibTDes " + printData("key", key) + " keyNo: " + keyNo);
        try {
            return authenticate(key, keyNo, KeyType.TDES);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticateWithNfcjlibTkTDes(byte[] key, byte keyNo) {
        log("authenticateWithNfcjlibTkTDes", printData("key", key) + " keyNo: " + keyNo, true);
        //Log.d(TAG, "authenticateWithNfcjlibTkTDes " + printData("key", key) + " keyNo: " + keyNo);
        try {
            return authenticate(key, keyNo, KeyType.TKTDES);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticateWithNfcjlibAes(byte[] key, byte keyNo) {
        log("authenticateWithNfcjlibAes", printData("key", key) + " keyNo: " + keyNo, true);
        //Log.d(TAG, "authenticateWithNfcjlibAes " + printData("key", key) + " keyNo: " + keyNo);
        try {
            return authenticate(key, keyNo, KeyType.AES);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            return false;
        }
    }

    private enum KeyType {
        DES,
        TDES,
        TKTDES,
        AES;
    }

    /**
     * DES/3DES mode of operation.
     */
    private enum DESMode {
        SEND_MODE,
        RECEIVE_MODE;
    }

    // constants
    private final byte AUTHENTICATE_DES_2K3DES = (byte) 0x0A;
    private final byte AUTHENTICATE_3K3DES = (byte) 0x1A;
    private final byte AUTHENTICATE_AES	= (byte) 0xAA;


    /**
     * Mutual authentication between PCD and PICC.
     *
     * @param key	the secret key (8 bytes for DES, 16 bytes for 3DES/AES and
     * 				24 bytes for 3K3DES)
     * @param keyNo	the key number
     * @param type	the cipher
     * @return		true for success
     * @throws IOException
     */
    private boolean authenticate(byte[] key, byte keyNo, KeyType type) throws IOException {
        log("authenticate", printData("key", key) + " keyNo: " + keyNo + " keyType: " + type.toString(), true);
        //Log.d(TAG, "authenticate " + printData("key", key) + " keyNo: " + keyNo + " keyType: " + type.toString());
        if (!validateKey(key, type)) {
            throw new IllegalArgumentException();
        }
        if (type != KeyType.AES) {
            // remove version bits from Triple DES keys
            setKeyVersion(key, 0, key.length, (byte) 0x00);
        }
        final byte[] iv0 = type == KeyType.AES ? new byte[16] : new byte[8];
        byte[] apdu;
        byte[] responseAPDU;

        // 1st message exchange
        apdu = new byte[7];
        apdu[0] = (byte) 0x90;
        switch (type) {
            case DES:
            case TDES:
                apdu[1] = AUTHENTICATE_DES_2K3DES;
                break;
            case TKTDES:
                apdu[1] = AUTHENTICATE_3K3DES;
                break;
            case AES:
                apdu[1] = AUTHENTICATE_AES;
                break;
            default:
                assert false : type;
        }
        apdu[4] = 0x01;
        apdu[5] = keyNo;
        //responseAPDU = transmit(apdu);
        responseAPDU = isoDep.transceive(apdu);
        //this.code = getSW2(responseAPDU);
        errorCode = getSW2(responseAPDU);
        feedback(apdu, responseAPDU);
        if (getSW2(responseAPDU) != 0xAF)
            return false;

        //byte[] responseData = getData(responseAPDU);
        byte[] responseData = Arrays.copyOf(responseAPDU, responseAPDU.length - 2);

        // step 3
        byte[] randB = recv(key, getData(responseAPDU), type, iv0);
        if (randB == null)
            return false;
        byte[] randBr = rotateLeft(randB);
        byte[] randA = new byte[randB.length];

        //fillRandom(randA);
        randA = getRandomData(randA);

        // step 3: encryption
        byte[] plaintext = new byte[randA.length + randBr.length];
        System.arraycopy(randA, 0, plaintext, 0, randA.length);
        System.arraycopy(randBr, 0, plaintext, randA.length, randBr.length);
        byte[] iv1 = Arrays.copyOfRange(responseData,
                responseData.length - iv0.length, responseData.length);
        byte[] ciphertext = send(key, plaintext, type, iv1);
        if (ciphertext == null)
            return false;

        // 2nd message exchange
        apdu = new byte[5 + ciphertext.length + 1];
        apdu[0] = (byte) 0x90;
        apdu[1] = (byte) 0xAF;
        apdu[4] = (byte) ciphertext.length;
        System.arraycopy(ciphertext, 0, apdu, 5, ciphertext.length);
        //responseAPDU = transmit(apdu);
        responseAPDU = isoDep.transceive(apdu);

        //this.code = getSW2(responseAPDU);
        errorCode = getSW2(responseAPDU);
        feedback(apdu, responseAPDU);
        if (getSW2(responseAPDU) != 0x00)
            return false;

        // step 5
        byte[] iv2 = Arrays.copyOfRange(ciphertext,
                ciphertext.length - iv0.length, ciphertext.length);
        byte[] randAr = recv(key, getData(responseAPDU), type, iv2);
        if (randAr == null)
            return false;
        byte[] randAr2 = rotateLeft(randA);
        for (int i = 0; i < randAr2.length; i++)
            if (randAr[i] != randAr2[i])
                return false;

        // step 6
        byte[] skey = generateSessionKey(randA, randB, type);
        //Log.d(TAG, "The random A is " + Dump.hex(randA));
        Log.d(TAG, "The random A is " + Utils.bytesToHexNpeUpperCase(randA));
        //Log.d(TAG, "The random B is " + Dump.hex(randB));
        Log.d(TAG, "The random B is " + Utils.bytesToHexNpeUpperCase(randB));
        //Log.d(TAG, "The skey     is " + Dump.hex(skey));
        Log.d(TAG, "The skey     is " + Utils.bytesToHexNpeUpperCase(skey));
        //this.ktype = type;
        this.keyType = type;
        //this.kno = keyNo;
        this.keyUsedForAuthentication = keyNo;
        //this.iv = iv0;
        this.initializationVector = iv0;
        //this.skey = skey;
        this.sessionKey = skey;

        return true;
    }

    /**
     * Validates a key according with its type.
     *
     * @param key	the key
     * @param type	the type
     * @return		{@code true} if the key matches the type,
     * 				{@code false} otherwise
     */
    private boolean validateKey(byte[] key, KeyType type) {
        log("validateKey", printData("key", key) + " keyType: " + type.toString(), true);
        //Log.d(TAG, "validateKey " + printData("key", key) + " keyType: " + type.toString());
        if (type == KeyType.DES && (key.length != 8)
                || type == KeyType.TDES && (key.length != 16 || !isKey3DES(key))
                || type == KeyType.TKTDES && key.length != 24
                || type == KeyType.AES && key.length != 16) {
            Log.e(TAG, String.format("Key validation failed: length is %d and type is %s", key.length, type));
            return false;
        }
        return true;
    }

    /**
     * Checks whether a 16-byte key is a 3DES key.
     * <p>
     * Some 3DES keys may actually be DES keys because the LSBit of
     * each byte is used for key versioning by MDF. A 16-byte key is
     * also a DES key if the first half of the key is equal to the second.
     *
     * @param key	the 16-byte 3DES key to check
     * @return		<code>true</code> if the key is a 3DES key
     */
    private boolean isKey3DES(byte[] key) {
        log("isKey3DES", printData("key", key), true);
        //Log.d(TAG, "isKey3DES " + printData("key", key));
        if (key.length != 16)
            return false;
        byte[] tmpKey = Arrays.copyOfRange(key, 0, key.length);
        setKeyVersion(tmpKey, 0, tmpKey.length, (byte) 0x00);
        for (int i = 0; i < 8; i++)
            if (tmpKey[i] != tmpKey[i + 8])
                return true;
        return false;
    }

    /**
     * Returns a copy of the data bytes in the response body. If this APDU as
     * no body, this method returns a byte array with a length of zero.
     *
     * @return a copy of the data bytes in the response body or the empty
     *    byte array if this APDU has no body.
     */
    private byte[] getData(byte[] responseAPDU) {
        log("getData", printData("responseAPDU", responseAPDU), true);
        //Log.d(TAG, "getData " + printData("responseAPDU", responseAPDU));
        byte[] data = new byte[responseAPDU.length - 2];
        System.arraycopy(responseAPDU, 0, data, 0, data.length);
        return data;
    }

    /**
     * Returns the value of the status byte SW2 as a value between 0 and 255.
     *
     * @return the value of the status byte SW2 as a value between 0 and 255.
     */
    private int getSW2(byte[] responseAPDU) {
        log("getSW2", printData("responseAPDU", responseAPDU), true);
        //Log.d(TAG, "getSW2 " + printData("responseAPDU", responseAPDU));
        return responseAPDU[responseAPDU.length - 1] & 0xff;
    }

    private byte[] getRandomData(byte[] var) {
        log("getRandomData", printData("var", var), true);
        //Log.d(TAG, "getRandomData " + printData("var", var));
        int varLength = var.length;
        return getRandomData(varLength);
    }

    /**
     * generates a random 8 bytes long array
     *
     * @return 8 bytes long byte[]
     */
    private byte[] getRandomData(int length) {
        log("getRandomData", "length: " + length, true);
        //Log.d(TAG, "getRandomData " + " length: " + length);
        byte[] value = new byte[length];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(value);
        return value;
    }

    // rotate the array one byte to the left
    private byte[] rotateLeft(byte[] a) {
        log("rotateLeft", printData("a", a), true);
        byte[] ret = new byte[a.length];
        System.arraycopy(a, 1, ret, 0, a.length - 1);
        ret[a.length - 1] = a[0];
        return ret;
    }

    /**
     * Generate the session key using the random A generated by the PICC and
     * the random B generated by the PCD.
     *
     * @param randA	the random number A
     * @param randB	the random number B
     * @param type	the type of key
     * @return		the session key
     */
    private byte[] generateSessionKey(byte[] randA, byte[] randB, KeyType type) {
        log("generateSessionKey", printData("randA", randA) + printData(" randB", randB) + " keyType: " + type.toString(), true);
        //Log.d(TAG, "generateSessionKey " + printData("randA", randA) + printData(" randB", randB) + " keyType: " + type.toString());
        byte[] skey = null;

        switch (type) {
            case DES:
                skey = new byte[8];
                System.arraycopy(randA, 0, skey, 0, 4);
                System.arraycopy(randB, 0, skey, 4, 4);
                break;
            case TDES:
                skey = new byte[16];
                System.arraycopy(randA, 0, skey, 0, 4);
                System.arraycopy(randB, 0, skey, 4, 4);
                System.arraycopy(randA, 4, skey, 8, 4);
                System.arraycopy(randB, 4, skey, 12, 4);
                break;
            case TKTDES:
                skey = new byte[24];
                System.arraycopy(randA, 0, skey, 0, 4);
                System.arraycopy(randB, 0, skey, 4, 4);
                System.arraycopy(randA, 6, skey, 8, 4);
                System.arraycopy(randB, 6, skey, 12, 4);
                System.arraycopy(randA, 12, skey, 16, 4);
                System.arraycopy(randB, 12, skey, 20, 4);
                break;
            case AES:
                skey = new byte[16];
                System.arraycopy(randA, 0, skey, 0, 4);
                System.arraycopy(randB, 0, skey, 4, 4);
                System.arraycopy(randA, 12, skey, 8, 4);
                System.arraycopy(randB, 12, skey, 12, 4);
                break;
            default:
                assert false : type;  // never reached
        }
        return skey;
    }

    // Receiving data that needs decryption.
    private byte[] recv(byte[] key, byte[] data, KeyType type, byte[] iv) {
        log("recv", printData("key", key) + printData(" data", data) + " keyType: " + type.toString() + printData(" iv", iv), true);
        //Log.d(TAG, "recv " + printData("key", key) + printData(" data", data) + " keyType: " + type.toString() + printData(" iv", iv));
        switch (type) {
            case DES:
            case TDES:
                return decrypt(key, data, DESMode.RECEIVE_MODE);
            case TKTDES:
                return TripleDES.decrypt(iv == null ? new byte[8] : iv, key, data);
            case AES:
                return AES.decrypt(iv == null ? new byte[16] : iv, key, data);
            default:
                return null;
        }
    }

    // DES/3DES decryption: CBC send mode and CBC receive mode
    private byte[] decrypt(byte[] key, byte[] data, DESMode mode) {
        log("decrypt", printData("key", key) + printData(" data", data) + " DesMode: " + mode.toString(), true);
        //Log.d(TAG, "decrypt " + printData("key", key) + printData(" data", data) + " DesMode: " + mode.toString());
        byte[] modifiedKey = new byte[24];
        System.arraycopy(key, 0, modifiedKey, 16, 8);
        System.arraycopy(key, 0, modifiedKey, 8, 8);
        System.arraycopy(key, 0, modifiedKey, 0, key.length);

        /* MF3ICD40, which only supports DES/3DES, has two cryptographic
         * modes of operation (CBC): send mode and receive mode. In send mode,
         * data is first XORed with the IV and then decrypted. In receive
         * mode, data is first decrypted and then XORed with the IV. The PCD
         * always decrypts. The initial IV, reset in all operations, is all zeros
         * and the subsequent IVs are the last decrypted/plain block according with mode.
         *
         * MDF EV1 supports 3K3DES/AES and remains compatible with MF3ICD40.
         */
        byte[] ciphertext = new byte[data.length];
        byte[] cipheredBlock = new byte[8];

        switch (mode) {
            case SEND_MODE:
                // XOR w/ previous ciphered block --> decrypt
                for (int i = 0; i < data.length; i += 8) {
                    for (int j = 0; j < 8; j++) {
                        data[i + j] ^= cipheredBlock[j];
                    }
                    cipheredBlock = TripleDES.decrypt(modifiedKey, data, i, 8);
                    System.arraycopy(cipheredBlock, 0, ciphertext, i, 8);
                }
                break;
            case RECEIVE_MODE:
                // decrypt --> XOR w/ previous plaintext block
                cipheredBlock = TripleDES.decrypt(modifiedKey, data, 0, 8);
                // implicitly XORed w/ IV all zeros
                System.arraycopy(cipheredBlock, 0, ciphertext, 0, 8);
                for (int i = 8; i < data.length; i += 8) {
                    cipheredBlock = TripleDES.decrypt(modifiedKey, data, i, 8);
                    for (int j = 0; j < 8; j++) {
                        cipheredBlock[j] ^= data[i + j - 8];
                    }
                    System.arraycopy(cipheredBlock, 0, ciphertext, i, 8);
                }
                break;
            default:
                Log.e(TAG, "Wrong way (decrypt)");
                return null;
        }

        return ciphertext;
    }

    // IV sent is the global one but it is better to be explicit about it: can be null for DES/3DES
    // if IV is null, then it is set to zeros
    // Sending data that needs encryption.
    private byte[] send(byte[] key, byte[] data, KeyType type, byte[] iv) {
        log("send", printData("key", key) + printData(" data", data) + " keyType: " + type.toString() + printData(" iv", iv), true);
        //Log.d(TAG, "send " + printData("key", key) + printData(" data", data) + " keyType: " + type.toString() + printData(" iv", iv));
        switch (type) {
            case DES:
            case TDES:
                return decrypt(key, data, DESMode.SEND_MODE);
            case TKTDES:
                return TripleDES.encrypt(iv == null ? new byte[8] : iv, key, data);
            case AES:
                return AES.encrypt(iv == null ? new byte[16] : iv, key, data);
            default:
                return null;
        }
    }

    // feedback/debug: a request-response round
    private void feedback(byte[] command, byte[] response) {
        log("feedback", printData("command", command) + printData(" response", response), true);
        //Log.d(TAG, "feedback " + printData("command", command) + printData(" response", response));
        //if(print) {
        /*
        if(Mprint) {
            Log.d(TAG, "---> " + getHexString(command, true) + " (" + command.length + ")");
        }
        //if(print) {
        if(Mprint) {
            Log.d(TAG, "<--- " + getHexString(response, true) + " (" + response.length + ")");
        }
        */
        log("feedback", "---> " + getHexString(command, true) + " (" + command.length + ")", false);
        log("feedback", "<--- " + getHexString(response, true) + " (" + response.length + ")", false);
    }

    private String getHexString(byte[] a, boolean space) {
        log("getHexString", printData("a", a) + " space: " + space, true);
        //Log.d(TAG, "getHexString " + printData("a", a) + " space: " + space);
        StringBuilder sb = new StringBuilder();
        for (byte b : a) {
            sb.append(String.format("%02x", b & 0xff));
            if(space) {
                sb.append(' ');
            }
        }
        return sb.toString().trim().toUpperCase();
    }

    /**
     * Note on all KEY data (important for DES/TDES keys only)
     * A DES key has a length 64 bits (= 8 bytes) but only 56 bits are used for encryption, the remaining 8 bits are were
     * used as parity bits and within DESFire as key version information.
     * If you are using the 'original' key you will run into authentication issues.
     * You should always strip of the parity bits by running the setKeyVersion command
     * e.g. setKeyVersion(AID_DesLog_Key2_New, 0, AID_DesLog_Key2_New.length, (byte) 0x00);
     * This will set the key version to '0x00' by setting all parity bits to '0x00'
     */

    /**
     * Set the version on a DES key. Each least significant bit of each byte of
     * the DES key, takes one bit of the version. Since the version is only
     * one byte, the information is repeated if dealing with 16/24-byte keys.
     *
     * @param a       1K/2K/3K 3DES
     * @param offset  start position of the key within a
     * @param length  key length
     * @param version the 1-byte version
     */
    // source: nfcjLib
    private void setKeyVersion(byte[] a, int offset, int length, byte version) {
        log("setKeyVersion", printData("a", a) + " offset: " + offset + " length: " + length + " version: " + version, true);
        //Log.d(TAG, "setKeyVersion " + printData("a", a) + " offset: " + offset + " length: " + length + " version: " + version);
        if (length == 8 || length == 16 || length == 24) {
            for (int i = offset + length - 1, j = 0; i >= offset; i--, j = (j + 1) % 8) {
                a[i] &= 0xFE;
                a[i] |= ((version >>> j) & 0x01);
            }
        }
    }

    /**
     * END authentication codes taken from DESFireEv1.java (NFCJLIB)
     */

    private void clearData() {
        logData = "";
        keyUsedForAuthentication = (byte) 0xff;
        errorCode = -1;
        sessionKey = null;
        initializationVector = null;
    }


    private void log(String methodName, String data, boolean isMethodHeader) {
        if (printToLog) {
            logData += "\n" + "method: " + methodName + ":" + data;
            Log.d(TAG, "method: " + methodName + ":" + data);
        }
    }

    public String getLogData() {
        return logData;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public Byte getKeyUsedForAuthentication() {
        return keyUsedForAuthentication;
    }

    public String getKeyTypeString() {
        return keyType.toString();
    }

    public byte[] getInitializationVector() {
        return initializationVector;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

}
