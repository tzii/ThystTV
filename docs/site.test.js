const fs = require("fs");
const assert = require("assert");

const html = fs.readFileSync("index.html", "utf8");
const css = fs.readFileSync("styles.css", "utf8");
const js = fs.readFileSync("script.js", "utf8");

assert.match(html, /A BETTER[\s\S]*TWITCH[\s\S]*CLIENT[\s\S]*FOR ANDROID\./i);
assert.match(html, /polished fork of Xtra/i);
assert.match(html, /credit goes to the Xtra project/i);
assert.match(html, /Floating chat overlay/i);
assert.match(html, /Local stats and watch-history insights/i);
assert.match(html, /GNU Affero General Public License v3\.0|AGPL-3\.0/i);
assert.match(html, /20\s*stars/i);
assert.match(html, /1\s*fork/i);
assert.match(html, /ThystTV 1\.2/i);
assert.match(html, /popular tab clean\.png/i);
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

console.log("Static site smoke checks passed.");
