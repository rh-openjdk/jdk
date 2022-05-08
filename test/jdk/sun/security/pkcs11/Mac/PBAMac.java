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
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/*
 * @test
 * @bug 9999999
 * @summary test password based authentication on SunPKCS11's Mac service
 * @requires (jdk.version.major >= 8)
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBAMac
 */

public final class PBAMac {
    public static void main(String[] args) throws Exception {
        java.security.Security.getProviders();
        PBAMac2.main(args);
    }
}

final class PBAMac2 extends PKCS11Test {
    private static final char[] password = "123456".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes();
    private static final int iterations = 1000;
    private static final String plainText = "This is a know plain text!";
    private static final String sep =
    "=========================================================================";

    private static enum Configuration {
        // Provide salt & iterations through a PBEParameterSpec instance
        PBEParameterSpec,

        // Provide salt & iterations through an anonymous class implementing
        // the javax.crypto.interfaces.PBEKey interface
        AnonymousPBEKey,
    }

    // Generated with SunJCE
    private static final Map<String, BigInteger> assertionData = Map.of(
            "HmacPBESHA1", new BigInteger("febd26da5d63ce819770a2af1fc2857e" +
                    "e2c9c41c", 16),
            "HmacPBESHA224", new BigInteger("aa6a3a1c35a4b266fea62d1a871508" +
                    "bd45f8ec326bcf16e09699063", 16),
            "HmacPBESHA256", new BigInteger("af4d71121fd4e9d52eb42944d99b77" +
                    "8ff64376fcf6af8d1dca3ec688dfada5c8", 16),
            "HmacPBESHA384", new BigInteger("5d6d37764205985ffca7e4a6222752" +
                    "a8bbd0520858da08ecafdc57e6246894675e375b9ba084f9ce7142" +
                    "35f202cc3452", 16),
            "HmacPBESHA512", new BigInteger("f586c2006cc2de73fd5743e5cca701" +
                    "c942d3741a7a54a2a649ea36898996cf3c483f2d734179b47751db" +
                    "e8373c980b4072136d2e2810f4e7276024a3e9081cc1", 16)
            );

    private static Provider sunJCE = Security.getProvider("SunJCE");

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        for (Configuration conf : Configuration.values()) {
            testWith(sunPKCS11, "HmacPBESHA1", conf);
            testWith(sunPKCS11, "HmacPBESHA224", conf);
            testWith(sunPKCS11, "HmacPBESHA256", conf);
            testWith(sunPKCS11, "HmacPBESHA384", conf);
            testWith(sunPKCS11, "HmacPBESHA512", conf);
        }
        System.out.println("TEST PASS - OK");
    }

    private void testWith(Provider sunPKCS11, String algorithm,
            Configuration conf) throws Exception {
        System.out.println(sep + System.lineSeparator() + algorithm
                + " (with " + conf.name() + ")");

        BigInteger macResult = computeMac(sunPKCS11, algorithm, conf);
        printByteArray("HMAC Result", macResult);

        BigInteger expectedMacResult = computeExpectedMac(algorithm, conf);

        if (!macResult.equals(expectedMacResult)) {
            printByteArray("Expected HMAC Result", expectedMacResult);
            throw new Exception("Expected HMAC Result did not match");
        }
    }

    private BigInteger computeMac(Provider p, String algorithm,
            Configuration conf) throws Exception {
        Mac pbaMac;
        try {
            pbaMac = Mac.getInstance(algorithm, p);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        switch (conf) {
            case PBEParameterSpec -> {
                SecretKey key = getPasswordOnlyPBEKey();
                pbaMac.init(key, new PBEParameterSpec(salt, iterations));
            }
            case AnonymousPBEKey -> {
                SecretKey key = getPasswordSaltIterationsPBEKey();
                pbaMac.init(key);
            }
        }
        return new BigInteger(1, pbaMac.doFinal(plainText.getBytes()));
    }

    private BigInteger computeExpectedMac(String algorithm, Configuration conf)
            throws Exception {
        if (sunJCE != null) {
            BigInteger macResult = computeMac(sunJCE, algorithm, conf);
            if (macResult != null) {
                return macResult;
            }
            // Move to assertionData as it's unlikely that any of
            // the algorithms are available.
            sunJCE = null;
        }
        // If SunJCE or the algorithm are not available, assertionData
        // is used instead.
        return assertionData.get(algorithm);
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
            public char[] getPassword() { return password.clone(); }
            public byte[] getEncoded() { return null; } // unused in PBA Mac
        };
    }

    private static void printByteArray(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    public static void main(String[] args) throws Exception {
        PBAMac2 test = new PBAMac2();
        Provider p = Security.getProvider("SunPKCS11-NSS-FIPS");
        if (p != null) {
            test.main(p);
        } else {
            main(test);
        }
    }
}
