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

/*
 * @test
 * @bug 9999999
 * @requires (jdk.version.major >= 8)
 * @run main/othervm/timeout=30 VerifyMissingAttributes
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
        Provider sunProvider = Security.getProvider("SUN");
        for (String svcAlg : svcAlgImplementedIn) {
            String filter = svcAlg + " ImplementedIn:Software";
            doQuery(sunProvider, filter);
        }
        if (Double.parseDouble(
                System.getProperty("java.specification.version")) >= 17) {
            String filter = "KeyFactory.RSASSA-PSS SupportedKeyClasses:" +
                    "java.security.interfaces.RSAPublicKey" +
                    "|java.security.interfaces.RSAPrivateKey";
            doQuery(Security.getProvider("SunRsaSign"), filter);
        }
        System.out.println("TEST PASS - OK");
    }

    private static void doQuery(Provider expectedProvider, String filter)
            throws Exception {
        if (expectedProvider == null) {
            throw new Exception("Provider not found.");
        }
        Provider[] providers = Security.getProviders(filter);
        if (providers == null || providers.length != 1 ||
                providers[0] != expectedProvider) {
            throw new Exception("Failure retrieving the provider with this" +
                    " query: " + filter);
        }
    }
}
