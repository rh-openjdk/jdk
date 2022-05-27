Testing the installer
=====================

A set of JTreg tests in included with the implementation.

Vanilla installer tests
-----------------------

There are 7 tests that run checks on a resulting installer from "make installer-msi" target output. All 
these tests perform actual installation and uninstallation of an MSI package using system `msiexec` utility, thus
they can only be run under an OS user with `Administrator` privileges.

`make installer-msi` target must be run before running these tests.

When running with `make run-test`, JTreg tests can run in parallel. While added tests
use `msiexec` through `runMsiexecWithRetries` support utility, they still cannot be run in
parallel to each other. Unlike other `jpackage` MSI tests, they perform installation/uninstallation
of the same MSI package and `uninstall` call in one test affects all other tests that are running at the same time
and are performing checks on installed contents.

To solve this (without requiring `JTREG="JOBS=1"` option) all 7 tests are packed into a single 
`jdk/tools/jpackage/windows/WinInstallerMsiTest.java` test and are run strictly sequentially
using jpackage `jdk.jpackage.test.Main` test runner.

Usage:

```
make run-test TEST=jdk/tools/jpackage/windows/WinInstallerMsiTest.java
...
Running test 'jtreg:test/jdk/tools/jpackage/windows/WinInstallerMsiTest.java'
Passed: tools/jpackage/windows/WinInstallerMsiTest.java
Test results: passed: 1
```

Extension tests
---------------

There are 3 extension tests, that cover the extending of a vanilla installer, perform a transformation
of an XML descriptor and create a new installer package that is then being installed/uninstalled.
Two of these tests use Java API (DOM and JAXB) to perform the transformation, and one uses JavaScript.
Like vanilla test, these 3 tests are included into a single `jdk/tools/jpackage/windows/WinInstallerMsiExtendTest.java`
test with `jdk.jpackage.test.Main` test runner.

`make installer-msi-xml` target must be run before running these tests.

These tests require the following tools and resources to be specified using system environment variables:

 - `WIX`: path to WiX toolset directory, required by all extension tests
 - `DENO_HOME`: path to the directory where [Deno JavaScript runtime](https://deno.land/) resides, required by `SCRIPT` test run;
when not found - `SCRIPT` run is skipped
 - `INSTALLERMSI_JAXB_EXTEND_LIBS_DIR`: path to the directory with the set of JAXB libraries, required by `JAXB` test run;
 when not found - `JAXB` run is ignored`:
   - `msiextend-jaxb-1.0.jar` - generated JAXB classes, [download](https://github.com/akashche/msiextend-jaxb/releases/tag/1.0)
   - `jaxb-api-2.3.1.jar` - [download](https://repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.3.1/)
   - `jaxb-impl-2.3.1.jar` - [download](https://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.3.1/)
   - `istack-commons-runtime-4.0.1.jar` - [download](https://repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/4.0.1/)
   - `activation-1.1.1.jar` - [download](https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/)
   
Usage:

```
make run-test TEST=jdk/tools/jpackage/windows/WinInstallerMsiExtendTest.java
...
Running test 'jtreg:test/jdk/tools/jpackage/windows/WinInstallerMsiExtendTest.java'
Passed: tools/jpackage/windows/WinInstallerMsiExtendTest.java
Test results: passed: 1
```

