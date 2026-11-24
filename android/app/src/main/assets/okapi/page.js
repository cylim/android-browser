"use strict";
// Okapi's side of each page, in the top frame only. It talks to the app over one port and does
// only what the app asks, or what the app needs to show the page:
//   - "icon": the site's icon, once the page has loaded, for the Tab list;
//   - "dark": darken the page while it has no dark theme of its own;
//   - "fetch": a file the person chose to save or share, fetched as the page would;
//   - "measureVideo": the size of the video the page shows fullscreen;
//   - "scrollTo": scroll a page that comes back to where it was.
// The page itself can't reach the port, so nothing on the page can send the app anything.
//
// The questions the app may ask, by type. Each gets the message and gives its answer, or a
// Promise of it. Debug builds add one for tests (src/debug/assets/okapi/test.js).
var okapiQuestions = {};
(() => {
  let port = null;
  let dark = false;
  let darkened = false;

  function connect() {
    port = browser.runtime.connectNative("okapi");
    port.onMessage.addListener(onMessage);
    port.onDisconnect.addListener(() => { port = null; });
  }

  function post(message) {
    if (port) port.postMessage(message);
  }

  function onMessage(message) {
    if (!message) return;
    if (message.type === "dark") {
      dark = !!message.on;
      updateDark();
    } else if (message.type === "fetch") {
      fetchFile(message);
    } else if (Object.hasOwn(okapiQuestions, message.type)) {
      new Promise((resolve) => resolve(okapiQuestions[message.type](message)))
        .then((value) => answer(message, true, value), (error) => answer(message, false, String(error)));
    }
  }

  okapiQuestions.measureVideo = measureVideo;
  // A page coming back scrolls to where it was, in CSS pixels.
  okapiQuestions.scrollTo = (message) => {
    window.scrollTo(0, message.y);
    return window.scrollY;
  };

  // The answer to the question [message] asked, as JSON.
  function answer(message, ok, value) {
    let json = "null";
    try {
      json = JSON.stringify(value === undefined ? null : value);
    } catch (e) {
      // Not something JSON can hold.
    }
    post({ type: "answer", id: message.id, ok, value: json });
  }

  // --- Fullscreen video ---------------------------------------------------------------------
  // [width, height] of the fullscreen video, or of the first video in the fullscreen element,
  // or null. A video still loading is waited for, a few seconds at most. A video in another
  // site's frame can't be seen from here.
  function measureVideo() {
    const element = document.fullscreenElement;
    if (!element) return Promise.resolve(null);
    const video = element.tagName === "VIDEO" ? element : element.querySelector("video");
    if (!video) return Promise.resolve(null);
    const size = () => (video.videoWidth > 0 && video.videoHeight > 0 ? [video.videoWidth, video.videoHeight] : null);
    if (size()) return Promise.resolve(size());
    return new Promise((resolve) => {
      const done = () => {
        video.removeEventListener("loadedmetadata", done);
        video.removeEventListener("resize", done);
        clearTimeout(timer);
        resolve(size());
      };
      const timer = setTimeout(done, 3000);
      video.addEventListener("loadedmetadata", done);
      video.addEventListener("resize", done);
    });
  }

  // --- Icon -------------------------------------------------------------------------------
  function iconCandidates() {
    const links = Array.from(document.querySelectorAll("link[rel][href]"))
      .filter((l) => /(^|\s)(icon|apple-touch-icon|apple-touch-icon-precomposed)(\s|$)/i.test(l.rel))
      .filter((l) => !/svg/i.test(l.type || "") && !/\.svg(\?|#|$)/i.test(l.href));
    const size = (l) => {
      const m = /(\d+)x(\d+)/.exec(l.getAttribute("sizes") || "");
      return m ? parseInt(m[1], 10) : (/apple-touch/i.test(l.rel) ? 180 : 32);
    };
    // Closest to 96px wins: big enough for a tile, small enough to send.
    links.sort((a, b) => Math.abs(size(a) - 96) - Math.abs(size(b) - 96));
    const hrefs = links.map((l) => l.href);
    hrefs.push(new URL("/favicon.ico", location.href).href);
    return Array.from(new Set(hrefs));
  }

  async function sendIcon() {
    if (!/^https?:$/.test(location.protocol)) return;
    for (const href of iconCandidates()) {
      try {
        const response = await fetch(href, { credentials: "include", referrer: location.href });
        if (!response.ok) continue;
        const blob = await response.blob();
        if (!blob.size || blob.size > 512 * 1024 || /svg/i.test(blob.type)) continue;
        // Not an image, such as a page sent in its place: the next candidate.
        await createImageBitmap(blob);
        const data = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reject;
          reader.readAsDataURL(blob);
        });
        post({ type: "icon", page: location.href, data });
        return;
      } catch (e) {
        // The next candidate, or none.
      }
    }
  }

  // --- Dark pages ---------------------------------------------------------------------------
  function luminance(color) {
    const m = /rgba?\(([^)]+)\)/.exec(color || "");
    if (!m) return null;
    const parts = m[1].split(/[ ,/]+/).filter(Boolean).map(parseFloat);
    if (parts.length > 3 && parts[3] === 0) return null;
    const [r, g, b] = parts;
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  }

  // A page has a dark theme when, asked for one, its background is dark.
  function pageIsDark() {
    const root = document.documentElement;
    if (!root) return true;
    let light = document.body ? luminance(getComputedStyle(document.body).backgroundColor) : null;
    if (light === null) light = luminance(getComputedStyle(root).backgroundColor);
    if (light === null) {
      // No background of its own: the canvas, dark only if the page says it can be.
      const scheme = getComputedStyle(root).colorScheme || "";
      return /dark/.test(scheme) && matchMedia("(prefers-color-scheme: dark)").matches;
    }
    return light < 0.5;
  }

  function updateDark() {
    const want = dark && !pageIsDark();
    if (want === darkened) return;
    darkened = want;
    browser.runtime.sendMessage({ type: want ? "darken" : "undarken" }).catch(() => {});
  }

  // --- Files --------------------------------------------------------------------------------
  function base64(bytes) {
    let text = "";
    for (let i = 0; i < bytes.length; i += 0x8000) {
      text += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    }
    return btoa(text);
  }

  // How long a file may take to arrive before the app is told it can't be had.
  const FETCH_LIMIT_MS = 60000;

  async function fetchFile(message) {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), FETCH_LIMIT_MS);
    try {
      // As the page would ask for it: with its cookies, and the page as referrer. The engine
      // sends a content script's referrer as the page's origin only, which is what sites that
      // keep their images to their own pages check.
      const response = await fetch(message.url, { credentials: "include", referrer: location.href, signal: abort.signal });
      if (!response.ok) throw new Error("status " + response.status);
      const blob = await response.blob();
      if (blob.size > message.max) throw new Error("too large");
      const bytes = new Uint8Array(await blob.arrayBuffer());
      const mime = blob.type || response.headers.get("content-type") || "";
      const piece = 1536 * 1024;
      let at = 0;
      do {
        post({ type: "fetched", id: message.id, ok: true, mime, data: base64(bytes.subarray(at, at + piece)), last: at + piece >= bytes.length });
        at += piece;
      } while (at < bytes.length);
    } catch (e) {
      post({ type: "fetched", id: message.id, ok: false });
    } finally {
      clearTimeout(timer);
    }
  }

  connect();
  // A page coming back from the back-forward cache needs its port again.
  window.addEventListener("pageshow", (event) => {
    if (event.persisted && !port) connect();
  });
  document.addEventListener("DOMContentLoaded", updateDark);
  window.addEventListener("load", () => {
    updateDark();
    sendIcon();
    // Pages that style themselves late, once their scripts have run.
    setTimeout(updateDark, 500);
  });
  matchMedia("(prefers-color-scheme: dark)").addEventListener("change", updateDark);
})();
