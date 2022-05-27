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

import { js2xml } from "https://deno.land/x/js2xml@1.0.4/mod.ts";
import { xml2js } from "https://deno.land/x/xml2js@1.0.0/mod.ts";

// to support installation upgrades this UUID must be the same for old and new
// versions of the installer, this random value needs to be replaced with a
// new one that must be permanent and unique
const VENDOR_UPGRADE_CODE = crypto.randomUUID();

function existsSync(filePath) {
  try {
    Deno.lstatSync(filePath);
    return true;
  } catch (e) {
    if (e instanceof Deno.errors.NotFound) {
      return false;
    }
    throw e;
  }
}

function readXml(xmlPath) {
  const xml = Deno.readTextFileSync(xmlPath);
  return xml2js(xml, {
    compact: true,
  });
}

function writeXml(xmlPath, wix) {
  const xml = js2xml(wix, {
    compact: true,
    spaces: 4,
  });
  Deno.writeTextFileSync(xmlPath, xml);
}

function writeExtFiles() {
  const dir = "vendor_ext1";
  if (!existsSync(dir)) {
    Deno.mkdir(dir);
    Deno.writeTextFileSync(`${dir}/file1.txt`, "foo");
    Deno.writeTextFileSync(`${dir}/file2.txt`, "bar");
  }
}

function createVendorEnvComponent() {
  return {
    _attributes: {
      Id: "comp_env_vendor_java_home",
      Directory: "INSTALLDIR",
      Guid: crypto.randomUUID(),
      KeyPath: "yes",
    },
    Environment: {
      _attributes: {
        Id: "env_vendor_java_home",
        Name: "VENDOR_JAVA_HOME",
        Value: "[INSTALLDIR]",
        Action: "set",
        Part: "all",
        System: "yes",
      },
    },
  };
}

function createVendorEnvFeature() {
  return {
    _attributes: {
      Id: "jdk_env_vendor_java_home",
      Absent: "allow",
      AllowAdvertise: "no",
      Level: "2",
      Title: "VENDOR_JAVA_HOME Variable",
      Description: "Sets 'VENDOR_JAVA_HOME' system environment variable.",
    },
    ComponentRef: {
      _attributes: {
        "Id": "comp_env_vendor_java_home",
      },
    },
  };
}

function createVendorExtContentDir() {
  return {
    _attributes: {
      Id: "VENDOREXT1",
      Name: "vendor_ext1",
    },
    Component: [
      {
        _attributes: {
          Id: "comp_vendor_ext1_file1_txt",
          Guid: "*",
        },
        File: {
          _attributes: {
            Id: "file_vendor_ext1_file1_txt",
            Source: "../vendor_ext1/file1.txt",
          },
        },
      },
      {
        _attributes: {
          Id: "comp_vendor_ext1_file2_txt",
          Guid: "*",
        },
        File: {
          _attributes: {
            Id: "file_vendor_ext1_file2_txt",
            Source: "../vendor_ext1/file2.txt",
          },
        },
      },
    ],
  };
}

function createVendorExtContentFeature() {
  return {
    _attributes: {
      Id: "vendor_ext_content",
      Absent: "allow",
      AllowAdvertise: "no",
      Description: "Additional vendor-specific content",
      Level: "2",
      Title: "Vendor ext1 content",
    },
    ComponentRef: [
      {
        _attributes: {
          Id: "comp_vendor_ext1_file1_txt",
        },
      },
      {
        _attributes: {
          Id: "comp_vendor_ext1_file2_txt",
        },
      },
    ],
  };
}

function createCustomActionProp() {
  return {
    _attributes: {
      Id: "uninstall_cleanup_immediate",
      Property: "uninstall_cleanup_deferred",
      Value: '"[SystemFolder]cmd.exe" /c rd /s /q "[INSTALLDIR]"',
    },
  };
}

function createCustomAction() {
  return {
    _attributes: {
      Id: "uninstall_cleanup_deferred",
      BinaryKey: "WixCA",
      DllEntry: "WixQuietExec",
      Return: "ignore",
      Execute: "deferred",
      Impersonate: "no",
    },
  };
}

function createInstallExecuteSequence() {
  return {
    Custom: [
      {
        _attributes: {
          Action: "uninstall_cleanup_immediate",
          Before: "InstallInitialize",
        },
        _text: "REMOVE AND (NOT UPGRADINGPRODUCTCODE)",
      },
      {
        _attributes: {
          Action: "uninstall_cleanup_deferred",
          Before: "InstallFinalize",
        },
        _text: "REMOVE AND (NOT UPGRADINGPRODUCTCODE)",
      },
    ],
  };
}

function extend(wix) {
  const product = wix.Wix.Product;
  // vendor product attributes
  product._attributes.Name = "VENDOR OpenJDK";
  product._attributes.Manufacturer = "VENDOR Inc.";
  product._attributes.UpgradeCode = VENDOR_UPGRADE_CODE;
  product._attributes.Version = "1.2.3.4";

  // vendor help link
  for (const prop of product.Property) {
    if ("ARPHELPLINK" === prop._attributes.Id) {
      prop._attributes.Value = "https://openjdk.vendor.com/";
    }
  }

  // vendor env variable
  const updatedComps = [];
  for (const comp of product.Component) {
    updatedComps.push(comp);
    if ("comp_env_java_home" === comp._attributes.Id) {
      const vendorEnvComp = createVendorEnvComponent();
      updatedComps.push(vendorEnvComp);
    }
    // registry
    if ("comp_registry_runtime_current_version" === comp._attributes.Id) {
      comp.RegistryKey.RegistryValue._attributes.Value = "1.2.3";
    } else if ("comp_registry_runtime_java_home" === comp._attributes.Id) {
      comp.RegistryKey._attributes.Key = "Software\\JavaSoft\\JDK\\1.2.3";
    }
  }
  product.Component = updatedComps;
  const jdkFeature = product.Feature;
  jdkFeature._attributes.Title = "OpenJDK";
  jdkFeature._attributes.Display = "expand";
  const updatedChildren = [];
  for (const subfeature of jdkFeature.Feature) {
    updatedChildren.push(subfeature);
    if ("jdk_env_java_home" === subfeature._attributes.Id) {
      const vendorEnvFeature = createVendorEnvFeature();
      updatedChildren.push(vendorEnvFeature);
    }
  }
  jdkFeature.Feature = updatedChildren;

  // vendor install dir
  const targetDir = product.Directory;
  const programFilesDir = targetDir.Directory;
  const vendorDir = programFilesDir.Directory;
  vendorDir._attributes.Name = "VendorDirectory";
  const installDir = vendorDir.Directory;
  installDir._attributes.Name = "jdk-VENDOR_VERSION";

  // vendor additional component dir
  const vendorExt1Dir = createVendorExtContentDir();
  if (!installDir.Directory) {
    installDir.Directory = [];
  }
  installDir.Directory.push(vendorExt1Dir);

  // vendor additional component feature
  product.Feature = [product.Feature, createVendorExtContentFeature()];

  // vendor custom action
  product.CustomAction = [createCustomActionProp(), createCustomAction()];
  product.InstallExecuteSequence = createInstallExecuteSequence();
}


// main

if (2 != Deno.args.length) {
  console.log("Usage: deno run -A ExtendScript.js input.xml output.xml");
  Deno.exit(1);
}
const srcXml = Deno.args[0];
if (!existsSync(srcXml)) {
  console.error(
    "Error: specified input file does not exist, path: [" + srcXml + "]",
  );
  Deno.exit(1);
}

const destXml = Deno.args[1];
const wix = readXml(srcXml);
extend(wix);
writeExtFiles();
writeXml(destXml, wix);
console.log("Output XML written, path: [" + destXml + "]");
