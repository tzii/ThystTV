const fs = require("fs");
const path = require("path");
const assert = require("assert");

const docsDir = __dirname;
const repoRoot = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(docsDir, "index.html"), "utf8");
const css = fs.readFileSync(path.join(docsDir, "styles.css"), "utf8");
const js = fs.readFileSync(path.join(docsDir, "script.js"), "utf8");
const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");

const countOccurrences = (text, value) => text.split(value).length - 1;
const allFrontDoorText = `${html}\n${readme}`;

assert.doesNotMatch(allFrontDoorText, /release\/1\.2-prep|NEW RELEASE PREP/i);
assert.doesNotMatch(html, /aria-label="\d+ (stars|forks|releases)"/i);
assert.match(allFrontDoorText, /https:\/\/github\.com\/tzii\/ThystTV\/releases\/latest/);
assert.match(html, /<link rel="canonical" href="https:\/\/tzii\.github\.io\/ThystTV\/">/);
assert.match(html, /property="og:title"/);
assert.match(html, /name="twitter:card"/);
assert.match(html, /class="brand" href="#top"/);

assert.match(html, /A BETTER[\s\S]*TWITCH[\s\S]*CLIENT[\s\S]*FOR ANDROID\./i);
assert.match(html, /polished fork of Xtra/i);
assert.match(html, /credit goes to the Xtra project/i);
assert.match(html, /floating chat/i);
assert.match(html, /Local stats and watch-history insights/i);
assert.match(html, /GNU Affero General Public License v3\.0|AGPL-3\.0/i);
assert.match(html, /ThystTV 1\.2/i);
assert.match(html, /theme-toggle/i);
assert.match(html, /control-showcase/i);
assert.match(html, /rail-scrim/i);
assert.match(html, /Playback speed/i);
assert.match(html, /Video quality/i);

const topNav = html.match(/<nav class="top-nav"[\s\S]*?<\/nav>/i)?.[0] || "";
const drawer = html.match(/<aside class="side-rail"[\s\S]*?<\/aside>/i)?.[0] || "";
assert.ok(drawer.includes("DOCS"), "sidebar should expose a docked docs rail");
assert.ok(drawer.includes("CONTRIBUTE"), "sidebar should expose a docked contribute rail");
assert.ok(drawer.includes("VERSION 1.2"), "sidebar should include vertical version copy");
for (const label of ["FEATURES", "STATS", "GITHUB"]) {
  assert.ok(topNav.includes(label), `top nav should include ${label}`);
  assert.ok(!drawer.includes(`>${label}<`), `drawer should not duplicate ${label} as a nav item`);
}

assert.match(html, /aria-controls="rail-panel"/);
assert.match(html, /id="rail-panel"/);
assert.match(html, /data-theme="light"/);
assert.match(js, /aria-expanded/);
assert.match(js, /railScrim/);
assert.match(js, /localStorage/);
assert.match(js, /data-theme/);
assert.match(js, /URLSearchParams/);
assert.match(js, /Escape/);
assert.match(css, /@keyframes/);
assert.match(css, /\.side-rail/);
assert.match(css, /\.rail-scrim/);
assert.match(css, /\.eyebrow/);
assert.match(css, /data-theme="dark"/);

for (const phrase of [
  "Download and install",
  "Verify the APK",
  "GNU Affero General Public License",
  "Xtra",
  "not affiliated with Twitch or Amazon",
]) {
  assert.match(allFrontDoorText, new RegExp(phrase, "i"));
}
assert.match(readme, /docs\/APK_VERIFICATION\.md/);
assert.match(readme, /SECURITY\.md/);
assert.match(html, /https:\/\/github\.com\/tzii\/ThystTV\/issues/);

const head = html.match(/<head>[\s\S]*?<\/head>/i)?.[0] ?? "";
assert.ok(head.indexOf("thysttv-theme") < head.indexOf("styles.css"));
assert.match(css, /:focus-visible/);
assert.match(html, /<figure[\s\S]*?<figcaption/i);
assert.match(html, /<picture>[\s\S]*?discover-phone\.webp[\s\S]*?discover-phone\.png[\s\S]*?<\/picture>/i);
assert.match(html, /<picture>[\s\S]*?watch-player\.webp[\s\S]*?watch-player\.png[\s\S]*?<\/picture>/i);

for (const image of html.matchAll(/<img\b[^>]*>/gi)) {
  assert.match(image[0], /\bwidth="\d+"/);
  assert.match(image[0], /\bheight="\d+"/);
}
assert.match(html, /loading="lazy"/);
assert.doesNotMatch(html, /alt="[^"]*(popular tab clean|\.png|\.jpg|\.webp)[^"]*"/i);

for (const relativePath of [
  "cropped/discover-phone.png",
  "cropped/discover-phone.webp",
  "cropped/watch-player.png",
  "cropped/watch-player.webp",
]) {
  assert.ok(fs.existsSync(path.join(docsDir, relativePath)), `${relativePath} must exist`);
}
assert.ok(
  fs.statSync(path.join(docsDir, "cropped/discover-phone.webp")).size <
    fs.statSync(path.join(docsDir, "cropped/discover-phone.png")).size
);
assert.ok(
  fs.statSync(path.join(docsDir, "cropped/watch-player.webp")).size <
    fs.statSync(path.join(docsDir, "cropped/watch-player.png")).size
);

const liveFloatingChatVideoUrl =
  "https://github.com/user-attachments/assets/99d97579-3340-4200-8aa7-3cae0414560e";
const floatingChatSectionMatch = readme.match(
  /^## Floating chat\r?\n[\s\S]*?(?=^## |(?![\s\S]))/m
);
assert.ok(floatingChatSectionMatch, "README should include a ## Floating chat section");

const floatingChatSection = floatingChatSectionMatch[0].replace(/\r\n/g, "\n");
const centeredDownloadFallback = `<p align="center">
  <a href="docs/images/readme/floating-chat.mp4">Download the floating chat demo video</a>
</p>`;
const expectedPresentationBlock = `<p align="center">
  <img src="docs/images/readme/floating-chat.png" alt="Full-screen playback with floating chat overlay" width="760">
</p>

${liveFloatingChatVideoUrl}

${centeredDownloadFallback}`;

assert.match(
  floatingChatSection,
  /^https:\/\/github\.com\/user-attachments\/assets\/99d97579-3340-4200-8aa7-3cae0414560e$/m,
  "Floating chat section should include the exact live GitHub video URL"
);
assert.strictEqual(
  countOccurrences(readme, liveFloatingChatVideoUrl),
  1,
  "README should include the exact live GitHub video URL exactly once"
);
assert.ok(
  floatingChatSection.includes(expectedPresentationBlock),
  "Floating chat media should form one contiguous preview, live video, and download block"
);
assert.strictEqual(
  countOccurrences(floatingChatSection, "docs/images/readme/floating-chat.png"),
  1,
  "Floating chat section should include the PNG preview path exactly once"
);
assert.strictEqual(
  countOccurrences(floatingChatSection, "docs/images/readme/floating-chat.mp4"),
  1,
  "Floating chat section should include the MP4 fallback path exactly once"
);
assert.ok(
  floatingChatSection.includes(centeredDownloadFallback),
  "Floating chat section should retain the centered download fallback"
);
assert.doesNotMatch(
  readme,
  /<a href="docs\/images\/readme\/floating-chat\.mp4">Watch the floating chat demo video<\/a>/,
  "README should remove the old watch-video fallback label"
);

console.log("Static site smoke checks passed.");
