# Loudspeaker Layout Channel Order Documentation

This document describes the channel order for each loudspeaker layout defined in the OAR (Open Audio Renderer) system. The channel order is important for proper audio rendering and mixing.
Some layouts may have a position range, the concrete position for rendering is decided by each renderer library internally.

## Main Loudspeaker Layouts

They could be used as both an element layout and a target layout.
 
### Mono

Single front center loudspeaker at 0° azimuth.

OAR Layout ID: `ck_oar_layout_mono`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Mono                                                                 |
| Reference      | Single front center loudspeaker configuration                        |

Number of channels: 1

| Index | Loudspeaker | OAR Channel ID    | Azimuth  | Elevation |
|-------|-------------|-------------------|----------|-----------|
| 1     | Center      | `ck_channel_mp000`| 0°       | 0°        |

### Stereo

Standard stereo layout with left and right loudspeakers at ±30° azimuth.

OAR Layout ID: `ck_oar_layout_stereo`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo                                                               |
| Reference      | Loudspeaker configuration for Sound System A (0+2+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 2

| Index | Loudspeaker | OAR Channel ID    | Azimuth  | Elevation |
|-------|-------------|-------------------|----------|-----------|
| 1     | Left        | `ck_channel_mp030`| 30°      | 0°        |
| 2     | Right       | `ck_channel_mm030`| -30°     | 0°        |

### 5.1

5.1 layout with front left/right/center, LFE, and surround left/right speakers.

OAR Layout ID: `ck_oar_layout_51`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 5.1                                                                  |
| Reference      | Loudspeaker configuration for Sound System B (0+5+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 6

| Index | Loudspeaker    | OAR Channel ID    | Azimuth        | Elevation      |
|-------|----------------|-------------------|----------------|----------------|
| 1     | Left           | `ck_channel_mp030`| 30°            | 0°             |
| 2     | Right          | `ck_channel_mm030`| -30°           | 0°             |
| 3     | Center         | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1           | `ck_channel_lfe1` | –              | –              |
| 5     | Left Surround  | `ck_channel_mp110`| 110° ~ 120°    | 0° ~ 15°       |
| 6     | Right Surround | `ck_channel_mm110`| -110° ~ -120°  | 0° ~ 15°       |

### 5.1.2

5.1.2 layout with front left/right/center, LFE, surround left/right, and top front left/right speakers.

OAR Layout ID: `ck_oar_layout_512`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 5.1.2                                                                |
| Reference      | Loudspeaker configuration for Sound System C (2+5+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 8

| Index | Loudspeaker    | OAR Channel ID    | Azimuth        | Elevation      |
|-------|----------------|-------------------|----------------|----------------|
| 1     | Left           | `ck_channel_mp030`| 30°            | 0°             |
| 2     | Right          | `ck_channel_mm030`| -30°           | 0°             |
| 3     | Center         | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1           | `ck_channel_lfe1` | –              | –              |
| 5     | Left Surround  | `ck_channel_mp110`| 100° ~ 120°    | 0° ~ 15°       |
| 6     | Right Surround | `ck_channel_mm110`| -100° ~ -120°  | 0° ~ 15°       |
| 7     | Left Top Front | `ck_channel_up030`| 30° ~ 45°      | 30° ~ 55°      |
| 8     | Right Top Front| `ck_channel_um030`| -30° ~ -45°    | 30° ~ 55°      |

### 5.1.4

5.1.4 layout with front left/right/center, LFE, surround left/right, and top front/rear left/right speakers.

OAR Layout ID: `ck_oar_layout_514`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 5.1.4                                                                |
| Reference      | Loudspeaker configuration for Sound System D (4+5+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 10

| Index | Loudspeaker    | OAR Channel ID    | Azimuth        | Elevation      |
|-------|----------------|-------------------|----------------|----------------|
| 1     | Left           | `ck_channel_mp030`| 30°            | 0°             |
| 2     | Right          | `ck_channel_mm030`| -30°           | 0°             |
| 3     | Center         | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1           | `ck_channel_lfe1` | –              | –              |
| 5     | Left Surround  | `ck_channel_mp110`| 100° ~ 120°    | 0°             |
| 6     | Right Surround | `ck_channel_mm110`| -100° ~ -120°  | 0°             |
| 7     | Left Top Front | `ck_channel_up030`| 30° ~ 45°      | 30° ~ 55°      |
| 8     | Right Top Front| `ck_channel_um030`| -30° ~ -45°    | 30° ~ 55°      |
| 9     | Left Top Rear  | `ck_channel_up110`| 100° ~ 135°    | 30° ~ 55°      |
| 10    | Right Top Rear | `ck_channel_um110`| -100° ~ -135°  | 30° ~ 55°      |

### 7.1

7.1 layout with front left/right/center, LFE, side left/right, and rear left/right speakers.

OAR Layout ID: `ck_oar_layout_71`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 7.1                                                                  |
| Reference      | Loudspeaker configuration for Sound System I (0+7+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 8

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°      | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°    | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Side Surround  | `ck_channel_mp090`| 85° ~ 110°     | 0°             |
| 6     | Right Side Surround | `ck_channel_mm090`| -85° ~ -110°   | 0°             |
| 7     | Left Rear Surround  | `ck_channel_mp135`| 120° ~ 150°    | 0°             |
| 8     | Right Rear Surround | `ck_channel_mm135`| -120° ~ -150°  | 0°             |

### 7.1.2

7.1.2 layout with front left/right/center, LFE, side left/right, rear left/right, and top front left/right speakers.

OAR Layout ID: `ck_oar_layout_712`

Reference:

| IAMF Reference |                                                                           |
|----------------|---------------------------------------------------------------------------|
| Layout Name    | 7.1.2                                                                     |
| Reference      | The combination of 7.1ch and the Left and Right top front pair of 7.1.4ch |

Number of channels: 10

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°      | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°    | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Side Surround  | `ck_channel_mp090`| 85° ~ 110°     | 0°             |
| 6     | Right Side Surround | `ck_channel_mm090`| -85° ~ -110°   | 0°             |
| 7     | Left Rear Surround  | `ck_channel_mp135`| 120° ~ 150°    | 0°             |
| 8     | Right Rear Surround | `ck_channel_mm135`| -120° ~ -150°  | 0°             |
| 9     | Left Top Front      | `ck_channel_up045`| 30° ~ 45°      | 30° ~ 55°      |
| 10    | Right Top Front     | `ck_channel_um045`| -30° ~ -45°    | 30° ~ 55°      |

### 7.1.4

7.1.4 layout with front left/right/center, LFE, side left/right, rear left/right, and top front/rear left/right speakers.

OAR Layout ID: `ck_oar_layout_714`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 7.1.4                                                                |
| Reference      | Loudspeaker configuration for Sound System K (4+7+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 12

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°      | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°    | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Side Surround  | `ck_channel_mp090`| 85° ~ 110°     | 0°             |
| 6     | Right Side Surround | `ck_channel_mm090`| -85° ~ -110°   | 0°             |
| 7     | Left Rear Surround  | `ck_channel_mp135`| 120° ~ 150°    | 0°             |
| 8     | Right Rear Surround | `ck_channel_mm135`| -120° ~ -150°  | 0°             |
| 9     | Left Top Front      | `ck_channel_up045`| 30° ~ 45°      | 30° ~ 55°      |
| 10    | Right Top Front     | `ck_channel_um045`| -30° ~ -45°    | 30° ~ 55°      |
| 11    | Left Top Back       | `ck_channel_up135`| 100° ~ 150°    | 30° ~ 55°      |
| 12    | Right Top Back      | `ck_channel_um135`| -100° ~ -150°  | 30° ~ 55°      |

### 3.1.2

3.1.2 layout with front left/right/center, LFE, and top front left/right speakers.

OAR Layout ID: `ck_oar_layout_312`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 3.1.2                                                                |
| Reference      | The front subset (L/C/R/Ltf/Rtf/LFE) of 7.1.4ch                      |

Number of channels: 6

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°      | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°    | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Top Front      | `ck_channel_up045`| 30° ~ 45°      | 30° ~ 55°      |
| 6     | Right Top Front     | `ck_channel_um045`| -30° ~ -45°    | 30° ~ 55°      |

### 9.1.6

9.1.6 layout with front left/right/center/left center/right center, LFE, rear left/right, side left/right, and top front/rear/side left/right speakers.

OAR Layout ID: `ck_oar_layout_916`

Reference:

| IAMF Reference |                                                                                     |
|----------------|-------------------------------------------------------------------------------------|
| Layout Name    | 9.1.6                                                                               |
| Reference      | The subset of Loudspeaker configuration for Sound System H (9+10+3) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 16

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Front Left          | `ck_channel_mp060`| 45° ~ 60°      | 0° ~ 5°        |
| 2     | Front Right         | `ck_channel_mm060`| -45° ~ -60°    | 0° ~ 5°        |
| 3     | Front Center        | `ck_channel_mp000`| 0°             | 0° ~ 5°        |
| 4     | LFE1                | `ck_channel_lfe1` | 30° ~ 90°      | –15° ~ -30°    |
| 5     | Back Left           | `ck_channel_mp135`| 110° ~ 135°    | 0° ~ 15°       |
| 6     | Back Right          | `ck_channel_mm135`| -110° ~ -135°  | 0° ~ 15°       |
| 7     | Front Left Center   | `ck_channel_mp030`| 22.5° ~ 30°    | 0° ~ 5°        |
| 8     | Front Right Center  | `ck_channel_mm030`| -22.5° ~ -30°  | 0° ~ 5°        |
| 9     | Side Left           | `ck_channel_mp090`| 90°            | 0° ~ 15°       |
| 10    | Side Right          | `ck_channel_mm090`| -90°           | 0° ~ 15°       |
| 11    | Top Front Left      | `ck_channel_up045`| 45° ~ 60°      | 30° ~ 45°      |
| 12    | Top Front Right     | `ck_channel_um045`| -45° ~ -60°    | 30° ~ 45°      |
| 13    | Top Back Left       | `ck_channel_up135`| 110° ~ 135°    | 30° ~ 45°      |
| 14    | Top Back Right      | `ck_channel_um135`| -110° ~ -135°  | 30° ~ 45°      |
| 15    | Top Side Left       | `ck_channel_up090`| 90°            | 30° ~ 45°      |
| 16    | Top Side Right      | `ck_channel_um090`| -90°           | 30° ~ 45°      |

### 10.2.9.3

10.2.9.3 layout with front left/right/center/left center/right center, LFE1/LFE2, rear left/right, back center, side left/right, top front/rear/side left/right/center/back center, and bottom front left/right/center speakers.

OAR Layout ID: `ck_oar_layout_a293`

Reference:

| IAMF Reference |                                                                       |
|----------------|-----------------------------------------------------------------------|
| Layout Name    | 10.2.9.3                                                              |
| Reference      | Loudspeaker configuration for Sound System H (9+10+3) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 24

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Front Left          | `ck_channel_mp060`| 45° ~ 60°      | 0° ~ 5°        |
| 2     | Front Right         | `ck_channel_mm060`| -45° ~ -60°    | 0° ~ 5°        |
| 3     | Front Center        | `ck_channel_mp000`| 0°             | 0° ~ 5°        |
| 4     | LFE1                | `ck_channel_lfe1` | 30° ~ 90°      | –15° ~ -30°    |
| 5     | Back Left           | `ck_channel_mp135`| 110° ~ 135°    | 0° ~ 15°       |
| 6     | Back Right          | `ck_channel_mm135`| -110° ~ -135°  | 0° ~ 15°       |
| 7     | Front Left Center   | `ck_channel_mp030`| 22.5° ~ 30°    | 0° ~ 5°        |
| 8     | Front Right Center  | `ck_channel_mm030`| -22.5° ~ -30°  | 0° ~ 5°        |
| 9     | Back Center         | `ck_channel_mp180`| 180°           | 0° ~ 15°       |
| 10    | LFE2                | `ck_channel_lfe2` | -30° ~ -90°    | -15° ~ -30°    |
| 11    | Side Left           | `ck_channel_mp090`| 90°            | 0° ~ 15°       |
| 12    | Side Right          | `ck_channel_mm090`| -90°           | 0° ~ 15°       |
| 13    | Top Front Left      | `ck_channel_up045`| 45° ~ 60°      | 30° ~ 45°      |
| 14    | Top Front Right     | `ck_channel_um045`| -45° ~ -60°    | 30° ~ 45°      |
| 15    | Top Front Center    | `ck_channel_up000`| 0°             | 30° ~ 45°      |
| 16    | Top Center          | `ck_channel_tp000`| -              | 90°            |
| 17    | Top Back Left       | `ck_channel_up135`| 110° ~ 135°    | 30° ~ 45°      |
| 18    | Top Back Right      | `ck_channel_um135`| -110° ~ -135°  | 30° ~ 45°      |
| 19    | Top Side Left       | `ck_channel_up090`| 90°            | 30° ~ 45°      |
| 20    | Top Side Right      | `ck_channel_um090`| -90°           | 30° ~ 45°      |
| 21    | Top Back Center     | `ck_channel_up180`| 180°           | 30° ~ 45°      |
| 22    | Bottom Front Center | `ck_channel_bp000`| 0°             | -15° ~ -30°    |
| 23    | Bottom Front Left   | `ck_channel_bp045`| 45° ~ 60°      | -15° ~ -30°    |
| 24    | Bottom Front Right  | `ck_channel_bm045`| -45° ~ -60°    | -15° ~ -30°    |

### 7.1.5.4

7.1.5.4 layout with front left/right/center, LFE, side left/right, rear left/right, top front/rear left/right/center, and bottom front/rear left/right speakers.

OAR Layout ID: `ck_oar_layout_7154`

Reference:

| IAMF Reference |                                                                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Layout Name    | 7.1.5.4                                                                                                                                      |
| Reference      | Loudspeaker configuration with the top and the bottom speakers added to Loudspeaker configuration for Sound System J (4+7+0) of [ITU-2051-3] |

Number of channels: 17

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°      | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°    | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Side Surround  | `ck_channel_mp090`| 85° ~ 110°     | 0°             |
| 6     | Right Side Surround | `ck_channel_mm090`| -85° ~ -110°   | 0°             |
| 7     | Left Rear Surround  | `ck_channel_mp135`| 120° ~ 150°    | 0°             |
| 8     | Right Rear Surround | `ck_channel_mm135`| -120° ~ -150°  | 0°             |
| 9     | Left Top Front      | `ck_channel_up045`| 30° ~ 45°      | 30° ~ 55°      |
| 10    | Right Top Front     | `ck_channel_um045`| -30° ~ -45°    | 30° ~ 55°      |
| 11    | Top Center          | `ck_channel_tp000`| 0°             | 90°            |
| 12    | Left Top Back       | `ck_channel_up135`| 100° ~ 150°    | 30° ~ 55°      |
| 13    | Right Top Back      | `ck_channel_um135`| -100° ~ -150°  | 30° ~ 55°      |
| 14    | Bottom Front Left   | `ck_channel_bp045`| 45°            | -30°           |
| 15    | Bottom Front Right  | `ck_channel_bm045`| -45°           | -30°           |
| 16    | Bottom Back Left    | `ck_channel_bp135`| 135°           | -30°           |
| 17    | Bottom Back Right   | `ck_channel_bm135`| -135°          | -30°           |

## Target Loudspeaker Layouts

### Sound System E (4.5.1)

Sound System E (4.5.1) layout with front left/right/center, LFE, rear left/right, top front/rear left/right, and bottom front center speakers.

OAR Layout ID: `ck_oar_layout_sound_system_e_451`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Sound System E (4.5.1)                                               |
| Reference      | Loudspeaker configuration for Sound System E (4+5+1) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 11

| Index | Loudspeaker         | OAR Channel ID    | Azimuth        | Elevation      |
|-------|---------------------|-------------------|----------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30°            | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30°           | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°             | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | –              | –              |
| 5     | Left Surround       | `ck_channel_mp110`| 100° ~ 120°    | 0°             |
| 6     | Right Surround      | `ck_channel_mm110`| -100° ~ -120°  | 0°             |
| 7     | Left Top Front      | `ck_channel_up030`| 30° ~ 45°      | 30° ~ 55°      |
| 8     | Right Top Front     | `ck_channel_um030`| -30° ~ -45°    | 30° ~ 55°      |
| 9     | Left Top Rear       | `ck_channel_up110`| 100° ~ 135°    | 30° ~ 55°      |
| 10    | Right Top Rear      | `ck_channel_um110`| -100° ~ -135°  | 30° ~ 55°      |
| 11    | Center Bottom Front | `ck_channel_bp000`| 0°             | -15° ~ -30°    |

### Sound System F (3.7.0)

Sound System F (3.7.0) layout with front center/left/right, top front left/right, side left/right, rear left/right, top back center, and LFE1/LFE2 speakers.

OAR Layout ID: `ck_oar_layout_sound_system_f_370`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Sound System F (3.7.0)                                               |
| Reference      | Loudspeaker configuration for Sound System F (7+3+2) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 12

| Index | Loudspeaker      | OAR Channel ID    | Azimuth        | Elevation      |
|-------|------------------|-------------------|----------------|----------------|
| 1     | Center           | `ck_channel_mp000`| 0°             | 0°             |
| 2     | Left             | `ck_channel_mp030`| 30°            | 0°             |
| 3     | Right            | `ck_channel_mm030`| -30°           | 0°             |
| 4     | Left Height      | `ck_channel_up045`| 30° ~ 45°      | 30° ~ 45°      |
| 5     | Right Height     | `ck_channel_um045`| -30° ~ -45°    | 30° ~ 45°      |
| 6     | Left Side        | `ck_channel_mp090`| 60° ~ 150°     | 0°             |
| 7     | Right Side       | `ck_channel_mm090`| -60° ~ -150°   | 0°             |
| 8     | Left Back        | `ck_channel_mp135`| 60° ~ 150°     | 0°             |
| 9     | Right Back       | `ck_channel_mm135`| -60° ~ -150°   | 0°             |
| 10    | Centre Height    | `ck_channel_up180`| 180°           | 45° ~ 90°      |
| 11    | LFE1             | `ck_channel_lfe1` | 30° ~ 90°      | -15° ~ -30°    |
| 12    | LFE2             | `ck_channel_lfe2` | -30° ~ -90°    | -15° ~ -30°    |

### Sound System G (4.9.0)

Sound System G (4.9.0) layout with front left/right/center, LFE, side left/right, rear left/right, top front/rear left/right, and side left/right center speakers.

OAR Layout ID: `ck_oar_layout_sound_system_g_490`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Sound System G (4.9.0)                                               |
| Reference      | Loudspeaker configuration for Sound System G (9+4+0) of [ITU-2051-3](https://www.itu.int/rec/R-REC-BS.2051) |

Number of channels: 14

| Index | Loudspeaker         | OAR Channel ID    | Azimuth           | Elevation      |
|-------|---------------------|-------------------|-------------------|----------------|
| 1     | Left                | `ck_channel_mp030`| 30° ~ 45°         | 0°             |
| 2     | Right               | `ck_channel_mm030`| -30° ~ -45°       | 0°             |
| 3     | Center              | `ck_channel_mp000`| 0°                | 0°             |
| 4     | LFE1                | `ck_channel_lfe1` | -                 | -              |
| 5     | Left Side Surround  | `ck_channel_mp090`| 85° ~ 110°        | 0°             |
| 6     | Right Side Surround | `ck_channel_mm090`| -85° ~ -110°      | 0°             |
| 7     | Left Rear Surround  | `ck_channel_mp135`| 120° ~ 150°       | 0°             |
| 8     | Right Rear Surround | `ck_channel_mm135`| -120° ~ -150°     | 0°             |
| 9     | Left Top Front      | `ck_channel_up045`| 30° ~ 45°         | 30° ~ 55°      |
| 10    | Right Top Front     | `ck_channel_um045`| -30° ~ -45°       | 30° ~ 55°      |
| 11    | Left Top Back       | `ck_channel_up135`| 100° ~ 150°       | 30° ~ 55°      |
| 12    | Right Top Back      | `ck_channel_um135`| -100° ~ -150°     | 30° ~ 55°      |
| 13    | Left Screen         | N/A               | Left Screen Edge  | 0°             |
| 14    | Right Screen        | N/A               | Right Screen Edge | 0°             |

## Layout Subsets

They could only be used as an element layout.

### LFE

LFE (Low Frequency Effects) subset with a single LFE channel.

OAR Layout ID: `ck_oar_layout_lfe`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | LFE                                                                  |
| Reference      | The low-frequency effects subset (LFE) of 7.1.4ch                    |

Number of channels: 1

| Index | Loudspeaker |
|-------|-------------|
| 1     | LFE1        |

### Stereo S

Stereo S subset with surround left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_s`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo S                                                             |
| Reference      | Surround subset (Ls/Rs) of 5.1.4ch                                   |

Number of channels: 2

| Index | Loudspeaker     |
|-------|-----------------|
| 1     | Left Surround   |
| 2     | Right Surround  |

### Stereo SS

Stereo SS subset with side left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_ss`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo SS                                                            |
| Reference      | Side surround subset (Lss/Rss) of 7.1.4ch                            |

Number of channels: 2

| Index | Loudspeaker          |
|-------|----------------------|
| 1     | Left Side Surround   |
| 2     | Right Side Surround  |

### Stereo RS

Stereo RS subset with rear surround left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_rs`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo RS                                                            |
| Reference      | Rear surround subset (Lrs/Rrs) of 7.1.4ch                            |

Number of channels: 2

| Index | Loudspeaker         |
|-------|---------------------|
| 1     | Left Rear Surround  |
| 2     | Right Rear Surround |

### Stereo TF

Stereo TF subset with top front left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_tf`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo TF                                                            |
| Reference      | Top front subset (Ltf/Rtf) of 7.1.4ch                                |

Number of channels: 2

| Index | Loudspeaker     |
|-------|-----------------|
| 1     | Left Top Front  |
| 2     | Right Top Front |

### Stereo TB

Stereo TB subset with top back left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_tb`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo TB                                                            |
| Reference      | Top back subset (Ltb/Rtb) of 7.1.4ch                                 |

Number of channels: 2

| Index | Loudspeaker    |
|-------|----------------|
| 1     | Left Top Back  |
| 2     | Right Top Back |

### Top 4 Channel

Top 4 Channel subset with top front left/right and top back left/right speakers.

OAR Layout ID: `ck_oar_layout_top_4ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Top 4 Channel                                                        |
| Reference      | Top 4 channels (Ltf/Rtf/Ltb/Rtb) of 7.1.4ch                          |

Number of channels: 4

| Index | Loudspeaker     |
|-------|-----------------|
| 1     | Left Top Front  |
| 2     | Right Top Front |
| 3     | Left Top Back   |
| 4     | Right Top Back  |

### 3 Channel

3 Channel subset with front left, right, and center speakers.

OAR Layout ID: `ck_oar_layout_3ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | 3 Channel                                                            |
| Reference      | The front 3 channels (L/C/R) of 7.1.4ch                              |

Number of channels: 3

| Index | Loudspeaker   |
|-------|---------------|
| 1     | Left          |
| 2     | Right         |
| 3     | Center        |

### Stereo F

Stereo F subset with front left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_f`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo F                                                             |
| Reference      | The front subset (FL/FR) of 9.1.6ch                                  |

Number of channels: 2

| Index | Loudspeaker |
|-------|-------------|
| 1     | Front Left  |
| 2     | Front Right |

### Stereo Si

Stereo Si subset with side left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_si`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo Si                                                            |
| Reference      | The side subset (SiL/SiR) of 9.1.6ch                                 |

Number of channels: 2

| Index | Loudspeaker |
|-------|-------------|
| 1     | Side Left   |
| 2     | Side Right  |

### Stereo Tpsi

Stereo Tpsi subset with top side left and right speakers.

OAR Layout ID: `ck_oar_layout_stereo_tpsi`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Stereo Tpsi                                                          |
| Reference      | The top side subset (TpSiL/TpSiR) of 9.1.6ch                         |

Number of channels: 2

| Index | Loudspeaker    |
|-------|----------------|
| 1     | Top Side Left  |
| 2     | Top Side Right |

### Top 6 Channel

Top 6 Channel subset with top front left/right, top back left/right, and top side left/right speakers.

OAR Layout ID: `ck_oar_layout_top_6ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Top 6 Channel                                                        |
| Reference      | The top 6 channels (TpFL/TpFR/TpSiL/TpSiR/TpBL/TpBR) of 9.1.6ch      |

Number of channels: 6

| Index | Loudspeaker    |
|-------|----------------|
| 1     | Top Front Left |
| 2     | Top Front Right|
| 3     | Top Back Left  |
| 4     | Top Back Right |
| 5     | Top Side Left  |
| 6     | Top Side Right |

### LFE Pair

LFE Pair subset with two LFE channels.

OAR Layout ID: `ck_oar_layout_lfe_pair`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | LFE Pair                                                             |
| Reference      | The low-frequency effects subset (LFE1/LFE2) of 10.2.9.3ch           |

Number of channels: 2

| Index | Loudspeaker |
|-------|-------------|
| 1     | LFE1        |
| 2     | LFE2        |

### Bottom 3 Channel

Bottom 3 Channel subset with bottom front center, left, and right speakers.

OAR Layout ID: `ck_oar_layout_bottom_3ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Bottom 3 Channel                                                     |
| Reference      | The bottom 3 channels (BtFL/BtFC/BtFR) of 10.2.9.3ch                 |

Number of channels: 3

| Index | Loudspeaker        |
|-------|--------------------|
| 1     | Bottom Front Center|
| 2     | Bottom Front Left  |
| 3     | Bottom Front Right |

### Bottom 4 Channel

Bottom 4 Channel subset with bottom front left/right and bottom back left/right speakers.

OAR Layout ID: `ck_oar_layout_bottom_4ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Bottom 4 Channel                                                     |
| Reference      | The bottom 4 channels (BtFL/BtFR/BtBL/BtBR) of 7.1.5.4ch             |

Number of channels: 4

| Index | Loudspeaker        |
|-------|--------------------|
| 1     | Bottom Front Left  |
| 2     | Bottom Front Right |
| 3     | Bottom Back Left   |
| 4     | Bottom Back Right  |

### Top 1 Channel

Top 1 Channel subset with top center speaker.

OAR Layout ID: `ck_oar_layout_top_1ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Top 1 Channel                                                        |
| Reference      | The top subset (TpC) of 7.1.5.4ch                                    |

Number of channels: 1

| Index | Loudspeaker |
|-------|-------------|
| 1     | Top Center  |

### Top 5 Channel

Top 5 Channel subset with top front left/right, top center, and top rear left/right speakers.

OAR Layout ID: `ck_oar_layout_top_5ch`

Reference:

| IAMF Reference |                                                                      |
|----------------|----------------------------------------------------------------------|
| Layout Name    | Top 5 Channel                                                        |
| Reference      | The top 5 channels (Ltf/Rtf/TpC/Ltb/Rtb) of 7.1.5.4ch                                  |

Number of channels: 5

| Index | Loudspeaker    |
|-------|----------------|
| 1     | Left Top Front |
| 2     | Right Top Front|
| 3     | Top Center     |
| 4     | Left Top Back  |
| 5     | Right Top Back |

## Channel Name Reference

The `OAR channel ID` names in OAR follow this naming convention:

- **ck_channel_**: Prefix for all OAR channel identifiers
- **First letter**:
  - **m**: Middle layer (horizontal plane)
  - **u**: Upper layer (elevated speakers)
  - **b**: Bottom layer (lowered speakers)
  - **t**: Top layer (directly overhead)
  - **lfe**: Low Frequency Effects channels
- **Second letter** (for non-LFE channels):
  - **p**: Positive azimuth (left of center)
  - **m**: Negative azimuth (right of center)
- **Numbers**: Azimuth angles in degrees (0 = front center, positive = left, negative = right)

For example:
- ck_channel_mp000: Middle layer, 0 degrees azimuth (front center)
- ck_channel_mp030: Middle layer, 30 degrees left of front center
- ck_channel_mm030: Middle layer, 30 degrees right of front center
- ck_channel_up045: Upper layer, 45 degrees left of front center
- ck_channel_bm045: Bottom layer, 45 degrees right of front center
- ck_channel_tp000: Top layer, directly overhead
- ck_channel_lfe1: Low Frequency Effects channel 1
