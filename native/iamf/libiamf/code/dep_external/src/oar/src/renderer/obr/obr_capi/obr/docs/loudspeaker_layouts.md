# Loudspeaker Layouts

The following loudspeaker layouts can be rendered binaurally using `obr`.

## Loudspeaker Layout Matrix

This matrix shows which loudspeakers are present in each layout and their channel ordering (1-based index).
Rows: Loudspeakers, Columns: Layouts (subsets excluded)

| Loudspeaker            | Mono  | Stereo | IAMF 5.1 | IAMF 5.1.2 | IAMF 5.1.4 | IAMF 7.1 | IAMF 7.1.2 | IAMF 7.1.4 | IAMF 3.1.2 | IAMF 9.1.6 | IAMF 9.1.6 Alt | IAMF 7.1.5.4 | IAMF 10.2.9.3 |
|------------------------|-------|--------|----------|------------|------------|----------|------------|------------|------------|------------|----------------|--------------|---------------|
| `kL30` (30°, 0°)       |       | 1 (L)  | 1 (L)    | 1 (L)      | 1 (L)      | 1 (L)    | 1 (L)      | 1 (L)      | 1 (L)      | 7 (FLc)    | 1 (FLc)        | 1 (L)        | 7 (FLc)       |
| `kR30` (-30°, 0°)      |       | 2 (R)  | 2 (R)    | 2 (R)      | 2 (R)      | 2 (R)    | 2 (R)      | 2 (R)      | 2 (R)      | 8 (FRc)    | 2 (FRc)        | 2 (R)        | 8 (FRc)       |
| `kC` (0°, 0°)          | 1 (C) |        | 3 (C)    | 3 (C)      | 3 (C)      | 3 (C)    | 3 (C)      | 3 (C)      | 3 (C)      | 3 (FC)     | 3 (FC)         | 3 (C)        | 3 (FC)        |
| `kLFE` (0°, -30°)      |       |        | 4 (LFE)  | 4 (LFE)    | 4 (LFE)    | 4 (LFE)  | 4 (LFE)    | 4 (LFE)    | 4 (LFE)    |            | 4 (LFE)        | 4 (LFE)      |               |
| `kL60` (60°, 0°)       |       |        |          |            |            |          |            |            |            | 1 (FL)     | 5 (FL)         |              | 1 (FL)        |
| `kR60` (-60°, 0°)      |       |        |          |            |            |          |            |            |            | 2 (FR)     | 6 (FR)         |              | 2 (FR)        |
| `kL90` (90°, 0°)       |       |        |          |            |            | 5 (Lss)  | 5 (Lss)    | 5 (Lss)    |            | 9 (SiL)    | 7 (SiL)        | 5 (Lss)      | 11 (SiL)      |
| `kR90` (-90°, 0°)      |       |        |          |            |            | 6 (Rss)  | 6 (Rss)    | 6 (Rss)    |            | 10 (SiR)   | 8 (SiR)        | 6 (Rss)      | 12 (SiR)      |
| `kL110` (110°, 0°)     |       |        | 5 (Ls)   | 5 (Ls)     | 5 (Ls)     |          |            |            |            |            |                |              |               |
| `kR110` (-110°, 0°)    |       |        | 6 (Rs)   | 6 (Rs)     | 6 (Rs)     |          |            |            |            |            |                |              |               |
| `kL135` (135°, 0°)     |       |        |          |            |            | 7 (Lrs)  | 7 (Lrs)    | 7 (Lrs)    |            | 5 (BL)     | 9 (BL)         | 7 (Lrs)      | 5 (BL)        |
| `kR135` (-135°, 0°)    |       |        |          |            |            | 8 (Rrs)  | 8 (Rrs)    | 8 (Rrs)    |            | 6 (BR)     | 10 (BR)        | 8 (Rrs)      | 6 (BR)        |
| `k180` (180°, 0°)      |       |        |          |            |            |          |            |            |            |            |                |              | 9 (BC)        |
| `kUC` (0°, 45°)        |       |        |          |            |            |          |            |            |            |            |                |              | 15 (TpFC)     |
| `kUL30` (30°, 45°)     |       |        |          | 7 (Ltf)    |            |          |            |            |            |            |                |              |               |
| `kUR30` (-30°, 45°)    |       |        |          | 8 (Rtf)    |            |          |            |            |            |            |                |              |               |
| `kUL45` (45°, 45°)     |       |        |          |            | 7 (Ltf)    |          | 9 (Ltf)    | 9 (Ltf)    | 5 (Ltf)    | 11 (TpFL)  | 11 (TpFL)      | 9 (Ltf)      | 13 (TpFL)     |
| `kUR45` (-45°, 45°)    |       |        |          |            | 8 (Rtf)    |          | 10 (Rtf)   | 10 (Rtf)   | 6 (Rtf)    | 12 (TpFR)  | 12 (TpFR)      | 10 (Rtf)     | 14 (TpFR)     |
| `kUL90` (90°, 45°)     |       |        |          |            |            |          |            |            |            | 15 (TpSiL) | 13 (TpSiL)     |              | 19 (TpSiL)    |
| `kUR90` (-90°, 45°)    |       |        |          |            |            |          |            |            |            | 16 (TpSiR) | 14 (TpSiR)     |              | 20 (TpSiR)    |
| `kT` (0°, 90°)         |       |        |          |            |            |          |            |            |            |            |                | 11 (TpC)     | 16 (TpC)      |
| `kUL135` (135°, 45°)   |       |        |          |            | 9 (Ltr)    |          |            | 11 (Ltb)   |            | 13 (TpBL)  | 15 (TpBL)      | 12 (Ltb)     | 17 (TpBL)     |
| `kUR135` (-135°, 45°)  |       |        |          |            | 10 (Rtr)   |          |            | 12 (Rtb)   |            | 14 (TpBR)  | 16 (TpBR)      | 13 (Rtb)     | 18 (TpBR)     |
| `kU180` (180°, 45°)    |       |        |          |            |            |          |            |            |            |            |                |              | 21 (TpBC)     |
| `kBC` (0°, -30°)       |       |        |          |            |            |          |            |            |            |            |                |              | 22 (BtFC)     |
| `kBL45` (45°, -30°)    |       |        |          |            |            |          |            |            |            |            |                | 14 (BtFL)    | 23 (BtFL)     |
| `kBR45` (-45°, -30°)   |       |        |          |            |            |          |            |            |            |            |                | 15 (BtFR)    | 24 (BtFR)     |
| `kBL135` (135°, -30°)  |       |        |          |            |            |          |            |            |            |            |                | 16 (BtBL)    |               |
| `kBR135` (-135°, -30°) |       |        |          |            |            |          |            |            |            |            |                | 17 (BtBR)    |               |
| `kLFE1` (45°, -30°)    |       |        |          |            |            |          |            |            |            | 4 (LFE1)   |                |              | 4 (LFE1)      |
| `kLFE2` (-45°, -30°)   |       |        |          |            |            |          |            |            |            |            |                |              | 10 (LFE2)     |


## Individual Layouts

### Mono

Single center loudspeaker.

OBR kID: `kLayoutMono`

Reference:

| IAMF Reference                |      |
|-------------------------------|------|
| Layout Name                   | Mono |
| Reference                     |      |
| `loudspeaker_layout`          | `0`  |
| `expanded_loudspeaker_layout` | N/A  |

Number of channels: 1

| Index | Label | Name   | OBR kID | Azimuth | Elevation |
|-------|-------|--------|---------|---------|-----------|
| 1     | C     | Centre | `kC`    | 0°      | 0°        |

### Stereo

Standard stereo layout with left and right loudspeakers at ±30° azimuth.

OBR kID: `kLayoutStereo`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | Stereo                                                               |
| Reference                     | Loudspeaker configuration for Sound System A (0+2+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `1`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 2

| Index | Label | Name  | OBR kID | Azimuth | Elevation |
|-------|-------|-------|---------|---------|-----------|
| 1     | L     | Left  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right | `kR30`  | -30°    | 0°        |

### IAMF 5.1

IAMF 5.1 Layout

OBR kID: `kLayout5_1_0_ch`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | 5.1ch                                                                |
| Reference                     | Loudspeaker configuration for Sound System B (0+5+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `2`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 6

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | L     | Left                  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre                | `kC`    | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |
| 5     | Ls    | Left Surround         | `kL110` | 110°    | 0°        |
| 6     | Rs    | Right Surround        | `kR110` | -110°   | 0°        |

### IAMF 5.1.2

IAMF 5.1.2 Layout

OBR kID: `kLayout5_1_2_ch`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | 5.1.2ch                                                              |
| Reference                     | Loudspeaker configuration for Sound System C (2+5+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `3`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 8

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | L     | Left                  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre                | `kC`    | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |
| 5     | Ls    | Left Surround         | `kL110` | 110°    | 0°        |
| 6     | Rs    | Right Surround        | `kR110` | -110°   | 0°        |
| 7     | Ltf   | Left Top Front        | `kUL30` | 30°     | 45°       |
| 8     | Rtf   | Right Top Front       | `kUR30` | -30°    | 45°       |

### IAMF 5.1.4

IAMF 5.1.4 Layout

OBR kID: `kLayout5_1_4_ch`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | 5.1.4ch                                                              |
| Reference                     | Loudspeaker configuration for Sound System D (4+5+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `4`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 10

| Index | Label | Name                  | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------------|----------|---------|-----------|
| 1     | L     | Left                  | `kL30`   | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`   | -30°    | 0°        |
| 3     | C     | Centre                | `kC`     | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`   | 0°      | -30°      |
| 5     | Ls    | Left Surround         | `kL110`  | 110°    | 0°        |
| 6     | Rs    | Right Surround        | `kR110`  | -110°   | 0°        |
| 7     | Ltf   | Left Top Front        | `kUL45`  | 45°     | 45°       |
| 8     | Rtf   | Right Top Front       | `kUR45`  | -45°    | 45°       |
| 9     | Ltr   | Left Top Rear         | `kUL135` | 135°    | 45°       |
| 10    | Rtr   | Right Top Rear        | `kUR135` | -135°   | 45°       |

### IAMF 7.1

IAMF 7.1 Layout

OBR kID: `kLayout7_1_0_ch`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | 7.1ch                                                                |
| Reference                     | Loudspeaker configuration for Sound System I (0+7+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `5`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 8

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | L     | Left                  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre                | `kC`    | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |
| 5     | Lss   | Left Side Surround    | `kL90`  | 90°     | 0°        |
| 6     | Rss   | Right Side Surround   | `kR90`  | -90°    | 0°        |
| 7     | Lrs   | Left Rear Surround    | `kL135` | 135°    | 0°        |
| 8     | Rrs   | Right Rear Surround   | `kR135` | -135°   | 0°        |

### IAMF 7.1.2

IAMF 7.1.2 Layout

OBR kID: `kLayout7_1_2_ch`

Reference:

| IAMF Reference                |                                                                           |
|-------------------------------|---------------------------------------------------------------------------|
| Layout Name                   | 7.1.2ch                                                                   |
| Reference                     | The combination of 7.1ch and the Left and Right top front pair of 7.1.4ch |
| `loudspeaker_layout`          | `6`                                                                       |
| `expanded_loudspeaker_layout` | N/A                                                                       |

Number of channels: 10

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | L     | Left                  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre                | `kC`    | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |
| 5     | Lss   | Left Side Surround    | `kL90`  | 90°     | 0°        |
| 6     | Rss   | Right Side Surround   | `kR90`  | -90°    | 0°        |
| 7     | Lrs   | Left Rear Surround    | `kL135` | 135°    | 0°        |
| 8     | Rrs   | Right Rear Surround   | `kR135` | -135°   | 0°        |
| 9     | Ltf   | Left Top Front        | `kUL45` | 45°     | 45°       |
| 10    | Rtf   | Right Top Front       | `kUR45` | -45°    | 45°       |

### IAMF 7.1.4

IAMF 7.1.4 Layout

OBR kID: `kLayout7_1_4_ch`

Reference:

| IAMF Reference                |                                                                      |
|-------------------------------|----------------------------------------------------------------------|
| Layout Name                   | 7.1.4ch                                                              |
| Reference                     | Loudspeaker configuration for Sound System J (4+7+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `7`                                                                  |
| `expanded_loudspeaker_layout` | N/A                                                                  |

Number of channels: 12

| Index | Label | Name                  | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------------|----------|---------|-----------|
| 1     | L     | Left                  | `kL30`   | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`   | -30°    | 0°        |
| 3     | C     | Centre                | `kC`     | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`   | 0°      | -30°      |
| 5     | Lss   | Left Side Surround    | `kL90`   | 90°     | 0°        |
| 6     | Rss   | Right Side Surround   | `kR90`   | -90°    | 0°        |
| 7     | Lrs   | Left Rear Surround    | `kL135`  | 135°    | 0°        |
| 8     | Rrs   | Right Rear Surround   | `kR135`  | -135°   | 0°        |
| 9     | Ltf   | Left Top Front        | `kUL45`  | 45°     | 45°       |
| 10    | Rtf   | Right Top Front       | `kUR45`  | -45°    | 45°       |
| 11    | Ltb   | Left Top Back         | `kUL135` | 135°    | 45°       |
| 12    | Rtb   | Right Top Back        | `kUR135` | -135°   | 45°       |

### IAMF 3.1.2

IAMF 3.1.2 Layout

OBR kID: `kLayout3_1_2_ch`

Reference:

| IAMF Reference                |                                                 |
|-------------------------------|-------------------------------------------------|
| Layout Name                   | 3.1.2ch                                         |
| Reference                     | The front subset (L/C/R/Ltf/Rtf/LFE) of 7.1.4ch |
| `loudspeaker_layout`          | `8`                                             |
| `expanded_loudspeaker_layout` | N/A                                             |

Number of channels: 6

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | L     | Left                  | `kL30`  | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre                | `kC`    | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |
| 5     | Ltf   | Left Top Front        | `kUL45` | 45°     | 45°       |
| 6     | Rtf   | Right Top Front       | `kUR45` | -45°    | 45°       |

### IAMF 9.1.6

IAMF 9.1.6 Layout

OBR kID: `kLayout9_1_6_ch`

Reference:

| IAMF Reference                |                                                                                     |
|-------------------------------|-------------------------------------------------------------------------------------|
| Layout Name                   | 9.1.6ch                                                                             |
| Reference                     | The subset of Loudspeaker configuration for Sound System H (9+10+3) of [ITU-2051-3] |
| `loudspeaker_layout`          | `15`                                                                                |
| `expanded_loudspeaker_layout` | `8`                                                                                 |

Number of channels: 16

| Index | Label | Name                    | OBR kID  | Azimuth | Elevation |
|-------|-------|-------------------------|----------|---------|-----------|
| 1     | FL    | Front Left              | `kL60`   | 60°     | 0°        |
| 2     | FR    | Front Right             | `kR60`   | -60°    | 0°        |
| 3     | FC    | Front Centre            | `kC`     | 0°      | 0°        |
| 4     | LFE1  | Low-Frequency Effects-1 | `kLFE1`  | 45°     | -30°      |
| 5     | BL    | Back Left               | `kL135`  | 135°    | 0°        |
| 6     | BR    | Back Right              | `kR135`  | -135°   | 0°        |
| 7     | FLc   | Front Left Centre       | `kL30`   | 30°     | 0°        |
| 8     | FRc   | Front Right Centre      | `kR30`   | -30°    | 0°        |
| 9     | SiL   | Side Left               | `kL90`   | 90°     | 0°        |
| 10    | SiR   | Side Right              | `kR90`   | -90°    | 0°        |
| 11    | TpFL  | Top Front Left          | `kUL45`  | 45°     | 45°       |
| 12    | TpFR  | Top Front Right         | `kUR45`  | -45°    | 45°       |
| 13    | TpBL  | Top Back Left           | `kUL135` | 135°    | 45°       |
| 14    | TpBR  | Top Back Right          | `kUR135` | -135°   | 45°       |
| 15    | TpSiL | Top Side Left           | `kUL90`  | 90°     | 45°       |
| 16    | TpSiR | Top Side Right          | `kUR90`  | -90°    | 45°       |

### IAMF 9.1.6 Alt

IAMF 9.1.6 Alternative Ordering (Sequential in Matrix) + LFE instead of LFE1

OBR kID: `kLayout9_1_6_ch_alt`

Reference:

| IAMF Reference                |                                                                                                           |
|-------------------------------|-----------------------------------------------------------------------------------------------------------|
| Layout Name                   | 9.1.6ch Alternative Ordering                                                                              |
| Reference                     | The subset of Loudspeaker configuration for Sound System H (9+10+3) of [ITU-2051-3] + LFE instead of LFE1 |
| `loudspeaker_layout`          | `15`                                                                                                      |
| `expanded_loudspeaker_layout` | `8`                                                                                                       |

Number of channels: 16

| Index | Label | Name                  | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------------|----------|---------|-----------|
| 1     | FLc   | Front Left Centre     | `kL30`   | 30°     | 0°        |
| 2     | FRc   | Front Right Centre    | `kR30`   | -30°    | 0°        |
| 3     | FC    | Front Centre          | `kC`     | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`   | 0°      | -30°      |
| 5     | FL    | Front Left            | `kL60`   | 60°     | 0°        |
| 6     | FR    | Front Right           | `kR60`   | -60°    | 0°        |
| 7     | SiL   | Side Left             | `kL90`   | 90°     | 0°        |
| 8     | SiR   | Side Right            | `kR90`   | -90°    | 0°        |
| 9     | BL    | Back Left             | `kL135`  | 135°    | 0°        |
| 10    | BR    | Back Right            | `kR135`  | -135°   | 0°        |
| 11    | TpFL  | Top Front Left        | `kUL45`  | 45°     | 45°       |
| 12    | TpFR  | Top Front Right       | `kUR45`  | -45°    | 45°       |
| 13    | TpSiL | Top Side Left         | `kUL90`  | 90°     | 45°       |
| 14    | TpSiR | Top Side Right        | `kUR90`  | -90°    | 45°       |
| 15    | TpBL  | Top Back Left         | `kUL135` | 135°    | 45°       |
| 16    | TpBR  | Top Back Right        | `kUR135` | -135°   | 45°       |

### IAMF 7.1.5.4

IAMF 7.1.5.4 Layout

OBR kID: `kLayout7_1_5_4_ch`

Reference:

| IAMF Reference                |                                                                                                                                              |
|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Layout Name                   | 7.1.5.4ch                                                                                                                                    |
| Reference                     | Loudspeaker configuration with the top and the bottom speakers added to Loudspeaker configuration for Sound System J (4+7+0) of [ITU-2051-3] |
| `loudspeaker_layout`          | `15`                                                                                                                                         |
| `expanded_loudspeaker_layout` | `16`                                                                                                                                         |

Number of channels: 17

| Index | Label | Name                  | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------------|----------|---------|-----------|
| 1     | L     | Left                  | `kL30`   | 30°     | 0°        |
| 2     | R     | Right                 | `kR30`   | -30°    | 0°        |
| 3     | C     | Centre                | `kC`     | 0°      | 0°        |
| 4     | LFE   | Low-Frequency Effects | `kLFE`   | 0°      | -30°      |
| 5     | Lss   | Left Side Surround    | `kL90`   | 90°     | 0°        |
| 6     | Rss   | Right Side Surround   | `kR90`   | -90°    | 0°        |
| 7     | Lrs   | Left Rear Surround    | `kL135`  | 135°    | 0°        |
| 8     | Rrs   | Right Rear Surround   | `kR135`  | -135°   | 0°        |
| 9     | Ltf   | Left Top Front        | `kUL45`  | 45°     | 45°       |
| 10    | Rtf   | Right Top Front       | `kUR45`  | -45°    | 45°       |
| 11    | TpC   | Top Centre            | `kT`     | 0°      | 90°       |
| 12    | Ltb   | Left Top Back         | `kUL135` | 135°    | 45°       |
| 13    | Rtb   | Right Top Back        | `kUR135` | -135°   | 45°       |
| 14    | BtFL  | Bottom Front Left     | `kBL45`  | 45°     | -30°      |
| 15    | BtFR  | Bottom Front Right    | `kBR45`  | -45°    | -30°      |
| 16    | BtBL  | Bottom Back Left      | `kBL135` | 135°    | -30°      |
| 17    | BtBR  | Bottom Back Right     | `kBR135` | -135°   | -30°      |

### IAMF 10.2.9.3

IAMF 10.2.9.3 Layout

OBR kID: `kLayout10_2_9_3_ch`

Reference:

| IAMF Reference                |                                                                       |
|-------------------------------|-----------------------------------------------------------------------|
| Layout Name                   | 10.2.9.3ch                                                            |
| Reference                     | Loudspeaker configuration for Sound System H (9+10+3) of [ITU-2051-3] |
| `loudspeaker_layout`          | `15`                                                                  |
| `expanded_loudspeaker_layout` | `13`                                                                  |

Number of channels: 24

| Index | Label | Name                    | OBR kID  | Azimuth | Elevation |
|-------|-------|-------------------------|----------|---------|-----------|
| 1     | FL    | Front Left              | `kL60`   | 60°     | 0°        |
| 2     | FR    | Front Right             | `kR60`   | -60°    | 0°        |
| 3     | FC    | Front Centre            | `kC`     | 0°      | 0°        |
| 4     | LFE1  | Low-Frequency Effects-1 | `kLFE1`  | 45°     | -30°      |
| 5     | BL    | Back Left               | `kL135`  | 135°    | 0°        |
| 6     | BR    | Back Right              | `kR135`  | -135°   | 0°        |
| 7     | FLc   | Front Left Centre       | `kL30`   | 30°     | 0°        |
| 8     | FRc   | Front Right Centre      | `kR30`   | -30°    | 0°        |
| 9     | BC    | Back Centre             | `k180`   | 180°    | 0°        |
| 10    | LFE2  | Low-Frequency Effects-2 | `kLFE2`  | -45°    | -30°      |
| 11    | SiL   | Side Left               | `kL90`   | 90°     | 0°        |
| 12    | SiR   | Side Right              | `kR90`   | -90°    | 0°        |
| 13    | TpFL  | Top Front Left          | `kUL45`  | 45°     | 45°       |
| 14    | TpFR  | Top Front Right         | `kUR45`  | -45°    | 45°       |
| 15    | TpFC  | Top Front Centre        | `kUC`    | 0°      | 45°       |
| 16    | TpC   | Top Centre              | `kT`     | 0°      | 90°       |
| 17    | TpBL  | Top Back Left           | `kUL135` | 135°    | 45°       |
| 18    | TpBR  | Top Back Right          | `kUR135` | -135°   | 45°       |
| 19    | TpSiL | Top Side Left           | `kUL90`  | 90°     | 45°       |
| 20    | TpSiR | Top Side Right          | `kUR90`  | -90°    | 45°       |
| 21    | TpBC  | Top Back Centre         | `kU180`  | 180°    | 45°       |
| 22    | BtFC  | Bottom Front Centre     | `kBC`    | 0°      | -30°      |
| 23    | BtFL  | Bottom Front Left       | `kBL45`  | 45°     | -30°      |
| 24    | BtFR  | Bottom Front Right      | `kBR45`  | -45°    | -30°      |

### IAMF LFE

IAMF LFE Subset

OBR kID: `kSubsetLFE`

Reference:

| IAMF Reference                |                                                   |
|-------------------------------|---------------------------------------------------|
| Layout Name                   | LFE                                               |
| Reference                     | The low-frequency effects subset (LFE) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                              |
| `expanded_loudspeaker_layout` | `0`                                               |

Number of channels: 1

| Index | Label | Name                  | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------------|---------|---------|-----------|
| 1     | LFE   | Low-Frequency Effects | `kLFE`  | 0°      | -30°      |

### IAMF Stereo-S

IAMF Stereo-S Subset

OBR kID: `kSubsetStereo_S`

Reference:

| IAMF Reference                |                                        |
|-------------------------------|----------------------------------------|
| Layout Name                   | Stereo-S                               |
| Reference                     | The surround subset (Ls/Rs) of 5.1.4ch |
| `loudspeaker_layout`          | `15`                                   |
| `expanded_loudspeaker_layout` | `1`                                    |

Number of channels: 2

| Index | Label | Name           | OBR kID | Azimuth | Elevation |
|-------|-------|----------------|---------|---------|-----------|
| 1     | Ls    | Left Surround  | `kL110` | 110°    | 0°        |
| 2     | Rs    | Right Surround | `kR110` | -110°   | 0°        |

### IAMF Stereo-SS

IAMF Stereo-SS Subset

OBR kID: `kSubsetStereo_SS`

Reference:

| IAMF Reference                |                                               |
|-------------------------------|-----------------------------------------------|
| Layout Name                   | Stereo-SS                                     |
| Reference                     | The side surround subset (Lss/Rss) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                          |
| `expanded_loudspeaker_layout` | `2`                                           |

Number of channels: 2

| Index | Label | Name                | OBR kID | Azimuth | Elevation |
|-------|-------|---------------------|---------|---------|-----------|
| 1     | Lss   | Left Side Surround  | `kL90`  | 90°     | 0°        |
| 2     | Rss   | Right Side Surround | `kR90`  | -90°    | 0°        |

### IAMF Stereo-RS

IAMF Stereo-RS Subset

OBR kID: `kSubsetStereo_RS`

Reference:

| IAMF Reference                |                                               |
|-------------------------------|-----------------------------------------------|
| Layout Name                   | Stereo-RS                                     |
| Reference                     | The rear surround subset (Lrs/Rrs) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                          |
| `expanded_loudspeaker_layout` | `3`                                           |

Number of channels: 2

| Index | Label | Name                | OBR kID | Azimuth | Elevation |
|-------|-------|---------------------|---------|---------|-----------|
| 1     | Lrs   | Left Rear Surround  | `kL135` | 135°    | 0°        |
| 2     | Rrs   | Right Rear Surround | `kR135` | -135°   | 0°        |

### IAMF Stereo-TF

IAMF Stereo-TF Subset

OBR kID: `kSubsetStereo_TF`

Reference:

| IAMF Reference                |                                           |
|-------------------------------|-------------------------------------------|
| Layout Name                   | Stereo-TF                                 |
| Reference                     | The top front subset (Ltf/Rtf) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                      |
| `expanded_loudspeaker_layout` | `4`                                       |

Number of channels: 2

| Index | Label | Name            | OBR kID | Azimuth | Elevation |
|-------|-------|-----------------|---------|---------|-----------|
| 1     | Ltf   | Left Top Front  | `kUL45` | 45°     | 45°       |
| 2     | Rtf   | Right Top Front | `kUR45` | -45°    | 45°       |

### IAMF Stereo-TB

IAMF Stereo-TB Subset

OBR kID: `kSubsetStereo_TB`

Reference:

| IAMF Reference                |                                          |
|-------------------------------|------------------------------------------|
| Layout Name                   | Stereo-TB                                |
| Reference                     | The top back subset (Ltb/Rtb) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                     |
| `expanded_loudspeaker_layout` | `5`                                      |

Number of channels: 2

| Index | Label | Name           | OBR kID  | Azimuth | Elevation |
|-------|-------|----------------|----------|---------|-----------|
| 1     | Ltb   | Left Top Back  | `kUL135` | 135°    | 45°       |
| 2     | Rtb   | Right Top Back | `kUR135` | -135°   | 45°       |

### IAMF Top-4ch

IAMF Top-4ch Subset

OBR kID: `kSubsetTop_4ch`

Reference:

| IAMF Reference                |                                                 |
|-------------------------------|-------------------------------------------------|
| Layout Name                   | Top-4ch                                         |
| Reference                     | The top 4 channels (Ltf/Rtf/Ltb/Rtb) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                            |
| `expanded_loudspeaker_layout` | `6`                                             |

Number of channels: 4

| Index | Label | Name            | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------|----------|---------|-----------|
| 1     | Ltf   | Left Top Front  | `kUL45`  | 45°     | 45°       |
| 2     | Rtf   | Right Top Front | `kUR45`  | -45°    | 45°       |
| 3     | Ltb   | Left Top Back   | `kUL135` | 135°    | 45°       |
| 4     | Rtb   | Right Top Back  | `kUR135` | -135°   | 45°       |

### IAMF 3.0ch

IAMF 3.0ch Subset

OBR kID: `kSubset3_0ch`

Reference:

| IAMF Reference                |                                         |
|-------------------------------|-----------------------------------------|
| Layout Name                   | 3.0ch                                   |
| Reference                     | The front 3 channels (L/C/R) of 7.1.4ch |
| `loudspeaker_layout`          | `15`                                    |
| `expanded_loudspeaker_layout` | `7`                                     |

Number of channels: 3

| Index | Label | Name   | OBR kID | Azimuth | Elevation |
|-------|-------|--------|---------|---------|-----------|
| 1     | L     | Left   | `kL30`  | 30°     | 0°        |
| 2     | R     | Right  | `kR30`  | -30°    | 0°        |
| 3     | C     | Centre | `kC`    | 0°      | 0°        |

### IAMF Stereo-F

IAMF Stereo-F Subset

OBR kID: `kSubsetStereo_F`

Reference:

| IAMF Reference                |                                     |
|-------------------------------|-------------------------------------|
| Layout Name                   | Stereo-F                            |
| Reference                     | The front subset (FL/FR) of 9.1.6ch |
| `loudspeaker_layout`          | `15`                                |
| `expanded_loudspeaker_layout` | `9`                                 |

Number of channels: 2

| Index | Label | Name        | OBR kID | Azimuth | Elevation |
|-------|-------|-------------|---------|---------|-----------|
| 1     | FL    | Front Left  | `kL60`  | 60°     | 0°        |
| 2     | FR    | Front Right | `kR60`  | -60°    | 0°        |

### IAMF Stereo-Si

IAMF Stereo-Si Subset

OBR kID: `kSubsetStereo_Si`

Reference:

| IAMF Reference                |                                      |
|-------------------------------|--------------------------------------|
| Layout Name                   | Stereo-Si                            |
| Reference                     | The side subset (SiL/SiR) of 9.1.6ch |
| `loudspeaker_layout`          | `15`                                 |
| `expanded_loudspeaker_layout` | `10`                                 |

Number of channels: 2

| Index | Label | Name       | OBR kID | Azimuth | Elevation |
|-------|-------|------------|---------|---------|-----------|
| 1     | SiL   | Side Left  | `kL90`  | 90°     | 0°        |
| 2     | SiR   | Side Right | `kR90`  | -90°    | 0°        |

### IAMF Stereo-TpSi

IAMF Stereo-TpSi Subset

OBR kID: `kSubsetStereo_TpSi`

Reference:

| IAMF Reference                |                                              |
|-------------------------------|----------------------------------------------|
| Layout Name                   | Stereo-TpSi                                  |
| Reference                     | The top side subset (TpSiL/TpSiR) of 9.1.6ch |
| `loudspeaker_layout`          | `15`                                         |
| `expanded_loudspeaker_layout` | `11`                                         |

Number of channels: 2

| Index | Label | Name           | OBR kID | Azimuth | Elevation |
|-------|-------|----------------|---------|---------|-----------|
| 1     | TpSiL | Top Side Left  | `kUL90` | 90°     | 45°       |
| 2     | TpSiR | Top Side Right | `kUR90` | -90°    | 45°       |

### IAMF Top-6ch

IAMF Top-6ch Subset

OBR kID: `kSubsetTop_6ch`

Reference:

| IAMF Reference                |                                                                 |
|-------------------------------|-----------------------------------------------------------------|
| Layout Name                   | Top-6ch                                                         |
| Reference                     | The top 6 channels (TpFL/TpFR/TpSiL/TpSiR/TpBL/TpBR) of 9.1.6ch |
| `loudspeaker_layout`          | `15`                                                            |
| `expanded_loudspeaker_layout` | `12`                                                            |

Number of channels: 6

| Index | Label | Name            | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------|----------|---------|-----------|
| 1     | TpFL  | Top Front Left  | `kUL45`  | 45°     | 45°       |
| 2     | TpFR  | Top Front Right | `kUR45`  | -45°    | 45°       |
| 3     | TpBL  | Top Back Left   | `kUL135` | 135°    | 45°       |
| 4     | TpBR  | Top Back Right  | `kUR135` | -135°   | 45°       |
| 5     | TpSiL | Top Side Left   | `kUL90`  | 90°     | 45°       |
| 6     | TpSiR | Top Side Right  | `kUR90`  | -90°    | 45°       |

### IAMF LFE-Pair

IAMF LFE-Pair Subset

OBR kID: `kSubsetLFE_Pair`

Reference:

| IAMF Reference                |                                                            |
|-------------------------------|------------------------------------------------------------|
| Layout Name                   | LFE-Pair                                                   |
| Reference                     | The low-frequency effects subset (LFE1/LFE2) of 10.2.9.3ch |
| `loudspeaker_layout`          | `15`                                                       |
| `expanded_loudspeaker_layout` | `14`                                                       |

Number of channels: 2

| Index | Label | Name                    | OBR kID | Azimuth | Elevation |
|-------|-------|-------------------------|---------|---------|-----------|
| 1     | LFE1  | Low-Frequency Effects-1 | `kLFE1` | 45°     | -30°      |
| 2     | LFE2  | Low-Frequency Effects-2 | `kLFE2` | -45°    | -30°      |

### IAMF Bottom-3ch

IAMF Bottom-3ch Subset

OBR kID: `kSubsetBottom_3ch`

Reference:

| IAMF Reference                |                                                      |
|-------------------------------|------------------------------------------------------|
| Layout Name                   | Bottom-3ch                                           |
| Reference                     | The bottom 3 channels (BtFL/BtFC/BtFR) of 10.2.9.3ch |
| `loudspeaker_layout`          | `15`                                                 |
| `expanded_loudspeaker_layout` | `15`                                                 |

Number of channels: 3

| Index | Label | Name                | OBR kID | Azimuth | Elevation |
|-------|-------|---------------------|---------|---------|-----------|
| 1     | BtFC  | Bottom Front Centre | `kBC`   | 0°      | -30°      |
| 2     | BtFL  | Bottom Front Left   | `kBL45` | 45°     | -30°      |
| 3     | BtFR  | Bottom Front Right  | `kBR45` | -45°    | -30°      |

### IAMF Bottom-4ch

IAMF Bottom-4ch Subset

OBR kID: `kSubsetBottom_4ch`

Reference:

| IAMF Reference                |                                                          |
|-------------------------------|----------------------------------------------------------|
| Layout Name                   | Bottom-4ch                                               |
| Reference                     | The bottom 4 channels (BtFL/BtFR/BtBL/BtBR) of 7.1.5.4ch |
| `loudspeaker_layout`          | `15`                                                     |
| `expanded_loudspeaker_layout` | `17`                                                     |

Number of channels: 4

| Index | Label | Name               | OBR kID  | Azimuth | Elevation |
|-------|-------|--------------------|----------|---------|-----------|
| 1     | BtFL  | Bottom Front Left  | `kBL45`  | 45°     | -30°      |
| 2     | BtFR  | Bottom Front Right | `kBR45`  | -45°    | -30°      |
| 3     | BtBL  | Bottom Back Left   | `kBL135` | 135°    | -30°      |
| 4     | BtBR  | Bottom Back Right  | `kBR135` | -135°   | -30°      |

### IAMF Top-1ch

IAMF Top-1ch Subset

OBR kID: `kSubsetTop_1ch`

Reference:

| IAMF Reference                |                                   |
|-------------------------------|-----------------------------------|
| Layout Name                   | Top-1ch                           |
| Reference                     | The top subset (TpC) of 7.1.5.4ch |
| `loudspeaker_layout`          | `15`                              |
| `expanded_loudspeaker_layout` | `18`                              |

Number of channels: 1

| Index | Label | Name       | OBR kID | Azimuth | Elevation |
|-------|-------|------------|---------|---------|-----------|
| 1     | TpC   | Top Centre | `kT`    | 0°      | 90°       |

### IAMF Top-5ch

IAMF Top-5ch Subset

OBR kID: `kSubsetTop_5ch`

Reference:

| IAMF Reference                |                                                       |
|-------------------------------|-------------------------------------------------------|
| Layout Name                   | Top-5ch                                               |
| Reference                     | The top 5 channels (Ltf/Rtf/TpC/Ltb/Rtb) of 7.1.5.4ch |
| `loudspeaker_layout`          | `15`                                                  |
| `expanded_loudspeaker_layout` | `19`                                                  |

Number of channels: 5

| Index | Label | Name            | OBR kID  | Azimuth | Elevation |
|-------|-------|-----------------|----------|---------|-----------|
| 1     | Ltf   | Left Top Front  | `kUL45`  | 45°     | 45°       |
| 2     | Rtf   | Right Top Front | `kUR45`  | -45°    | 45°       |
| 3     | TpC   | Top Centre      | `kT`     | 0°      | 90°       |
| 4     | Ltb   | Left Top Back   | `kUL135` | 135°    | 45°       |
| 5     | Rtb   | Right Top Back  | `kUR135` | -135°   | 45°       |
