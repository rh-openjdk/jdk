Building the installer
======================

Installer is completely optional and is not built by default.

Prerequisites
-------------

[WiX Toolset](https://wixtoolset.org/) is used to create an MSI installer from the jdk image and `jdk.wxs` descriptor.
Either WiX installer (`wix311.exe`) or WiX ZIP bundle (`wix311-binaries.zip`) can be used.

Configure
---------

Path to WiX can be specified to `configure` using `--with-wix` option:

```
bash configure --with-boot-jdk=... --with-jtreg=... --with-wix=C:/apps/wix311-binaries
...
checking for WiX toolset... yes, /cygdrive/c/apps/wix311-binaries
```

If option is not present, `configure` will try to autodetect it using `WIX` environment variable that is set by WiX installer:

```
bash configure --with-boot-jdk=... --with-jtreg=...
...
checking for WiX toolset... yes, /cygdrive/c/progra~2/wixtoo~1.11/
```

Build
-----

Installer will be created in `<buildroot>/images/installermsi` directory:

```
make installer-msi
...
Creating jdk image
Creating CDS archive for jdk image
Creating CDS-NOCOOPS archive for jdk image
Creating MSI installer in /cygdrive/c/projects/openjdk/jdk/build/windows-x86_64-server-release/images/installermsi
Stopping sjavac server
Finished building target 'installer-msi' in configuration 'windows-x86_64-server-release'
```
