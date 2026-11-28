# ThystTV 1.7 native media

Captured 2026-11-28 on an Android 16 phone emulator (1080×2400 at 420 dpi, held
in landscape), signed-out debug app `com.tzii.thysttv.debug` from the 1.7.1 test
build `debug-patch171-9f9e5cb58` (the 1.7.1 integration branch with floating chat
v2, PR #130). These are actual Android screens with no generated UI, personal
account data or substituted values.

The video is a past broadcast of the public AlveusSanctuary 24/7 animal cams (the
channel's recording, opened from its Videos tab), chosen because it shows no
streamer faces. Chat is that recording's chat replay, so names and messages are
public Twitch chat from the broadcast. 7TV, BTTV and FFZ emotes were turned off
for the capture because some show faces; Twitch emotes and badges are unchanged.
Each published frame was checked for faces, unsuitable text and third-party links.

The player was set to **Fill**, so the 16:9 video covers the 20:9 screen and the
card sits over the picture. In the default **Fit** mode the card can also sit on
the letterbox bars, which is intended.

- `floating-chat-appearance.webp`: **Aa** open beside the floating chat card, with
  the Clear, Shade and Solid backgrounds and text size. The player controls are up,
  so the card shows its header (**Aa** and close) and the corner resize grip.
- `floating-chat-clear.webp`, `floating-chat-solid.webp`: the card with the Clear
  and Solid backgrounds. The site and README hero (`docs/cropped/watch-player.*`)
  shows Shade, the default, flush with the top-right corner and the controls hidden.
- `gesture-brightness.webp`: the brightness slider at the left edge while the card
  fades back on the right.
- `gesture-seek.webp`: a VoD seek slider with the change, the target time and the
  recording's length, while the card fades back.
- `floating-chat.mp4`: silent screen recording from one take, cut into seven
  segments joined by 0.4 s cross-fades: drag the card from the top-right corner
  across to the left, down, and back up to the corner; show the controls and
  resize it from the corner grip; then switch Clear, Solid and Shade in **Aa**.
  Idle time between gestures was trimmed; encoding normalized to H.264/yuv420p,
  1280 px wide, 15 fps, with MP4 faststart. No frames were replaced or generated.
- `floating-chat-poster.webp`: the recording's first frame, used as its poster.

Stills are full-size lossy WebP re-encodes (quality 88) of unedited PNG
screencaps; the two gesture stills were taken with `adb screencap` during a slow
swipe. The hero scales its capture into the same device frame as before. The raw
captures and recording are retained privately, unchanged.

## File checksums

| File | Dimensions / duration | SHA-256 |
| --- | --- | --- |
| `floating-chat-appearance.webp` | 2400×1080 | `5fab051727f3f33fc5a52dab7db63e1805bd09083d0a99ad5eba0d591fae136e` |
| `floating-chat-clear.webp` | 2400×1080 | `3a0f44e0e030b660a96fd6ba35f695c1fe4b8a1f2167e3c0351ee92f43559a8f` |
| `floating-chat-poster.webp` | 1280×576 | `408b780c5b33503a3bbbd39037bc9b6f83f4e1897419eb39666860e8db37bbd1` |
| `floating-chat-solid.webp` | 2400×1080 | `0e7149f4736780055be0abad3d078058ce18b7f9c94f27af63e2f1b3beafd910` |
| `floating-chat.mp4` | 1280×576; 27.53s; 15fps | `6504a62544a30da89e9d2164cdc87c62fabe6e333645f7825d40eb90a8856d50` |
| `gesture-brightness.webp` | 2400×1080 | `0670345d771937b0f06a61484b955bc9465472737c5278028022e5e52c790539` |
| `gesture-seek.webp` | 2400×1080 | `f9023a5f567f9af980b4ae027bcb69654bbbe8977632b9effa3fa868f1c85c63` |
| `../../cropped/watch-player.png` | 2042×986 | `1b75675a6850950a3b45a47492d7430daa7528f9f4957b6e2192551eb80e437e` |
| `../../cropped/watch-player.webp` | 2042×986 | `e22e66b0337219c988072dedc69505324cffa8c693ad7af78ce72bd2deb52274` |
