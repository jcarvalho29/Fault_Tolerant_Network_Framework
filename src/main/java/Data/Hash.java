package Data;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {
    private MessageDigest md;

    public Hash(String Algorithm){
        try {
            this.md = null;
            this.md = MessageDigest.getInstance(Algorithm);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void updateHash(byte[] info) {
        this.md.update(info);
    }

    public String extractHash() {
        // digest() method called
        // to calculate message digest of an inputCHECK HASH TIME => 24 ms

        // and return array of byte
        byte[] hash = this.md.digest();

        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 32)
        {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }
}
