VANTA spatial playback components, 2026-09-07
Steam Audio SDK 4.8.1; Media3 MPEG-H adapter 1.10.0 (Apache-2.0).
native/mpegh/libmpegh: 335a2587fed4d769f8a21ae8816afd0aaa226b4f
native/ittiam-mpegh: f7ff0ac78d4d83f0b853bf2dff2ef075c92724f8
native/openjoc: 77e842303d4cb9d95b29ff41a3b9ed230f246b80

MPEG-H source and modified adapters are included free of charge in mpeg-h-source.zip in this APK.
FDK decoder source is unchanged. VANTA modified the Media3 adapter to use CICP 19 and Steam Audio headphone rendering, 2026-09-07. Ittiam standard integer typedefs were corrected for Android, 2026-09-07.
OpenJOC includes SADIE II D1 HRTF attribution in its third-party notices.
OpenJOC is experimental and is not Dolby certification. Decoder availability does not supply provider rights or streams. See each full license for terms, including patent provisions.

VANTA modification, 2026-09-08: OpenJOC QMF direct modulation is computed with a 128-point inverse FFT, retaining f64 precision. A stateful direct-equation comparison and upstream QMF tests verify equivalence. OpenJOC speaker output passes through Steam Audio for efficient portable headphone playback.
