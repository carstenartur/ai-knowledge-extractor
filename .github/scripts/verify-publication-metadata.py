#!/usr/bin/env python3
"""Validate the actual signed Maven repository, including Gradle's marker graph."""
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def verify(repository, version):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("public publication requires X.Y.Z")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    marker = "org.aiknowledge.extractor.gradle.plugin"
    artifacts = {
        "ai-knowledge-core": "org.aiknowledge",
        "ai-knowledge-maven-plugin": "org.aiknowledge",
        "ai-knowledge-gradle-plugin": "org.aiknowledge",
        "gradle-plugin": "org.aiknowledge",
        marker: "org.aiknowledge.extractor",
    }
    manifest = []
    for artifact, group in artifacts.items():
        directory = repository / group.replace(".", "/") / artifact / version
        pom = directory / f"{artifact}-{version}.pom"
        root = ET.parse(pom).getroot()
        for field, expected in [("groupId", group), ("artifactId", artifact), ("version", version)]:
            if root.findtext(f"m:{field}", namespaces=ns) != expected:
                raise ValueError(f"wrong {field} in {pom}")
        for field in ["name", "description", "url", "licenses/m:license/m:name",
                      "licenses/m:license/m:url", "developers/m:developer/m:id",
                      "scm/m:connection", "scm/m:url"]:
            if not (root.findtext(f"m:{field}", namespaces=ns) or "").strip():
                raise ValueError(f"missing {field} in {pom}")
        dependencies = []
        for dep in root.findall("m:dependencies/m:dependency", ns):
            values = tuple(dep.findtext(f"m:{field}", namespaces=ns)
                           for field in ["groupId", "artifactId", "version"])
            dependencies.append(values)
            if values[0].startswith("org.aiknowledge"):
                if artifacts.get(values[1]) != values[0] or values[2] != version:
                    raise ValueError(f"invalid internal dependency in {pom}: {values}")
        if artifact == marker:
            if ("org.aiknowledge", "gradle-plugin", version) not in dependencies:
                raise ValueError("marker must resolve the published Gradle implementation")
        else:
            for classifier in ["", "-sources", "-javadoc"]:
                jar = directory / f"{artifact}-{version}{classifier}.jar"
                with zipfile.ZipFile(jar) as archive:
                    if len(archive.namelist()) < 2 or archive.testzip():
                        raise ValueError(f"empty or corrupt JAR: {jar}")
            if artifact != "ai-knowledge-core" and (
                "org.aiknowledge", "ai-knowledge-core", version
            ) not in dependencies:
                raise ValueError(f"missing released Core dependency in {pom}")
        for path in sorted(directory.iterdir()):
            if path.suffix not in {".pom", ".jar", ".module"}:
                continue
            content = path.read_bytes()
            for algorithm in ["md5", "sha1"]:
                expected = path.with_name(path.name + "." + algorithm).read_text().strip()
                if hashlib.new(algorithm, content).hexdigest() != expected:
                    raise ValueError(f"invalid {algorithm}: {path}")
            subprocess.run(["gpg", "--batch", "--verify", str(path) + ".asc", str(path)],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            manifest.append({"path": str(path.relative_to(repository)),
                             "sha256": hashlib.sha256(content).hexdigest()})
    return {"version": version, "signedFiles": manifest, "publicAvailabilityVerified": False}


if __name__ == "__main__":
    print(json.dumps(verify(Path(sys.argv[1]), sys.argv[2]), indent=2))
