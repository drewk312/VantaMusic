# Open Audio Renderer (OAR)

Official reference implementation for the AOM Open Audio Renderer (OAR).

The `oar` library is designed to render [IAMF](https://aomediacodec.github.io/iamf/) bitstream, it supports both loudspeaker and binaural rendering.

## Features

The library renders the following types of Audio Elements:

* Ambisonics (from 0th to 4th order).
* Channel-based (e.g. 5.1, 7.1.4). See [loudspeaker layout](https://aomediacodec.github.io/iamf/#loudspeaker_layout) and [expanded loudspeaker layout](https://aomediacodec.github.io/iamf/#expanded_loudspeaker_layout) for the full list of supported loudspeaker layouts.
* Object-based. The API allows for controlling the position of the object in the 3D space.

## Usage

The `oar` library provides a C API for rendering IAMF (Immersive Audio Model and Formats) bitstreams. It supports channel-based, scene-based (Ambisonics), and object-based audio rendering to various loudspeaker or binaural layouts.

### Build

~~~
    $ cmake -B build
    $ cmake --build build
~~~

### Basic Workflow

1.  **Initialization**: Create an `oar_t` instance by specifying the target output layout, samples per channel, and sampling rate.
2.  **Audio Group Management**: Add audio groups using `oar_add_audio_group`. Audio elements must be added to a group, and the library supports up to 2 groups.
3.  **Audio Element Configuration**: Add one or more audio elements to specific groups using `oar_add_audio_element`. Each element can be channel-based, scene-based, or object-based.
    *   **Important**: Object-based audio elements cannot be mixed with other types (channel-based or scene-based) in the same `oar_t` instance.
4.  **Data Processing**: For each audio block:
    *   Provide audio data for each configured element using `oar_update_audio_element_data`.
    *   (Optional) Update metadata for each element (e.g., object positions for object-based audio) using `oar_update_audio_element_metadata`.
5.  **Rendering**: Call `oar_render` to process the inputs and generate the rendered output.
6.  **Cleanup**: Destroy the `oar_t` instance to free resources.

### Audio Element Types

#### Channel-Based Audio
For channel-based audio, you specify the input loudspeaker layout (e.g., `ck_oar_layout_mono`, `ck_oar_layout_51`, `ck_oar_layout_714`). The renderer will handle the necessary downmixing or upmixing to the target layout.

```c
oar_audio_element_config_t element_cfg;
element_cfg.type = ck_channel_based;
element_cfg.cbc.layout = ck_oar_layout_51; // Example: 5.1 input
// ... (add element, provide data for 6 channels)
```

#### Scene-Based Audio (Ambisonics)
For scene-based audio, you specify the Ambisonics order (e.g., `ck_oar_zoa` for 0th order, `ck_oar_1oa` for 1st order, up to `ck_oar_4oa`). The number of input channels is determined by the order (e.g., 4 channels for 1OA: W, Y, Z, X following ACN ordering).

```c
oar_audio_element_config_t element_cfg;
element_cfg.type = ck_scene_based;
element_cfg.sbc.order = ck_oar_2oa; // Example: 2nd Order Ambisonics (9 channels)
// ... (add element, provide Ambisonics data)
```

#### Object-Based Audio
For object-based audio, you configure the element type and can dynamically update object positions (azimuth, elevation, distance) for each audio block using metadata.

```c
// Configuration
oar_audio_element_config_t element_cfg;
element_cfg.type = ck_object_based;
element_cfg.obc.num_objects = 1;
// ... (add element, provide mono audio data)

// Metadata Update (e.g., in a processing loop)
oar_metadata_t metadata;
metadata.type = ck_metadata_object_positions;
metadata.duration = samples_per_channel; // Valid for this block
metadata.object_positions.param_type = ck_param_constant;
metadata.object_positions.position_type = ck_polar;
metadata.object_positions.num_objects = 1;
metadata.object_positions.polar_positions[0].azimuth = 30.0f;   // degrees
metadata.object_positions.polar_positions[0].elevation = 10.0f; // degrees
metadata.object_positions.polar_positions[0].distance = 1.0f;   // meters

int ret = oar_update_audio_element_metadata(oar, element_id, &metadata);
```
Refer to `tests/examples/test_object_based_rendering.c` for a more complete example, including handling multiple objects within a single element.

### Loudspeaker Layouts
The OAR library supports a wide range of loudspeaker layouts for both input and output. These layouts define the channel configuration and spatial arrangement of speakers. For detailed information about each supported layout and their channel ordering, please refer to the [Loudspeaker Layout Channel Order Documentation](doc/loudspeaker_layout_channel_order.md).

When configuring channel-based audio elements, you specify the input layout using the `oar_layout_t` enum. The renderer automatically handles conversion to the target layout specified during initialization.

### Advanced Features

*   **Loudness Processing**: Group-specific loudness normalization.
    ```c
    oar_enable_loudness_processor(oar, 1); // Enable
    oar_set_loudness(oar, group_id, current_loudness_db, target_loudness_db);
    ```
*   **Limiter**: Enable or disable the output limiter.
    ```c
    oar_enable_limiter(oar, 1); // Enable
    ```
*   **Head Tracking (for Binaural Rendering)**: If the target layout is `ck_oar_layout_binaural`, head tracking can be enabled.
    ```c
    oar_enable_head_tracking(oar, 1); // Enable
    ```

For more detailed examples, please refer to the source files in the `tests/examples/` directory:
*   `test_channel_based_rendering.c`
*   `test_scene_based_rendering.c`
*   `test_object_based_rendering.c`
*   `test_audio_element_types.c`



For more usage instructions, please refer to [libiamf](https://github.com/AOMediaCodec/libiamf/)
