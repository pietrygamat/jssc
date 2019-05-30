### Prerequisites
* [ ] Create a branch (e.g. `2.9.1`)
* [ ] Bump and commit the version in `pom.xml`
* [ ] Bump and commit the version in `src/java/jssc/SerialNativeInterface.java`
* [ ] Checkout the branch

### Ubuntu
(Can build for Windows 32/64, Linux 32/64, Arm 32/64)
* (FIXME deps and build commands)
* Commit changes and push to branch

### Solaris
(Built using Solaris 10)
```bash
# Optionally enable SSH from root
vi /etc/ssh/sshd_config # Change PermitRootLogin yes
svcadm restart ssh

# Enable vi support through ssh
export TERM=xterm

# Add OpenCSW repos
pkgadd -d http://get.opencsw.org/now
/opt/csw/bin/pkgutil -U
/opt/csw/bin/pkgutil -y -i git
# /opt/csw/bin/pkgutil -y -i cmake # broken on Solaris 10
/opt/csw/bin/pkgutil -y -i wget

wget http://mirror.opencsw.org/opencsw/allpkgs/cmake-3.6.3%2cREV%3d2016.11.13.2-SunOS5.10-i386-CSW.pkg.gz
gunzip *.pkg.gz
pkgadd -d cmake-*

# Add git, cmake, gcc to path
export PATH=$PATH:/usr/sfw/bin:/opt/csw/bin

# clone repo
git clone https://github.com/java-native/jssc
# remember to checkout the proper branch

# copy jssc_SerialNativeInterface.h AND version.h to jssc directory (from a Unix machine)
# edit version.h, bump version

cd jssc

# build the 32-bit binary
rm -f CMakeCache.txt && cmake . && cmake --build .
cp -Rf natives/ src/main/resources-precompiled/

# build the 64-bit binary
rm -f CMakeCache.txt && cmake . -DFORCE_M64=True && cmake --build .
cp -Rf natives/ src/main/resources-precompiled/
```
* Commit changes and push to branch

### MacOS
These steps are for publishing a 10.5-compatible binary using the 10.9 SDK.  They should work on MacOS 10.9 or higher.
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
   cmake .. -DCMAKE_OSX_SYSROOT=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -DCMAKE_OSX_DEPLOYMENT_TARGET=10.5
   make
   ```
* Commit the new `.dylib` file inside `/src/main/resources-precompiled/natives/osx_64/`

### Publishing
* [ ] Create a Pull Request and merge the branch
* [ ] Create build artifact using `mvn -P package,jar-with-dependencies`
* [ ] Create release in GitHub releases area, add release notes matching previous release formatting
   * [ ] Upload build artifact and javadoc artifact
* [ ] Append `-SNAPSHOT` to the version in `pom.xml` and commit to master branch
