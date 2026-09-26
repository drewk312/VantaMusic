# Open Binaural Renderer (obr)

The `obr` binaural rendering library is designed to render immersive audio content.

## Features

The library renders the following types of Audio Elements:

- **Ambisonics** (1st to 4th order)
- **Channel-based** (e.g., 5.1, 7.1.4) - see [Loudspeaker Layouts](docs/loudspeaker_layouts.md)
- **Object-based** with 3D position control (mono and dual-channel)
- **Passthrough** for stereo/mono content that bypasses binaural processing

Additional capabilities:
- Head tracking with quaternion input
- World-locked and head-locked rendering modes
- Output peak limiting
- Three binaural filter profiles: Direct, Ambient, Reverberant

## Quick Start

```bash
# Build with Bazel (recommended)
bazel build -c opt //obr/...

# Or build with CMake
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)
```

See [Building](docs/building.md) for detailed build instructions.

## Documentation

| Document                                           | Description                   |
|:---------------------------------------------------|:------------------------------|
| [API Reference](docs/api_reference.md)             | C++ API documentation         |
| [CLI](docs/cli.md)                                 | Command line interface usage  |
| [Signal Flow](docs/signal_flow.md)                 | Audio processing architecture |
| [Binaural Rendering](docs/binaural_rendering.md)   | Technical rendering details   |
| [Loudspeaker Layouts](docs/loudspeaker_layouts.md) | Supported channel layouts     |
| [Building](docs/building.md)                       | Build system configuration    |

## Basic Usage

```cpp
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/renderer/obr_impl.h"

constexpr int kBufferSize = 512;
constexpr int kSampleRate = 48000;

// --- Setup (called once at initialization) ---

// Create renderer.
obr::ObrImpl renderer(kBufferSize, kSampleRate);

// OBR input/output audio buffers.
obr::AudioBuffer input_buffer;   // Multichannel input.
obr::AudioBuffer output_buffer;  // Stereo output.

// --- Parameter updates (can be called asynchronously from any thread) ---

// Add audio elements.
renderer.AddAudioElement(obr::AudioElementType::k3OA);             // 16-ch 3OA.
renderer.AddAudioElement(obr::AudioElementType::kLayout5_1_0_ch);  // 6-ch 5.1.
renderer.AddAudioElement(obr::AudioElementType::kObjectMono);      // 1-ch object.

// Enable head tracking (optional).
renderer.EnableHeadTracking(true);

// Update head rotation.
renderer.SetHeadRotation(w, x, y, z);  // Quaternion (w, x, y, z).

// Position the object audio element (index 2) in 3D space.
renderer.UpdateObjectPosition(
    /*audio_element_index=*/2,
    /*azimuth=*/45.0f,    // Degrees: 0=front, 90=left.
    /*elevation=*/0.0f,   // Degrees: 0=horizon, 90=above.
    /*distance=*/1.0f);   // Normalized: 0 to 1.

// --- Audio processing callback (called repeatedly on audio thread) ---

size_t num_input_channels = renderer.GetNumberOfInputChannels();
size_t num_output_channels = renderer.GetNumberOfOutputChannels();

// Resize OBR input/output buffers if needed.
if (input_buffer.num_channels() != num_input_channels ||
    input_buffer.num_frames() != kBufferSize) {
  input_buffer = obr::AudioBuffer(num_input_channels, kBufferSize);
}
if (output_buffer.num_channels() != num_output_channels ||
    output_buffer.num_frames() != kBufferSize) {
  output_buffer = obr::AudioBuffer(num_output_channels, kBufferSize);
}

// Fill input buffer from your audio source.
for (size_t ch = 0; ch < num_input_channels; ++ch) {
  const float* source = /* pointer to your channel data */;
  std::copy(source, source + kBufferSize, input_buffer[ch].begin());
}

// Process audio.
renderer.Process(input_buffer, &output_buffer);

// Copy output to your audio destination.
for (size_t ch = 0; ch < 2; ++ch) {
  float* dest = /* pointer to your output channel */;
  std::copy(output_buffer[ch].begin(), output_buffer[ch].end(), dest);
}
```

## Testing

```bash
# Bazel
bazel test -c opt //obr/...

# CMake
ctest --test-dir build -L obr --output-on-failure
```
