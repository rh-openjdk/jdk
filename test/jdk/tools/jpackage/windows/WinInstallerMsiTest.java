/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
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

import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.TKit;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.Files.walkFileTree;
import static jdk.jpackage.test.TKit.assertRegistryValueAbsent;
import static jdk.jpackage.test.TKit.assertRegistryValueEquals;
import static jdk.jpackage.test.WindowsHelper.*;

/*
 * @test
 * @summary test installation of the jdk MSI installer with various features selected
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile WinInstallerMsiTest.java
 *
 * @run main/othervm/timeout=1400 jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerMsiTest
 */
public class WinInstallerMsiTest {
    @Test
    @Parameter("")
    @Parameter("ADDLOCAL=ALL")
    @Parameter("ADDLOCAL=jdk")
    @Parameter("ADDLOCAL=jdk_env_java_home")
    @Parameter("ADDLOCAL=jdk_env_path")
    @Parameter("ADDLOCAL=jdk_registry_jar")
    @Parameter("ADDLOCAL=jdk_registry_runtime")
    public static void test(String option) throws Exception {
        Path msi = findInstallerMsi();
        Path installed = installPackage(msi, option);
        try {
            if (option.isEmpty() || option.endsWith("ALL") || option.endsWith("jdk")) {
                testFiles(installed);
            } else if (option.endsWith("jdk_env_java_home")) {
                testEnvJavaHome(installed);
            } else if (option.endsWith("jdk_env_path")) {
                testEnvPath(installed);
            } else if (option.endsWith("jdk_registry_jar")) {
                testRegistryJar(installed);
            } else if (option.endsWith("jdk_registry_runtime")) {
                testRegistryRuntime(installed);
            } else throw new Exception("Invalid option: [" + option + "]");
        } finally {
            uninstallPackage(msi);
        }
    }

    private static void testFiles(Path installed) throws Exception {
        Path buildRoot = findBuildRoot();
        Path image = buildRoot.resolve("images/jdk");
        if (!Files.exists(image)) {
            throw new RuntimeException("Cannot find jdk image");
        }

        walkFileTree(image, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.visitFile(file, attrs);
                Path rel = image.relativize(file);
                Path inst = installed.resolve(rel);
                TKit.assertFileExists(inst);
                TKit.assertEquals(Files.size(inst), Files.size(file),
                        "files size must be the same, path: [" + rel + "]");
                return result;
            }
        });
    }

    private static void testEnvJavaHome(Path installed) throws Exception {
        assertRegistryValueEquals(SYSTEM_ENVIRONMENT_REGKEY, "JAVA_HOME",
                installed.toAbsolutePath().toString() + "\\");
        String pathVar = queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, "PATH");
        TKit.assertNotNull(pathVar, "PATH");
        TKit.assertFalse(pathVar.endsWith(installed.resolve("bin").toAbsolutePath().toString()), pathVar);
    }

    private static void testEnvPath(Path installed) throws Exception {
        String pathVar = queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, "PATH");
        TKit.assertNotNull(pathVar, "PATH");
        TKit.assertTrue(pathVar.endsWith(installed.resolve("bin").toAbsolutePath().toString()), pathVar);
        assertRegistryValueAbsent(SYSTEM_ENVIRONMENT_REGKEY, "JAVA_HOME");
    }

    private static void testRegistryJar(Path installed) throws Exception {
        assertRegistryValueEquals("HKLM\\Software\\Classes\\.jar",
                "", "JARFile");
        assertRegistryValueEquals("HKLM\\Software\\Classes\\.jar",
                "Content Type", "application/java-archive");
        assertRegistryValueEquals("HKLM\\Software\\Classes\\JARFile",
                "", "JAR File");
        assertRegistryValueEquals("HKLM\\Software\\Classes\\JARFile",
                "EditFlags", "0x10000");
        assertRegistryValueEquals("HKLM\\Software\\Classes\\JARFile\\Shell\\Open",
                "", "&Launch with OpenJDK");
        String javaw = installed.resolve("bin\\javaw.exe").toAbsolutePath().toString();
        assertRegistryValueEquals("HKLM\\Software\\Classes\\JARFile\\Shell\\Open\\Command",
                "", "\"" + javaw + "\" -jar \"%1\" %*");
    }

    private static void testRegistryRuntime(Path installed) throws Exception {
        String curVer = queryRegistryValue("HKLM\\Software\\JavaSoft\\JDK\\", "CurrentVersion");
        TKit.assertNotNull(curVer, "current version");
        String[] curVerParts = curVer.split("\\.");
        TKit.assertTrue(curVerParts.length >= 1 && curVerParts.length <= 4, "current version parts count");
        for (String part : curVerParts) {
            TKit.assertTrue(part.matches("\\d+"), "current version part");
        }
        assertRegistryValueEquals("HKLM\\Software\\JavaSoft\\JDK\\" + curVer,
                "JavaHome", installed.toAbsolutePath().toString() + "\\");
    }
}
