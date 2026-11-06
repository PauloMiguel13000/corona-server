#!/bin/bash
set -e
cd /tmp/claude-0/coronapack
rm -rf out && mkdir -p out
/usr/lib/jvm/java-25-openjdk-amd64/bin/javac --release 21 -encoding UTF-8 -nowarn -Xlint:none -cp "$(cat cp-extra.txt)" -d out src/br/servidor/coronapack/*.java 2>&1 | grep -v JAVA_TOOL | grep -v "^Note:" || true
cp -r res/plugin.yml res/config.yml res/pack out/
rm -f CoronaPack-1.0.jar
(cd out && /usr/lib/jvm/java-25-openjdk-amd64/bin/jar cf ../CoronaPack-1.0.jar .) 2>&1 | grep -v JAVA_TOOL || true
ls -la CoronaPack-1.0.jar
