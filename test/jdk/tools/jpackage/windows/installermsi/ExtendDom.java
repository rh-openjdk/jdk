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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class ExtendDom {

    // to support installation upgrades this UUID must be the same for old and new
    // versions of the installer, this random value needs to be replaced with a
    // new one that must be permanent and unique
    static final String VENDOR_UPGRADE_CODE = UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        if (2 != args.length) {
            System.err.println("Usage: java ExtendDom.java input.xml output.xml");
            System.exit(1);
        }
        Path srcXml = Path.of(args[0]);
        if (!Files.exists(srcXml)) {
            System.err.println("Error: specified input file does not exist, path: [" + srcXml + "]");
            System.exit(1);
        }
        Path destXml = Path.of(args[1]);
        Document doc = readXml(srcXml);
        extend(doc);
        writeExtFiles();
        writeXml(destXml, doc);
        System.out.println("Output XML written, path: [" + destXml + "]");
    }

    static void extend(Document doc) throws Exception {
        Node product = findProductNode(doc);
        // vendor product attributes
        product.getAttributes().getNamedItem("Name").setNodeValue("VENDOR OpenJDK");
        product.getAttributes().getNamedItem("Manufacturer").setNodeValue("VENDOR Inc.");
        product.getAttributes().getNamedItem("UpgradeCode").setNodeValue(VENDOR_UPGRADE_CODE);
        product.getAttributes().getNamedItem("Version").setNodeValue("1.2.3.4");
        for (int i = 0; i < product.getChildNodes().getLength(); i++) {
            Node node = product.getChildNodes().item(i);
            // vendor help link
            if ("Property".equals(node.getNodeName()) &&
                    "ARPHELPLINK".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                node.getAttributes().getNamedItem("Value").setNodeValue("https://openjdk.vendor.com/");
            }
            // vendor env variable
            if ("Component".equals(node.getNodeName()) &&
                    "comp_env_java_home".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                Node comp = createVendorEnvComponent(doc);
                product.insertBefore(comp, node.getNextSibling());
            }
            if ("Feature".equals(node.getNodeName()) &&
                    "jdk".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                node.getAttributes().getNamedItem("Title").setNodeValue("OpenJDK");
                node.getAttributes().setNamedItem(doc.createAttribute("Display"));
                node.getAttributes().getNamedItem("Display").setNodeValue("expand");
                for (int j = 0; j < node.getChildNodes().getLength(); j++) {
                    Node child = node.getChildNodes().item(j);
                    if ("Feature".equals(child.getNodeName()) &&
                            "jdk_env_java_home".equals(child.getAttributes().getNamedItem("Id").getNodeValue())) {
                        Node feature = createVendorEnvFeature(doc);
                        node.insertBefore(feature, child.getNextSibling());
                    }
                }
            }
            // vendor install dir
            if ("Directory".equals(node.getNodeName()) &&
                    "TARGETDIR".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                Node pf64 = findChildWithId(node, "ProgramFiles64Folder");
                Node vendorDir = findChildWithId(pf64, "dir_vendor");
                vendorDir.getAttributes().getNamedItem("Name").setNodeValue("VendorDirectory");
                Node installDir = findChildWithId(vendorDir, "INSTALLDIR");
                installDir.getAttributes().getNamedItem("Name").setNodeValue("jdk-VENDOR_VERSION");

                // vendor additional component dir
                Node vendorExt1Dir = createVendorExtContentDir(doc);
                installDir.appendChild(vendorExt1Dir);
            }
            // vendor additional component feature
            if ("Feature".equals(node.getNodeName()) &&
                    "jdk".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                Node feature = createVendorExtContentFeature(doc);
                product.insertBefore(feature, node.getNextSibling());
            }
            // registry
            if ("Component".equals(node.getNodeName()) &&
                "comp_registry_runtime".equals(node.getAttributes().getNamedItem("Id").getNodeValue())) {
                Node curVer = findRegistryValueCurrentVersion(product);
                curVer.getAttributes().getNamedItem("Value").setNodeValue("1.2.3");
                Node regKey = findRegistryKeyJavaHome(product);
                regKey.getAttributes().getNamedItem("Key").setNodeValue("Software\\JavaSoft\\JDK\\1.2.3");
            }
        }
        // vendor custom action
        product.appendChild(createCustomActionProp(doc));
        product.appendChild(createCustomAction(doc));
        product.appendChild(createInstallExecuteSequence(doc));
    }

    static void writeExtFiles() throws Exception {
        Path dir = Path.of("vendor_ext1");
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
            Files.writeString(dir.resolve("file1.txt"), "foo");
            Files.writeString(dir.resolve("file2.txt"), "bar");
        }
    }

    static Document readXml(Path path) throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        return docBuilder.parse(path.toFile());
    }

    static void writeXml(Path path, Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(path.toFile());
        transformer.transform(source, result);
    }

    static Node findProductNode(Document doc) {
        for (int i1 = 0; i1 < doc.getChildNodes().getLength(); i1++) {
            Node node = doc.getChildNodes().item(i1);
            if ("Wix".equals(node.getNodeName())) {
                for (int i2 = 0; i2 < node.getChildNodes().getLength(); i2++) {
                    Node product = node.getChildNodes().item(i2);
                    if ("Product".equals(product.getNodeName())) {
                        return product;
                    }
                }
            }
        }
        throw new IllegalStateException("Product node not found");
    }

    private static Node findRegistryValueCurrentVersion(Node product) {
        Node compRegistryRuntime = findChildWithId(product, "comp_registry_runtime_current_version");
        Node regKey = findChildWithId(compRegistryRuntime, "registry_runtime_current_version");
        for (int i = 0; i < regKey.getChildNodes().getLength(); i++) {
            Node regVal = regKey.getChildNodes().item(i);
            if ("RegistryValue".equals(regVal.getNodeName()) &&
                    "CurrentVersion".equals(regVal.getAttributes().getNamedItem("Name").getNodeValue())) {
                return regVal;
            }
        }
        throw new IllegalStateException("Registry value current version not found");
    }

    private static Node findRegistryKeyJavaHome(Node product) {
        Node compRegistryRuntime = findChildWithId(product, "comp_registry_runtime_java_home");
        return findChildWithId(compRegistryRuntime, "registry_runtime_java_home");
    }

    private static Node findChildWithId(Node parent, String id) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node child = parent.getChildNodes().item(i);
            NamedNodeMap attrs = child.getAttributes();
            if (null != attrs && null != attrs.getNamedItem("Id") &&
                    id.equals(attrs.getNamedItem("Id").getNodeValue())) {
                return child;
            }
        }
        throw new IllegalStateException("Child node not found, id: [" + id + "]");
    }

    static void addAttr(Node node, String name, String value) {
        Attr attr = node.getOwnerDocument().createAttribute(name);
        attr.setNodeValue(value);
        node.getAttributes().setNamedItem(attr);
    }

    static Node createVendorEnvComponent(Document doc) throws Exception {
        Node comp = doc.createElement("Component");
        addAttr(comp, "Id", "comp_env_vendor_java_home");
        addAttr(comp, "Directory", "INSTALLDIR");
        addAttr(comp, "Guid", UUID.randomUUID().toString());
        addAttr(comp, "KeyPath", "yes");
        Node env = doc.createElement("Environment");
        addAttr(env, "Id", "env_vendor_java_home");
        addAttr(env, "Name", "VENDOR_JAVA_HOME");
        addAttr(env, "Value", "[INSTALLDIR]");
        addAttr(env, "Action", "set");
        addAttr(env, "Part", "all");
        addAttr(env, "System", "yes");
        comp.appendChild(env);
        return comp;
    }

    static Node createVendorEnvFeature(Document doc) {
        Node feature = doc.createElement("Feature");
        addAttr(feature, "Id", "jdk_env_vendor_java_home");
        addAttr(feature, "Absent", "allow");
        addAttr(feature, "AllowAdvertise", "no");
        addAttr(feature, "Level", "2");
        addAttr(feature, "Title", "VENDOR_JAVA_HOME Variable");
        addAttr(feature, "Description", "Sets 'VENDOR_JAVA_HOME' system environment variable.");
        Node cr = doc.createElement("ComponentRef");
        addAttr(cr, "Id", "comp_env_vendor_java_home");
        feature.appendChild(cr);
        return feature;
    }

    static Node createVendorExtContentDir(Document doc) {
        Node dir = doc.createElement("Directory");
        addAttr(dir, "Id", "VENDOREXT1");
        addAttr(dir, "Name", "vendor_ext1");
        Node comp1 = doc.createElement("Component");
        addAttr(comp1, "Id", "comp_vendor_ext1_file1_txt");
        addAttr(comp1, "Guid", "*");
        Node comp1File = doc.createElement("File");
        addAttr(comp1File, "Id", "file_vendor_ext1_file1_txt");
        addAttr(comp1File, "Source", "../vendor_ext1/file1.txt");
        comp1.appendChild(comp1File);
        dir.appendChild(comp1);
        Node comp2 = doc.createElement("Component");
        addAttr(comp2, "Id", "comp_vendor_ext1_file2_txt");
        addAttr(comp2, "Guid", "*");
        Node comp2File = doc.createElement("File");
        addAttr(comp2File, "Id", "file_vendor_ext1_file2_txt");
        addAttr(comp2File, "Source", "../vendor_ext1/file2.txt");
        comp2.appendChild(comp2File);
        dir.appendChild(comp2);
        return dir;
    }

    static Node createVendorExtContentFeature(Document doc) {
        Node feature = doc.createElement("Feature");
        addAttr(feature, "Id", "vendor_ext_content");
        addAttr(feature, "Absent", "allow");
        addAttr(feature, "AllowAdvertise", "no");
        addAttr(feature, "Level", "2");
        addAttr(feature, "Description", "Additional vendor-specific content");
        addAttr(feature, "Title", "Vendor ext1 content");
        Node cr1 = doc.createElement("ComponentRef");
        addAttr(cr1, "Id", "comp_vendor_ext1_file1_txt");
        feature.appendChild(cr1);
        Node cr2 = doc.createElement("ComponentRef");
        addAttr(cr2, "Id", "comp_vendor_ext1_file2_txt");
        feature.appendChild(cr2);
        return feature;
    }

    static Node createCustomActionProp(Document doc) {
        Node ca = doc.createElement("CustomAction");
        addAttr(ca, "Id", "uninstall_cleanup_immediate");
        addAttr(ca, "Property", "uninstall_cleanup_deferred");
        addAttr(ca, "Value", "\"[SystemFolder]cmd.exe\" /c rd /s /q \"[INSTALLDIR]\"");
        return ca;
    }

    static Node createCustomAction(Document doc) {
        Node ca = doc.createElement("CustomAction");
        addAttr(ca, "Id", "uninstall_cleanup_deferred");
        addAttr(ca, "BinaryKey", "WixCA");
        addAttr(ca, "DllEntry", "WixQuietExec");
        addAttr(ca, "Return", "ignore");
        addAttr(ca, "Execute", "deferred");
        addAttr(ca, "Impersonate", "no");
        return ca;
    }

    static Node createInstallExecuteSequence(Document doc) {
        Node seq = doc.createElement("InstallExecuteSequence");
        Node c1 = doc.createElement("Custom");
        addAttr(c1, "Action", "uninstall_cleanup_immediate");
        addAttr(c1, "Before", "InstallInitialize");
        c1.appendChild(doc.createTextNode("REMOVE AND (NOT UPGRADINGPRODUCTCODE)"));
        seq.appendChild(c1);
        Node c2 = doc.createElement("Custom");
        addAttr(c2, "Action", "uninstall_cleanup_deferred");
        addAttr(c2, "Before", "InstallFinalize");
        c2.appendChild(doc.createTextNode("REMOVE AND (NOT UPGRADINGPRODUCTCODE)"));
        seq.appendChild(c2);
        return seq;
    }
}
