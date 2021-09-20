# Overview
Releasing a new version is a rather involved process and there are several important aspects to keep in mind:
1. Native libraries need to be prebuilt for each of 12 supported architectures and committed to the repository.
2. Versions in `src/java/jssc/SerialNativeInterface.java` and in `pom.xml` should be in sync. Note that version in `pom.xml` should have a `-SNAPSHOT` suffix appended to it most of the time.
3. Release artifact should be built using JDK11 to ensure maximum compatibility between JREs.
4. Maven central deployment is handled by maven-relase-plugin + nexus-staging-maven-plugin.
5. Manual release from Maven staging repository must be performed by a project member with access to group `io.github.java-native` on https://oss.sonatype.org.
5. [Github Releases](https://github.com/java-native/jssc/releases) deployment follows Maven central release. Note, the artifact deployed to Github is a fat-jar, so it is **not** the same artifact that is sent to Maven Central. It must be built from the same git revion though.
6. During the release process, the `pom.xml` version is bumped automatically, but it needs to be matched in several places that reference it afterwards.

# Automatic pipelines
The steps described above are automated using Github actions, but still some manual supervision is required:
* [ ] Create a release branch, e.g. `2.9.5-release`
* [ ] Rebuild precompiled libaries: trigger [this pipeline](https://github.com/java-native/jssc/actions/workflows/cross-compile.yml) on the release branch. This step may be skipped if rebuilding libraries is not needed or performed outside of the release.
    * [ ] Make sure all artifacts have been pushed to the newly created branch (suffixed with `-precompiled-natives`)
        * [ ] Native libraries for PC (Linux / Windows, see below)
        * [ ] Native libraries for Mac (see below)
        * [ ] Native libraries for Solaris (see below)
        * [ ] `jssc.SerialNativeInterface`'s version is up to date with version in `pom.xml` (without `-SNAPSHOT` qualifier)
    * [ ] If artifacts are generated correctly - merge the `*-precompiled-natives` branch into release branch. The pipeline may be rerun multiple times. The `*-precompiled-natives` branch will be dropped and recreated each time.
* [ ] Trigger [Release New Version](https://github.com/java-native/jssc/actions/workflows/release-new-version.yml) github action
* [ ] Login to [OSSRH](https://oss.sonatype.org/#stagingRepositories) and verify the artifacts are in good shape. This is the time for last minute integration tests:
    * [ ] Are compiled classes at bytecode version 50 (Java 6)?
    * [ ] Is there module-info.class in the package?
    * [ ] Are there no undesired entries in jar manifest file?
    * [ ] Is the artifact compatible with maven projects build with jdk7,8,11...17? Note, staging repository is publicly available, and may be included in test project in `<repositories>` section of `pom.xml`.
    * [ ] Is the artifact usable on JRE6...17?
    * [ ] Is the artifact usable on all supported OSes?
    * [ ] Is the version in `README.md` up to date?
    * [ ] Is the version in the wiki up to date?
* Check the [Github Releases](https://github.com/java-native/jssc/releases) page 
    * [ ] Verify the release has been created on the correct tagged commit
    * [ ] Verify the sources and javadoc jars are included
    * [ ] Verify jar *with dependencies* is included
    * [ ] Update release notes in the description
* [ ] In case of errors with any of the steps so far - you may discard the branch, drop the repository in OSSRH and start over
* [ ] If all is good:
    * [ ] Release the artifact from OSSRH staging repository 
    * [ ] Merge release branch back to master. **Do not** use rebase as to preserve tagged revision.

# Building native libraries manually
### Ubuntu (for Linux and Windows)
* Use oldest possible Ubuntu version (e.g. 14.04) and follow [[Compiling]] or [[Cross-Compilation]] guide respectively.
* Commit the new `.so` or `.dll` file inside `/src/main/resources-precompiled/natives/linux_64/`, etc.

### Solaris
(Built using Solaris 10)
```bash
# Optionally enable SSH from root
vi /etc/ssh/sshd_config # Change PermitRootLogin yes
svcadm restart ssh

# Enable vi support through ssh
TERM=xterm
export TERM

# Add OpenCSW repos
pkgadd -d http://get.opencsw.org/now
/opt/csw/bin/pkgutil -U
/opt/csw/bin/pkgutil -y -i git
# /opt/csw/bin/pkgutil -y -i cmake # broken on Solaris 10
/opt/csw/bin/pkgutil -y -i wget
/opt/csw/bin/pkgutil -y -i libstlport1

wget http://mirror.opencsw.org/opencsw/allpkgs/cmake-3.6.3%2cREV%3d2016.11.13.2-SunOS5.10-i386-CSW.pkg.gz
gunzip *.pkg.gz
pkgadd -d cmake-*

# Add git, cmake, gcc to path
PATH=$PATH:/usr/sfw/bin:/opt/csw/bin
export PATH

# clone repo
git clone https://github.com/java-native/jssc
# remember to checkout the proper branch

cd jssc

# build the 32-bit binary
mkdir build && cd build
rm -f CMakeCache.txt && cmake .. && cmake --build .
cp -Rf natives/ ../src/main/resources-precompiled/

# build the 64-bit binary
rm -f CMakeCache.txt && cmake .. -DFORCE_M64=True && cmake --build .
cp -Rf natives/ ../src/main/resources-precompiled/
```
* Commit changes and push to branch

### MacOS
#### x86_64
These steps are for publishing a 10.8-compatible binary using the 10.9 SDK.  They should work on MacOS 10.9 or higher.
* Install XCode and Command Line Tools
* Install Homebrew
   ```bash
   brew install cmake git maven
   ```
* Install the 10.9 SDKs from XCode 6.2 https://stackoverflow.com/a/10335943/3196753
   * Double-click the DMG, Right-click XCode, Show Package Contents
   * Show Package Contents
   * Copy `Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs` to `/Applications/XCode.app/`
* Compile manually using `cmake`
   ```bash
   cd jssc # if not already
   mkdir build && cd build
   rm -f CMakeCache.txt # clear previous build cache
   cmake .. -DCMAKE_OSX_SYSROOT=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -DCMAKE_OSX_DEPLOYMENT_TARGET=10.8 -DCMAKE_OSX_ARCHITECTURES=x86_64
   make
   ```
* Commit the new `.dylib` file inside `/src/main/resources-precompiled/natives/osx_64/`

#### aarch64
These steps are for publishing a 11.0-compatible binary using the 11.0 SDK.  They should work on MacOS 11.0 or higher running Apple Silicon
* Install XCode and Command Line Tools
* Install Homebrew
   ```bash
   brew install cmake git maven
   ```
* Install the 11.0 SDKs from XCode 12.2 https://stackoverflow.com/a/10335943/3196753
   * Double-click the DMG, Right-click XCode, Show Package Contents
   * Show Package Contents
   * Copy `Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs` to `/Applications/XCode.app/`
* Compile manually using `cmake`
   ```bash
   cd jssc # if not already
   mkdir build && cd build
   rm -f CMakeCache.txt # clear previous build cache
   cmake .. -DCMAKE_OSX_SYSROOT=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX11.0.sdk -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0 -DCMAKE_OSX_ARCHITECTURES=arm64
   make
   ```
* Commit the new `.dylib` file inside `/src/main/resources-precompiled/natives/osx_arm64/`

