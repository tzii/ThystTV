# ThystTV 1.7 native media

Captured 2026-11-23 on an Android 16 phone emulator (1080×2400 at 420 dpi, held
in landscape), signed-out debug app `com.tzii.thysttv.debug` built from the 1.7.1
integration branch at `f7099a2ab`. These are actual Android screens with no
generated UI, personal account data or substituted values.

The video is a past broadcast of the public AlveusSanctuary 24/7 animal cams (the
channel's recording, opened from Rewind), chosen because it shows no streamer
faces. Chat is that recording's chat replay, so names and messages are public
Twitch chat from the broadcast. 7TV, BTTV and FFZ emotes were turned off for the
capture because some show faces; Twitch emotes and badges are unchanged. Each
published frame was checked for faces and unsuitable text.

The player was set to **Fill**, so the 16:9 video covers the 20:9 screen and the
card sits over the picture. In the default **Fit** mode the card can also sit on
the letterbox bars, which is intended.

- `floating-chat-appearance.webp`: **Aa** open above the floating chat card, with
  the Clear, Shade and Solid backgrounds and text size.
- `floating-chat-clear.webp`, `floating-chat-solid.webp`: the card with the Clear
  and Solid backgrounds. The site and README hero (`docs/cropped/watch-player.*`)
  shows Shade, the default.
- `floating-chat-tucked.webp`: the card tucked into an edge tab with an unread count.
- `gesture-brightness.webp`: the brightness slider at the left edge while the card
  fades back on the right.
- `gesture-seek.webp`: a VoD seek slider with the change, the target time and the
  recording's length, while the card fades back.
- `floating-chat.mp4`: silent screen recording made from two takes, cut into three
  segments joined by 0.4 s cross-fades: throw the card across, drag it down and
  back up, and tuck it; then bring it back from the tab and switch Clear, Solid and
  Shade in **Aa**. Idle time and a black camera switch on the stream were trimmed;
  encoding normalized to H.264/yuv420p, 1280 px wide, 15 fps, with MP4 faststart.
  No frames were replaced or generated.
- `floating-chat-poster.webp`: the recording's first frame, used as its poster.

Stills are full-size lossy WebP re-encodes (quality 88) of unedited PNG
screencaps; the hero scales its capture into the same device frame as before.
The raw recordings are retained privately, unchanged.

## File checksums

| File | Dimensions / duration | SHA-256 |
| --- | --- | --- |
| `floating-chat-appearance.webp` | 2400×1080 | `d20512695d48519a5e37c718217fc2b88a1f6c45fdd34fdbc278738f2e85cea9` |
| `floating-chat-clear.webp` | 2400×1080 | `1719a4e0d62a6fc33e8766ea0d07bc5ed241b68dce956bcd26eaed9ffeff00cc` |
| `floating-chat-poster.webp` | 1280×576 | `4c0e65c52dec88e5e04f918fac134648d52b6dd8d3a42bed6523be99fde06187` |
| `floating-chat-solid.webp` | 2400×1080 | `98e0705ceb92934dde6874fffb9f984838a5dc3a7786d1a22448d70789a0982b` |
| `floating-chat-tucked.webp` | 2400×1080 | `d7f7d9dcc045f8d7a56b1c0f21e77a609bfc6a2057f839bd15caad389dab5f17` |
| `floating-chat.mp4` | 1280×576; 19.13s; 15fps | `58fd91d4b13e8cb59eb67865fe5db9c79049db7e173397d3c97ab0ac5154e96c` |
| `gesture-brightness.webp` | 2400×1080 | `f7a1329b858d3b2b14d182f04e1b6af6365e19b373823a7191384fbaccc4dbc9` |
| `gesture-seek.webp` | 2400×1080 | `c6fd4bbc39effc792875ab2b68e10907d4f5a6772e7d3b170fb77b32f6e3f409` |
| `../../cropped/watch-player.png` | 2042×986 | `04f1134e05730898249ca5f9dca5c7703f0d6a3696467c8a327be233f8dd3e86` |
| `../../cropped/watch-player.webp` | 2042×986 | `9188dfc13e1d938d37f08ad60c13abe8b87e0e1397013614cbf80aca0a849fc2` |
