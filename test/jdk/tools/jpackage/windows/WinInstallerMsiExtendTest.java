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

import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.TKit;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static jdk.jpackage.test.WindowsHelper.*;

/*
 * @test
 * @summary test extension examples for jdk MSI installer
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile WinInstallerMsiExtendTest.java
 *
 * @run main/othervm/timeout=1400 jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerMsiExtendTest
 */
public class WinInstallerMsiExtendTest {

    private static final String INSTALLERMSI_JAXB_EXTEND_LIBS_DIR = "INSTALLERMSI_JAXB_EXTEND_LIBS_DIR";
    private static final String DENO_HOME = "DENO_HOME";

    @Test
    @Parameter("DOM")
    @Parameter("JAXB")
    @Parameter("SCRIPT")
    public static void test(String option) throws Exception {
        // JAXB and SCRIPT tests require external resources that may be unavailable
        if ("JAXB".equals(option) &&
                null == queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, INSTALLERMSI_JAXB_EXTEND_LIBS_DIR)) {
            System.out.println("JAXB extending test is skipped, please set" +
                    " '" + INSTALLERMSI_JAXB_EXTEND_LIBS_DIR + "' system environment" +
                    " variable to enable it");
            return;
        }
        if ("SCRIPT".equals(option) &&
                null == queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, DENO_HOME)) {
            System.out.println("Script extending test is skipped, please set" +
                    " '" + DENO_HOME + "' system environment" +
                    " variable to enable it");
            return;
        }

        Path scratchImagesDir = Path.of(option);
        Files.createDirectory(scratchImagesDir);
        Path instBuildDir = scratchImagesDir.resolve("instbuild");
        Files.createDirectory(instBuildDir);

        Path buildRoot = findBuildRoot();
        Path srcRoot = TKit.SRC_ROOT.resolve("../..");
        final Path jdkWxs;
        if ("DOM".equals(option)) {
           jdkWxs = transformXmlWithDom(buildRoot, srcRoot, instBuildDir);
        } else if ("JAXB".equals(option)) {
            jdkWxs = transformXmlWithJaxb(buildRoot, srcRoot, instBuildDir);
        } else if ("SCRIPT".equals(option)) {
            jdkWxs = transformXmlWithScript(buildRoot, srcRoot, instBuildDir);
        } else {
            throw new RuntimeException("Invalid option, value: [" + option + "]");
        }
        Path jdkMsi = createInstaller(buildRoot, scratchImagesDir, instBuildDir, jdkWxs);

        Path installed = installPackage(jdkMsi, "ADDLOCAL=ALL");
        try {

            TKit.assertDirectoryExists(installed.resolve("bin"));
            TKit.assertFileExists(installed.resolve("bin/java.exe"));
            TKit.assertFileExists(installed.resolve("bin/server/jvm.dll"));
            TKit.assertFileExists(installed.resolve("lib/modules"));
            TKit.assertDirectoryExists(installed.resolve("vendor_ext1"));

        } finally {
            uninstallPackage(jdkMsi);
        }
    }

    private static Path transformXmlWithDom(Path buildRoot, Path srcRoot, Path instBuildDir) throws Exception {
        Path testJdk = Path.of(System.getProperty("test.jdk"));
        Path javaExe = testJdk.resolve("bin/java.exe");
        Path jdkXml = findMsiXml(buildRoot);
        Path extendDomJava = srcRoot.resolve("test/jdk/tools/jpackage/windows/installermsi/ExtendDom.java");
        Path jdkWxs = instBuildDir.resolve("jdk.wxs");
        int status = new ProcessBuilder(
                javaExe.toAbsolutePath().toString(),
                extendDomJava.toAbsolutePath().toString(),
                jdkXml.toAbsolutePath().toString(),
                jdkWxs.toAbsolutePath().toString()
        ).directory(instBuildDir.toFile()).inheritIO().start().waitFor();
        if (0 != status) {
            throw new RuntimeException("Error transforming XML");
        }
        return jdkWxs;
    }

    private static Path transformXmlWithJaxb(Path buildRoot, Path srcRoot, Path instBuildDir) throws Exception {
        Path testJdk = Path.of(System.getProperty("test.jdk"));
        Path javaExe = testJdk.resolve("bin/java.exe");
        Path jdkXml = findMsiXml(buildRoot);
        Path extendJaxbJava = srcRoot.resolve("test/jdk/tools/jpackage/windows/installermsi/ExtendJaxb.java");
        Path jdkWxs = instBuildDir.resolve("jdk.wxs");
        String cp = collectJaxbClasspath();
        int status = new ProcessBuilder(
                javaExe.toAbsolutePath().toString(),
                "-cp", cp,
                extendJaxbJava.toAbsolutePath().toString(),
                jdkXml.toAbsolutePath().toString(),
                jdkWxs.toAbsolutePath().toString()
        ).directory(instBuildDir.toFile()).inheritIO().start().waitFor();
        if (0 != status) {
            throw new RuntimeException("Error transforming XML");
        }
        return jdkWxs;
    }

    private static Path transformXmlWithScript(Path buildRoot, Path srcRoot, Path instBuildDir) throws Exception {
        Path denoExe = findDenoExe();
        Path jdkXml = findMsiXml(buildRoot);
        Path extendScript = srcRoot.resolve("test/jdk/tools/jpackage/windows/installermsi/ExtendScript.js");
        Path jdkWxs = instBuildDir.resolve("jdk.wxs");
        int status = new ProcessBuilder(
                denoExe.toAbsolutePath().toString(),
                "run",
                "-A",
                extendScript.toAbsolutePath().toString(),
                jdkXml.toAbsolutePath().toString(),
                jdkWxs.toAbsolutePath().toString()
        ).directory(instBuildDir.toFile()).inheritIO().start().waitFor();
        if (0 != status) {
            throw new RuntimeException("Error transforming XML");
        }
        return jdkWxs;
    }

    private static Path createInstaller(Path buildRoot, Path scratchImagesDir, Path instBuildRoot, Path jdkWxs) throws Exception {
        Path wixDir = findWixDir();
        Path candleExe = wixDir.resolve("bin/candle.exe");
        if (!Files.exists(candleExe)) {
            candleExe = wixDir.resolve("candle.exe");
            if (!Files.exists(candleExe)) {
                throw new RuntimeException("Unable to find 'candle' utility," +
                        " WiX directory: [" + wixDir.toAbsolutePath() + "]");
            }
        }
        Path lightExe = wixDir.resolve("bin/light.exe");
        if (!Files.exists(lightExe)) {
            lightExe = wixDir.resolve("light.exe");
            if (!Files.exists(lightExe)) {
                throw new RuntimeException("Unable to find 'light' utility," +
                        " WiX directory: [" + wixDir.toAbsolutePath() + "]");
            }
        }

        Path jdkImage = buildRoot.resolve("images/jdk");
        copyDir(jdkImage, scratchImagesDir.resolve("jdk"));
        Path instRes = jdkImage.getParent().resolve("installermsi/resources");
        copyDir(instRes, instBuildRoot.resolve("resources"));
        Files.move(instBuildRoot.resolve("vendor_ext1"), scratchImagesDir.resolve("vendor_ext1"));

        int candleStatus = new ProcessBuilder(
                candleExe.toAbsolutePath().toString(),
                "-nologo",
                "-arch", "x64",
                jdkWxs.toAbsolutePath().toString()
        ).directory(instBuildRoot.toFile()).inheritIO().start().waitFor();
        if (0 != candleStatus) {
            throw new RuntimeException("Error running WiX candle utility");
        }

        Path jdkWixobj = instBuildRoot.resolve("jdk.wixobj");
        int lightStatus = new ProcessBuilder(
                lightExe.toAbsolutePath().toString(),
                "-nologo",
                "-sw1076",
                "-ext", "WixUIExtension",
                "-ext", "WixUtilExtension",
                jdkWixobj.toAbsolutePath().toString()
        ).directory(instBuildRoot.toFile()).inheritIO().start().waitFor();
        if (0 != lightStatus) {
            throw new RuntimeException("Error running WiX light utility");
        }

        return instBuildRoot.resolve("jdk.msi");
    }

    private static Path findWixDir() {
        String envOpt = queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, "WIX");
        if (null == envOpt) {
            throw new RuntimeException("Unable to find WiX directory," +
                    " 'WIX' environment variable is not set");
        }
        Path wixDir = Path.of(envOpt);
        if (!Files.exists(wixDir)) {
            throw new RuntimeException("Unable to find WiX directory," +
                    " please check that 'WIX' environment variable is set correctly");
        }
        return wixDir;
    }

    private static Path findMsiXml(Path buildRoot) throws Exception {
        Path instDir = buildRoot.resolve("images/installermsi");
        for (String name : instDir.toFile().list()) {
            if (name.startsWith("openjdk-") && name.endsWith(".xml")) {
                return instDir.resolve(name);
            }
        }
        throw new RuntimeException("MSI XML file not found, please run 'make installer-msi-xml'" +
                " before running this test");
    }

    private static String collectJaxbClasspath() throws Exception {
        String envOpt = queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, INSTALLERMSI_JAXB_EXTEND_LIBS_DIR);
        if (null == envOpt) {
            throw new RuntimeException("Unable to find JAXB libraries," +
                    " '" + INSTALLERMSI_JAXB_EXTEND_LIBS_DIR + "' variable is not set");
        }
        Path dir = Path.of(envOpt);
        if (!Files.exists(dir)) {
            throw new RuntimeException("Unable to find JAXB libraries," +
                    " please check that '" + INSTALLERMSI_JAXB_EXTEND_LIBS_DIR + "' variable is set correctly");
        }
        StringBuilder sb = new StringBuilder();
        for (String child : dir.toFile().list()) {
            if (child.endsWith(".jar")) {
                sb.append(dir.resolve(child).toAbsolutePath().toString());
                sb.append(";");
            }
        }
        return sb.toString();
    }

    private static Path findDenoExe() {
        String envOpt = queryRegistryValue(SYSTEM_ENVIRONMENT_REGKEY, DENO_HOME);
        if (null == envOpt) {
            throw new RuntimeException("Unable to find Deno directory," +
                    " '" + DENO_HOME + "' variable is not set");
        }
        Path denoDir = Path.of(envOpt);
        if (!Files.exists(denoDir)) {
            throw new RuntimeException("Unable to find Deno directory," +
                    " please check that '" + DENO_HOME + "' variable is set correctly");
        }
        Path denoExe = denoDir.resolve("deno.exe");
        if (!Files.exists(denoExe)) {
            throw new RuntimeException("Unable to find Deno executable, path: [" + denoExe.toAbsolutePath() + "]" +
                    " please check that '" + DENO_HOME + "' variable set correctly");
        }
        return denoExe;
    }

    private static void copyDir(Path source, Path target, CopyOption... options) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), options);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
