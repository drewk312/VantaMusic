IAMF reference decoder: https://github.com/AOMediaCodec/libiamf at e55e1832a608affe602de2ee39929bd7759a75ab
OAR submodule: 3d1d23b807543f993a1d0cf0a9839c7f0746d94b
Media3 Java/JNI bridge: 1.9.3 (Apache 2.0), compiled against Media3 1.10.0.
Local changes: explicit binaural output, no upstream test executables, ABI-isolated codec builds.

ARM renderer fix: matrix_render_arm.c must include matrix_render.h before testing
its architecture macro. The upstream dispatch included that header while the
NEON implementation did not, causing ARM builds to call an empty function and
produce silent PCM. Covered by device PCM-amplitude tests (Opus + ambisonic IAMF).
Android adaptation: clock_gettime for API 26 compatibility and native errors sent
to Android logcat. Binaural output uses 48 kHz, 16-bit PCM. Source codec bit depth
must not be confused with this output precision.
