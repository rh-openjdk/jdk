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
import java.lang.ReflectiveOperationException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/*
 * @test
 * @bug 9999999
 * @summary test key derivation on a SunPKCS11 SecretKeyFactory service
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
    private static final String sep = "======================================" +
            "===================================";

    private enum Configuration {
        // Pass password, salt and iterations to a
        // SecretKeyFactory through a PBEKeySpec.
        PBEKeySpec,

        // Pass password, salt and iterations and iterations to a
        // SecretKeyFactory through an anonymous class implementing
        // the javax.crypto.interfaces.PBEKey interface.
        AnonymousPBEKey,
    }

    private static Provider sunJCE = Security.getProvider("SunJCE");

    private static BigInteger i(byte[] data) {
        return new BigInteger(1, data);
    }

    private record AssertionData(String algo, PBEKeySpec keySpec,
            BigInteger expectedKey) {}

    private static AssertionData p12PBKDAssertionData(String algo,
            char[] password, int keyLen, String hashAlgo, int blockLen,
            String staticExpectedKey) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLen);
        BigInteger expectedKey;
        try {
            // Since we need to access an internal
            // SunJCE API, we use reflection.
            Class<?> PKCS12PBECipherCore = Class.forName(
                    "com.sun.crypto.provider.PKCS12PBECipherCore");

            Field macKeyField =
                    PKCS12PBECipherCore.getDeclaredField("MAC_KEY");
            macKeyField.setAccessible(true);
            int MAC_KEY = (int) macKeyField.get(null);

            Method deriveMethod = PKCS12PBECipherCore.getDeclaredMethod(
                    "derive", char[].class, byte[].class, int.class,
                    int.class, int.class, String.class, int.class);
            deriveMethod.setAccessible(true);
            expectedKey = i((byte[]) deriveMethod.invoke(null,
                    keySpec.getPassword(), keySpec.getSalt(),
                    keySpec.getIterationCount(), keySpec.getKeyLength() / 8,
                    MAC_KEY, hashAlgo, blockLen));
        } catch (ReflectiveOperationException ignored) {
            expectedKey = new BigInteger(staticExpectedKey, 16);
        }
        return new AssertionData(algo, keySpec, expectedKey);
    }

    private static AssertionData pbkd2AssertionData(String algo,
            char[] password, int keyLen, String kdfAlgo,
            String staticExpectedKey) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLen);
        BigInteger expectedKey = null;
        if (sunJCE != null) {
            try {
                expectedKey = i(SecretKeyFactory.getInstance(kdfAlgo, sunJCE)
                        .generateSecret(keySpec).getEncoded());
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                // Move to staticExpectedKey as it's unlikely
                // that any of the algorithms are available.
                sunJCE = null;
            }
        }
        if (expectedKey == null) {
            expectedKey = new BigInteger(staticExpectedKey, 16);
        }
        return new AssertionData(algo, keySpec, expectedKey);
    }

    private static final char[] pwd = "123456".toCharArray();
    private static final char[] emptyPwd = new char[0];
    private static final byte[] salt = "abcdefgh".getBytes();
    private static final int iterations = 1000;

    // Generated with SunJCE. Keep a reference to some
    // entries for tests executing invalid conditions.
    private static final AssertionData hmacPBESHA1Data =
            p12PBKDAssertionData("HmacPBESHA1", pwd, 160, "SHA-1", 64,
                    "5f7d1c360d1703cede76f47db2fa3facc62e7694");
    private static final AssertionData hmacPBESHA224Data =
            p12PBKDAssertionData("HmacPBESHA224", pwd, 224, "SHA-224", 64,
                    "289563f799b708f522ab2a38d283d0afa8fc1d3d227fcb9236c3a035");
    private static final AssertionData pbeWithHmacSHA512AndAES256Data =
            pbkd2AssertionData("PBEWithHmacSHA512AndAES_256", pwd, 256,
                    "PBKDF2WithHmacSHA512", "b27e8f7fb6a4bd5ebea892cd9a7f5043" +
                    "cefff9c38b07e599721e8d1161895482");
    private static final AssertionData pbkdf2WithHmacSHA256Data =
            pbkd2AssertionData("PBKDF2WithHmacSHA256", pwd, 384,
                    "PBKDF2WithHmacSHA256", "c5af597b01b4f6baac8f62ff6f22bfb1" +
                    "f319c3278c8b31cc616294716d4eab080b9add9db34a42ceb2fea8d2" +
                    "7adc00f4");
    private static final AssertionData[] assertionData = new AssertionData[]{
            hmacPBESHA1Data,
            hmacPBESHA224Data,
            p12PBKDAssertionData("HmacPBESHA256", pwd, 256, "SHA-256", 64,
                    "888defcf4ef37eb0647014ad172dd6fa3b3e9d024b962dba47608eea" +
                    "9b9c4b79"),
            p12PBKDAssertionData("HmacPBESHA384", pwd, 384, "SHA-384", 128,
                    "f5464b34253fadab8838d0db11980c1787a99bf6f6304f2d8c942e30" +
                    "bada523494f9d5a0f3741e411de21add8b5718a8"),
            p12PBKDAssertionData("HmacPBESHA512", pwd, 512, "SHA-512", 128,
                    "18ae94337b132c68c611bc2e723ac24dcd44a46d900dae2dd6170380" +
                    "d4c34f90fef7bdeb5f6fddeb0d2230003e329b7a7eefcd35810d364b" +
                    "a95d31b68bb61e52"),
            pbkd2AssertionData("PBEWithHmacSHA1AndAES_128", pwd, 128,
                    "PBKDF2WithHmacSHA1", "fdb3dcc2e81244d4d56bf7ec8dd61dd7"),
            pbkd2AssertionData("PBEWithHmacSHA224AndAES_128", pwd, 128,
                    "PBKDF2WithHmacSHA224", "5ef9e5c6fdf7c355f3b424233a9f24c2"),
            pbkd2AssertionData("PBEWithHmacSHA256AndAES_128", pwd, 128,
                    "PBKDF2WithHmacSHA256", "c5af597b01b4f6baac8f62ff6f22bfb1"),
            pbkd2AssertionData("PBEWithHmacSHA384AndAES_128", pwd, 128,
                    "PBKDF2WithHmacSHA384", "c3208ebc5d6db88858988ec00153847d"),
            pbkd2AssertionData("PBEWithHmacSHA512AndAES_128", pwd, 128,
                    "PBKDF2WithHmacSHA512", "b27e8f7fb6a4bd5ebea892cd9a7f5043"),
            pbkd2AssertionData("PBEWithHmacSHA1AndAES_256", pwd, 256,
                    "PBKDF2WithHmacSHA1", "fdb3dcc2e81244d4d56bf7ec8dd61dd78a" +
                    "1b6fb3ad11d9ebd7f62027a2ccde98"),
            pbkd2AssertionData("PBEWithHmacSHA224AndAES_256", pwd, 256,
                    "PBKDF2WithHmacSHA224", "5ef9e5c6fdf7c355f3b424233a9f24c2" +
                    "c9c41793cb0948b8ea3aac240b8df64d"),
            pbkd2AssertionData("PBEWithHmacSHA256AndAES_256", pwd, 256,
                    "PBKDF2WithHmacSHA256", "c5af597b01b4f6baac8f62ff6f22bfb1" +
                    "f319c3278c8b31cc616294716d4eab08"),
            pbkd2AssertionData("PBEWithHmacSHA384AndAES_256", pwd, 256,
                    "PBKDF2WithHmacSHA384", "c3208ebc5d6db88858988ec00153847d" +
                    "5b1b7a8723640a022dc332bcaefeb356"),
            pbeWithHmacSHA512AndAES256Data,
            pbkd2AssertionData("PBKDF2WithHmacSHA1", pwd, 240,
                    "PBKDF2WithHmacSHA1", "fdb3dcc2e81244d4d56bf7ec8dd61dd78a" +
                    "1b6fb3ad11d9ebd7f62027a2cc"),
            pbkd2AssertionData("PBKDF2WithHmacSHA224", pwd, 336,
                    "PBKDF2WithHmacSHA224", "5ef9e5c6fdf7c355f3b424233a9f24c2" +
                    "c9c41793cb0948b8ea3aac240b8df64d1a0736ec1c69eef1c7b2"),
            pbkdf2WithHmacSHA256Data,
            pbkd2AssertionData("PBKDF2WithHmacSHA384", pwd, 576,
                    "PBKDF2WithHmacSHA384", "c3208ebc5d6db88858988ec00153847d" +
                    "5b1b7a8723640a022dc332bcaefeb356995d076a949d35c42c7e1e1c" +
                    "a936c12f8dc918e497edf279a522b7c99580e2613846b3919af637da"),
            pbkd2AssertionData("PBKDF2WithHmacSHA512", pwd, 768,
                    "PBKDF2WithHmacSHA512", "b27e8f7fb6a4bd5ebea892cd9a7f5043" +
                    "cefff9c38b07e599721e8d1161895482da255746844cc1030be37ba1" +
                    "969df10ff59554d1ac5468fa9b72977bb7fd52103a0a7b488cdb8957" +
                    "616c3e23a16bca92120982180c6c11a4f14649b50d0ade3a"),
            p12PBKDAssertionData("HmacPBESHA512", emptyPwd, 512, "SHA-512",
                    128, "90b6e088490c6c5e6b6e81209bd769d27df3868cae79591577a" +
                    "c35b46e4c6ebcc4b90f4943e3cb165f9d1789d938235f4b35ba74df9" +
                    "e509fbbb7aa329a432445"),
            pbkd2AssertionData("PBEWithHmacSHA512AndAES_256", emptyPwd, 256,
                    "PBKDF2WithHmacSHA512", "3a5c5fd11e4d381b32e11baa93d7b128" +
                    "09e016e48e0542c5d3453fc240a0fa76"),
    };

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());

        // Test valid cases.
        for (Configuration conf : Configuration.values()) {
            for (AssertionData data : assertionData) {
                testValidWith(sunPKCS11, data, conf);
            }
        }

        // Test invalid cases.
        testInvalidTranslateKey(sunPKCS11);
        testInvalidGenerateSecret(sunPKCS11);
        testInvalidGetKeySpec(sunPKCS11);

        System.out.println("TEST PASS - OK");
    }

    private static void testValidWith(Provider sunPKCS11, AssertionData data,
            Configuration conf) throws Exception {
        System.out.println(sep + System.lineSeparator() + data.algo
                + " (with " + conf.name() + ")");

        SecretKeyFactory skf = SecretKeyFactory.getInstance(data.algo,
                sunPKCS11);
        SecretKey derivedKey = switch (conf) {
            case PBEKeySpec -> skf.generateSecret(data.keySpec);
            case AnonymousPBEKey -> skf.translateKey(getAnonymousPBEKey(
                    data.algo, data.keySpec));
        };
        BigInteger derivedKeyValue = i(derivedKey.getEncoded());
        printHex("Derived Key", derivedKeyValue);

        if (!derivedKeyValue.equals(data.expectedKey)) {
            printHex("Expected Derived Key", data.expectedKey);
            throw new Exception("Expected Derived Key did not match");
        }

        if (skf.translateKey(derivedKey) != derivedKey) {
            throw new Exception("SecretKeyFactory::translateKey must return " +
                    "the same key when a P11PBEKey from the same token is " +
                    "passed");
        }

        testGetKeySpec(data, skf, derivedKey);
        if (sunJCE != null && data.algo.startsWith("PBKDF2")) {
            testTranslateP11PBEKeyToSunJCE(data.algo, (PBEKey) derivedKey);
        }
    }

    private static SecretKey getAnonymousPBEKey(String algorithm,
            PBEKeySpec keySpec) {
        return new PBEKey() {
            public byte[] getSalt() { return keySpec.getSalt(); }
            public int getIterationCount() {
                return keySpec.getIterationCount();
            }
            public String getAlgorithm() { return algorithm; }
            public String getFormat() { return "RAW"; }
            public char[] getPassword() { return keySpec.getPassword(); }
            public byte[] getEncoded() {
                return new byte[keySpec.getKeyLength() / 8];
            }
        };
    }

    private static void printHex(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    private static void testGetKeySpec(AssertionData data,
            SecretKeyFactory skf, SecretKey derivedKey) throws Exception {
        System.out.println(sep + System.lineSeparator()
                + "SecretKeyFactory::getKeySpec() (for " + data.algo + ")");
        KeySpec skfKeySpec = skf.getKeySpec(derivedKey, PBEKeySpec.class);
        if (skfKeySpec instanceof PBEKeySpec skfPBEKeySpec) {
            char[] specPassword = skfPBEKeySpec.getPassword();
            byte[] specSalt = skfPBEKeySpec.getSalt();
            int specIterations = skfPBEKeySpec.getIterationCount();
            int specKeyLength = skfPBEKeySpec.getKeyLength();
            System.out.println("  spec key length (bits): " + specKeyLength);
            System.out.println("           spec password: "
                    + String.valueOf(specPassword));
            System.out.println("    spec iteration count: " + specIterations);
            printHex("               spec salt", i(specSalt));

            if (!Arrays.equals(specPassword, data.keySpec.getPassword())) {
                throw new Exception("Password differs");
            }
            if (!Arrays.equals(specSalt, data.keySpec.getSalt())) {
                throw new Exception("Salt differs");
            }
            if (specIterations != data.keySpec.getIterationCount()) {
                throw new Exception("Iteration count differs");
            }
            if (specKeyLength != data.keySpec.getKeyLength()) {
                throw new Exception("Key length differs");
            }
        } else {
            throw new Exception("Invalid key spec type: " + skfKeySpec);
        }

        // Test extracting key bytes with a SecretKeySpec.
        SecretKeySpec secretKeySpec = (SecretKeySpec)
                skf.getKeySpec(derivedKey, SecretKeySpec.class);
        if (!Arrays.equals(secretKeySpec.getEncoded(),
                derivedKey.getEncoded())) {
            throw new Exception("Unable to extract key bytes with a " +
                    "SecretKeySpec");
        }
    }

    private static void testTranslateP11PBEKeyToSunJCE(String algorithm,
            PBEKey p11PbeK) throws Exception {
        System.out.println(sep + System.lineSeparator()
                + "Translate P11PBEKey to SunJCE (for " + algorithm + ")");
        SecretKey jceK = SecretKeyFactory.getInstance(algorithm, sunJCE)
                .translateKey(p11PbeK);
        BigInteger jceEncoded = i(jceK.getEncoded());
        printHex("    translated to SunJCE", jceEncoded);
        if (jceK instanceof PBEKey jcePbeK) {
            if (!Arrays.equals(jcePbeK.getPassword(), p11PbeK.getPassword())) {
                throw new Exception("Password differs");
            }
            if (!Arrays.equals(jcePbeK.getSalt(), p11PbeK.getSalt())) {
                throw new Exception("Salt differs");
            }
            if (jcePbeK.getIterationCount() != p11PbeK.getIterationCount()) {
                throw new Exception("Iteration count differs");
            }
            if (!jceEncoded.equals(i(p11PbeK.getEncoded()))) {
                throw new Exception("Encoded key differs");
            }
        } else {
            throw new Exception("Unexpected key type for SunJCE key: "
                    + jceK.getClass().getName());
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void assertThrows(Class<?> expectedExc, String expectedMsg,
            Action action) throws Exception {
        String shtExpected = "Should have thrown '"
                + expectedExc.getSimpleName() + ": " + expectedMsg + "'";
        try {
            action.run();
        } catch (Exception e) {
            if (expectedExc.isAssignableFrom(e.getClass()) &&
                    e.getMessage().equals(expectedMsg)) {
                return;
            }
            e.printStackTrace();
            throw new Exception(shtExpected + ", but threw '" +
                    e.getClass().getSimpleName() + ": " + e.getMessage() + "'");
        }
        throw new Exception(shtExpected + ", but it didn't throw");
    }

    private static void testInvalidTranslateKey(Provider sunPKCS11)
            throws Exception {
        System.out.println(sep + System.lineSeparator()
                + "Invalid SecretKeyFactory::translateKey tests");

        SecretKeyFactory skf1 = SecretKeyFactory.getInstance(
                hmacPBESHA1Data.algo, sunPKCS11);
        SecretKeyFactory skf2 = SecretKeyFactory.getInstance("AES", sunPKCS11);
        SecretKeyFactory skf3 = SecretKeyFactory.getInstance(
                pbkdf2WithHmacSHA256Data.algo, sunPKCS11);
        PBEKey p11PbeKey = (PBEKey) skf1.translateKey(getAnonymousPBEKey(
                skf1.getAlgorithm(), hmacPBESHA1Data.keySpec));
        Class<?> e = InvalidKeyException.class;

        System.out.println(" * Non-PBEKey key to PBE SecretKeyFactory");
        assertThrows(e, "PBE service requires a PBE key",
                () -> skf1.translateKey(new SecretKeySpec(
                        new byte[10], hmacPBESHA1Data.algo)));

        System.out.println(" * PBEKey key to PBE SecretKeyFactory of a " +
                "different algorithm");
        assertThrows(e, "Cannot use a " + hmacPBESHA1Data.algo + " key for a " +
                hmacPBESHA224Data.algo + " service",
                () -> SecretKeyFactory.getInstance(hmacPBESHA224Data.algo,
                        sunPKCS11).translateKey(p11PbeKey));

        System.out.println(" * Non-AES PBEKey key to AES SecretKeyFactory");
        assertThrows(e, "Cannot use a " + hmacPBESHA1Data.algo + " key for a " +
                skf2.getAlgorithm() + " service",
                () -> skf2.translateKey(p11PbeKey));

        System.out.println(" * Inconsistent key length between key and " +
                "algorithm");
        PBEKeySpec kSpec1 = new PBEKeySpec(pwd, salt, 1, 16);
        assertThrows(e, InvalidKeySpecException.class.getName() + ": Key " +
                "length is invalid for " + skf1.getAlgorithm() + " (expecting" +
                " " + hmacPBESHA1Data.keySpec.getKeyLength() + " but was " +
                kSpec1.getKeyLength() + ")",
                () -> skf1.translateKey(getAnonymousPBEKey(
                        skf1.getAlgorithm(), kSpec1)));

        System.out.println(" * Invalid key length in bits");
        PBEKeySpec kSpec2 = new PBEKeySpec(pwd, salt, 1);
        assertThrows(e, InvalidKeySpecException.class.getName() + ": Key " +
                "length must be multiple of 8 and greater than zero",
                () -> skf3.translateKey(getAnonymousPBEKey(
                        skf3.getAlgorithm(), kSpec2)));

        System.out.println();
    }

    private static void testInvalidGenerateSecret(Provider sunPKCS11)
            throws Exception {
        System.out.println(sep + System.lineSeparator()
                + "Invalid SecretKeyFactory::generateSecret tests");

        SecretKeyFactory skf1 = SecretKeyFactory.getInstance(
                hmacPBESHA1Data.algo, sunPKCS11);
        SecretKeyFactory skf2 = SecretKeyFactory.getInstance(
                pbeWithHmacSHA512AndAES256Data.algo, sunPKCS11);
        SecretKeyFactory skf3 = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA512", sunPKCS11);
        SecretKeyFactory skf4 = SecretKeyFactory.getInstance("AES", sunPKCS11);
        Class<?> e = InvalidKeySpecException.class;

        System.out.println(" * Missing salt and iteration count");
        assertThrows(e, "Salt not found",
                () -> skf1.generateSecret(new PBEKeySpec(pwd)));

        System.out.println(" * Inconsistent key length between spec and " +
                "algorithm");
        PBEKeySpec kSpec = new PBEKeySpec(pwd, salt, 1, 16);
        assertThrows(e, "Key length is invalid for " + skf1.getAlgorithm() +
                " (expecting " + hmacPBESHA1Data.keySpec.getKeyLength() +
                " but was " + kSpec.getKeyLength() + ")",
                () -> skf1.generateSecret(kSpec));
        assertThrows(e, "Key length is invalid for " + skf2.getAlgorithm() +
                " (expecting " + pbeWithHmacSHA512AndAES256Data.keySpec
                .getKeyLength() + " but was " + kSpec.getKeyLength() + ")",
                () -> skf2.generateSecret(kSpec));

        System.out.println(" * Invalid key length in bits");
        String msg = "Key length must be multiple of 8 and greater than zero";
        assertThrows(e, msg,
                () -> skf3.generateSecret(new PBEKeySpec(pwd, salt, 1)));
        assertThrows(e, msg,
                () -> skf3.generateSecret(new PBEKeySpec(pwd, salt, 1, 3)));

        System.out.println(" * PBEKeySpec to non-PBE SecretKeyFactory");
        assertThrows(e, "Unsupported spec: javax.crypto.spec.PBEKeySpec",
                () -> skf4.generateSecret(kSpec));

        System.out.println();
    }

    private static void testInvalidGetKeySpec(Provider sunPKCS11)
            throws Exception {
        System.out.println(sep + System.lineSeparator()
                + "Invalid SecretKeyFactory::getKeySpec tests");

        SecretKeyFactory skf1 = SecretKeyFactory.getInstance(
                hmacPBESHA1Data.algo, sunPKCS11);
        SecretKeyFactory skf2 = SecretKeyFactory.getInstance(
                "AES", sunPKCS11);
        PBEKey p11PbeKey = (PBEKey) skf1.translateKey(getAnonymousPBEKey(
                skf1.getAlgorithm(), hmacPBESHA1Data.keySpec));
        Class<?> e = InvalidKeySpecException.class;

        System.out.println(" * null KeySpec class");
        assertThrows(e, "key and keySpec must not be null",
                () -> skf1.getKeySpec(p11PbeKey, null));

        System.out.println(" * Invalid key type for PBEKeySpec");
        assertThrows(e, "Unsupported spec: " + PBEKeySpec.class.getName(),
                () -> skf1.getKeySpec(new SecretKeySpec(new byte[16],
                        skf1.getAlgorithm()), PBEKeySpec.class));

        System.out.println(" * Invalid PBE key and PBEKeySpec for " +
                skf2.getAlgorithm() + " SecretKeyFactory");
        assertThrows(e, "Unsupported spec: " + PBEKeySpec.class.getName(),
                () -> skf2.getKeySpec(p11PbeKey, PBEKeySpec.class));

        System.out.println();
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
