#!/bin/bash
# uso: compilar.sh <nome-da-pasta-em-fontes> <saida.jar>
P=/tmp/claude-0/novo/fontes/$1; OUT="$2"
CP="$(cat /tmp/claude-0/poderes/cp.txt):/tmp/claude-0/teste/libraries/org/joml/joml/1.10.9/joml-1.10.9.jar:/tmp/claude-0/teste/libraries/org/apache/logging/log4j/log4j-api/2.26.0/log4j-api-2.26.0.jar:/tmp/claude-0/teste/libraries/org/apache/logging/log4j/log4j-core/2.26.0/log4j-core-2.26.0.jar"
T=$(mktemp -d); mkdir -p $T/out
/usr/lib/jvm/java-25-openjdk-amd64/bin/javac --release 21 -encoding UTF-8 -nowarn -Xlint:none -proc:none -cp "$CP:$EXTRA_CP" -d $T/out $(find "$P/src" -name '*.java') > $T/log 2>&1
rc=$?
grep -v JAVA_TOOL_OPTIONS $T/log | grep -v '^Note:'
if [ $rc -ne 0 ]; then echo "FALHOU ($P)"; rm -rf $T; exit 1; fi
cp -r "$P"/res/* $T/out/
rm -f "$OUT"
(cd $T/out && /usr/lib/jvm/java-25-openjdk-amd64/bin/jar cf "$OUT" . 2>&1 | grep -v JAVA_TOOL_OPTIONS)
rm -rf $T
echo "ok $OUT"
