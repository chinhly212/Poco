# CapCap Android Port — Implementation Plan

## Scope

Build the editor as an Android-native feature inside the existing MUCHI app. Do not replace the app, change its application ID/signing setup, or rewrite unrelated tabs. Keep the port isolated under `com.contentautomation.videoeditor`.

The CapCap repository is the feature reference: https://github.com/notepower2k1/CapCap

## Non-negotiable feature set

1. Import a local video or open a video already downloaded by MUCHI.
2. Speech recognition (STT), including language selection and timestamped segments.
3. OCR recognition of text embedded in video frames, with selectable sampling rate and crop region.
4. Context-aware translation with editable source and translated text.
5. Subtitle generation, timing correction, styling, preview, and SRT import/export.
6. Text-to-speech dubbing, voice selection, per-segment regeneration, and audio mixing.
7. Multi-layer editing for subtitles, text, logo, blur/mask overlays, and background audio where Android APIs allow.
8. Video preview, project save/restore, export presets, progress, cancellation, and recoverable jobs.

Only Windows-specific packaging, Windows DLLs, PySide6 widgets, CUDA/NVENC and CapCut desktop automation may be replaced. Their user-facing capabilities should be preserved with Android equivalents wherever feasible.

## Android implementation strategy

- UI: Jetpack Compose, touch-first portrait layout with landscape workspace support.
- Video playback/preview: AndroidX Media3.
- Export: Android MediaCodec hardware encoders where supported; FFmpeg Android build as a compatibility/fallback path. Never assume NVIDIA NVENC exists on Android.
- STT: evaluate Sherpa-ONNX / ONNX Runtime Android and supported Whisper runtimes. Keep model downloads optional and expose model size before download.
- OCR: evaluate ML Kit Text Recognition and an ONNX OCR path; allow frame sampling/cropping to control cost.
- Translation: retain provider abstraction and CapCap's dialogue-context/prompt behavior; local/offline translation is optional and model-size gated.
- TTS: Android-compatible local Piper/Sherpa-ONNX voices first, with optional network TTS providers. Do not require a paid API for the editor to open or edit.
- Audio: Android audio APIs plus FFmpeg processing for mixing/separation where compatible.
- Background tasks: WorkManager/foreground execution with persistent progress and cancellation.

## Device resource policy

Use device resources, but do so adaptively:
- Detect available memory and device capabilities before loading models.
- Provide Low / Balanced / Performance processing profiles.
- Run one heavy inference stage at a time by default; release model sessions and temporary buffers between stages.
- Process long videos in chunks and stream frames/audio instead of loading the entire media file into RAM.
- Prefer GPU/NPU delegates only after runtime capability checks; always keep a CPU fallback.
- Respect thermal throttling and Android background execution limits.
- Keep original media untouched and use app-scoped working copies/cache with cleanup.

## Porting order

1. Domain formats and project model (subtitle cues, SRT, timing, project persistence).
2. Android editor shell and video import/preview.
3. STT and OCR engine interfaces plus downloadable model manager.
4. Translation orchestration and dialogue context.
5. Subtitle editor and preview overlays.
6. TTS/dubbing and audio mixing.
7. Timeline layers and final export.
8. Device profiling, memory/thermal safeguards, tests, and release build.

## Current implementation status

- Dedicated branch: `feature/capcap-android-editor`.
- Initial Android subtitle domain core added: `videoeditor/domain/SubtitleCue.kt`.
- No UI route, recognition engine, translation engine, TTS engine, or export pipeline has been wired yet.
- No APK has been built or device-tested for this feature.
