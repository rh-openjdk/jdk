/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.util.*;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.*;

import static sun.security.pkcs11.TemplateManager.*;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * SecretKeyFactory implementation class. This class currently supports
 * DES, DESede, AES, ARCFOUR, and Blowfish.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11SecretKeyFactory extends SecretKeyFactorySpi {

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    P11SecretKeyFactory(Token token, String algorithm) {
        super();
        this.token = token;
        this.algorithm = algorithm;
    }

    private static final Map<String,Long> keyTypes;

    static {
        keyTypes = new HashMap<String,Long>();
        addKeyType("RC4",      CKK_RC4);
        addKeyType("ARCFOUR",  CKK_RC4);
        addKeyType("DES",      CKK_DES);
        addKeyType("DESede",   CKK_DES3);
        addKeyType("AES",      CKK_AES);
        addKeyType("Blowfish", CKK_BLOWFISH);
        addKeyType("ChaCha20", CKK_CHACHA20);

        // we don't implement RC2 or IDEA, but we want to be able to generate
        // keys for those SSL/TLS ciphersuites.
        addKeyType("RC2",      CKK_RC2);
        addKeyType("IDEA",     CKK_IDEA);

        addKeyType("TlsPremasterSecret",    PCKK_TLSPREMASTER);
        addKeyType("TlsRsaPremasterSecret", PCKK_TLSRSAPREMASTER);
        addKeyType("TlsMasterSecret",       PCKK_TLSMASTER);
        addKeyType("Generic",               CKK_GENERIC_SECRET);
    }

    private static void addKeyType(String name, long id) {
        Long l = Long.valueOf(id);
        keyTypes.put(name, l);
        keyTypes.put(name.toUpperCase(Locale.ENGLISH), l);
    }

    // returns the PKCS11 key type of the specified algorithm
    // no psuedo KeyTypes
    static long getPKCS11KeyType(String algorithm) {
        long kt = getKeyType(algorithm);
        if (kt == -1 || kt > PCKK_ANY) {
            kt = CKK_GENERIC_SECRET;
        }
        return kt;
    }

    // returns direct lookup result of keyTypes using algorithm
    static long getKeyType(String algorithm) {
        Long l = keyTypes.get(algorithm);
        if (l == null) {
            algorithm = algorithm.toUpperCase(Locale.ENGLISH);
            l = keyTypes.get(algorithm);
            if (l == null) {
                if (algorithm.startsWith("HMAC")) {
                    return PCKK_HMAC;
                } else if (algorithm.startsWith("SSLMAC")) {
                    return PCKK_SSLMAC;
                }
            }
        }
        return (l != null) ? l.longValue() : -1;
    }

    /**
     * Convert an arbitrary key of algorithm into a P11Key of provider.
     * Used in engineTranslateKey(), P11Cipher.init(), and P11Mac.init().
     */
    static P11Key convertKey(Token token, Key key, String algo)
            throws InvalidKeyException {
        return convertKey(token, key, algo, null);
    }

    /**
     * Convert an arbitrary key of algorithm w/ custom attributes into a
     * P11Key of provider.
     * Used in P11KeyStore.storeSkey.
     */
    static P11Key convertKey(Token token, Key key, String algo,
            CK_ATTRIBUTE[] extraAttrs)
            throws InvalidKeyException {
        token.ensureValid();
        if (key == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        if (key instanceof SecretKey == false) {
            throw new InvalidKeyException("Key must be a SecretKey");
        }
        long algoType;
        if (algo == null) {
            algo = key.getAlgorithm();
            algoType = getKeyType(algo);
        } else {
            algoType = getKeyType(algo);
            long keyAlgorithmType = getKeyType(key.getAlgorithm());
            if (algoType != keyAlgorithmType) {
                if ((algoType == PCKK_HMAC) || (algoType == PCKK_SSLMAC)) {
                    // ignore key algorithm for MACs
                } else {
                    throw new InvalidKeyException
                            ("Key algorithm must be " + algo);
                }
            }
        }
        if (key instanceof P11Key) {
            P11Key p11Key = (P11Key)key;
            if (p11Key.token == token) {
                if (extraAttrs != null) {
                    P11Key newP11Key = null;
                    Session session = null;
                    long p11KeyID = p11Key.getKeyID();
                    try {
                        session = token.getObjSession();
                        long newKeyID = token.p11.C_CopyObject(session.id(),
                            p11KeyID, extraAttrs);
                        newP11Key = (P11Key) (P11Key.secretKey(session,
                                newKeyID, p11Key.algorithm, p11Key.keyLength,
                                extraAttrs));
                    } catch (PKCS11Exception p11e) {
                        throw new InvalidKeyException
                                ("Cannot duplicate the PKCS11 key", p11e);
                    } finally {
                        p11Key.releaseKeyID();
                        token.releaseSession(session);
                    }
                    p11Key = newP11Key;
                }
                return p11Key;
            }
        }
        P11Key p11Key = token.secretCache.get(key);
        if (p11Key != null) {
            return p11Key;
        }
        if ("RAW".equalsIgnoreCase(key.getFormat()) == false) {
            throw new InvalidKeyException("Encoded format must be RAW");
        }
        byte[] encoded = key.getEncoded();
        p11Key = createKey(token, encoded, algo, algoType, extraAttrs);
        token.secretCache.put(key, p11Key);
        return p11Key;
    }

    static P11Key derivePBEKey(Token token, PBEKeySpec keySpec, String algo)
            throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec == null) {
            throw new InvalidKeySpecException("PBEKeySpec must not be null");
        }
        Session session = null;
        try {
            session = token.getObjSession();
            P11Util.KDFData kdfData = P11Util.kdfDataMap.get(algo);
            CK_MECHANISM ckMech;
            char[] password = keySpec.getPassword();
            byte[] salt = keySpec.getSalt();
            int itCount = keySpec.getIterationCount();
            int keySize = keySpec.getKeyLength();
            if (kdfData.keyLen != -1) {
                if (keySize == 0) {
                    keySize = kdfData.keyLen;
                } else if (keySize != kdfData.keyLen) {
                    throw new InvalidKeySpecException(
                            "Key length is invalid for " + algo);
                }
            }

            if (kdfData.kdfMech == CKM_PKCS5_PBKD2) {
                CK_VERSION p11Ver = token.p11.getInfo().cryptokiVersion;
                if (P11Util.isNSS(token) || p11Ver.major < 2 ||
                        p11Ver.major == 2 && p11Ver.minor < 40) {
                    // NSS keeps using the old structure beyond PKCS #11 v2.40
                    ckMech = new CK_MECHANISM(kdfData.kdfMech,
                            new CK_PKCS5_PBKD2_PARAMS(password, salt,
                                    itCount, kdfData.prfMech));
                } else {
                    ckMech = new CK_MECHANISM(kdfData.kdfMech,
                            new CK_PKCS5_PBKD2_PARAMS2(password, salt,
                                    itCount, kdfData.prfMech));
                }
            } else {
                // PKCS #12 "General Method" PBKD (RFC 7292, Appendix B.2)
                if (P11Util.isNSS(token)) {
                    // According to PKCS #11, "password" in CK_PBE_PARAMS has
                    // a CK_UTF8CHAR_PTR type. This suggests that it is encoded
                    // in UTF-8. However, NSS expects the password to be encoded
                    // as BMPString with a NULL terminator when C_GenerateKey
                    // is called for a PKCS #12 "General Method" derivation
                    // (see RFC 7292, Appendix B.1).
                    //
                    // The char size in Java is 2 bytes. When a char is
                    // converted to a CK_UTF8CHAR, the high-order byte is
                    // discarded (see jCharArrayToCKUTF8CharArray in
                    // p11_util.c). In order to have a BMPString passed to
                    // C_GenerateKey, we need to account for that and expand:
                    // the high and low parts of each char are split into 2
                    // chars. As an example, this is the transformation for
                    // a NULL terminated password "a":
                    // char[]    =>        [   0x0061,          0x0000     ]
                    //                          /    \           /    \
                    // Expansion =>       [0x0000, 0x0061, 0x0000, 0x0000]
                    //                         |       |       |       |
                    // BMPString =>       [  0x00,   0x61,   0x00,   0x00]
                    //
                    int inputLength = (password == null) ? 0 : password.length;
                    char[] expPassword = new char[inputLength * 2 + 2];
                    for (int i = 0, j = 0; i < inputLength; i++, j += 2) {
                        expPassword[j] = (char) ((password[i] >>> 8) & 0xFF);
                        expPassword[j + 1] = (char) (password[i] & 0xFF);
                    }
                    password = expPassword;
                }
                ckMech = new CK_MECHANISM(kdfData.kdfMech,
                        new CK_PBE_PARAMS(password, salt, itCount));
            }

            long keyType = getKeyType(kdfData.keyAlgo);
            CK_ATTRIBUTE[] attrs = new CK_ATTRIBUTE[
                    switch (kdfData.op) {
                        case ENCRYPTION, AUTHENTICATION -> 4;
                        case GENERIC -> 5;
                    }];
            attrs[0] = new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY);
            attrs[1] = new CK_ATTRIBUTE(CKA_VALUE_LEN, keySize >> 3);
            attrs[2] = new CK_ATTRIBUTE(CKA_KEY_TYPE, keyType);
            switch (kdfData.op) {
                case ENCRYPTION -> attrs[3] = CK_ATTRIBUTE.ENCRYPT_TRUE;
                case AUTHENTICATION -> attrs[3] = CK_ATTRIBUTE.SIGN_TRUE;
                case GENERIC -> {
                    attrs[3] = CK_ATTRIBUTE.ENCRYPT_TRUE;
                    attrs[4] = CK_ATTRIBUTE.SIGN_TRUE;
                }
            }
            CK_ATTRIBUTE[] attr = token.getAttributes(
                    O_GENERATE, CKO_SECRET_KEY, keyType, attrs);
            long keyID = token.p11.C_GenerateKey(session.id(), ckMech, attr);
            return (P11Key)P11Key.secretKey(
                    session, keyID, kdfData.keyAlgo, keySize, attr);
        } catch (PKCS11Exception e) {
            throw new InvalidKeySpecException("Could not create key", e);
        } finally {
            token.releaseSession(session);
        }
    }

    static P11Key derivePBEKey(Token token, PBEKey key, String algo)
            throws InvalidKeyException {
        token.ensureValid();
        if (key == null) {
            throw new InvalidKeyException("PBEKey must not be null");
        }
        P11Key p11Key = token.secretCache.get(key);
        if (p11Key != null) {
            return p11Key;
        }
        try {
            p11Key = derivePBEKey(token, new PBEKeySpec(key.getPassword(),
                    key.getSalt(), key.getIterationCount()), algo);
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException(e);
        }
        token.secretCache.put(key, p11Key);
        return p11Key;
    }

    static void fixDESParity(byte[] key, int offset) {
        for (int i = 0; i < 8; i++) {
            int b = key[offset] & 0xfe;
            b |= (Integer.bitCount(b) & 1) ^ 1;
            key[offset++] = (byte)b;
        }
    }

    private static P11Key createKey(Token token, byte[] encoded,
            String algorithm, long keyType, CK_ATTRIBUTE[] extraAttrs)
            throws InvalidKeyException {
        int n = encoded.length << 3;
        int keyLength = n;
        try {
            switch ((int)keyType) {
                case (int)CKK_DES:
                    keyLength =
                        P11KeyGenerator.checkKeySize(CKM_DES_KEY_GEN, n, token);
                    fixDESParity(encoded, 0);
                    break;
                case (int)CKK_DES3:
                    keyLength =
                        P11KeyGenerator.checkKeySize(CKM_DES3_KEY_GEN, n, token);
                    fixDESParity(encoded, 0);
                    fixDESParity(encoded, 8);
                    if (keyLength == 112) {
                        keyType = CKK_DES2;
                    } else {
                        keyType = CKK_DES3;
                        fixDESParity(encoded, 16);
                    }
                    break;
                case (int)CKK_AES:
                    keyLength =
                        P11KeyGenerator.checkKeySize(CKM_AES_KEY_GEN, n, token);
                    break;
                case (int)CKK_RC4:
                    keyLength =
                        P11KeyGenerator.checkKeySize(CKM_RC4_KEY_GEN, n, token);
                    break;
                case (int)CKK_BLOWFISH:
                    keyLength =
                        P11KeyGenerator.checkKeySize(CKM_BLOWFISH_KEY_GEN, n,
                        token);
                    break;
                case (int)CKK_CHACHA20:
                    keyLength = P11KeyGenerator.checkKeySize(
                        CKM_CHACHA20_KEY_GEN, n, token);
                    break;
                case (int)CKK_GENERIC_SECRET:
                case (int)PCKK_TLSPREMASTER:
                case (int)PCKK_TLSRSAPREMASTER:
                case (int)PCKK_TLSMASTER:
                    keyType = CKK_GENERIC_SECRET;
                    break;
                case (int)PCKK_SSLMAC:
                case (int)PCKK_HMAC:
                    if (n == 0) {
                        throw new InvalidKeyException
                                ("MAC keys must not be empty");
                    }
                    keyType = CKK_GENERIC_SECRET;
                    break;
                default:
                    throw new InvalidKeyException("Unknown algorithm " +
                            algorithm);
            }
        } catch (InvalidAlgorithmParameterException iape) {
            throw new InvalidKeyException("Invalid key for " + algorithm,
                    iape);
        } catch (ProviderException pe) {
            throw new InvalidKeyException("Could not create key", pe);
        }
        Session session = null;
        try {
            CK_ATTRIBUTE[] attributes;
            if (extraAttrs != null) {
                attributes = new CK_ATTRIBUTE[3 + extraAttrs.length];
                System.arraycopy(extraAttrs, 0, attributes, 3,
                        extraAttrs.length);
            } else {
                attributes = new CK_ATTRIBUTE[3];
            }
            attributes[0] = new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY);
            attributes[1] = new CK_ATTRIBUTE(CKA_KEY_TYPE, keyType);
            attributes[2] = new CK_ATTRIBUTE(CKA_VALUE, encoded);
            attributes = token.getAttributes
                (O_IMPORT, CKO_SECRET_KEY, keyType, attributes);
            session = token.getObjSession();
            long keyID = token.p11.C_CreateObject(session.id(), attributes);
            P11Key p11Key = (P11Key)P11Key.secretKey
                (session, keyID, algorithm, keyLength, attributes);
            return p11Key;
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not create key", e);
        } finally {
            token.releaseSession(session);
        }
    }

    // see JCE spec
    protected SecretKey engineGenerateSecret(KeySpec keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec == null) {
            throw new InvalidKeySpecException("KeySpec must not be null");
        }
        if (keySpec instanceof SecretKeySpec) {
            try {
                Key key = convertKey(token, (SecretKey)keySpec, algorithm);
                return (SecretKey)key;
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (algorithm.equalsIgnoreCase("DES")) {
            if (keySpec instanceof DESKeySpec) {
                byte[] keyBytes = ((DESKeySpec)keySpec).getKey();
                keySpec = new SecretKeySpec(keyBytes, "DES");
                return engineGenerateSecret(keySpec);
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            if (keySpec instanceof DESedeKeySpec) {
                byte[] keyBytes = ((DESedeKeySpec)keySpec).getKey();
                keySpec = new SecretKeySpec(keyBytes, "DESede");
                return engineGenerateSecret(keySpec);
            }
        } else if (keySpec instanceof PBEKeySpec) {
            return (SecretKey)derivePBEKey(token,
                    (PBEKeySpec)keySpec, algorithm);
        }
        throw new InvalidKeySpecException
                ("Unsupported spec: " + keySpec.getClass().getName());
    }

    private byte[] getKeyBytes(SecretKey key) throws InvalidKeySpecException {
        try {
            key = engineTranslateKey(key);
            if ("RAW".equalsIgnoreCase(key.getFormat()) == false) {
                throw new InvalidKeySpecException
                    ("Could not obtain key bytes");
            }
            byte[] k = key.getEncoded();
            return k;
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e);
        }
    }

    // see JCE spec
    protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if ((key == null) || (keySpec == null)) {
            throw new InvalidKeySpecException
                ("key and keySpec must not be null");
        }
        if (keySpec.isAssignableFrom(SecretKeySpec.class)) {
            return new SecretKeySpec(getKeyBytes(key), algorithm);
        } else if (algorithm.equalsIgnoreCase("DES")) {
            try {
                if (keySpec.isAssignableFrom(DESKeySpec.class)) {
                    return new DESKeySpec(getKeyBytes(key));
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            try {
                if (keySpec.isAssignableFrom(DESedeKeySpec.class)) {
                    return new DESedeKeySpec(getKeyBytes(key));
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        }
        throw new InvalidKeySpecException
                ("Unsupported spec: " + keySpec.getName());
    }

    // see JCE spec
    protected SecretKey engineTranslateKey(SecretKey key)
            throws InvalidKeyException {
        if (key instanceof PBEKey) {
            return (SecretKey)derivePBEKey(token, (PBEKey)key, algorithm);
        }
        return (SecretKey)convertKey(token, key, algorithm);
    }

}
