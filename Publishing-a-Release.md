### Prerequisites
* Create a branch (e.g. `2.9.1`)
* Bump and commit the version in `pom.xml`
* Checkout the branch

### Ubuntu
(Can build for Windows 32/64, Linux 32/64, Arm 32/64)
* (FIXME ddeps and build commands)
* Commit changes and push to branch

### Solaris
(Built using Solaris 9)

### MacOS
(Built using MacOS 10.8)
* Install XCode 5.1.1 from here: https://stackoverflow.com/a/10335943/3196753
* Install Homebrew from here: https://brew.sh/
* (FIXME GIT, MAVEN, ANT, JAVA)

### Publishing
* Create a Pull Request and merge the branch
* Create build artifact using `mvn -P FIXME`
* Create release in GitHub releases area, match previous release formatting
* Append `-SNAPSHOT` to the version in `pom.xml` and commit to master branch
