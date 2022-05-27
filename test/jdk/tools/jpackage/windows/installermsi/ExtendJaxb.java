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

import com.redhat.openjdk.msiextend.jaxb.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

import static javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

public class ExtendJaxb {

    // to support installation upgrades this UUID must be the same for old and new
    // versions of the installer, this random value needs to be replaced with a
    // new one that must be permanent and unique
    static final String VENDOR_UPGRADE_CODE = UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        if (2 != args.length) {
            System.err.println("Usage: java" +
                    " -cp msiextend-jaxb-1.0.jar;jaxb-api-2.3.1.jar;jaxb-impl-2.3.1.jar;istack-commons-runtime-4.0.1.jar;activation-1.1.1.jar" +
                    " ExtendJaxb.java input.xml output.xml");
            System.exit(1);
        }
        Path srcXml = Paths.get(args[0]);
        if (!Files.exists(srcXml)) {
            System.err.println("Error: specified input file does not exist, path: [" + srcXml + "]");
            System.exit(1);
        }
        JAXBContext jaxb = JAXBContext.newInstance(Wix.class.getPackage().getName());
        Path destXml = Paths.get(args[1]);
        Wix wix = readXml(jaxb, srcXml);
        extend(wix);
        writeExtFiles();
        writeXml(jaxb, destXml, wix);
        System.out.println("Output XML written, path: [" + destXml + "]");
    }

    static void extend(Wix wix) throws Exception {
        Product product = wix.getProduct();
        // vendor product attributes
        product.withName("VENDOR OpenJDK")
                .withManufacturer("VENDOR Inc.")
                .withUpgradeCode(VENDOR_UPGRADE_CODE)
                .withVersion("1.2.3.4");
        ArrayList<Object> updated = new ArrayList<>();
        for (Object node : product.getAppIdOrBinaryOrComplianceCheck()) {
            updated.add(node);
            // vendor help link
            if (node instanceof Property) {
                Property prop = (Property) node;
                if ("ARPHELPLINK".equals(prop.getId())) {
                    prop.setValue("https://openjdk.vendor.com/");
                }
            }
            // vendor env variable
            if (node instanceof Component) {
                Component comp = (Component) node;
                if ("comp_env_java_home".equals(comp.getId())) {
                    Component vendorEnvComp = createVendorEnvComponent();
                    updated.add(vendorEnvComp);
                }
                // registry
                if ("comp_registry_runtime_current_version".equals(comp.getId())) {
                    RegistryKey rk = (RegistryKey) comp.getAppIdOrCategoryOrClazz().get(0);
                    RegistryValue rv = (RegistryValue) rk.getRegistryKeyOrRegistryValueOrPermission().get(0);
                    rv.withValue("1.2.3");
                } else if ("comp_registry_runtime_java_home".equals(comp.getId())) {
                    RegistryKey rk = (RegistryKey) comp.getAppIdOrCategoryOrClazz().get(0);
                    rk.withKey("Software\\JavaSoft\\JDK\\1.2.3");
                }
            }
            if (node instanceof Feature) {
                Feature feature = (Feature) node;
                if ("jdk".equals(feature.getId())) {
                    feature.setTitle("OpenJDK");
                    feature.setDisplay("expand");
                    ArrayList<Object> updatedChildren = new ArrayList<>();
                    for (Object subnode : feature.getComponentOrComponentGroupRefOrComponentRef()) {
                        updatedChildren.add(subnode);
                        if (subnode instanceof Feature) {
                            Feature subFeature = (Feature) subnode;
                            if ("jdk_env_java_home".equals(subFeature.getId())) {
                                Feature vendorEnvFeature = createVendorEnvFeature();
                                updatedChildren.add(vendorEnvFeature);
                            }
                        }
                    }
                    feature.getComponentOrComponentGroupRefOrComponentRef().clear();
                    feature.getComponentOrComponentGroupRefOrComponentRef().addAll(updatedChildren);
                }
            }
            // vendor install dir
            if (node instanceof Directory) {
                Directory dir = (Directory) node;
                if ("TARGETDIR".equals(dir.getId())) {
                    Directory programFilesDir = (Directory) dir.getComponentOrDirectoryOrMerge().get(0);
                    Directory vendorDir = (Directory) programFilesDir.getComponentOrDirectoryOrMerge().get(0);
                    vendorDir.setName("VendorDirectory");
                    Directory installDir = (Directory) vendorDir.getComponentOrDirectoryOrMerge().get(0);
                    installDir.setName("jdk-VENDOR_VERSION");

                    // vendor additional component dir
                    Directory vendorExt1Dir = createVendorExtContentDir();
                    installDir.getComponentOrDirectoryOrMerge().add(vendorExt1Dir);
                }
            }
            // vendor additional component feature
            if (node instanceof Feature) {
                Feature feature = (Feature) node;
                if ("jdk".equals(feature.getId())) {
                    Feature extFeature = createVendorExtContentFeature();
                    updated.add(extFeature);
                }
            }
        }
        product.getAppIdOrBinaryOrComplianceCheck().clear();
        product.getAppIdOrBinaryOrComplianceCheck().addAll(updated);

        // vendor custom action
        product.getAppIdOrBinaryOrComplianceCheck().add(createCustomActionProp());
        product.getAppIdOrBinaryOrComplianceCheck().add(createCustomAction());
        product.getAppIdOrBinaryOrComplianceCheck().add(createInstallExecuteSequence());
    }

    static void writeExtFiles() throws Exception {
        Path dir = Paths.get("vendor_ext1");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
            Files.writeString(dir.resolve("file1.txt"), "foo");
            Files.writeString(dir.resolve("file2.txt"), "bar");
        }
    }

    static Wix readXml(JAXBContext jaxb, Path path) throws Exception {
        try (InputStream is = new FileInputStream(path.toFile())) {
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            return (Wix) jaxb.createUnmarshaller().unmarshal(reader);
        }
    }

    static void writeXml(JAXBContext jaxb, Path path, Wix wix) throws Exception {
        try (OutputStream os = new FileOutputStream(path.toFile())) {
            Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            Marshaller marshaller = jaxb.createMarshaller();
            marshaller.setProperty(JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(wix, writer);
            writer.close();
        }
    }

    static Component createVendorEnvComponent() {
        return new Component()
                .withId("comp_env_vendor_java_home")
                .withDirectory("INSTALLDIR")
                .withGuid(UUID.randomUUID().toString())
                .withKeyPath("yes")
                .withAppIdOrCategoryOrClazz(new Environment()
                        .withId("env_vendor_java_home")
                        .withName("VENDOR_JAVA_HOME")
                        .withValue("[INSTALLDIR]")
                        .withAction("set")
                        .withPart("all")
                        .withSystem("yes"));
    }

    static Feature createVendorEnvFeature() {
        return new Feature()
                .withId("jdk_env_vendor_java_home")
                .withAbsent("allow")
                .withAllowAdvertise("no")
                .withLevel(BigInteger.valueOf(2))
                .withTitle("VENDOR_JAVA_HOME Variable")
                .withDescription("Sets 'VENDOR_JAVA_HOME' system environment variable.")
                .withComponentOrComponentGroupRefOrComponentRef(new ComponentRef()
                        .withId("comp_env_vendor_java_home"));
    }

    static Directory createVendorExtContentDir() {
        return new Directory()
                .withId("VENDOREXT1")
                .withName("vendor_ext1")
                .withComponentOrDirectoryOrMerge(new Component()
                        .withId("comp_vendor_ext1_file1_txt")
                        .withGuid("*")
                        .withAppIdOrCategoryOrClazz(new com.redhat.openjdk.msiextend.jaxb.File()
                                .withId("file_vendor_ext1_file1_txt")
                                .withSource("../vendor_ext1/file1.txt")))
                .withComponentOrDirectoryOrMerge(new Component()
                        .withId("comp_vendor_ext1_file2_txt")
                        .withGuid("*")
                        .withAppIdOrCategoryOrClazz(new com.redhat.openjdk.msiextend.jaxb.File()
                                .withId("file_vendor_ext1_file2_txt")
                                .withSource("../vendor_ext1/file2.txt")));
    }

    static Feature createVendorExtContentFeature() {
        return new Feature()
                .withId("vendor_ext_content")
                .withAbsent("allow")
                .withAllowAdvertise("no")
                .withLevel(BigInteger.valueOf(2))
                .withDescription("Additional vendor-specific content")
                .withTitle("Vendor ext1 content")
                .withComponentOrComponentGroupRefOrComponentRef(new ComponentRef()
                        .withId("comp_vendor_ext1_file1_txt"))
                .withComponentOrComponentGroupRefOrComponentRef(new ComponentRef()
                        .withId("comp_vendor_ext1_file2_txt"));
    }

    static CustomAction createCustomActionProp() {
        return new CustomAction()
                .withId("uninstall_cleanup_immediate")
                .withProperty("uninstall_cleanup_deferred")
                .withCustomActionValue("\"[SystemFolder]cmd.exe\" /c rd /s /q \"[INSTALLDIR]\"");
    }

    static CustomAction createCustomAction() {
        return new CustomAction()
                .withId("uninstall_cleanup_deferred")
                .withBinaryKey("WixCA")
                .withDllEntry("WixQuietExec")
                .withReturn("ignore")
                .withExecute("deferred")
                .withImpersonate("no");
    }

    static InstallExecuteSequence createInstallExecuteSequence() {
        return new InstallExecuteSequence()
                .withCustomOrScheduleRebootOrForceReboot(new Custom()
                    .withAction("uninstall_cleanup_immediate")
                    .withBefore("InstallInitialize")
                    .withValue("REMOVE AND (NOT UPGRADINGPRODUCTCODE)"))
                .withCustomOrScheduleRebootOrForceReboot(new Custom()
                    .withAction("uninstall_cleanup_deferred")
                    .withBefore("InstallFinalize")
                    .withValue("REMOVE AND (NOT UPGRADINGPRODUCTCODE)"));
    }
}
