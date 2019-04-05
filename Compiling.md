### Prerequisites
 * JDK7+
 * C++
   * **Linux:** `gcc`, `clang` or equivalent
   * **macOS:** XCode with Command Line Utilities
   * **Windows:** Microsoft Visual C++ or mingw-w64
 * Maven, CMake
   * **Linux:** `sudo apt install maven cmake g++`
   * **macOS:** `brew install maven cmake`
   * **Windows:**
      * Manually install Maven from https://maven.apache.org/, adding `mvn.bat` directory to your `%PATH%`
      * Manually install CMake from https://cmake.org/download/, adding `cmake.exe` directory to your `%PATH%`

### Steps
   ```bash
   mvn             # compile for current system
   mvn -P mingw64  # cross-compile using mingw-w64 for Windows
   ```