# Portrait live control layouts

Native Android layout previews, rendered with sample stats via Robolectric API 28
native graphics on 2026-11-15. No generated UI or real stream/chat/account content.

- before-busy-1.6.png: existing 1.6 rules with six left/two right actions and sample
  12:34:56 / 123.4K stats, 360×203 mdpi. Preserved unchanged from the combined
  310d65ee5 test run; that source uses the published 1.6 controls helper unchanged.
- single-row-phone.png / single-row-rtl.png: the new default four left/one right
  controls and 7:19:43 / 2.9K stats, 412×231 mdpi, with play centered. These are a
  different optional-button profile from the busy baseline; no pixel-equivalent
  before/after claim is made.
- overflow-large-text.png: 320dp, 200% text, all optional actions including Create
  clip enabled. The single horizontal bar is scrolled to its stats end. Fullscreen
  and the other right-side actions remain pinned. Text is not scaled down.

These verify native geometry, not physical touch/decoder behavior. Original user
screenshots and emulator evidence are retained privately. Phone and TV checks are
listed in MANUAL_QA.md.
