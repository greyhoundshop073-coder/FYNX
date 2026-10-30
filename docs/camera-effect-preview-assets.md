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
