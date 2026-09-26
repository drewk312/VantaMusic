# Building

The library supports two build systems: [Bazel](https://bazel.build/) (recommended) and CMake.

## Prerequisites

- C++20 compatible compiler
- Bazel 7.0+ or CMake 3.28+

## Bazel Build

Bazel is the recommended build system for this project.

### Building

```bash
bazel build -c opt //obr/...
```

### Running Tests

```bash
bazel test -c opt //obr/...
```

### Building the CLI

```bash
bazel build -c opt //obr/cli:obr_cli
```

### Dependencies

Bazel dependencies are managed through both `MODULE.bazel` (bazelmod) and `WORKSPACE`. The project uses:

- [Abseil](https://abseil.io/) - C++ common libraries
- [Eigen](https://eigen.tuxfamily.org/) - Linear algebra
- [GoogleTest](https://google.github.io/googletest/) - Testing framework
- [PFFFT](https://bitbucket.org/jpommier/pffft) - Fast FFT implementation
- [audio-to-tactile](https://github.com/google/audio-to-tactile) - Audio utilities (for CLI)

## CMake Build

CMake configuration is provided as an alternative build system.

### Building

```bash
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)
```

### Running Tests

```bash
ctest --test-dir build -L obr --output-on-failure
```

### Dependencies

CMake uses `FetchContent` to automatically download dependencies:

| Dependency | Version    | Purpose           |
|:-----------|:-----------|:------------------|
| GoogleTest | v1.17.0    | Testing framework |
| Abseil     | 20250512.1 | C++ utilities     |
| Eigen      | 3147391d   | Linear algebra    |
| PFFFT      | 180c2d62   | Fast FFT          |

All dependencies are fetched at configure time; no manual installation required.

### CMake Options

| Option             | Default | Description                                 |
|:-------------------|:--------|:--------------------------------------------|
| `CMAKE_BUILD_TYPE` | —       | Build type (Debug, Release, RelWithDebInfo) |

### Platform Notes

**Windows:**
- Defines `_USE_MATH_DEFINES` and `NOMINMAX` automatically
- Type conversion warnings are disabled (4244, 4305, 4722)

## Code Formatting

The project uses `clang-format` for consistent code style:

```bash
find obr -type f \( -name "*.cc" -o -name "*.h" \) -exec clang-format -i {} \;
```
