const root = document.documentElement;
const railPanel = document.querySelector(".rail-panel");
const railToggle = document.querySelector(".rail-toggle");
const railClose = document.querySelector(".rail-close");
const railScrim = document.querySelector(".rail-scrim");
const themeToggle = document.querySelector(".theme-toggle");
const themeText = document.querySelector(".theme-text");

function setRail(open) {
  railPanel.classList.toggle("is-open", open);
  railPanel.setAttribute("aria-hidden", String(!open));
  railToggle.setAttribute("aria-expanded", String(open));
  railScrim.hidden = !open;
}

function setTheme(theme) {
  root.setAttribute("data-theme", theme);
  localStorage.setItem("thysttv-theme", theme);
  const dark = theme === "dark";
  themeToggle.setAttribute("aria-pressed", String(dark));
  themeToggle.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
  themeText.textContent = dark ? "LIGHT" : "DARK";
}

const params = new URLSearchParams(window.location.search);
const requestedTheme = params.get("theme");
const storedTheme = localStorage.getItem("thysttv-theme");
const preferredTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
setTheme(requestedTheme === "dark" || requestedTheme === "light" ? requestedTheme : storedTheme || preferredTheme);

if (params.get("panel") === "open") {
  setRail(true);
}

railToggle.addEventListener("click", () => {
  setRail(railToggle.getAttribute("aria-expanded") !== "true");
});

railClose.addEventListener("click", () => setRail(false));
railScrim.addEventListener("click", () => setRail(false));

themeToggle.addEventListener("click", () => {
  setTheme(root.getAttribute("data-theme") === "dark" ? "light" : "dark");
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    setRail(false);
  }
});
