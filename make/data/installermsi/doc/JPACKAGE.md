Vanilla installer with JPackage
===============================

`jpackage` from [JEP 392: Packaging Tool](https://openjdk.java.net/jeps/392) is a universal packaging tool for
java applications. Besides applications, it can also package "runtime images" from a jdk runtime files.
`jpackage` also supports creating MSI packages using WiX toolset under the hood. 

Creating MSI installers
-----------------------

[MSI installers](https://en.wikipedia.org/wiki/Windows_Installer) built with a [WiX toolset](https://wixtoolset.org/)
are a de-facto standard for Windows installers.
This approach is used by many big open-source projects like Node.js or CMake. In general, such installers are created
by running utilities from WiX toolset providing an XML description as an input.

Installer XML description must contain (besides other data) a list of file paths for all files to be included into installer.
For vanilla jdk installer such list can be maintained manually in a static form, but such approach appeared to be 
undesirable due to the possible variability in the "make images" output depending on autoconf options and a toolchain used.

Collecting the files from the filesystem and writing them to XML in WiX format is not a trivial task, if we take into
account the long term maintenance for such build-time tool. Fortunately this functionality already exists in `jpackage` tool.

JPackage usage
--------------

In current implementation of a `make installer-msi` target, after jdk images is created, additional resources
are copied to `images/installermsi` directory and then `jpackage` is invoked specifying `images/jdk` directory
and a resources directory as an input. Resulting MSI installer is created in `images/installermsi` directory.

JPackage and a custom template
------------------------------

`jpackage` allows to use custom templates instead of the default files [main.wxs](https://github.com/openjdk/jdk/blob/master/src/jdk.jpackage/windows/classes/jdk/jpackage/internal/resources/main.wxs)
and [overrides.wxi](https://github.com/openjdk/jdk/blob/master/src/jdk.jpackage/windows/classes/jdk/jpackage/internal/resources/overrides.wxi).

Existing implementation uses custom `main.wxs` template that is based on a default with the following modifications:

 - `JpProductName` is introduced in addition to `JpAppName`
 - a set of Windows Registry keys is added to allow java applications to discover installed jdk
 - support for environment variables `PATH` and `JAVA_HOME`
 - support for registering `.jar` files association with Windows File Explorer (such support already exists in
 `jpackage` but appeared to be non-trivial to use with runtime images)
 - use of a `FeatureTree` WiX UI component for installer GUI forms

JPackage additional features
----------------------------

It is intended for all features, that are currently added to a custom `main.wxs` template, to contribute
their support to the `jpackage` tool itself. To be able to create vanilla jdk installer using `jpackage`
with its default template.

`jpackage` already includes the support for files association with Windows File Explorer, it is expected that this feature
will require minimal tuning to be used in vanilla jdk installer.

`jpackage` is not very flexible with the installer labels, version numbers and a default installation path.
It is required to have 3 different version numbers in installer:

 - major version: `19` (default installation path)
 - user-friendly version: `19.0.1` (product name)
 - version with four positions: `19.0.1.0` (internal MSI versioning)
 
Installation path needs to allow to include a "vendor directory" (as a parent of "install directory") to be
compatible with JEP draft JDK Packaging Guidelines (JDK-8278252).
 
`jpackage` currently discovers the WiX toolset installation automatically and does not allow to specify the path
to it with a command-line argument. To have installer build process more robust (in different environments)
it may be better to introduce such feature to `jpackage`. Current implementation performs WiX toolset discovery
(with a possible manual override) on a `configure` step, but the resulting autoconf variables are currently not
used during the build.  

JPackage output XML
-------------------

`jpackage`, being an universal packaging tool, generates description files with a sole purpose to be used as an input
to WiX toolset. Resulting set of XML and XML-like files is not optimized to be easily extendable. This implementation adds
a `CreateExtXml.java` build tool that transforms `jpackage` output into a single XML file that should be easier to extend.

While the functionality from `CreateExtXml.java` build tool in theory can be added to `jpackage` itself,
it is currently not clear whether such functionality may have any use outside of jdk runtime installers. Thus
currently it is intended to keep this functionality in a separate build tool.
