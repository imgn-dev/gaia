#!/usr/bin/env bash
#
# Maintains the documentation in a checkout of the gh-pages branch, served at
# https://imgn-dev.github.io/gaia/:
#
#   api/<version>/   the API documentation of each released version, never
#                    changed once published, unless explicitly replaced
#   api/latest/      a copy of the newest version, rebuilt from scratch every
#                    time, so that nothing of an earlier version lingers in it
#   api/index.html   the list of versions, newest first
#   everything else  the guide of the newest version (docs/target/guide),
#                    replaced as a whole, so that no page of an earlier guide
#                    lingers
#
# Usage:  scripts/docs-site.sh publish <site> <version> <javadoc> [--replace]
#         scripts/docs-site.sh remove  <site> <version>
#         scripts/docs-site.sh guide   <site> <version> <guide>

set -euo pipefail

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

usage() {
    die "Usage: $0 publish <site> <version> <javadoc> [--replace]
       $0 remove  <site> <version>
       $0 guide   <site> <version> <guide>"
}

VERSION_PATTERN='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

# The released versions in the site, oldest first.
versions() {
    local dir
    for dir in "$site"/api/*/; do
        dir=${dir%/}
        dir=${dir##*/}
        if [[ $dir =~ $VERSION_PATTERN ]]; then
            printf '%s\n' "$dir"
        fi
    done | sort -V
}

refresh() {
    local all newest version
    all=$(versions)
    newest=$(tail -n 1 <<<"$all")

    rm -rf "$site/api/latest"
    [[ -n $newest ]] && cp -R "$site/api/$newest" "$site/api/latest"

    {
        cat <<'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Gaia API documentation</title>
</head>
<body>
<h1>Gaia API documentation</h1>
<ul>
EOF
        if [[ -n $newest ]]; then
            printf '<li><a href="latest/">Latest (%s)</a></li>\n' "$newest"
            for version in $(sort -rV <<<"$all"); do
                printf '<li><a href="%s/">%s</a></li>\n' "$version" "$version"
            done
        fi
        cat <<'EOF'
</ul>
</body>
</html>
EOF
    } >"$site/api/index.html"

    # GitHub Pages serves the files as they are, without Jekyll.
    touch "$site/.nojekyll"
}

[[ $# -ge 3 ]] || usage
action=$1
site=$2
version=$3
[[ -d $site ]] || die "$site is not a directory."
[[ $version =~ $VERSION_PATTERN ]] || die "Version '$version' must be MAJOR.MINOR.PATCH, e.g. 0.1.0."

case $action in
publish)
    [[ $# -eq 4 || ( $# -eq 5 && $5 == --replace ) ]] || usage
    javadoc=$4
    [[ -f $javadoc/index.html ]] || die "$javadoc has no index.html."
    if [[ -e $site/api/$version && $# -eq 4 ]]; then
        die "The documentation of $version is already published. Pass --replace to replace it."
    fi
    mkdir -p "$site/api"
    rm -rf "$site/api/$version"
    cp -R "$javadoc" "$site/api/$version"
    ;;
guide)
    [[ $# -eq 4 ]] || usage
    guide=$4
    [[ -f $guide/index.html ]] || die "$guide has no index.html."
    # Publishing an older version again must not bring back its guide.
    newest=$(versions | tail -n 1)
    if [[ $version != "$newest" ]]; then
        printf 'The guide of %s is not published: the newest version is %s.\n' "$version" "${newest:-none}"
        exit 0
    fi
    find "$site" -mindepth 1 -maxdepth 1 ! -name api ! -name .nojekyll ! -name .git -exec rm -rf {} +
    cp -R "$guide"/. "$site"/
    exit 0
    ;;
remove)
    [[ $# -eq 3 ]] || usage
    [[ -e $site/api/$version ]] || die "The documentation of $version is not published."
    rm -rf "$site/api/$version"
    ;;
*)
    usage
    ;;
esac

refresh
