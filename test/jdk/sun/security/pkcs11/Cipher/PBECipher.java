/*
 * Copyright (c) 2022, Red Hat, Inc.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/*
 * @test
 * @bug 9999999
 * @summary test password based encryption on SunPKCS11's Cipher service
 * @requires (jdk.version.major >= 8)
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBECipher
 */

public final class PBECipher {
    public static void main(String[] args) throws Exception {
        java.security.Security.getProviders();
        PBECipher2.main(args);
    }
}

final class PBECipher2 extends PKCS11Test {
    private static final char[] password = "123456".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes();
    private static final byte[] iv = new byte[16];
    private static final int iterations = 1000;
    private static final String plainText = "This is a know plain text!";
    private static final String sep =
    "=========================================================================";

    private static enum Configuration {
        // Provide salt and iterations through a PBEParameterSpec instance
        PBEParameterSpec,

        // Provide salt and iterations through a AlgorithmParameters instance
        AlgorithmParameters,

        // Provide salt and iterations through an anonymous class implementing
        // the javax.crypto.interfaces.PBEKey interface
        AnonymousPBEKey,
    }

    private static Provider sunJCE = Security.getProvider("SunJCE");

    // Generated with SunJCE
    private static final Map<String, BigInteger> assertionData = Map.of(
            "PBEWithHmacSHA1AndAES_128", new BigInteger("8eebe98a580fb09d026" +
                    "dbfe60b3733b079e0de9ea7b0b1ccba011a1652d1e257", 16),
            "PBEWithHmacSHA224AndAES_128", new BigInteger("1cbabdeb5d483af4a" +
                    "841942f4b1095b7d6f60e46fabfd2609c015adc38cc227", 16),
            "PBEWithHmacSHA256AndAES_128", new BigInteger("4d82f6591df3508d2" +
                    "4531f06cdc4f90f4bdab7aeb07fbb57a3712e999d5b6f59", 16),
            "PBEWithHmacSHA384AndAES_128", new BigInteger("3a0ed0959d51f40b9" +
                    "ba9f506a5277f430521f2fbe1ba94bae368835f221b6cb9", 16),
            "PBEWithHmacSHA512AndAES_128", new BigInteger("1388287a446009309" +
                    "1418f4eca3ba1735b1fa025423d74ced36ce578d8ebf9da", 16),
            "PBEWithHmacSHA1AndAES_256", new BigInteger("80f8208daab27ed02dd" +
                    "8a354ef6f23ff7813c84dd1c8a1b081d6f4dee27182a2", 16),
            "PBEWithHmacSHA224AndAES_256", new BigInteger("7e3b9ce20aec2e52f" +
                    "f6c781602d4f79a55a88495b5217f1e22e1a068268e6247", 16),
            "PBEWithHmacSHA256AndAES_256", new BigInteger("9d6a8b6a351dfd0dd" +
                    "9e9f45924b2860dca7719c4c07e207a64ebc1acd16cc157", 16),
            "PBEWithHmacSHA384AndAES_256", new BigInteger("6f1b386cee3a8e2d9" +
                    "8c2e81828da0467dec8b989d22258efeab5932580d01d53", 16),
            "PBEWithHmacSHA512AndAES_256", new BigInteger("30aaa346b2edd394f" +
                    "50916187876ac32f1287b19d55c5eea6f7ef9b84aaf291e", 16)
            );

    private static final class NoRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            return;
        }
    }

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        for (Configuration conf : Configuration.values()) {
            testWith(sunPKCS11, "PBEWithHmacSHA1AndAES_128", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA224AndAES_128", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA256AndAES_128", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA384AndAES_128", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA512AndAES_128", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA1AndAES_256", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA224AndAES_256", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA256AndAES_256", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA384AndAES_256", conf);
            testWith(sunPKCS11, "PBEWithHmacSHA512AndAES_256", conf);
        }
        System.out.println("TEST PASS - OK");
    }

    private void testWith(Provider sunPKCS11, String algorithm,
            Configuration conf) throws Exception {
        System.out.println(sep + System.lineSeparator() + algorithm
                + " (with " + conf.name() + ")");

        Cipher pbeCipher = getCipher(sunPKCS11, algorithm, conf);
        BigInteger cipherText = new BigInteger(1, pbeCipher.doFinal(
                plainText.getBytes()));
        printByteArray("Cipher Text", cipherText);

        BigInteger expectedCipherText = null;
        if (sunJCE != null) {
            Cipher c = getCipher(sunJCE, algorithm, conf);
            if (c != null) {
                expectedCipherText = new BigInteger(1, c.doFinal(
                        plainText.getBytes()));
            } else {
                // Move to assertionData as it's unlikely that any of
                // the algorithms are available.
                sunJCE = null;
            }
        }
        if (expectedCipherText == null) {
            // If SunJCE or the algorithm are not available, assertionData
            // is used instead.
            expectedCipherText = assertionData.get(algorithm);
        }

        if (!cipherText.equals(expectedCipherText)) {
            printByteArray("Expected Cipher Text", expectedCipherText);
            throw new Exception("Expected Cipher Text did not match");
        }
    }

    private Cipher getCipher(Provider p, String algorithm,
            Configuration conf) throws Exception {
        Cipher pbeCipher = null;
        try {
            pbeCipher = Cipher.getInstance(algorithm, p);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        switch (conf) {
            case PBEParameterSpec, AlgorithmParameters -> {
                SecretKey key = getPasswordOnlyPBEKey();
                PBEParameterSpec paramSpec = new PBEParameterSpec(
                        salt, iterations, new IvParameterSpec(iv));
                switch (conf) {
                    case PBEParameterSpec -> {
                        pbeCipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
                    }
                    case AlgorithmParameters -> {
                        AlgorithmParameters algoParams =
                                AlgorithmParameters.getInstance("PBES2");
                        algoParams.init(paramSpec);
                        pbeCipher.init(Cipher.ENCRYPT_MODE, key, algoParams);
                    }
                }
            }
            case AnonymousPBEKey -> {
                SecretKey key = getPasswordSaltIterationsPBEKey();
                pbeCipher.init(Cipher.ENCRYPT_MODE, key, new NoRandom());
            }
        }
        return pbeCipher;
    }

    private static SecretKey getPasswordOnlyPBEKey() throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(password);
        SecretKeyFactory skFac = SecretKeyFactory.getInstance("PBE");
        SecretKey skey = skFac.generateSecret(keySpec);
        keySpec.clearPassword();
        return skey;
    }

    private static SecretKey getPasswordSaltIterationsPBEKey() {
        return new PBEKey() {
            public byte[] getSalt() { return salt.clone(); }
            public int getIterationCount() { return iterations; }
            public String getAlgorithm() { return "PBE"; }
            public String getFormat() { return "RAW"; }
            public char[] getPassword() { return null; } // unused in PBE Cipher
            public byte[] getEncoded() {
                byte[] passwdBytes = new byte[password.length];
                for (int i = 0; i < password.length; i++)
                    passwdBytes[i] = (byte) (password[i] & 0x7f);
                return passwdBytes;
            }
        };
    }

    private static void printByteArray(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    public static void main(String[] args) throws Exception {
        PBECipher2 test = new PBECipher2();
        Provider p = Security.getProvider("SunPKCS11-NSS-FIPS");
        if (p != null) {
            test.main(p);
        } else {
            main(test);
        }
    }
}
