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

package build.tools.installermsi;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates installer XML file that can be extended by jdk vendors
 */
public class CreateExtXml {

    private static final String PLACEHOLDER_VERSION_FEATURE = "PLACEHOLDER_VERSION_FEATURE";
    private static final String PLACEHOLDER_VERSION_NUMBER = "PLACEHOLDER_VERSION_NUMBER";
    private static final String PLACEHOLDER_VERSION_NUMBER_FOUR_POSITIONS = "PLACEHOLDER_VERSION_NUMBER_FOUR_POSITIONS";

    private static final String COMPS_BEGIN_MARKER = "<!-- components begin -->";
    private static final String COMPS_END_MARKER = "<!-- components end -->";
    private static final String FEATURES_BEGIN_MARKER = "<!-- features begin -->";
    private static final String FEATURES_END_MARKER = "<!-- features end -->";

    // <?define name="value"?>
    private static final Pattern OVERRIDES_REGEX = Pattern.compile("^\\s*<\\?\\s*define\\s*([A-Za-z0-9_]+)\\s*=\\s*\"(.+)\"\\s*\\?>\\s*$");

    private static final Comparator<Node> ID_ATTR_COMPARATOR = new IdAttrComparator();
    private static final Comparator<Node> NAME_ATTR_COMPARATOR = new NameAttrComparator();

    public static void main(String[] args) throws Exception {
        if (6 != args.length) {
            System.err.println("Usage: java CreateExtXml.java path/to/template.xml path/to/config_dir" +
                    " VERSION_FEATURE VERSION_NUMBER VERSION_NUMBER_FOUR_POSITIONS output.xml");
            System.exit(1);
        }

        // load template
        Path templateXml = Path.of(args[0]);
        if (!Files.exists(templateXml)) {
            System.err.println("Error: specified input file does not exist, path: [" + templateXml + "]");
            System.exit(1);
        }
        Document doc = readXml(templateXml);

        // prepare files and properties
        Path configDir = Path.of(args[1]);
        if (!Files.isDirectory(configDir)) {
            System.err.println("Error: config directory not found, path: [" + configDir + "]");
            System.exit(1);
        }
        Document filesDoc = readXml(configDir.resolve("bundle.wxf"));
        cleanupFiles(filesDoc);
        Map<String, String> overrides = loadOverrides(configDir.resolve("overrides.wxi"));
        overrides.put("JpAppVersion", args[4]);

        // extend template
        Path mainWxsPath = configDir.resolve("main.wxs");
        MainWxsNodes mainWxsNodes = readMainWxsNodes(mainWxsPath, overrides);
        insertComps(doc, mainWxsNodes.components);
        insertFeature(doc, mainWxsNodes.feature);
        setVersion(doc, args[2], args[3], args[4]);
        insertFiles(doc, filesDoc);

        // write output
        Path destXml = Path.of(args[5]);
        Document pretty = prettifyDoc(doc);
        writeWixXml(destXml, pretty);
    }

    private static void setVersion(Document doc, String versionFeature, String versionNumber, String versionNumberFourPositions) {
        Node wix = findChildWithNodeName(doc, "Wix");
        Node product = findChildWithNodeName(wix, "Product");

        Node productAttrName = product.getAttributes().getNamedItem("Name");
        productAttrName.setNodeValue(productAttrName.getNodeValue().replace(PLACEHOLDER_VERSION_NUMBER, versionNumber));
        Node productAttrVersion = product.getAttributes().getNamedItem("Version");
        productAttrVersion.setNodeValue(productAttrVersion.getNodeValue().replace(PLACEHOLDER_VERSION_NUMBER_FOUR_POSITIONS, versionNumberFourPositions));
        Node productAttrUpgradeCode = product.getAttributes().getNamedItem("UpgradeCode");
        productAttrUpgradeCode.setNodeValue(UUID.randomUUID().toString());

        Node rvcv = findRegistryValueCurrentVersion(product);
        Node rvcvAttrValue = rvcv.getAttributes().getNamedItem("Value");
        rvcvAttrValue.setNodeValue(rvcvAttrValue.getNodeValue().replace(PLACEHOLDER_VERSION_NUMBER, versionNumber));
        Node crkjh = findChildWithId(product, "comp_registry_runtime_java_home");
        Node rkjh = findChildWithId(crkjh, "registry_runtime_java_home");
        Node rkjhKey = rkjh.getAttributes().getNamedItem("Key");
        rkjhKey.setNodeValue(rkjhKey.getNodeValue().replace(PLACEHOLDER_VERSION_NUMBER, versionNumber));

        Node installDir = findInstallDirNode(product);
        Node installDirAttrName = installDir.getAttributes().getNamedItem("Name");
        installDirAttrName.setNodeValue(installDirAttrName.getNodeValue().replace(PLACEHOLDER_VERSION_FEATURE, versionFeature));
    }

    private static void insertComps(Document doc, List<Node> comps) {
        Node wix = findChildWithNodeName(doc, "Wix");
        Node product = findChildWithNodeName(wix, "Product");
        Node dir = findChildWithId(product, "TARGETDIR");
        Node first = null;
        for (Node node : comps) {
            if (Node.ELEMENT_NODE == node.getNodeType()) {
                Node adopted = doc.adoptNode(node);
                if (null == first) {
                    first = adopted;
                }
                product.insertBefore(adopted, dir.getPreviousSibling());
            }
        }
    }

    private static void cleanupFiles(Document filesDoc) {
        Node wix = findChildWithNodeName(filesDoc, "Wix");
        Node fragment = findChildWithNodeName(wix, "Fragment");
        DirRefs dirRefs = dirRefsFromFilesDoc(filesDoc);

        // prepare dirs for processing
        // directory id -> directory ref id
        Map<String, String> parentMap = new LinkedHashMap<>();
        // top level directories
        List<Node> topLevelDirs = new ArrayList<>();
        // all other directories
        Set<Node> unrootedDirs = new LinkedHashSet<>();
        // directory id -> directory
        Map<String, Node> dirsMap = new LinkedHashMap<>();
        for (Node dr : dirRefs.dirs) {
            Node dir = findChildWithNodeName(dr, "Directory");
            String dirId = dir.getAttributes().getNamedItem("Id").getNodeValue();
            String refId = dr.getAttributes().getNamedItem("Id").getNodeValue();
            dirsMap.put(dirId, dir);
            if ("INSTALLDIR".equals(refId)) {
                topLevelDirs.add(dir);
            } else {
                parentMap.put(dirId, refId);
                unrootedDirs.add(dir);
            }
        }
        topLevelDirs.sort(NAME_ATTR_COMPARATOR);

        // create dummy root install dir
        Node dummyInstallDir = filesDoc.createElement("Directory");
        dummyInstallDir.getAttributes().setNamedItem(filesDoc.createAttribute("Id"));
        dummyInstallDir.getAttributes().getNamedItem("Id").setNodeValue("INSTALLDIR");
        fragment.appendChild(dummyInstallDir);
        dirsMap.put("INSTALLDIR", dummyInstallDir);

        // build hierarchy attaching dirs to top level ones
        restoreDirHierarchy(topLevelDirs, unrootedDirs, parentMap);

        // remove dir refs between fragment and top level dirs
        attachTopLevelDirsToFragment(topLevelDirs, fragment, dummyInstallDir);

        // reformat components with file entries
        DirsWithComps dirsWithComps = processDirsWithComps(dirRefs, dirsMap, fragment);

        // component file entries need to be sorted
        reorderCompsInDirs(dirsWithComps.dirs);

        // rewrite dir ids to human-readable ones
        rewriteDirIds(dirsMap);

        // replace ids, sort and re-attach comp refs
        processCompRefs(fragment, dirsWithComps.compIdsMap);
    }

    private static void processCompRefs(Node fragment, Map<String, String> compIdsMap) {
        Node group = findChildWithId(fragment, "Files");
        group.getAttributes().getNamedItem("Id").setNodeValue("compgroup_files");
        List<Node> compRefs = new ArrayList<>();
        for (int i = 0; i < group.getChildNodes().getLength(); i++) {
            Node ref = group.getChildNodes().item(i);
            if ("ComponentRef".equals(ref.getNodeName())) {
                Node idAttr = ref.getAttributes().getNamedItem("Id");
                String newId = compIdsMap.get(idAttr.getNodeValue());
                if (null == newId) {
                    newId = "removeme";
                }
                idAttr.setNodeValue(newId);
                compRefs.add(ref);
            }
        }

        for (Node ref : compRefs) {
            group.removeChild(ref);
        }

        compRefs.sort(ID_ATTR_COMPARATOR);

        for (Node ref : compRefs) {
            if (!"removeme".equals(ref.getAttributes().getNamedItem("Id").getNodeValue())) {
                group.appendChild(ref);
            }
        }
    }

    private static void rewriteDirIds(Map<String, Node> dirsMap) {
        for (Node dir : dirsMap.values()) {
            String dirId = dir.getAttributes().getNamedItem("Id").getNodeValue();
            if ("INSTALLDIR".equals(dirId)) {
                continue;
            }
            List<String> names = new ArrayList<>();
            Node cur = dir;
            for (int depth = 0; depth < 32 && !"INSTALLDIR".equals(cur.getAttributes().getNamedItem("Id").getNodeValue()); depth++) {
                names.add(cur.getAttributes().getNamedItem("Name").getNodeValue());
                cur = cur.getParentNode();
            }
            if (!"INSTALLDIR".equals(cur.getAttributes().getNamedItem("Id").getNodeValue())) {
                throw new RuntimeException("Error collecting directory id, root dir not found");
            }

            StringBuilder sb = new StringBuilder("..\\jdk");
            for (int i = names.size() - 1; i >= 0; i--) {
                sb.append("\\");
                sb.append(names.get(i));
            }
            String id = idFromRelPath(sb.toString());
            dir.getAttributes().getNamedItem("Id").setNodeValue("dir_" + id);
        }
    }

    private static void reorderCompsInDirs(Set<Node> dirsWithComps) {
        for (Node dir : dirsWithComps) {
            List<Node> comps = new ArrayList<>();
            for (int i = 0; i < dir.getChildNodes().getLength(); i++) {
                Node comp = dir.getChildNodes().item(i);
                if ("Component".equals(comp.getNodeName())) {
                    comps.add(comp);
                }
            }
            for (Node comp : comps) {
                dir.removeChild(comp);
            }
            comps.sort(ID_ATTR_COMPARATOR);
            for (Node comp : comps) {
                dir.appendChild(comp);
            }
        }
    }

    private static DirsWithComps processDirsWithComps(DirRefs dirRefs, Map<String, Node> dirsMap, Node fragment) {
        Map<String, String> compIds = new LinkedHashMap<>();
        Set<Node> dirsWithComps = new LinkedHashSet<>();
        for (Node ref : dirRefs.comps) {
            String dirId = ref.getAttributes().getNamedItem("Id").getNodeValue();
            Node comp = findChildWithNodeName(ref, "Component");
            comp.getAttributes().removeNamedItem("Win64");
            comp.getAttributes().getNamedItem("Guid").setNodeValue("*");
            Node file = findChildWithNodeName(comp, "File");
            file.getAttributes().removeNamedItem("KeyPath");
            relativizeFile(file);
            String relPath = file.getAttributes().getNamedItem("Source").getNodeValue();
            String id = idFromRelPath(relPath);
            file.getAttributes().getNamedItem("Id").setNodeValue("file_" + id);
            String compIdOld = comp.getAttributes().getNamedItem("Id").getNodeValue();
            String compIdNew = "comp_" + id;
            compIds.put(compIdOld, compIdNew);
            comp.getAttributes().getNamedItem("Id").setNodeValue(compIdNew);

            Node dir = dirsMap.get(dirId);
            ref.removeChild(comp);
            fragment.removeChild(ref);
            dir.appendChild(comp);
            dirsWithComps.add(dir);
        }
        return new DirsWithComps(compIds, dirsWithComps);
    }

    private static void attachTopLevelDirsToFragment(List<Node> topLevelDirs, Node fragment, Node dummyInstallDir) {
        for (Node dir : topLevelDirs) {
            Node ref = dir.getParentNode();
            ref.removeChild(dir);
            fragment.removeChild(ref);
            dummyInstallDir.appendChild(dir);
        }
    }

    private static void restoreDirHierarchy(List<Node> topLevelDirs, Set<Node> unrootedDirs, Map<String, String> parentMap) {
        Map<String, Node> rootedDirs = new LinkedHashMap<>(topLevelDirs.size());
        for (Node dir : topLevelDirs) {
            String dirId = dir.getAttributes().getNamedItem("Id").getNodeValue();
            rootedDirs.put(dirId, dir);
        }

        for (int depth = 0; depth < 32 && unrootedDirs.size() > 0; depth++) {
            Map<String, Node> rootedChildren = new LinkedHashMap<>();
            for (Node dir : unrootedDirs) {
                String dirId = dir.getAttributes().getNamedItem("Id").getNodeValue();
                String parentId = parentMap.get(dirId);
                Node parent = rootedDirs.get(parentId);
                if (null != parent) {
                    Node ref = dir.getParentNode();
                    ref.removeChild(dir);
                    ref.getParentNode().removeChild(ref);
                    parent.appendChild(dir);
                    rootedChildren.put(dirId, dir);
                }
            }
            for (Node parent : rootedDirs.values()) {
                List<Node> children = new ArrayList<>();
                for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
                    Node child = parent.getChildNodes().item(i);
                    if ("Directory".equals(child.getNodeName())) {
                        children.add(child);
                    }
                }
                for (Node child : children) {
                    parent.removeChild(child);
                }
                children.sort(NAME_ATTR_COMPARATOR);
                for (Node child : children) {
                    parent.appendChild(child);
                }
            }
            for (Node rooted : rootedChildren.values()) {
                unrootedDirs.remove(rooted);
            }
            rootedDirs = rootedChildren;
        }

        if (0 != unrootedDirs.size()) {
            throw new RuntimeException("Error restoring directory hierarchy," +
                    " remaining unrooted dirs: [" + unrootedDirs + "]");
        }
    }

    private static DirRefs dirRefsFromFilesDoc(Document filesDoc) {
        Node wix = findChildWithNodeName(filesDoc, "Wix");
        Node fragment = findChildWithNodeName(wix, "Fragment");
        List<Node> comps = new ArrayList<>();
        List<Node> dirs = new ArrayList<>();
        for (int i = 0; i < fragment.getChildNodes().getLength(); i++) {
            Node dr = fragment.getChildNodes().item(i);
            if ("DirectoryRef".equals(dr.getNodeName())) {
                String id = dr.getAttributes().getNamedItem("Id").getNodeValue();
                if (!"TARGETDIR".equals(id)) {
                    for (int j = 0; j < dr.getChildNodes().getLength(); j++) {
                        Node node = dr.getChildNodes().item(j);
                        if ("Component".equals(node.getNodeName())) {
                            comps.add(dr);
                            break;
                        } else if ("Directory".equals(node.getNodeName())) {
                            dirs.add(dr);
                            break;
                        }
                    }
                }
            }
        }
        // last comp is not a file comp
        comps.remove(comps.size() - 1);
        return new DirRefs(comps, dirs);
    }

    private static void insertFiles(Document doc, Document filesDoc) {
        Node wix = findChildWithNodeName(doc, "Wix");
        Node product = findChildWithNodeName(wix, "Product");
        Node dir = findChildWithId(product, "TARGETDIR");
        Node filesWix = findChildWithNodeName(filesDoc, "Wix");
        Node fragment = findChildWithNodeName(filesWix, "Fragment");
        Node compGroup = findChildWithId(fragment, "compgroup_files");
        Node compGroupCloned = compGroup.cloneNode(true);
        Node compGroupAdopted = doc.adoptNode(compGroupCloned);
        product.insertBefore(compGroupAdopted, dir);
        Node feature = findChildWithNodeName(product, "Feature");
        Node compGroupRef = findChildWithNodeName(feature, "ComponentGroupRef");
        compGroupRef.getAttributes().getNamedItem("Id").setNodeValue("compgroup_files");

        Node srcInstDir = findChildWithNodeName(fragment, "Directory");
        Node pf64Dir = findChildWithId(dir, "ProgramFiles64Folder");
        Node vendorDir = findChildWithId(pf64Dir, "dir_vendor");
        Node destInstDir = findChildWithId(vendorDir, "INSTALLDIR");
        for (int i = 0; i < srcInstDir.getChildNodes().getLength(); i++) {
            Node node = srcInstDir.getChildNodes().item(i);
            String name = node.getNodeName();
            if ("Directory".equals(name) || "Component".equals(name)) {
                Node cloned = node.cloneNode(true);
                Node adopted = doc.adoptNode(cloned);
                destInstDir.appendChild(adopted);
            }
        }
    }

    private static void relativizeFile(Node file) {
        Node srcAttr = file.getAttributes().getNamedItem("Source");
        Path srcPath = Path.of(srcAttr.getNodeValue());
        Path parent = srcPath.getParent();
        boolean found = false;
        for (int i = 0; i < 32; i++) {
            if ("jdk".equals(parent.getFileName().toString()) &&
                    "images".equals(parent.getParent().getFileName().toString())) {
                found = true;
                break;
            }
            parent = parent.getParent();
        }
        if (!found) {
            return;
        }
        Path rel = Path.of("..", parent.getParent().relativize(srcPath).toString());
        srcAttr.setNodeValue(rel.toString());
    }

    private static String idFromRelPath(String relPath) {
        String shortened = relPath.substring("..\\jdk\\".length());
        String lower = shortened.toLowerCase();
        String replaced = lower.replaceAll("(-|\\s|\\.|\\\\)", "_");
        int len = replaced.length();
        int limit = 72 - 5;
        if (len <= limit) {
           return replaced;
        }
        int dropped = 0;
        String[] parts = replaced.split("_");
        for (int i = 1; i < parts.length && len - dropped > limit; i++) {
            dropped += parts[i].length();
            parts[i] = "";
        }
        return String.join("_", parts);
    }

    private static void insertFeature(Document doc, Node feature) {
        Node wix = findChildWithNodeName(doc, "Wix");
        Node product = findChildWithNodeName(wix, "Product");
        Node icon = findChildWithId(product, "icon_resources_icon_ico");
        Node adopted = doc.adoptNode(feature);
        product.insertBefore(adopted, icon.getNextSibling());
    }

    private static Node findInstallDirNode(Node product) {
        Node targetDir = findChildWithId(product, "TARGETDIR");
        Node pf64 = findChildWithId(targetDir, "ProgramFiles64Folder");
        Node vendorDir = findChildWithId(pf64, "dir_vendor");
        return findChildWithId(vendorDir, "INSTALLDIR");
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

    private static Node findChildWithNodeName(Node parent, String name) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node child = parent.getChildNodes().item(i);
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        throw new IllegalStateException("Child node not found, name: [" + name + "]");
    }

    private static Document readXml(Path path) throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            Reader reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            return docBuilder.parse(new InputSource(reader));
        }
    }

    private static void writeWixXml(Path path, Document doc) throws Exception {
        Node wix = findChildWithNodeName(doc, "Wix");
        wix.getAttributes().setNamedItem(doc.createAttribute("xmlns"));
        wix.getAttributes().getNamedItem("xmlns").setNodeValue("http://schemas.microsoft.com/wix/2006/wi");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(path.toFile());
        transformer.transform(source, result);
    }

    private static Document prettifyDoc(Document docUgly) throws Exception {
        // prettify
        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
        LSSerializer writer = impl.createLSSerializer();
        writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        LSOutput output = impl.createLSOutput();
        output.setEncoding(StandardCharsets.UTF_8.name());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        output.setByteStream(baos);
        writer.write(docUgly, output);

        // reload and return
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Reader reader = new InputStreamReader(bais, StandardCharsets.UTF_8);
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        return docBuilder.parse(new InputSource(reader));
    }

    private static Map<String, String> loadOverrides(Path overridesPath) throws Exception {
        List<String> lines = Files.readAllLines(overridesPath, StandardCharsets.UTF_8);
        Map<String, String> res = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = OVERRIDES_REGEX.matcher(line);
            if (matcher.matches()) {
                res.put(matcher.group(1), matcher.group(2));
            }
        }
        return res;
    }

    private static MainWxsNodes readMainWxsNodes(Path mainWxsPath, Map<String, String> overrides) throws Exception {
        List<String> mainWxsLines = Files.readAllLines(mainWxsPath, StandardCharsets.UTF_8);
        StringBuilder comps = new StringBuilder("<root>");
        StringBuilder feature = new StringBuilder();
        boolean compStartReached = false;
        boolean compEndReached = false;
        boolean featuresStartReached = false;
        for (String line : mainWxsLines) {
            String replaced = line;
            // note: maybe replace this map with a list
            for (Map.Entry<String, String> en : overrides.entrySet()) {
                replaced = replaced.replace("$(var." + en.getKey() + ")", en.getValue());
            }
            String trimmed = replaced.trim();
            if (!compStartReached) {
                if (COMPS_BEGIN_MARKER.equals(trimmed)) {
                    compStartReached = true;
                }
                continue;
            }
            if (!compEndReached) {
                if (COMPS_END_MARKER.equals(trimmed)) {
                    comps.append("</root>");
                    compEndReached = true;
                    continue;
                } else {
                    comps.append(replaced);
                }
            }
            if (!featuresStartReached) {
                if (FEATURES_BEGIN_MARKER.equals(trimmed)) {
                    featuresStartReached = true;
                }
                continue;
            }
            if (FEATURES_END_MARKER.equals(trimmed)) {
                break;
            }
            feature.append(replaced);
        }
        // parse
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document compsDoc = prettifyDoc(db.parse(new InputSource(new StringReader(comps.toString()))));
        Document featureDoc = prettifyDoc(db.parse(new InputSource(new StringReader(feature.toString()))));

        return new MainWxsNodes(compsDoc.getFirstChild().getChildNodes(), featureDoc.getFirstChild());
    }

    private static class MainWxsNodes {
        final List<Node> components;
        final Node feature;

        MainWxsNodes(NodeList components, Node feature) {
            this.components = new ArrayList<>(components.getLength());
            for (int i = 0; i < components.getLength(); i++) {
                this.components.add(components.item(i));
            }
            this.feature = feature;
        }
    }

    private static class DirRefs {
        final List<Node> comps;
        final List<Node> dirs;

        DirRefs(List<Node> comps, List<Node> dirs) {
            this.comps = comps;
            this.dirs = dirs;
        }
    }

    private static class DirsWithComps {
        final Map<String, String> compIdsMap;
        final Set<Node> dirs;

        public DirsWithComps(Map<String, String> compIdsMap, Set<Node> dirs) {
            this.compIdsMap = compIdsMap;
            this.dirs = dirs;
        }
    }

    private static class IdAttrComparator implements Comparator<Node> {
        @Override
        public int compare(Node o1, Node o2) {
            String id1 = o1.getAttributes().getNamedItem("Id").getNodeValue();
            String id2 = o2.getAttributes().getNamedItem("Id").getNodeValue();
            return id1.compareTo(id2);
        }
    }

    private static class NameAttrComparator implements Comparator<Node> {
        @Override
        public int compare(Node o1, Node o2) {
            String name1 = o1.getAttributes().getNamedItem("Name").getNodeValue();
            String name2 = o2.getAttributes().getNamedItem("Name").getNodeValue();
            return name1.compareTo(name2);
        }
    }
}

