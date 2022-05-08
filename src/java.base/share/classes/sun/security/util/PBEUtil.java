/*
 * Copyright (c) 2022, Red Hat, Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.util;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public final class PBEUtil {

    // Used by SunJCE and SunPKCS11
    public final static class PBES2Helper {
        private int iCount;
        private byte[] salt;
        private IvParameterSpec ivSpec;
        private final int defaultSaltLength;
        private final int defaultCount;

        public PBES2Helper(int defaultSaltLength, int defaultCount) {
            this.defaultSaltLength = defaultSaltLength;
            this.defaultCount = defaultCount;
        }

        public IvParameterSpec getIvSpec() {
            return ivSpec;
        }

        public AlgorithmParameters getAlgorithmParameters(
                int blkSize, String pbeAlgo, Provider p, SecureRandom random) {
            AlgorithmParameters params = null;
            if (salt == null) {
                // generate random salt and use default iteration count
                salt = new byte[defaultSaltLength];
                random.nextBytes(salt);
                iCount = defaultCount;
            }
            if (ivSpec == null) {
                // generate random IV
                byte[] ivBytes = new byte[blkSize];
                random.nextBytes(ivBytes);
                ivSpec = new IvParameterSpec(ivBytes);
            }
            PBEParameterSpec pbeSpec = new PBEParameterSpec(
                    salt, iCount, ivSpec);
            try {
                params = (p == null) ?
                        AlgorithmParameters.getInstance(pbeAlgo) :
                        AlgorithmParameters.getInstance(pbeAlgo, p);
                params.init(pbeSpec);
            } catch (NoSuchAlgorithmException nsae) {
                // should never happen
                throw new RuntimeException("AlgorithmParameters for "
                        + pbeAlgo + " not configured");
            } catch (InvalidParameterSpecException ipse) {
                // should never happen
                throw new RuntimeException("PBEParameterSpec not supported");
            }
            return params;
        }

        public PBEKeySpec getPBEKeySpec(
                int blkSize, int keyLength, int opmode, Key key,
                AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {

            if (key == null) {
                throw new InvalidKeyException("Null key");
            }

            byte[] passwdBytes = key.getEncoded();
            char[] passwdChars = null;
            PBEKeySpec pbeSpec;
            try {
                if ((passwdBytes == null) || !(key.getAlgorithm().regionMatches(
                        true, 0, "PBE", 0, 3))) {
                    throw new InvalidKeyException("Missing password");
                }

                // TBD: consolidate the salt, ic and IV parameter checks below

                // Extract salt and iteration count from the key, if present
                if (key instanceof javax.crypto.interfaces.PBEKey) {
                    salt = ((javax.crypto.interfaces.PBEKey)key).getSalt();
                    if (salt != null && salt.length < 8) {
                        throw new InvalidAlgorithmParameterException(
                                "Salt must be at least 8 bytes long");
                    }
                    iCount = ((javax.crypto.interfaces.PBEKey)key)
                            .getIterationCount();
                    if (iCount == 0) {
                        iCount = defaultCount;
                    } else if (iCount < 0) {
                        throw new InvalidAlgorithmParameterException(
                                "Iteration count must be a positive number");
                    }
                }

                // Extract salt, iteration count and IV from the params,
                // if present
                if (params == null) {
                    if (salt == null) {
                        // generate random salt and use default iteration count
                        salt = new byte[defaultSaltLength];
                        random.nextBytes(salt);
                        iCount = defaultCount;
                    }
                    if ((opmode == Cipher.ENCRYPT_MODE) ||
                            (opmode == Cipher.WRAP_MODE)) {
                        // generate random IV
                        byte[] ivBytes = new byte[blkSize];
                        random.nextBytes(ivBytes);
                        ivSpec = new IvParameterSpec(ivBytes);
                    }
                } else {
                    if (!(params instanceof PBEParameterSpec)) {
                        throw new InvalidAlgorithmParameterException
                                ("Wrong parameter type: PBE expected");
                    }
                    // salt and iteration count from the params take precedence
                    byte[] specSalt = ((PBEParameterSpec) params).getSalt();
                    if (specSalt != null && specSalt.length < 8) {
                        throw new InvalidAlgorithmParameterException(
                                "Salt must be at least 8 bytes long");
                    }
                    salt = specSalt;
                    int specICount = ((PBEParameterSpec) params)
                            .getIterationCount();
                    if (specICount == 0) {
                        specICount = defaultCount;
                    } else if (specICount < 0) {
                        throw new InvalidAlgorithmParameterException(
                                "Iteration count must be a positive number");
                    }
                    iCount = specICount;

                    AlgorithmParameterSpec specParams =
                            ((PBEParameterSpec) params).getParameterSpec();
                    if (specParams != null) {
                        if (specParams instanceof IvParameterSpec) {
                            ivSpec = (IvParameterSpec)specParams;
                        } else {
                            throw new InvalidAlgorithmParameterException(
                                    "Wrong parameter type: IV expected");
                        }
                    } else if ((opmode == Cipher.ENCRYPT_MODE) ||
                            (opmode == Cipher.WRAP_MODE)) {
                        // generate random IV
                        byte[] ivBytes = new byte[blkSize];
                        random.nextBytes(ivBytes);
                        ivSpec = new IvParameterSpec(ivBytes);
                    } else {
                        throw new InvalidAlgorithmParameterException(
                                "Missing parameter type: IV expected");
                    }
                }

                passwdChars = new char[passwdBytes.length];
                for (int i = 0; i < passwdChars.length; i++)
                    passwdChars[i] = (char) (passwdBytes[i] & 0x7f);

                pbeSpec = new PBEKeySpec(passwdChars, salt, iCount, keyLength);
                // password char[] was cloned in PBEKeySpec constructor,
                // so we can zero it out here
            } finally {
                if (passwdChars != null) Arrays.fill(passwdChars, '\0');
                if (passwdBytes != null) Arrays.fill(passwdBytes, (byte)0x00);
            }
            return pbeSpec;
        }

        public static AlgorithmParameterSpec getParameterSpec(
                AlgorithmParameters params)
                throws InvalidAlgorithmParameterException {
            AlgorithmParameterSpec pbeSpec = null;
            if (params != null) {
                try {
                    pbeSpec = params.getParameterSpec(PBEParameterSpec.class);
                } catch (InvalidParameterSpecException ipse) {
                    throw new InvalidAlgorithmParameterException(
                            "Wrong parameter type: PBE expected");
                }
            }
            return pbeSpec;
        }
    }

    // Used by SunJCE and SunPKCS11
    public static PBEKeySpec getPBAKeySpec(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        char[] passwdChars;
        byte[] salt = null;
        int iCount = 0;
        if (key instanceof javax.crypto.interfaces.PBEKey) {
            javax.crypto.interfaces.PBEKey pbeKey =
                (javax.crypto.interfaces.PBEKey) key;
            passwdChars = pbeKey.getPassword();
            salt = pbeKey.getSalt(); // maybe null if unspecified
            iCount = pbeKey.getIterationCount(); // maybe 0 if unspecified
        } else if (key instanceof SecretKey) {
            byte[] passwdBytes;
            if (!(key.getAlgorithm().regionMatches(true, 0, "PBE", 0, 3)) ||
                    (passwdBytes = key.getEncoded()) == null) {
                throw new InvalidKeyException("Missing password");
            }
            passwdChars = new char[passwdBytes.length];
            for (int i=0; i<passwdChars.length; i++) {
                passwdChars[i] = (char) (passwdBytes[i] & 0x7f);
            }
            Arrays.fill(passwdBytes, (byte)0x00);
        } else {
            throw new InvalidKeyException("SecretKey of PBE type required");
        }

        try {
            if (params == null) {
                // should not auto-generate default values since current
                // javax.crypto.Mac api does not have any method for caller to
                // retrieve the generated defaults.
                if ((salt == null) || (iCount == 0)) {
                    throw new InvalidAlgorithmParameterException
                            ("PBEParameterSpec required for salt and iteration count");
                }
            } else if (!(params instanceof PBEParameterSpec)) {
                throw new InvalidAlgorithmParameterException
                        ("PBEParameterSpec type required");
            } else {
                PBEParameterSpec pbeParams = (PBEParameterSpec) params;
                // make sure the parameter values are consistent
                if (salt != null) {
                    if (!Arrays.equals(salt, pbeParams.getSalt())) {
                        throw new InvalidAlgorithmParameterException
                                ("Inconsistent value of salt between key and params");
                    }
                } else {
                    salt = pbeParams.getSalt();
                }
                if (iCount != 0) {
                    if (iCount != pbeParams.getIterationCount()) {
                        throw new InvalidAlgorithmParameterException
                                ("Different iteration count between key and params");
                    }
                } else {
                    iCount = pbeParams.getIterationCount();
                }
            }
            // For security purpose, we need to enforce a minimum length
            // for salt; just require the minimum salt length to be 8-byte
            // which is what PKCS#5 recommends and openssl does.
            if (salt.length < 8) {
                throw new InvalidAlgorithmParameterException
                        ("Salt must be at least 8 bytes long");
            }
            if (iCount <= 0) {
                throw new InvalidAlgorithmParameterException
                        ("IterationCount must be a positive number");
            }
            return new PBEKeySpec(passwdChars, salt, iCount);
        } finally {
            Arrays.fill(passwdChars, '\0');
        }
    }
}
