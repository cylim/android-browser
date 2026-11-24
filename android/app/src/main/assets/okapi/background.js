"use strict";
// Darkens a page that has no dark theme of its own (SPEC, "Dark pages"), when its content
// script asks. A user style sheet goes over the page's own and isn't subject to its Content
// Security Policy. Images and video are turned back, so they keep their colors.
const DARKEN = `
html { filter: invert(90%) hue-rotate(180deg) !important; background-color: #fff !important; }
img, video, picture, canvas, iframe, embed, object, svg image, [style*="background-image"] {
  filter: invert(100%) hue-rotate(180deg) !important;
}`;

browser.runtime.onMessage.addListener((message, sender) => {
  if (!sender.tab || !message) return undefined;
  const css = { code: DARKEN, cssOrigin: "user" };
  if (message.type === "darken") return browser.tabs.insertCSS(sender.tab.id, css).catch(() => {});
  if (message.type === "undarken") return browser.tabs.removeCSS(sender.tab.id, css).catch(() => {});
  return undefined;
});
