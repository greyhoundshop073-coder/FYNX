# FYNX camera effect thumbnail assets

The camera creator and captured-photo editor use three bundled female portrait previews:

| Resource | Upstream Unsplash photo |
| --- | --- |
| \`camera_effect_portrait_1.webp\` | https://unsplash.com/photos/1534528741775-53994a69daeb |
| \`camera_effect_portrait_2.webp\` | https://unsplash.com/photos/1517841905240-472988babdf9 |
| \`camera_effect_portrait_3.webp\` | https://unsplash.com/photos/1573496359142-b8d87734a5a2 |

The photographs are credited in the upstream asset repository and are distributed under the
Unsplash License. The photo assets are bundled in \`app/src/main/res/drawable-nodpi/\`, so the tray
remains available offline. The thumbnail selector rotates through all three portraits and applies
each effect's own local colour matrix. Face FX also draws its real sticker overlay over the portrait.

These images are illustrative previews only. Live face tracking, background segmentation, and
saved-photo/video processing continue to use their existing on-device processing paths.
