#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

CYDIR=$(git rev-parse --show-toplevel)
CYDIR=$CYDIR/chipyard-1.11.0

# get helpful utilities
source $CYDIR/scripts/utils.sh

common_setup

usage() {
    echo "Usage: ${0} [OPTIONS] [riscv-tools | esp-tools]"
    echo ""
    echo "Installation Types"
    echo "  riscv-tools: if set, builds the riscv toolchain (this is also the default)"
    echo "  esp-tools: if set, builds esp-tools toolchain used for the hwacha vector accelerator"
    echo ""
    echo "Helper script to fully initialize repository that wraps other scripts."
    echo "By default it initializes/installs things in the following order:"
    echo "   1. Conda environment"
    echo "   2. Unzip bigfiles"
    echo "   3. Toolchain collateral (Spike, PK, tests, libgloss)"
    echo "   4. Ctags"
    echo "   5. Chipyard pre-compile sources"
    echo "   6. FireSim"
    echo "   7. FireSim pre-compile sources"
    echo "   8. FireMarshal"
    echo "   9. FireMarshal pre-compile default buildroot Linux sources"
    echo "  10. Install CIRCT"
    echo "  11. Runs repository clean-up"
    echo ""
    echo "**See below for options to skip parts of the setup. Skipping parts of the setup is not guaranteed to be tested/working.**"
    echo ""
    echo "Options"
    echo "  --help -h               : Display this message"
    echo "  --verbose -v            : Verbose printout"
    echo "  --use-unpinned-deps -ud : Use unpinned conda environment"
    echo "  --use-lean-conda        : Install a leaner version of the repository (Smaller conda env, no FireSim, no FireMarshal)"

    echo "  --skip -s N             : Skip step N in the list above. Use multiple times to skip multiple steps ('-s N -s M ...')."
    echo "  --skip-conda            : Skip Conda initialization (step 1)"
    echo "  --skip-submodules       : Skip submodule initialization (step 2)"
    echo "  --skip-toolchain        : Skip toolchain collateral (step 3)"
    echo "  --skip-ctags            : Skip ctags (step 4)"
    echo "  --skip-precompile       : Skip precompiling sources (steps 5/7)"
    echo "  --skip-firesim          : Skip Firesim initialization (steps 6/7)"
    echo "  --skip-marshal          : Skip firemarshal initialization (steps 8/9)"
    echo "  --skip-circt            : Skip CIRCT install (step 10)"
    echo "  --skip-clean            : Skip repository clean-up (step 11)"

    exit "$1"
}

TOOLCHAIN_TYPE="riscv-tools"
VERBOSE=false
VERBOSE_FLAG=""
USE_UNPINNED_DEPS=false
USE_LEAN_CONDA=false
SKIP_LIST=()

# getopts does not support long options, and is inflexible
while [ "$1" != "" ];
do
    case $1 in
        -h | --help )
            usage 3 ;;
        riscv-tools | esp-tools)
            TOOLCHAIN_TYPE=$1 ;;
        --verbose | -v)
            VERBOSE_FLAG=$1
            set -x ;;
        --use-lean-conda)
            USE_LEAN_CONDA=true
            SKIP_LIST+=(4 6 7 8 9) ;;
        -ud | --use-unpinned-deps )
            USE_UNPINNED_DEPS=true ;;
        --skip | -s)
            shift
            SKIP_LIST+=(${1}) ;;
        --skip-conda)
            SKIP_LIST+=(1) ;;
        --skip-submodules)
            SKIP_LIST+=(2) ;;
        --skip-toolchain)
            SKIP_LIST+=(3) ;;
        --skip-ctags)
            SKIP_LIST+=(4) ;;
        --skip-precompile)
            SKIP_LIST+=(5 6) ;;
        --skip-firesim)
            SKIP_LIST+=(6 7) ;;
        --skip-marshal)
            SKIP_LIST+=(8 9) ;;
        --skip-circt)
            SKIP_LIST+=(10) ;;
        --skip-clean)
            SKIP_LIST+=(11) ;;
        --force | -f | --skip-validate) # Deprecated flags
            ;;
        * )
            error "invalid option $1"
            usage 1 ;;
    esac
    shift
done

# return true if the arg is not found in the SKIP_LIST
run_step() {
    local value=$1
    [[ ! " ${SKIP_LIST[*]} " =~ " ${value} " ]]
}

{

# esp-tools should ONLY be used for hwacha.
# Check for this, since many users will be attempting to use this with gemmini
if [ $TOOLCHAIN_TYPE == "esp-tools" ]; then
    while true; do
        printf '\033[2J'
        read -p "WARNING: You are trying to install the esp-tools toolchain."$'\n'"This should ONLY be used for Hwacha development."$'\n'"Gemmini should be used with riscv-tools."$'\n'"Type \"y\" to continue if this is intended, or \"n\" if not: " validate
        case "$validate" in
            y | Y)
                echo "Installing esp-tools."
                break
                ;;
            n | N)
                error "Rerun with riscv-tools"
                exit 3
                ;;
            *)
                error "Invalid response. Please type \"y\" or \"n\""
                ;;
        esac
    done
fi


#######################################
###### BEGIN STEP-BY-STEP SETUP #######
#######################################

# In order to run code on error, we must handle errors manually
set +e;

function begin_step
{
    thisStepNum=$1;
    thisStepDesc=$2;
    echo " ========== BEGINNING STEP $thisStepNum: $thisStepDesc =========="
}
function exit_if_last_command_failed
{
    local exitcode=$?;
    if [ $exitcode -ne 0 ]; then
        die "Build script failed with exit code $exitcode at step $thisStepNum: $thisStepDesc" $exitcode;
    fi
}

# setup and install conda environment
if run_step "1"; then
    begin_step "1" "Conda environment setup"
    # note: lock file must end in .conda-lock.yml - see https://github.com/conda-incubator/conda-lock/issues/154
    CONDA_REQS=$CYDIR/conda-reqs
    CONDA_LOCK_REQS=$CONDA_REQS/conda-lock-reqs
    # must match with the file generated by generate-conda-lockfile.sh
    if [ "$USE_LEAN_CONDA" = false ]; then
      LOCKFILE=$CONDA_LOCK_REQS/conda-requirements-$TOOLCHAIN_TYPE-linux-64.conda-lock.yml
    else
      LOCKFILE=$CONDA_LOCK_REQS/conda-requirements-$TOOLCHAIN_TYPE-linux-64-lean.conda-lock.yml
    fi

    if [ "$USE_UNPINNED_DEPS" = true ]; then
        # auto-gen the lockfiles
        $CYDIR/scripts/generate-conda-lockfiles.sh
        exit_if_last_command_failed
    fi
    echo "Using lockfile: $LOCKFILE"

    # use conda-lock to create env
    conda-lock install --conda $(which conda) -p $CYDIR/.conda-env $LOCKFILE &&
    source $CYDIR/.conda-env/etc/profile.d/conda.sh &&
    conda activate $CYDIR/.conda-env
    exit_if_last_command_failed

    # Conda Setup
    # Provide a sourceable snippet that can be used in subshells that may not have
    # inhereted conda functions that would be brought in under a login shell that
    # has run conda init (e.g., VSCode, CI)
    read -r -d '\0' CONDA_ACTIVATE_PREAMBLE <<'END_CONDA_ACTIVATE'
if ! type conda >& /dev/null; then
    echo "::ERROR:: you must have conda in your environment first"
    return 1  # don't want to exit here because this file is sourced
fi

# if we're sourcing this in a sub process that has conda in the PATH but not as a function, init it again
conda activate --help >& /dev/null || source $(conda info --base)/etc/profile.d/conda.sh
\0
END_CONDA_ACTIVATE

    replace_content env.sh build-setup-conda "# line auto-generated by $0
$CONDA_ACTIVATE_PREAMBLE
conda activate $CYDIR/.conda-env
source $CYDIR/scripts/fix-open-files.sh"

fi

if [ -z ${CONDA_DEFAULT_ENV+x} ]; then
    echo "!!!!! WARNING: No conda environment detected. Did you activate the conda environment (e.x. 'conda activate base')?"
fi

# initialize all submodules (without the toolchain submodules)
if run_step "2"; then
    begin_step "2" "Unzipping bigfiles"
    $CYDIR/bigfiles/script_cp.sh
    exit_if_last_command_failed
fi

# build extra toolchain collateral (i.e. spike, pk, riscv-tests, libgloss)
if run_step "3"; then
    begin_step "3" "Building toolchain collateral"
    if run_step "1"; then
        PREFIX=$CONDA_PREFIX/$TOOLCHAIN_TYPE
    else
        if [ -z "$RISCV" ] ; then
            error "ERROR: If conda initialization skipped, \$RISCV variable must be defined."
            exit 1
        fi
        PREFIX=$RISCV
    fi
    $CYDIR/scripts/build-toolchain-extra.sh $TOOLCHAIN_TYPE -p $PREFIX
    exit_if_last_command_failed
fi

# run ctags for code navigation
if run_step "4"; then
    begin_step "4" "Running ctags for code navigation"
    $CYDIR/scripts/gen-tags.sh
    exit_if_last_command_failed
fi

# precompile chipyard scala sources
if run_step "5"; then
    begin_step "5" "Pre-compiling Chipyard Scala sources"
    pushd $CYDIR/sims/verilator &&
    make launch-sbt SBT_COMMAND=";project chipyard; compile" &&
    make launch-sbt SBT_COMMAND=";project tapeout; compile" &&
    popd
    exit_if_last_command_failed
fi

# setup firesim
if run_step "6"; then
    begin_step "6" "Setting up FireSim"
    $CYDIR/scripts/firesim-setup.sh &&
    $CYDIR/sims/firesim/gen-tags.sh
    exit_if_last_command_failed

    # precompile firesim scala sources
    if run_step "7"; then
        begin_step "7" "Pre-compiling Firesim Scala sources"
        pushd $CYDIR/sims/firesim &&
        (
            set -e # Subshells un-set "set -e" so it must be re enabled
            echo $CYDIR
            source sourceme-manager.sh --skip-ssh-setup
            pushd sim
            make sbt SBT_COMMAND="project {file:$CYDIR}firechip; compile" TARGET_PROJECT=firesim
            popd
        )
        exit_if_last_command_failed
        popd
    fi
fi

# setup firemarshal
if run_step "8"; then
    begin_step "8" "Setting up FireMarshal"
    pushd $CYDIR/software/firemarshal
    #pushd $CYDIR/software/firemarshal &&
    #./init-submodules.sh
    #exit_if_last_command_failed

    # precompile firemarshal buildroot sources
    if run_step "9"; then
        begin_step "9" "Pre-compiling FireMarshal buildroot sources#"
        source $CYDIR/scripts/fix-open-files.sh &&
        #./marshal $VERBOSE_FLAG build br-base.json &&
        ./marshal $VERBOSE_FLAG -d build br-base.json &&
        ./marshal $VERBOSE_FLAG -d install -t prototype br-base.json &&
        ./marshal $VERBOSE_FLAG clean br-base.json
        exit_if_last_command_failed
    fi
    popd
fi

if run_step "10"; then
    # install circt into conda
    if run_step "1"; then
        PREFIX=$CONDA_PREFIX/$TOOLCHAIN_TYPE
    else
        if [ -z "$RISCV" ] ; then
            error "ERROR: If conda initialization skipped, \$RISCV variable must be defined."
            exit 1
        fi
        PREFIX=$RISCV
    fi

    git submodule update --init $CYDIR/tools/install-circt &&
    $CYDIR/tools/install-circt/bin/download-release-or-nightly-circt.sh \
        -f circt-full-shared-linux-x64.tar.gz \
        -i $PREFIX \
        -v version-file \
        -x $CYDIR/conda-reqs/circt.json \
        -g null
    exit_if_last_command_failed
fi


# do misc. cleanup for a "clean" git status
if run_step "11"; then
    begin_step "10" "Cleaning up repository"
    $CYDIR/scripts/repo-clean.sh
    exit_if_last_command_failed
fi

echo "Setup complete!"

} 2>&1 | tee build-setup.log
