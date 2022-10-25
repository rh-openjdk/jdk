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

import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

/*
 * @test
 * @bug 9999999
 * @requires (jdk.version.major >= 8)
 * @run main/othervm/timeout=30 Main
 * @author Martin Balao (mbalao@redhat.com)
 */

public final class VerifyMissingAttributes {

    private static final String[] svcAlgImplementedIn = {
            "AlgorithmParameterGenerator.DSA",
            "AlgorithmParameters.DSA",
            "CertificateFactory.X.509",
            "KeyStore.JKS",
            "KeyStore.CaseExactJKS",
            "KeyStore.DKS",
            "CertStore.Collection",
            "CertStore.com.sun.security.IndexedCollection"
    };

    public static void main(String[] args) throws Throwable {
        Map<String, String> attrs = new HashMap<>();
        Provider sunProvider = Security.getProvider("SUN");
        for (String svcAlg : svcAlgImplementedIn) {
            attrs.clear();
            attrs.put(svcAlg + " ImplementedIn", "Software");
            doQuery(sunProvider, attrs);
        }
        if (Double.parseDouble(
                System.getProperty("java.specification.version")) >= 17) {
            attrs.clear();
            attrs.put("KeyFactory.RSASSA-PSS SupportedKeyClasses",
                    "java.security.interfaces.RSAPublicKey" +
                            "|java.security.interfaces.RSAPrivateKey");
            doQuery(Security.getProvider("SunRsaSign"), attrs);
        }
        System.out.println("TEST PASS - OK");
    }

    private static void doQuery(Provider expectedProvider,
                Map<String, String> attrs) throws Exception {
        if (expectedProvider == null) {
            throw new Exception("Provider not found.");
        }
        Provider[] providers = Security.getProviders(attrs);
        if (providers == null || providers.length != 1 ||
                providers[0] != expectedProvider) {
            StringBuffer sb = new StringBuffer();
            attrs.entrySet().stream().forEach(ent -> {
                sb.append(ent.getKey());
                sb.append(": ");
                sb.append(ent.getValue());
                sb.append(System.lineSeparator());
            });
            throw new Exception("Failure retrieving the provider with this" +
                    " query: " + sb.toString());
        }
    }
}
