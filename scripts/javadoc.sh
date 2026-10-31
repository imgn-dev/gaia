#!/usr/bin/env bash
#
# Builds the API documentation of every published module of a gaia checkout as
# one site, the way the JDK documentation is built: a single javadoc run over
# all modules, so that the site has one module index, one search over every
# module, and links from one module to another and to the JDK.
#
# The published modules are those with a src/main/java/module-info.java; the
# *-tests modules have none. A module's src/main/java24, if any, is part of its
# sources, as in its javadoc jar.
#
# It works on the sources of any tag, so that older versions can be documented
# with the script of today.
#
# Usage:  scripts/javadoc.sh <checkout> <output directory>
#         scripts/javadoc.sh . target/apidocs

set -euo pipefail

# The JDK whose API the documentation links to. Every JDK type gaia uses is in
# it, including the Java 24 ones of src/main/java24.
JDK=25
# The element-list of its documentation, committed beside this script, so that
# building the documentation does not depend on docs.oracle.com being reachable:
# with -Werror, javadoc fails when it cannot fetch it. Copied from the JDK's
# docs/api/element-list; the links still go to docs.oracle.com.
JDK_ELEMENT_LIST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/jdk-$JDK

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

[[ $# -eq 2 ]] || die "Usage: $0 <checkout> <output directory>"
checkout=$(cd "$1" && pwd)
output=$2
[[ -f $checkout/.mvn/maven.config ]] || die "$checkout is not a gaia checkout."
version=$(sed -nE 's/.*-Drevision=([^[:space:]]+).*/\1/p' "$checkout/.mvn/maven.config")
[[ -n $version ]] || die "No -Drevision in $checkout/.mvn/maven.config."

modules=()
source_paths=()
for descriptor in "$checkout"/*/src/main/java/module-info.java; do
    [[ -e $descriptor ]] || continue
    # The module name follows the keyword, after an optional "open".
    name=$(grep -m 1 -oE '^(open[[:space:]]+)?module[[:space:]]+[A-Za-z0-9_.]+' "$descriptor" | awk '{print $NF}')
    [[ -n $name ]] || die "No module declaration in $descriptor."
    main=${descriptor%/java/module-info.java}
    path=$main/java
    [[ -d $main/java24 ]] && path=$path:$main/java24
    modules+=("$name")
    source_paths+=(--module-source-path "$name=$path")
done
[[ ${#modules[@]} -gt 0 ]] || die "No module in $checkout."
[[ -f $JDK_ELEMENT_LIST_DIR/element-list ]] || die "No $JDK_ELEMENT_LIST_DIR/element-list."

# The third-party modules the gaia modules require, from each module's compile
# class path. The gaia modules themselves come from their sources: compiling
# lets the reactor resolve them without their version being in a repository.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
(cd "$checkout" && ./mvnw -B -q --no-transfer-progress -DskipTests compile \
    dependency:build-classpath -DincludeScope=compile \
    -Dmdep.outputFile="$work/classpath-\${project.artifactId}.txt")
# The files end without a newline: awk adds it, so that the last entry of one
# file and the first of the next stay apart.
module_path=$(awk 1 "$work"/classpath-*.txt | tr ':' '\n' \
    | grep -v -e '^$' -e '/be/imgn/gaia/' -e '/target/classes' | sort -u | paste -sd: -)

rm -rf "$output"
# The same checks as the javadoc jars of the parent POM: -Werror for
# failOnWarnings, -Xdoclint:all,-missing for doclint.
javadoc -d "$output" -quiet -notimestamp -Werror -Xdoclint:all,-missing \
    --release "$JDK" \
    "${source_paths[@]}" \
    --module "$(IFS=,; echo "${modules[*]}")" \
    ${module_path:+--module-path "$module_path"} \
    -linkoffline "https://docs.oracle.com/en/java/javase/$JDK/docs/api/" "$JDK_ELEMENT_LIST_DIR" \
    -doctitle "Gaia $version API" \
    -windowtitle "Gaia $version API"
