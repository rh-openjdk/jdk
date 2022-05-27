MSI Installer extension examples
================================

OpenJDK vendors may want to use vanilla jdk installer as a "base layer" and extend it with vendor-specific features,
instead of creating their installers from scratch.

The following changes are expected:

 - change installer art to a vendor-branded one
 - change vendor name in installation directory and in `Manufacturer` field, change the "help" URL
 (that is displayed in `Programs and Features` system UI)
 - change version number of the installer package including additional vendor-specific version suffix
 - add new installation files and allow to select them in GUI as optional features
 - add new system integration entries - registry keys or environment variables
 - add custom actions that are performed at a specified point during installation/uninstallation 
 
Installer art can be adjusted by replacing the picture files, all other changes require to transform the 
installer XML descriptor. 
 
Installer art
-------------
 
Installer picture files (and a license file) are picked up by WiX from `installer_build_dir/resources` directory.
For vanilla installer these files are stored in `make/data/installermsi/resources` and are copied to the
`build` directory when building vanilla installer. To change these resources - vanilla picture files need to be
replaced with modified ones.
 
Transforming installer descriptor
---------------------------------
 
Installer description in a form of a single XML file can be produced with `make installer-msi-xml` target.

Various tools may be used to transform it, three different examples included with the implementation are described below.

Descriptor structure can be seen in its [XML schema](https://github.com/wixtoolset/wix3/blob/develop/src/tools/wix/Xsd/wix.xsd).
It contains a top-level `Product` element, its attributes and its first child `Package` element
contain the metadata of the installer.

Other `Product` child elements describe the set of files and features in the installer. Straightforward approach to
adjust it is to iterate over `Product` children, include additional ones and change attributes of the existing ones:

 - `Component` elements contain installation entries like files, registry keys or environment variables
 - `RegistryKey` and `Environment` elements (inside `Component`s) are listed first, after them installation
files are listed with `Directory` elements denoting the filesystem layout
 - `Feature` elements contain a set of installation features, they can contain other sub-features and
 `ComponentRef` references to `Component`s; each component must be referenced in a feature
 - `CustomAction` elements can be used to add a custom executable run at some stage of the installation/uninstallation

Extension examples
------------------

Three different examples are included with the implementation (in `jdk/test/jdk/tools/jpackage/windows/installermsi` directory),
all examples are functionally equivalent (produce the same modified MSI):

 - `ExtendDom.java`: XML is loaded as a DOM tree and then transformed using `org.w3c.dom` API
 - `ExtendJaxb.java`: XML is loaded as a strongly-typed JAXB-generated object
 - `ExtendScript.js`: XML is loaded as a JavaScript object

`vendor_ext1` directory, that is created by these examples, needs to be copied to `<jdkbuildroot>/images`
directory to allow the modified installer to be built.

Extending with DOM
------------------

`ExtendDom.java` is a standalone example, it loads and transform the XML using `org.w3c.dom` API 
that is included with jdk. This approach does not require any additional libraries or tools,
besides the JDK itself, but have a downside of DOM API being very verbose and weakly typed.

Usage:

```
java ExtendDom.java jdk.xml jdk_ext.xml
```

Extending with JAXB
-------------------

`ExtendJaxb.java` uses JAXB classes generated from WiX XSD schema to allow more safe and fluent way to transform the XML.
It requires the following libraries to be present on a classpath:

 - `msiextend-jaxb-1.0.jar` - generated JAXB classes, [download](https://github.com/akashche/msiextend-jaxb/releases/tag/1.0)
 - `jaxb-api-2.3.1.jar` - [download](https://repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.3.1/)
 - `jaxb-impl-2.3.1.jar` - [download](https://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.3.1/)
 - `istack-commons-runtime-4.0.1.jar` - [download](https://repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/4.0.1/)
 - `activation-1.1.1.jar` - [download](https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/)
 
Usage:

```
java -cp msiextend-jaxb-1.0.jar;jaxb-api-2.3.1.jar;jaxb-impl-2.3.1.jar;istack-commons-runtime-4.0.1.jar;activation-1.1.1.jar ExtendJaxb.java jdk.xml jdk_ext.xml
```

Extending with JavaScript
-------------------------

`ExtendScript.js` example uses [Deno JavaScipt runtime](https://deno.land/) and additional libraries to convert the XML
into a JavaScript object and transform it in a concise and declarative way. Dependencies are downloaded automatically
on a first script run. Similar approach can be used with other JavaScript runtimes (like Node.js or GraalJS),
Deno runtime was chosen for these example because it requires minimal setup.

Usage:

```
deno run -A ExtendScript.js jdk.xml jdk_ext.xml
```
