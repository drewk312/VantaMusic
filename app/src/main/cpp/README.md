# JamesDSP (native)

Immersive Sound uses the JamesDSP core library from [JamesDSPManager](https://github.com/james34602/JamesDSPManager) (same engine as RootlessJamesDSP / JDSP4Linux).

Sources live under `libjamesdsp/Main/libjamesdsp/jni/jamesdsp/`. See `Main/LICENSE` in the upstream repo for license terms.

To refresh sources:

```powershell
git clone --depth 1 --filter=blob:none --sparse https://github.com/james34602/JamesDSPManager.git jdsp-repo
cd jdsp-repo
git sparse-checkout init --cone
git sparse-checkout set Main/libjamesdsp/jni/jamesdsp/jdsp Main/libjamesdsp/jni/jamesdsp/cpthread.c Main/libjamesdsp/jni/jamesdsp/cpthread.h Main/libjamesdsp/jni/jamesdsp/essential.h Main/libjamesdsp/jni/jamesdsp/MemoryUsage.h
# copy into libjamesdsp/Main/libjamesdsp/jni/jamesdsp/
```
