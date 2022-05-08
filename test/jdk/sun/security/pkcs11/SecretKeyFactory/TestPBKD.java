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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * @test
 * @bug 9999999
 * @summary test key derivation on SunPKCS11's SecretKeyFactory service
 * @requires (jdk.version.major >= 8)
 * @library /test/lib ..
 * @modules java.base/com.sun.crypto.provider:open
 * @run main/othervm/timeout=30 TestPBKD
 */

public final class TestPBKD {
    public static void main(String[] args) throws Exception {
        java.security.Security.getProviders();
        TestPBKD2.main(args);
    }
}

final class TestPBKD2 extends PKCS11Test {
    private static final char[] password = "123456".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes();
    private static final int iterations = 1000;
    private static final String sep =
    "=========================================================================";

    private static Provider sunJCE = Security.getProvider("SunJCE");

    // Generated with SunJCE
    private static final Map<String, BigInteger> assertionData =
            new HashMap<>() {{
                put("HmacPBESHA1", new BigInteger("5f7d1c360d1703cede76f47db" +
                        "2fa3facc62e7694", 16));
                put("HmacPBESHA224", new BigInteger("289563f799b708f522ab2a3" +
                        "8d283d0afa8fc1d3d227fcb9236c3a035", 16));
                put("HmacPBESHA256", new BigInteger("888defcf4ef37eb0647014a" +
                        "d172dd6fa3b3e9d024b962dba47608eea9b9c4b79", 16));
                put("HmacPBESHA384", new BigInteger("f5464b34253fadab8838d0d" +
                        "b11980c1787a99bf6f6304f2d8c942e30bada523494f9d5a0f3" +
                        "741e411de21add8b5718a8", 16));
                put("HmacPBESHA512", new BigInteger("18ae94337b132c68c611bc2" +
                        "e723ac24dcd44a46d900dae2dd6170380d4c34f90fef7bdeb5f" +
                        "6fddeb0d2230003e329b7a7eefcd35810d364ba95d31b68bb61" +
                        "e52", 16));
                put("PBEWithHmacSHA1AndAES_128", new BigInteger("fdb3dcc2e81" +
                        "244d4d56bf7ec8dd61dd7", 16));
                put("PBEWithHmacSHA224AndAES_128", new BigInteger("5ef9e5c6f" +
                        "df7c355f3b424233a9f24c2", 16));
                put("PBEWithHmacSHA256AndAES_128", new BigInteger("c5af597b0" +
                        "1b4f6baac8f62ff6f22bfb1", 16));
                put("PBEWithHmacSHA384AndAES_128", new BigInteger("c3208ebc5" +
                        "d6db88858988ec00153847d", 16));
                put("PBEWithHmacSHA512AndAES_128", new BigInteger("b27e8f7fb" +
                        "6a4bd5ebea892cd9a7f5043", 16));
                put("PBEWithHmacSHA1AndAES_256", new BigInteger("fdb3dcc2e81" +
                        "244d4d56bf7ec8dd61dd78a1b6fb3ad11d9ebd7f62027a2ccde" +
                        "98", 16));
                put("PBEWithHmacSHA224AndAES_256", new BigInteger("5ef9e5c6f" +
                        "df7c355f3b424233a9f24c2c9c41793cb0948b8ea3aac240b8d" +
                        "f64d", 16));
                put("PBEWithHmacSHA256AndAES_256", new BigInteger("c5af597b0" +
                        "1b4f6baac8f62ff6f22bfb1f319c3278c8b31cc616294716d4e" +
                        "ab08", 16));
                put("PBEWithHmacSHA384AndAES_256", new BigInteger("c3208ebc5" +
                        "d6db88858988ec00153847d5b1b7a8723640a022dc332bcaefe" +
                        "b356", 16));
                put("PBEWithHmacSHA512AndAES_256", new BigInteger("b27e8f7fb" +
                        "6a4bd5ebea892cd9a7f5043cefff9c38b07e599721e8d116189" +
                        "5482", 16));
                put("PBKDF2WithHmacSHA1", new BigInteger("fdb3dcc2e81244d4d5" +
                        "6bf7ec8dd61dd78a1b6fb3ad11d9ebd7f62027a2cc", 16));
                put("PBKDF2WithHmacSHA224", new BigInteger("5ef9e5c6fdf7c355" +
                        "f3b424233a9f24c2c9c41793cb0948b8ea3aac240b8df64d1a0" +
                        "736ec1c69eef1c7b2", 16));
                put("PBKDF2WithHmacSHA256", new BigInteger("c5af597b01b4f6ba" +
                        "ac8f62ff6f22bfb1f319c3278c8b31cc616294716d4eab080b9" +
                        "add9db34a42ceb2fea8d27adc00f4", 16));
                put("PBKDF2WithHmacSHA384", new BigInteger("c3208ebc5d6db888" +
                        "58988ec00153847d5b1b7a8723640a022dc332bcaefeb356995" +
                        "d076a949d35c42c7e1e1ca936c12f8dc918e497edf279a522b7" +
                        "c99580e2613846b3919af637da", 16));
                put("PBKDF2WithHmacSHA512", new BigInteger("b27e8f7fb6a4bd5e" +
                        "bea892cd9a7f5043cefff9c38b07e599721e8d1161895482da2" +
                        "55746844cc1030be37ba1969df10ff59554d1ac5468fa9b7297" +
                        "7bb7fd52103a0a7b488cdb8957616c3e23a16bca92120982180" +
                        "c6c11a4f14649b50d0ade3a", 16));
                }};

    static interface AssertData {
        BigInteger derive(String pbAlgo, PBEKeySpec keySpec) throws Exception;
    }

    static final class P12PBKDAssertData implements AssertData {
        private final int outLen;
        private final String kdfAlgo;
        private final int blockLen;

        P12PBKDAssertData(int outLen, String kdfAlgo, int blockLen) {
            this.outLen = outLen;
            this.kdfAlgo = kdfAlgo;
            this.blockLen = blockLen;
        }

        @Override
        public BigInteger derive(String pbAlgo, PBEKeySpec keySpec)
                throws Exception {
            // Since we need to access an internal SunJCE API, we use reflection
            Class<?> PKCS12PBECipherCore = Class.forName(
                    "com.sun.crypto.provider.PKCS12PBECipherCore");

            Field macKeyField = PKCS12PBECipherCore.getDeclaredField("MAC_KEY");
            macKeyField.setAccessible(true);
            int MAC_KEY = (int) macKeyField.get(null);

            Method deriveMethod = PKCS12PBECipherCore.getDeclaredMethod(
                    "derive", char[].class, byte[].class, int.class,
                    int.class, int.class, String.class, int.class);
            deriveMethod.setAccessible(true);

            return new BigInteger(1, (byte[]) deriveMethod.invoke(null,
                    keySpec.getPassword(), keySpec.getSalt(),
                    keySpec.getIterationCount(), this.outLen,
                    MAC_KEY, this.kdfAlgo, this.blockLen));
        }
    }

    static final class PBKD2AssertData implements AssertData {
        private final String kdfAlgo;
        private final int keyLen;

        PBKD2AssertData(String kdfAlgo, int keyLen) {
            // Key length is pinned by the algorithm name (not kdfAlgo,
            // but the algorithm under test: PBEWithHmacSHA*AndAES_*)
            this.kdfAlgo = kdfAlgo;
            this.keyLen = keyLen;
        }

        PBKD2AssertData(String kdfAlgo) {
            // Key length is variable for the algorithm under test
            // (kdfAlgo is the algorithm under test: PBKDF2WithHmacSHA*)
            this(kdfAlgo, -1);
        }

        @Override
        public BigInteger derive(String pbAlgo, PBEKeySpec keySpec)
                throws Exception {
            if (this.keyLen != -1) {
                keySpec = new PBEKeySpec(
                        keySpec.getPassword(), keySpec.getSalt(),
                        keySpec.getIterationCount(), this.keyLen);
            }
            if (sunJCE != null) {
                try {
                    return new BigInteger(1, SecretKeyFactory.getInstance(
                            this.kdfAlgo, sunJCE).generateSecret(keySpec)
                            .getEncoded());
                } catch (NoSuchAlgorithmException e) {
                    // Move to assertionData as it's unlikely that any of
                    // the algorithms are available.
                    sunJCE = null;
                }
            }
            // If SunJCE or the algorithm are not available, assertionData
            // is used instead.
            return assertionData.get(pbAlgo);
        }
    }

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        testWith(sunPKCS11, "HmacPBESHA1",
                new P12PBKDAssertData(20, "SHA-1", 64));
        testWith(sunPKCS11, "HmacPBESHA224",
                new P12PBKDAssertData(28, "SHA-224", 64));
        testWith(sunPKCS11, "HmacPBESHA256",
                new P12PBKDAssertData(32, "SHA-256", 64));
        testWith(sunPKCS11, "HmacPBESHA384",
                new P12PBKDAssertData(48, "SHA-384", 128));
        testWith(sunPKCS11, "HmacPBESHA512",
                new P12PBKDAssertData(64, "SHA-512", 128));

        testWith(sunPKCS11, "PBEWithHmacSHA1AndAES_128",
                new PBKD2AssertData("PBKDF2WithHmacSHA1", 128));
        testWith(sunPKCS11, "PBEWithHmacSHA224AndAES_128",
                new PBKD2AssertData("PBKDF2WithHmacSHA224", 128));
        testWith(sunPKCS11, "PBEWithHmacSHA256AndAES_128",
                new PBKD2AssertData("PBKDF2WithHmacSHA256", 128));
        testWith(sunPKCS11, "PBEWithHmacSHA384AndAES_128",
                new PBKD2AssertData("PBKDF2WithHmacSHA384", 128));
        testWith(sunPKCS11, "PBEWithHmacSHA512AndAES_128",
                new PBKD2AssertData("PBKDF2WithHmacSHA512", 128));
        testWith(sunPKCS11, "PBEWithHmacSHA1AndAES_256",
                new PBKD2AssertData("PBKDF2WithHmacSHA1", 256));
        testWith(sunPKCS11, "PBEWithHmacSHA224AndAES_256",
                new PBKD2AssertData("PBKDF2WithHmacSHA224", 256));
        testWith(sunPKCS11, "PBEWithHmacSHA256AndAES_256",
                new PBKD2AssertData("PBKDF2WithHmacSHA256", 256));
        testWith(sunPKCS11, "PBEWithHmacSHA384AndAES_256",
                new PBKD2AssertData("PBKDF2WithHmacSHA384", 256));
        testWith(sunPKCS11, "PBEWithHmacSHA512AndAES_256",
                new PBKD2AssertData("PBKDF2WithHmacSHA512", 256));

        // Use 1,5 * digest size as the testing derived key length (in bits)
        testWith(sunPKCS11, "PBKDF2WithHmacSHA1", 240,
                new PBKD2AssertData("PBKDF2WithHmacSHA1"));
        testWith(sunPKCS11, "PBKDF2WithHmacSHA224", 336,
                new PBKD2AssertData("PBKDF2WithHmacSHA224"));
        testWith(sunPKCS11, "PBKDF2WithHmacSHA256", 384,
                new PBKD2AssertData("PBKDF2WithHmacSHA256"));
        testWith(sunPKCS11, "PBKDF2WithHmacSHA384", 576,
                new PBKD2AssertData("PBKDF2WithHmacSHA384"));
        testWith(sunPKCS11, "PBKDF2WithHmacSHA512", 768,
                new PBKD2AssertData("PBKDF2WithHmacSHA512"));

        System.out.println("TEST PASS - OK");
    }

    private static void testWith(Provider sunPKCS11, String algorithm,
            AssertData assertData) throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations);
        testWith(sunPKCS11, algorithm, keySpec, assertData);
    }

    private static void testWith(Provider sunPKCS11, String algorithm,
            int keyLen, AssertData assertData) throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLen);
        testWith(sunPKCS11, algorithm, keySpec, assertData);
    }

    private static void testWith(Provider sunPKCS11, String algorithm,
            PBEKeySpec keySpec, AssertData assertData) throws Exception {
        System.out.println(sep + System.lineSeparator() + algorithm);

        SecretKeyFactory skFac = SecretKeyFactory.getInstance(
                algorithm, sunPKCS11);
        BigInteger derivedKey = new BigInteger(1,
                skFac.generateSecret(keySpec).getEncoded());
        printByteArray("Derived Key", derivedKey);

        BigInteger expectedDerivedKey = assertData.derive(algorithm, keySpec);

        if (!derivedKey.equals(expectedDerivedKey)) {
            printByteArray("Expected Derived Key", expectedDerivedKey);
            throw new Exception("Expected Derived Key did not match");
        }
    }

    private static void printByteArray(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    public static void main(String[] args) throws Exception {
        TestPBKD2 test = new TestPBKD2();
        Provider p = Security.getProvider("SunPKCS11-NSS-FIPS");
        if (p != null) {
            test.main(p);
        } else {
            main(test);
        }
    }
}
