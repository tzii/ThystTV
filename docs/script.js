(() => {
  "use strict";

  const root = document.documentElement;
  const themeToggle = document.querySelector(".theme-toggle");
  const motionToggle = document.querySelector(".motion-toggle");
  const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
  const finePointer = matchMedia("(hover: hover) and (pointer: fine)");
  const device = document.querySelector("[data-tilt]");
  const tabs = [...document.querySelectorAll("[role='tab']")];
  const panels = [...document.querySelectorAll("[role='tabpanel']")];
  const tabList = document.querySelector(".feature-tabs");
  const narrowScreen = matchMedia("(max-width: 767px)");
  const dialog = document.querySelector(".demo-dialog");
  const video = dialog.querySelector("video");
  let demoTrigger;
  let tiltFrame = 0;
  let motionPreference = "running";
  try { if (localStorage.getItem("thysttv-motion") === "paused") motionPreference = "paused"; } catch (_) {}

  function persist(key, value) {
    try { localStorage.setItem(key, value); } catch (_) { /* Preferences still work without storage. */ }
  }

  function setTheme(theme) {
    root.dataset.theme = theme;
    const dark = theme === "dark";
    themeToggle.setAttribute("aria-pressed", String(dark));
    themeToggle.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
    themeToggle.title = themeToggle.getAttribute("aria-label");
    document.querySelector('meta[name="theme-color"]').content = dark ? "#121416" : "#f4f6f3";
  }

  function resetTilt() {
    cancelAnimationFrame(tiltFrame);
    tiltFrame = 0;
    device.style.removeProperty("transform");
  }

  function syncMotion() {
    const paused = reducedMotion.matches || motionPreference === "paused";
    root.dataset.motion = paused ? "paused" : "running";
    motionToggle.setAttribute("aria-pressed", String(paused));
    motionToggle.setAttribute("aria-label", reducedMotion.matches ? "Decorative motion disabled by system preference" : paused ? "Resume decorative motion" : "Pause decorative motion");
    motionToggle.disabled = reducedMotion.matches;
    motionToggle.title = reducedMotion.matches ? "Motion is off because of your system preference" : motionToggle.getAttribute("aria-label");
    if (paused) resetTilt();
  }

  function setFeature(feature, focus = false) {
    const selected = tabs.find((tab) => tab.dataset.feature === feature);
    if (!selected) return;
    tabs.forEach((tab) => {
      const active = tab === selected;
      tab.setAttribute("aria-selected", String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    panels.forEach((panel) => { panel.hidden = panel.id !== selected.getAttribute("aria-controls"); });
    if (focus) selected.focus({ preventScroll: true });
  }

  function syncTabOrientation() {
    tabList.setAttribute("aria-orientation", narrowScreen.matches ? "horizontal" : "vertical");
    resetTilt();
  }

  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => setFeature(tab.dataset.feature));
    tab.addEventListener("keydown", (event) => {
      const horizontal = narrowScreen.matches;
      let next;
      if (event.key === (horizontal ? "ArrowRight" : "ArrowDown")) next = (index + 1) % tabs.length;
      if (event.key === (horizontal ? "ArrowLeft" : "ArrowUp")) next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === "Home") next = 0;
      if (event.key === "End") next = tabs.length - 1;
      if (next === undefined) return;
      event.preventDefault();
      setFeature(tabs[next].dataset.feature, true);
    });
  });

  document.querySelectorAll("[data-select-feature]").forEach((link) => {
    link.addEventListener("click", () => setFeature(link.dataset.selectFeature, true));
  });

  themeToggle.addEventListener("click", () => {
    const theme = root.dataset.theme === "dark" ? "light" : "dark";
    setTheme(theme);
    persist("thysttv-theme", theme);
  });

  motionToggle.addEventListener("click", () => {
    motionPreference = root.dataset.motion === "paused" ? "running" : "paused";
    persist("thysttv-motion", motionPreference);
    syncMotion();
  });
  reducedMotion.addEventListener("change", syncMotion);
  narrowScreen.addEventListener("change", syncTabOrientation);
  finePointer.addEventListener("change", resetTilt);

  device.addEventListener("pointermove", (event) => {
    if (!finePointer.matches || narrowScreen.matches || root.dataset.motion === "paused") return;
    const bounds = device.parentElement.getBoundingClientRect();
    const x = Math.max(-.5, Math.min(.5, (event.clientX - bounds.left) / bounds.width - .5));
    const y = Math.max(-.5, Math.min(.5, (event.clientY - bounds.top) / bounds.height - .5));
    cancelAnimationFrame(tiltFrame);
    tiltFrame = requestAnimationFrame(() => {
      device.style.transform = "perspective(1000px) rotateX(" + (-y * 7) + "deg) rotateY(" + (-8 + x * 9) + "deg) rotateZ(-5deg)";
      tiltFrame = 0;
    });
  });
  device.addEventListener("pointerleave", resetTilt);

  if (typeof dialog.showModal === "function") {
    document.querySelectorAll("[data-demo]").forEach((link) => {
      link.addEventListener("click", (event) => {
        event.preventDefault();
        demoTrigger = link;
        dialog.showModal();
        // Playback starts only through the video's native controls.
        dialog.querySelector(".demo-close").focus();
      });
    });
    dialog.querySelector(".demo-close").addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", (event) => {
      if (event.target !== dialog) return;
      const bounds = dialog.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
    dialog.addEventListener("close", () => {
      video.pause();
      demoTrigger?.focus({ preventScroll: true });
    });
  }

  setTheme(root.dataset.theme);
  syncMotion();
  syncTabOrientation();
  const requestedFeature = location.hash.slice(1);
  setFeature(requestedFeature === "stats" ? "stats" : "chat");
  root.classList.add("js-ready");
  themeToggle.hidden = false;
  motionToggle.hidden = false;
  tabList.hidden = false;

  // Stop looping decoration and video while the page is in the background.
  document.addEventListener("visibilitychange", () => {
    document.querySelectorAll(".stage-float, .chat-sticker").forEach((element) => {
      element.style.animationPlayState = document.hidden ? "paused" : "running";
    });
    if (document.hidden) {
      resetTilt();
      video.pause();
    }
  });
})();
