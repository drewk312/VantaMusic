# Command Line Interface (CLI)

The command line interface provides a straightforward way to render audio files of certain types.

> **Note:** The CLI can only be built using Bazel due to additional dependencies (protobuf, audio-to-tactile).

## Building

```bash
bazel build -c opt //obr/cli:obr_cli
```

## Usage

```
obr_cli
  --input_type=<1OA|2OA|3OA|4OA|7.1.4|OBA>
  --oba_metadata_file=<full path to textproto file>
  --input_file=<full path to wav file>
  --output_file=<full path to wav file>
  --buffer_size=<number of samples>
  --filter_type=<Direct|Ambient|Reverberant>
```

## Parameters

| Parameter             | Description                                             | Required             |
|:----------------------|:--------------------------------------------------------|:---------------------|
| `--input_type`        | The type of audio content in the input file             | Yes                  |
| `--oba_metadata_file` | Path to textproto file with object-based audio metadata | Only for OBA         |
| `--input_file`        | Path to the input WAV file                              | Yes                  |
| `--output_file`       | Path to the output WAV file                             | Yes                  |
| `--buffer_size`       | Number of samples per processing buffer                 | No (default: 256)    |
| `--filter_type`       | Binaural filter profile to use                          | No (default: Ambient)|

## Input Types

- **1OA, 2OA, 3OA, 4OA**: Ambisonic content of 1st to 4th order
- **7.1.4**: 7.1.4 channel-based layout
- **OBA**: Object-based audio (requires metadata file)

## Filter Types

`Direct`, `Ambient` (default), or `Reverberant`. See [Binaural Rendering](binaural_rendering.md#filter-profiles) for details.

## Examples

Render 3rd order Ambisonics file:
```bash
obr_cli --input_type=3OA --input_file=input.wav --output_file=output.wav
```

Render 7.1.4 content with reverberant filter:
```bash
obr_cli --input_type=7.1.4 --input_file=input.wav --output_file=output.wav --filter_type=Reverberant
```

Render object-based audio:
```bash
obr_cli --input_type=OBA --oba_metadata_file=metadata.textproto --input_file=input.wav --output_file=output.wav
```
