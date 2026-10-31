# FYNX camera effect thumbnail assets

The camera creator tray and captured-photo editor share one offline portrait-preview set:

- `camera_effect_portrait_1.webp` through `camera_effect_portrait_3.webp`: the three existing portrait photographs, retained unchanged.
- `camera_effect_portrait_4.xml` through `camera_effect_portrait_9.xml`: six original FYNX VectorDrawable portrait illustrations, added to give long effect carousels more variety without network downloads.

The selector rotates through all nine portraits in Looks, Face FX, Background, AI Studio, and Video FX. The same tile component is used in the live creator tray and captured-photo editor. Each tile applies the effect's local preview grade; Face FX also draws its actual sticker overlay over the portrait.

These thumbnails are illustrative previews only. Live face tracking, background segmentation, and saved-photo/video processing continue to use their separate on-device processing paths.
