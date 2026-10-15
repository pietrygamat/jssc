# Overview
Releasing a new version is a rather involved process and there are several important aspects to keep in mind:
1. Native libraries need to be prebuilt for each of 12 supported architectures and committed to the repository.
2. Versions in `src/main/java/jssc/SerialNativeInterface.java` and in `pom.xml` should be in sync. Note that version in `pom.xml` should have a `-SNAPSHOT` suffix appended to it most of the time.
3. Release artifact should be built using JDK11 to ensure maximum compatibility between JREs.
4. Maven central deployment is handled by maven-release-plugin + central-publishing-maven-plugin.
5. Manual publishing of the deployment must be performed by a project member with access to namespace `io.github.java-native` on https://central.sonatype.com.
6. [Github Releases](https://github.com/java-native/jssc/releases) deployment follows Maven central release. Note, the artifact deployed to Github is a fat-jar, so it is **not** the same artifact that is sent to Maven Central. It must be built from the same git revision though.
7. During the release process, the `pom.xml` version is bumped automatically, but it needs to be matched in several places that reference it afterwards.

# Automatic pipelines
The steps described above are automated using Github actions, but still some manual supervision is required:
* [ ] Create a release branch, e.g. `2.9.5-release`
* [ ] Rebuild precompiled libaries: trigger [this pipeline](https://github.com/java-native/jssc/actions/workflows/cross-compile.yml) on the release branch. This step may be skipped if rebuilding libraries is not needed or performed outside of the release.
    * [ ] Make sure all artifacts have been pushed to the newly created branch (suffixed with `-precompiled-natives`)
        * [ ] Native libraries for PC (Linux / Windows)
        * [ ] Native libraries for Mac
        * [ ] Native libraries differ from the ones in the previous release
        * [ ] `jssc.SerialNativeInterface`'s version is up to date with version in `pom.xml` (without `-SNAPSHOT` qualifier)
    * [ ] If artifacts are generated correctly - merge the `*-precompiled-natives` branch into release branch. The pipeline may be rerun multiple times. The `*-precompiled-natives` branch will be dropped and recreated each time.
* [ ] Trigger [Release New Version](https://github.com/java-native/jssc/actions/workflows/release-new-version.yml) github action
* [ ] Login to [Central Portal](https://central.sonatype.com/publishing/deployments) and verify the artifacts are in good shape. This is the time for last minute integration tests:
    * [ ] Are compiled classes at bytecode version 50 (Java 6)?
    * [ ] Is there module-info.class in the package?
    * [ ] Are there no undesired entries in jar manifest file?
    * [ ] Is the artifact compatible with maven projects build with jdk7,8,11...17?
    * [ ] Is the artifact usable on JRE6...17?
    * [ ] Is the artifact usable on all supported OSes?
    * [ ] Is the version in `README.md` up to date?
    * [ ] Is the version in the wiki up to date?
* Check the [Github Releases](https://github.com/java-native/jssc/releases) page 
    * [ ] Verify the release has been created on the correct tagged commit
    * [ ] Verify the sources and javadoc jars are included
    * [ ] Verify jar *with dependencies* is included
    * [ ] Update release notes in the description
    * [ ] Publish the draft release
* [ ] In case of errors with any of the steps so far - you may discard the branch, drop the deployment in Central Portal and start over
* [ ] If all is good:
    * [ ] Publish the deployment in Central Portal
    * [ ] Merge release branch back to master. **Do not** use rebase as to preserve tagged revision.

# Building native libraries manually
### Ubuntu (for Linux and Windows)
* Release binaries are built in [dockcross](https://github.com/dockcross/dockcross) images pinned by `DOCK_CROSS_TAG` in [cross-compile.yml](https://github.com/java-native/jssc/blob/master/.github/workflows/cross-compile.yml), using `mvn -P dockcross` with `-Dos.target.name`, `-Dos.target.arch` and `-Dos.target.bitness`. See [[Compiling]] or [[Cross-Compilation]] for other environments.
* Commit the new `.so` or `.dll` file inside `/src/main/resources-precompiled/natives/linux_64/`, etc.

### MacOS
#### x86_64
These steps are for publishing a 10.9-compatible binary using the 10.9 SDK.  They should work on MacOS 10.9 or higher.
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
   cmake .. -DCMAKE_OSX_SYSROOT=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -DCMAKE_OSX_DEPLOYMENT_TARGET=10.9 -DCMAKE_OSX_ARCHITECTURES=x86_64
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

