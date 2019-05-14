### Prerequisites
* Create a branch (e.g. `2.9.1`)
* Bump and commit the version in `pom.xml`
* Checkout the branch

### Ubuntu
(Can build for Windows 32/64, Linux 32/64, Arm 32/64)
* (FIXME ddeps and build commands)
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
(Built using MacOS 10.8)
* Update to 10.8.5
* Install XCode 5.1.1 from here: https://stackoverflow.com/a/10335943/3196753
* Install Command Line Tools 5.1: https://download.developer.apple.com/Developer_Tools/command_line_tools_os_x_mountain_lion_for_xcode__march_2014/commandline_tools_os_x_mountain_lion_for_xcode__march_2014.dmg
* Install Tigerbrew from here: https://github.com/mistydemeo/tigerbrew
```bash
brew install git cmake
git clone git://github.com/java-native/jssc # use git:// protocol
# remember to checkout the proper branch

# copy jssc_SerialNativeInterface.h AND version.h to jssc directory (from a Unix machine)
# edit version.h, bump version

cd jssc
rm -f CMakeCache.txt && cmake . && cmake --build .
cp -Rf natives/ src/main/resources-precompiled/natives/
```
* Commit changes and push to branch

### Publishing
* Create a Pull Request and merge the branch
* Create build artifact using `mvn -P FIXME`
* Create release in GitHub releases area, match previous release formatting
* Append `-SNAPSHOT` to the version in `pom.xml` and commit to master branch
