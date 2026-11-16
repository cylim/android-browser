// Opens a port to the app and answers its requests:
//   "list"  -> installed add-ons (browser.management)
//   "probe" -> what this extension can see across JRNY Profiles: cookie stores, httpbin.org
//              cookies in each store, and every tab. Only the test cookie "jrny_probe" is
//              reported with its value; other cookies are reported by name only.
const port = browser.runtime.connectNative("jrny");

async function probe() {
  const stores = await browser.cookies.getAllCookieStores();
  const cookies = [];
  for (const store of stores) {
    const list = await browser.cookies.getAll({ domain: "httpbin.org", storeId: store.id });
    for (const c of list) {
      cookies.push({ store: store.id, name: c.name, value: c.name === "jrny_probe" ? c.value : "…" });
    }
  }
  // Total cookie count per store (all sites), to tell "isolated" apart from "API sees nothing".
  const totals = {};
  for (const store of stores) {
    totals[store.id] = (await browser.cookies.getAll({ storeId: store.id })).length;
  }
  totals["(no storeId)"] = (await browser.cookies.getAll({})).length;
  totals["firefox-default"] = (await browser.cookies.getAll({ storeId: "firefox-default" })).length;
  const noStore = (await browser.cookies.getAll({ domain: "httpbin.org" }))
    .map((c) => (c.name === "jrny_probe" ? `${c.name}=${c.value}` : c.name));
  const tabs = (await browser.tabs.query({})).map((t) => ({
    id: t.id, active: t.active, incognito: t.incognito, cookieStoreId: t.cookieStoreId, url: t.url,
  }));
  return {
    type: "probe",
    stores: stores.map((s) => ({ id: s.id, incognito: s.incognito, tabIds: s.tabIds })),
    cookies,
    totals,
    getAllWithoutStoreId: noStore,
    tabs,
  };
}

port.onMessage.addListener(async (msg) => {
  try {
    if (msg.type === "list") {
      const all = await browser.management.getAll();
      port.postMessage({
        type: "list",
        addons: all.map((a) => ({ id: a.id, name: a.name, enabled: a.enabled, installType: a.installType })),
      });
    } else if (msg.type === "probe") {
      port.postMessage(await probe());
    } else {
      port.postMessage({ type: "echo", got: msg });
    }
  } catch (e) {
    port.postMessage({ type: "error", request: msg.type, error: String(e) });
  }
});
// Content scripts report passkey calls they rejected (passkeys-unsupported.js).
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === "passkey-blocked") port.postMessage(msg);
});
port.postMessage({ type: "hello", from: "background", ua: navigator.userAgent });
