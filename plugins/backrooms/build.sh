#!/bin/bash
set -e
cd /tmp/claude-0/backrooms
rm -rf out && mkdir -p out
/usr/lib/jvm/java-25-openjdk-amd64/bin/javac --release 21 -encoding UTF-8 -nowarn -Xlint:none -cp "$(cat /tmp/claude-0/poderes/cp.txt):/tmp/claude-0/teste/libraries/org/joml/joml/1.10.9/joml-1.10.9.jar" -d out src/br/servidor/backrooms/*.java 2>&1 | grep -v JAVA_TOOL | grep -v "^Note:" || true
cp res/plugin.yml res/config.yml out/
rm -f Backrooms-1.0.jar
(cd out && /usr/lib/jvm/java-25-openjdk-amd64/bin/jar cf ../Backrooms-1.0.jar .) 2>&1 | grep -v JAVA_TOOL || true
ls -la Backrooms-1.0.jar
