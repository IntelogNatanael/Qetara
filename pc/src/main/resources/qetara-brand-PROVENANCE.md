# Desktop brand resources

The contour in `qetara-brand.svg` is copied exactly from Android's `app/src/main/res/drawable/ic_launcher_foreground.xml`. Android resources are unchanged. A [Qetara Figma file](https://www.figma.com/design/FZhnKBdDPJWpPrbxn3AGQQ) was created, but the Starter MCP limit prevented writing its canvas. The reviewed reference is the local desktop implementation and its generated previews, not that empty file.

- Original contour bounds: `(8, 21.6103)..(100, 86.3897)`, centered at `(54,54)`.
- Aspect ratio: `92 / 64.7794 = 1.4202045712062783`.
- App icons preserve the mobile 108-square viewport and uniform `0.65625` scale about its center. Foreground bounds are `(23.8125,32.744259375)..(84.1875,75.255740625)`.
- Desktop supplies a rounded square of radius 24 in that viewport, background `#FFF7ED`, foreground `#102A43`, with transparent exterior corners and no outline.
- The flat header mark uses the same contour fitted uniformly to 78% of its slot. It does not include adaptive-launcher padding. Android applies its own mask/crop; desktop app tiles preserve the full viewport.

`QetaraBrand.kt` renders these constants directly with an explicit origin pivot. The PNG is rasterized from SVG by sharp 0.35.4 / librsvg 2.62.91 / libvips 8.18.6 at 1024-square resolution. Pillow 12.3.0 packages PNG-derived ICO frames at 16, 24, 32, 48, 64, 128 and 256 pixels and an ICNS container up to 1024 pixels. Small ICO frames use antialiased downsampling.

Regeneration with those tools, from the repository root:

```sh
node -e "require('sharp')('pc/src/main/resources/qetara-brand.svg',{density:768}).resize(1024,1024).png().toFile('pc/src/main/resources/qetara.png')"
```

```python
from PIL import Image
from pathlib import Path
resources = Path('pc/src/main/resources')
image = Image.open(resources / 'qetara.png').convert('RGBA')
image.save(resources / 'qetara.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
image.save(resources / 'qetara.icns')
```

The Pillow-only `scripts/generate-icons.py --desktop-only` preserves the same desktop corner radius and foreground geometry without writing Android resources. It approximates curves with polygons, so it is not the byte-identical generator of the SVG-rasterized resources above. Without that flag, it also exports Android legacy icons; that mode was not run in this iteration.
