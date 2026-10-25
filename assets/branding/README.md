# Sportcast TV — оформление

Основа: предоставленная пользователем белая надпись SPORTCAST. Финальные изображения созданы встроенным инструментом image_gen; CLI/API не использовались. Производные Android-ресурсы только обрезаны по полям и уменьшены, без перерисовки.

- `sportcast-tv-icon-master.png` — прежний квадратный мастер значка «S TV» (1254 × 1254), сохранён для истории.
- `sportcast-icon-master.png` — текущий квадратный мастер значка SPORTCAST.
- `sportcast-tv-banner-master.png` — горизонтальный мастер «SPORTCAST TV» (1672 × 941).
- `sportcast-tv-wordmark.png` — надпись для шапки клиента (900 × 171).
- `app/src/main/res/drawable-nodpi/ic_sportcast_tv.png` — значок 512 × 512.
- `app/src/main/res/drawable-xhdpi/sportcast_tv_banner.png` — баннер Android TV 320 × 180.
- `app/src/main/res/drawable-nodpi/sportcast_wordmark.png` — ресурс надписи.

## Финальный запрос: значок

Use case: logo-brand. Asset: square Android TV app icon, production graphic, based on reference image 1. A minimalist solid near-black square background. Place a very large white uppercase 'S' in the exact tall, ultra-condensed angular geometric letter style of the SPORTCAST reference, with squared counters and only very slightly softened corners. Add a smaller matching white 'TV' wordmark at the lower right beside the bottom of the S, aligned neatly as one compact S TV monogram. Exact text: S and TV only. Keep the complete monogram inside the central 65% of the square so circle/rounded-square launcher masks preserve it. Crisp flat vector-like white shapes, subtle white edge glow from the reference, high contrast, no bright color, no football imagery, no screen bezel, no extra text, no watermark, no rounded corners baked into the square canvas. Make the S dominant and recognizable at 48px.

## Финальный запрос: баннер

Use case: logo-brand. Input image 1 is the exact typography reference. Create a clean Android TV launcher banner. Preserve the original thin-stroke, tall narrow angular SPORTCAST lettering, correct exact word SPORTCAST, and add TV in the SAME narrow thin-stroke lettering after a small space. Exact text: SPORTCAST TV. All letters solid pure white, with smooth sharp clean geometric edges and small subtle white glow as in the reference. Flat digital logo on a uniform opaque near-black #090d12 background. Wide 16:9 canvas. One centred horizontal line of text occupying 80% of the width and about 23% of the canvas height, with generous empty padding around it for TV overscan. No illustration, no gradients, no grunge, no speckles, no distressed texture, no chipped paint, no other text, no watermark. Preserve the reference's thin strokes, do not make bold block lettering.

## Обновление 0.3.3

`sportcast-wordmark.png` — текущий мастер SPORTCAST без TV, отредактированный через imagegen. Его уменьшенная копия `sportcast_wordmark.png` используется и в шапке, и в `drawable/sportcast_banner.xml`. Баннер имеет пропорции 320 × 180, тёмный фон и центрированный логотип. Старые мастеры SPORTCAST TV сохранены для истории.

## Обновление основной иконки — 25 октября 2026

Основная квадратная иконка теперь использует полную надпись SPORTCAST вместо S TV: белый логотип по центру на тёмном фоне. Создана встроенным image_gen по прежней иконке и текущему логотипу, затем уменьшена до 512 × 512 для `app/src/main/res/drawable-nodpi/ic_sportcast_tv.png`. Манифест уже ссылается на этот ресурс.

Финальный запрос:

Use case: text-localization / logo-brand. Asset: production square Android app launcher icon. Input image 1 is edit target, existing S TV icon; input image 2 is exact SPORTCAST wordmark reference. Replace S TV completely with the full word SPORTCAST from image 2. Preserve the exact thin-stroke tall condensed angular geometric lettering of image 2, all nine letters in ONE unbroken horizontal line, no TV. Center the complete word horizontally and vertically on a uniform opaque near-black #101113 square canvas. Wordmark ink spans about 76% of canvas width, with original letter aspect ratios, so letter height is approximately 17% of square height. Generous empty space above and below. White lettering, crisp smooth edges, same minimal subtle white glow as reference. Flat graphic, no illustration, no texture, no extra text, no border, no rounded corners baked into canvas, no perspective, no icon mockup. Output square 1024x1024 or larger. This is the icon artwork itself.
