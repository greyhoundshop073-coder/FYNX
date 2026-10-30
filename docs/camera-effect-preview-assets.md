# FYNX camera effect thumbnail

The effect tray uses the bundled photographic portrait at
`app/src/main/res/drawable-nodpi/camera_effect_preview.jpg`, duplicated from the
instrumentation fixture so previews are available offline. The source portrait comes from
the Android ML Kit Vision codelab fixture. Its upstream repository states that image/audio
assets are licensed under CC BY 4.0:
https://github.com/android/codelab-mlkit-android/blob/master/LICENSE

Effect tiles apply a lightweight local colour-matrix treatment to the portrait. The preview is
illustrative; actual face tracking, background segmentation and saved-photo/video processing
continue to use their existing on-device pipelines.

The creator tray presents its options as a single horizontally scrollable portrait carousel. Tiles are deliberately larger than the earlier compact grid so effect names and previews remain legible on narrow Android screens. The same offline-safe reference portrait is graded locally for each illustrative look; Face FX additionally draws its real sticker overlay. These thumbnails are visual guides, while capture and saved-media effects continue through their dedicated on-device processing paths.
