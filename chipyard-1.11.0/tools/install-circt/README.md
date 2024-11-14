# Install CIRCT

Composite GitHub action to install [`llvm/circt`](https://github.com/llvm/circt)
libraries and binaries.

# Usage

```yaml
- name: Install CIRCT
  uses: circt/install-circt@v1.1.1
  with:
    # The version of CIRCT to install.  This must match a release of CIRCT,
    # e.g., 'firtool-1.56.1' or one of the following special strings:
    #   - 'nightly' installs the latest nightly
    #   - 'version-file' installs the release specified by a JSON file whose
    #     location is set by the 'version-file' input.  This is done to
    #     facilitate Continuous Delivery (CD) flows because GitHub Actions are
    #     not allowed to modify anything in the '.github/' directory.
    #
    # Default: 'version-file'
    version: ''

    # A path to a JSON file that sets the CIRCT version to use.  This is
    # required, but can be a dummy value if the user will not use the
    # 'version-file' option for the 'version' input.
    #
    # The format of this JSON file is as follows where '<version>' should be
    # replaced with a CIRCT release:
    #
    #     {
    #       "version": "<version>"
    #     }
    #
    # Default: './etc/circt.json'
    version-file: ''

    # The GitHub token used to download CIRCT nightly.
    #
    # Default: ${{ github.token }}
    github-token: ''

    # The name of the CIRCT tarball to install.  For available tarballs, see
    # what is uploaded by a nightly build or by a release.  Example values are:
    #   - 'circt-full-shared-linux-x64.tar.gz'
    #   - 'firrtl-bin-macos-x64.tar.gz'
    #
    # Default: 'circt-full-shared-linux-x64.tar.gz'
    file-name: ''

    # The directory where CIRCT will be installed.
    #
    # Default: 'circt'
    install-dir: ''
```
