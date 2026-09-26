# C++ API Reference

The `ObrImpl` class is the main interface for the binaural renderer. It provides methods to configure audio elements, process audio buffers, and retrieve information about the renderer. See [obr_cli_lib.cc](../obr/cli/obr_cli_lib.cc) for an example of how to use the library.

## Constructor

```cpp
ObrImpl(int buffer_size_per_channel, int sampling_rate);
```

**Parameters:**
- `buffer_size_per_channel`: The size of the audio processing buffer for each audio channel.
- `sampling_rate`: The sampling rate of the audio data.

## Configuration

### AddAudioElement

Configure the renderer using the `AddAudioElement` method. The method accepts `AudioElementType` enums defined within [audio_element_type.h](../obr/renderer/audio_element_type.h) and `BinauralFilterProfile` enums defined within [audio_element_config.h](../obr/renderer/audio_element_config.h).

```cpp
absl::Status AddAudioElement(AudioElementType type,
                             BinauralFilterProfile filter_type);
```

**Parameters:**
- `type`: The type of audio element to add (see [Audio Element Types](#audio-element-types) below).
- `filter_type`: The binaural filter profile to use (`kDirect`, `kAmbient`, or `kReverberant`). Defaults to `kAmbient`.

### Audio Element Types

The following audio element types are supported:

#### Ambisonics

| Type   | Channels | Description          |
|:-------|:--------:|:---------------------|
| `k1OA` |    4     | 1st Order Ambisonics |
| `k2OA` |    9     | 2nd Order Ambisonics |
| `k3OA` |    16    | 3rd Order Ambisonics |
| `k4OA` |    25    | 4th Order Ambisonics |

#### Channel-Based (Loudspeaker Layouts)

| Type              | Channels | Description       |
|:------------------|:--------:|:------------------|
| `kLayoutMono`     |    1     | Mono              |
| `kLayoutStereo`   |    2     | Stereo            |
| `kLayout5_1_0_ch` |    6     | 5.1 Surround      |
| `kLayout5_1_2_ch` |    8     | 5.1.2 with height |
| `kLayout5_1_4_ch` |    10    | 5.1.4 with height |
| `kLayout7_1_0_ch` |    8     | 7.1 Surround      |
| `kLayout7_1_2_ch` |    10    | 7.1.2 with height |
| `kLayout7_1_4_ch` |    12    | 7.1.4 with height |
| ...               |          |                   |

See [Loudspeaker Layouts](loudspeaker_layouts.md) for the full list of supported layouts and channel mappings.

#### Object-Based

| Type          | Channels | Description                                      |
|:--------------|:--------:|:-------------------------------------------------|
| `kObjectMono` |    1     | Single positionable object                       |
| `kObjectDual` |    2     | Dual-channel object with independent positioning |

#### Passthrough (No Binaural Processing)

| Type                 | Channels | Description                      |
|:---------------------|:--------:|:---------------------------------|
| `kPassthroughMono`   |    1     | Mono copied to both L/R outputs  |
| `kPassthroughStereo` |    2     | Stereo passed directly to output |

Passthrough elements bypass binaural processing and head tracking entirely. They can be used for non-spatialized audio content. Use `kPassthroughStereo` for delivering the traditional, static binaural audio to the listener, or non-spatialized stereo music.

## Head Tracking

### EnableHeadTracking

Head tracking can be enabled or disabled using the `EnableHeadTracking` method. When enabled, the renderer will apply head rotation to the Ambisonic bed to stabilize sound sources during head movement. Head tracking is disabled by default.

```cpp
void EnableHeadTracking(bool enable_head_tracking);
```

**Parameters:**
- `enable_head_tracking`: Set to `true` to enable head tracking, `false` to disable.

### SetHeadRotation

The head rotation can be set using quaternions:

```cpp
absl::Status SetHeadRotation(float w, float x, float y, float z);
```

**Parameters:**
- `w`: The scalar (real) component of the quaternion.
- `x`: The x component of the quaternion vector.
- `y`: The y component of the quaternion vector.
- `z`: The z component of the quaternion vector.

The quaternion head tracking input uses the ADM object coordinate system:
- **X-axis**: Left (negative) / Right (positive)
- **Y-axis**: Back (negative) / Front (positive)
- **Z-axis**: Down (negative) / Up (positive)

## World-Locked vs Head-Locked Rendering

By default, all audio elements are rendered as **world-locked**, meaning their spatial positions remain fixed in the world coordinate system and rotate with the listener's head when head tracking is enabled.

For certain audio types (e.g. head-relative spatial effects), you can render elements as **head-locked** using the `SetElementHeadLocked` method.

### SetElementHeadLocked

```cpp
absl::Status SetElementHeadLocked(size_t audio_element_index, bool head_locked);
```

**Parameters:**
- `audio_element_index`: Index of the audio element (0-based, in order of `AddAudioElement` calls).
- `head_locked`: Set to `true` for head-locked rendering, `false` for world-locked (default).

**Notes:**
- The global `EnableHeadTracking()` setting overrides individual head-locked settings. When head tracking is disabled globally, all elements are effectively head-locked (not rotated).
- Passthrough elements (`kPassthroughMono`, `kPassthroughStereo`) are unaffected by head-locked settings and cannot be rotated.
- Internally, world-locked and head-locked elements are processed through separate Ambisonic mix beds. Head tracking rotation is only applied to the world-locked bed.

**Example:**
```cpp
ObrImpl renderer(512, 48000);

// Add ambient scene (world-locked by default)
renderer.AddAudioElement(AudioElementType::k3OA);

// Add UI notification (head-locked)
renderer.AddAudioElement(AudioElementType::kObjectMono);
renderer.SetElementHeadLocked(1, true);

// Enable head tracking
renderer.EnableHeadTracking(true);

// Element 0: world position rotates with head movement
// Element 1: stays at fixed position relative to head
renderer.SetHeadRotation(w, x, y, z);
renderer.Process(input, &output);
```

## Peak Limiter

### EnableLimiter

The output peak limiter can be enabled or disabled using the `EnableLimiter` method. The limiter prevents the output signal from clipping. The limiter is enabled by default with a ceiling of -0.5 dB.

```cpp
void EnableLimiter(bool enable_limiter);
```

**Parameters:**
- `enable_limiter`: Set to `true` to enable the peak limiter, `false` to disable.

## Object Positioning

For object-based audio elements (`kObjectMono` and `kObjectDual`), position can be controlled using the following methods.

### Coordinate System

Object positions use a spherical coordinate system centered on the listener:

| Parameter   | Range         | Description                                                            |
|:------------|:--------------|:-----------------------------------------------------------------------|
| `azimuth`   | -180° to 180° | Horizontal angle. 0° = front, 90° = left, -90° = right, ±180° = behind |
| `elevation` | -90° to 90°   | Vertical angle. 0° = horizon, 90° = above, -90° = below                |
| `distance`  | 0 to 1        | Normalized distance from listener                                      |

This follows the ADM spherical coordinate convention (azimuth measured counter-clockwise from front when viewed from above).

### UpdateObjectPosition

Sets all channels of an object to the same position:

```cpp
absl::Status UpdateObjectPosition(size_t audio_element_index, float azimuth,
                                  float elevation, float distance);
```

### UpdateObjectChannelPosition

For dual objects that require independent positioning per channel:

```cpp
absl::Status UpdateObjectChannelPosition(size_t audio_element_index,
                                         size_t channel_index, float azimuth,
                                         float elevation, float distance);
```

**Parameters:**
- `audio_element_index`: Index of the audio element (0-based, in order of `AddAudioElement` calls).
- `channel_index`: Index of the channel within the object (0 or 1 for dual objects).
- `azimuth`, `elevation`, `distance`: See [Coordinate System](#coordinate-system) above.

## Audio Processing

### Process

Once the renderer is configured, the rendering of audio data can be done using the `Process` method. PCM audio data associated with all Audio Elements should be aggregated into a single audio buffer. The method uses [`AudioBuffer`](../obr/audio_buffer/audio_buffer.h) class for storing input and output audio data in a planar format.

```cpp
void Process(const AudioBuffer& input_buffer, AudioBuffer* output_buffer);
```

**Parameters:**
- `input_buffer`: The input audio buffer containing data in planar format for all configured audio elements.
- `output_buffer`: Pointer to the output buffer that will receive the stereo binaural output (2 channels).
