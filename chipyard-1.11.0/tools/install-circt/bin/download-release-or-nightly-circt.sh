#!/usr/bin/env bash

set -eo pipefail

usage() {
  cat << EOF
USAGE: $0 -f <filename> -g <github-token> -i <install-directory> -v <version> [-x <version-file>]

Download the latesty published or nighlty version of "firtool", the MLIR-based
FIRRTL Compiler, from the "llvm/circt" repository.  Install "firtool" into a
directory specified by <install-directory>.  The version to install is specified
with the <version> argument.

OPTIONS:
    -f                    The filename of the CIRCT installation to install
    -g                    A GitHub token to authenticate with
    -h                    Display available options
    -i                    The installation directory
    -v                    The version of CIRCT to download
    -x                    A file containing version information
EOF
}

OPT_FILENAME=
OPT_GITHUB_TOKEN=
OPT_INSTALL_DIR=
OPT_VERSION=
OPT_VERSION_FILE=
while getopts "f:g:hi:v:x:" option; do
  case $option in
    f)
      OPT_FILENAME=$OPTARG
      ;;
    g)
      OPT_GITHUB_TOKEN=$OPTARG
      ;;
    h)
      usage
      exit 0
      ;;
    i)
      OPT_INSTALL_DIR=$OPTARG
      ;;
    v)
      OPT_VERSION=$OPTARG
      ;;
    x)
      OPT_VERSION_FILE=$OPTARG
      ;;
  esac
done

# Check option correctness
if [[ ! $OPT_FILENAME ]] || [[ ! $OPT_GITHUB_TOKEN ]] || [[ ! $OPT_INSTALL_DIR ]] || [[ ! $OPT_VERSION ]] ; then
  echo "missing one or moore mandatory options"
  usage
  exit 1
fi
if [[ $OPT_VERSION = "version-file" ]] && [[ ! $OPT_VERSION_FILE ]]; then
  echo "a '-x <version-file>' must be specified if '-v version-file' is specified"
  exit 1
fi

# If the version is in a version file, then read the value from the "version" key.
if [[ $OPT_VERSION = "version-file" ]]; then
  OPT_VERSION=$(jq -r .version < $OPT_VERSION_FILE)
fi

# Download either the specified release or the latest nightly into the
# installation directory.
mkdir -p $OPT_INSTALL_DIR
case $OPT_VERSION in
  "nightly")
    ARTIFACT_ID=$(
      curl -L \
           -H "Accept: application/vnd.github+json" \
           -H "Authorization: Bearer $OPT_GITHUB_TOKEN" \
           -H "X-GitHub-Api-Version: 2022-11-28" \
           "https://api.github.com/repos/llvm/circt/actions/artifacts?name=${OPT_FILENAME}&per_page=1" | \
        jq '.artifacts[0].id')
    echo $ARTIFACT_ID
    curl -L \
         -H "Accept: application/vnd.github+json" \
         -H "Authorization: Bearer $OPT_GITHUB_TOKEN" \
         -H "X-GitHub-Api-Version: 2022-11-28" \
         "https://api.github.com/repos/llvm/circt/actions/artifacts/$ARTIFACT_ID/zip" \
         --output download.zip
    unzip -p download.zip | tar -zx -C $OPT_INSTALL_DIR/ --strip-components 1
    rm download.zip
    ;;
  *)
    wget -O - https://github.com/llvm/circt/releases/download/$OPT_VERSION/$OPT_FILENAME | tar -zx -C $OPT_INSTALL_DIR/ --strip-components 1
    ;;
esac
