# ThystTV 1.6 native media

Captured 2026-11-15 on an Android 16 phone emulator, signed-out debug app
`com.tzii.thysttv.debug`, version 1.6.0. Source: `5b60e79ad` (1.6.0 plus the
launcher-return fix from PR #105; that fix changes none of these visible screens).
These are actual Android screens, with no generated UI, personal account data,
chat usernames, streamer faces, notifications or substituted metrics.

- `spoiler-shield.png`: a public Chess category protected for 24 hours in Settings → UI.
- `live-buffer.png`: the default-off local rewind buffer, storage budget and nearby player options.
- `settings-wide.png`: the existing two-pane Settings with Player selected.
- `spoiler-shield.mp4`: silent native screen recording, selecting 24 hours and
  reopening the manager. Initial waiting time was trimmed; encoding normalized
  to H.264/yuv420p, 15fps, with MP4 faststart. The full raw recording is retained
  privately, unchanged. No frames were replaced or generated.

Phone images are 1080×2400 at 420 dpi. Wide capture uses a 1800×1200 window at
240 dpi (1200dp wide), then the emulator's original size and density were restored.
PNG captures are unedited. The rule is demonstration data on the anonymous
emulator and was removed after capture. No authenticated clip creation, Cast
receiver, physical TV or folding interaction is demonstrated here.

Public 1.6 settings organization is shown. The pending dedicated Spoilers screen
belongs to PR #106 and is not represented as a shipped 1.6 change.

The README and site's older 1.3 sample-data controls/Stats and pre-1.3 floating-chat
footage keep their original provenance labels and files.

## File checksums

| File | Dimensions / duration | SHA-256 |
| --- | --- | --- |
| `live-buffer.png` | 1080×2400 | `31e463ff61061281226762f1363c60181a0b45796df0ecece2978e3a42f7cb14` |
| `settings-wide.png` | 1800×1200 | `1571ef64d68895b27bffa52d0501c8e6786149f1264256e4181063766de21f32` |
| `spoiler-shield.mp4` | 1080×2400; 9.20s; 15fps | `811a89a0f9a5fe964c93fa2a3c2ed39866add2441a92f9bacec31f57f282700d` |
| `spoiler-shield.png` | 1080×2400 | `3b71b3c094976fa6451878e14c7185205f0fc62b6bfb5fc6357426be6ec6ef02` |
