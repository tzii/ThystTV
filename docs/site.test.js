const fs = require("fs");
const path = require("path");
const assert = require("assert");
const vm = require("node:vm");
const { test } = require("node:test");

const docsDir = __dirname;
const repoRoot = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(docsDir, "index.html"), "utf8");
const css = fs.readFileSync(path.join(docsDir, "styles.css"), "utf8");
const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");
const countOccurrences = (text, value) => text.split(value).length - 1;
const allFrontDoorText = html + "\n" + readme;

// Public distribution, attribution, and preview provenance remain release-independent.
assert.doesNotMatch(allFrontDoorText, /release\/1\.2-prep|NEW RELEASE PREP/i);
assert.doesNotMatch(html, /THYSTTV 1\.2|VERSION 1\.2|aria-label="\d+ (stars|forks|releases)"/i);
assert.match(html, /https:\/\/github\.com\/tzii\/ThystTV\/releases\/latest/);
assert.match(html, /<link rel="canonical" href="https:\/\/tzii\.github\.io\/ThystTV\/">/);
assert.match(html, /property="og:title"/);
assert.match(html, /name="twitter:card"/);
assert.match(html, /class="brand" href="#top"/);
assert.match(html, /Twitch client for Android/i);
assert.match(html, /polished fork of Xtra/i);
assert.match(html, /credit goes to the Xtra project/i);
assert.match(html, /floating chat/i);
assert.match(html, /Local stats and watch-history insights/i);
assert.match(html, /AGPL-3\.0/i);
assert.match(html, /Playback speed/i);
assert.match(html, /Video quality/i);
assert.match(html, /recorded on a ThystTV 1\.7\.1 test build/i);
assert.doesNotMatch(allFrontDoorText, /images\/readme\/floating-chat|recorded before 1\.3[^.]*floating|floating[^.]*recorded before 1\.3/i);
assert.match(html, /Native 1\.3 layout preview with sample data/i);

for (const phrase of ["Download and install", "Verify the APK", "GNU Affero General Public License", "Xtra", "not affiliated with Twitch or Amazon"]) {
  assert.match(allFrontDoorText, new RegExp(phrase, "i"));
}
assert.match(readme, /docs\/APK_VERIFICATION\.md/);
assert.match(readme, /SECURITY\.md/);
assert.match(html, /https:\/\/github\.com\/tzii\/ThystTV\/issues/);

// All navigation, tab/panel labels, and decorative SVG references resolve.
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
assert.strictEqual(new Set(ids).size, ids.length, "document IDs must be unique");
for (const match of html.matchAll(/\b(?:href|aria-controls)="#?([^"]+)"/g)) {
  if (!match[0].includes('href="#') && !match[0].includes("aria-controls=")) continue;
  assert.ok(ids.includes(match[1]), "reference must resolve: " + match[1]);
}
for (const match of html.matchAll(/\b(?:aria-labelledby|aria-describedby)="([^"]+)"/g)) {
  for (const id of match[1].split(" ")) assert.ok(ids.includes(id), "accessible label must resolve: " + id);
}
const tabs = [...html.matchAll(/<button\b[^>]*role="tab"[^>]*>/g)].map((match) => match[0]);
assert.strictEqual(tabs.length, 4, "all four feature groups must be selectable");
assert.strictEqual(tabs.filter((tab) => tab.includes('aria-selected="true"')).length, 1);
assert.strictEqual(tabs.filter((tab) => tab.includes('tabindex="0"')).length, 1);
for (const tab of tabs) {
  const target = tab.match(/aria-controls="([^"]+)"/)[1];
  assert.match(html, new RegExp('id="' + target + '" role="tabpanel"'));
}
assert.match(html, /class="skip-link" href="#main"/);
assert.match(css, /:focus-visible/);
assert.match(css, /prefers-reduced-motion/);
assert.match(html, /class="icon-button motion-toggle"/);
assert.match(html, /<dialog[^>]*aria-labelledby="demo-title"/);
assert.match(html, /<video controls playsinline preload="none"/);
assert.doesNotMatch(html, /<video[^>]*\bautoplay\b/);
assert.match(html, /href="images\/features-1\.7\/floating-chat\.mp4" data-demo/);
assert.match(css, /html:not\(\.js-ready\) \.feature-panel\[hidden\]/);
assert.match(css, /@media \(max-width: 480px\)/);

const head = html.match(/<head>[\s\S]*?<\/head>/)[0];
assert.ok(head.indexOf("thysttv-theme") < head.indexOf("styles.css"), "theme must initialize before styles");
assert.match(head, /src="script\.js[^"]*" defer/);

// Referenced local resources exist, images reserve layout space, and fonts are self-hosted.
for (const match of html.matchAll(/\b(?:src|srcset|poster|href)="([^"]+)"/g)) {
  const url = match[1].split("?")[0];
  if (/^(https?:|#)/.test(url)) continue;
  assert.ok(fs.existsSync(path.join(docsDir, decodeURIComponent(url))), "missing website resource: " + url);
}
for (const image of html.matchAll(/<img\b[^>]*>/g)) {
  assert.match(image[0], /\bwidth="\d+"/);
  assert.match(image[0], /\bheight="\d+"/);
  assert.match(image[0], /\balt="/);
}
assert.match(html, /loading="lazy"/);
assert.doesNotMatch(html, /alt="[^"]*(popular tab clean|\.png|\.jpg|\.webp)[^"]*"/i);
assert.match(html, /<picture>[\s\S]*?discover-phone\.webp[\s\S]*?discover-phone\.png[\s\S]*?<\/picture>/);
assert.match(html, /<picture>[\s\S]*?watch-player\.webp[\s\S]*?watch-player\.png[\s\S]*?<\/picture>/);
for (const asset of ["discover-phone", "watch-player"]) {
  assert.ok(fs.statSync(path.join(docsDir, "cropped", asset + ".webp")).size < fs.statSync(path.join(docsDir, "cropped", asset + ".png")).size);
}
assert.match(css, /url\("fonts\/outfit-latin\.woff2"\)/);
assert.ok(fs.existsSync(path.join(docsDir, "fonts/OFL-Outfit.txt")));
assert.doesNotMatch(html + css, /fonts\.googleapis\.com|fonts\.gstatic\.com/);

// Exercise the actual pre-paint bootstrap, including denied storage and invalid
// preferences. These failures used to break the site's theme or its controls.
const bootstrap = head.match(/<script>([\s\S]*?)<\/script>/)[1];
function initializePreferences({ search = "", stored = {}, denied = false, reduced = false } = {}) {
  const root = { dataset: {} };
  vm.runInNewContext(bootstrap, {
    document: { documentElement: root },
    location: { search },
    URLSearchParams,
    matchMedia: () => ({ matches: reduced }),
    localStorage: { getItem(key) { if (denied) throw new Error("Storage denied"); return stored[key] ?? null; } },
  });
  return { ...root.dataset };
}
test("brand defaults apply without stored preferences", () => {
  assert.deepStrictEqual(initializePreferences(), { theme: "dark", motion: "running" });
});
test("valid theme query wins over a saved choice; invalid query preserves it", () => {
  const stored = { "thysttv-theme": "light" };
  assert.equal(initializePreferences({ search: "?theme=dark", stored }).theme, "dark");
  assert.equal(initializePreferences({ search: "?theme=invalid", stored }).theme, "light");
  assert.equal(initializePreferences({ stored: { "thysttv-theme": "invalid" } }).theme, "dark");
});
test("storage denial leaves theme and reduced motion usable", () => {
  assert.deepStrictEqual(initializePreferences({ denied: true, reduced: true, search: "?theme=light" }), { theme: "light", motion: "paused" });
});
test("saved pause and system reduced motion independently suppress motion", () => {
  assert.equal(initializePreferences({ stored: { "thysttv-motion": "paused" } }).motion, "paused");
  assert.equal(initializePreferences({ reduced: true, stored: { "thysttv-motion": "running" } }).motion, "paused");
});

const floatingChatSectionMatch = readme.match(
  /^## Floating chat\r?\n[\s\S]*?(?=^## |(?![\s\S]))/m
);
assert.ok(floatingChatSectionMatch, "README should include a ## Floating chat section");

const floatingChatSection = floatingChatSectionMatch[0].replace(/\r\n/g, "\n");
const floatingChatVideo = "docs/images/features-1.7/floating-chat.mp4";
assert.ok(
  floatingChatSection.includes(
    `<a href="${floatingChatVideo}"><img src="docs/cropped/watch-player.webp" alt="Full-screen playback with the floating chat card on the right of the picture" width="760"></a>`
  ),
  "Floating chat preview should open the demo video"
);
assert.ok(
  floatingChatSection.includes(`<p align="center">
  <a href="${floatingChatVideo}">Watch the floating chat demo (MP4, silent)</a> · <a href="docs/images/features-1.7/README.md">Capture details</a>
</p>`),
  "Floating chat section should keep a centered link to the demo video"
);
assert.strictEqual(countOccurrences(floatingChatSection, floatingChatVideo), 2);
for (const look of ["clear", "appearance", "solid"]) {
  assert.ok(floatingChatSection.includes(`docs/images/features-1.7/floating-chat-${look}.webp`), "missing look: " + look);
}
// Every README image and link into docs/ resolves.
for (const match of readme.matchAll(/\b(?:src|href)="(docs\/[^"]+)"/g)) {
  assert.ok(fs.existsSync(path.join(repoRoot, match[1])), "missing README resource: " + match[1]);
}

console.log("Static site smoke checks passed.");
