// Runs on https://httpbin.org/html?op=...&tag=... inside whatever Profile (session context) the
// Tab belongs to, and reports in document.title, which the app reads through
// GeckoSession.ContentDelegate.onTitleChange. It only touches cookies and localStorage keys
// named jrny_iso_*, which the spike creates itself.
//   op=set   store cookie and localStorage key jrny_iso_<tag>, then report
//   op=read  report only
//   op=popup add a button that opens a popup with window.open (popup Profile test)
(() => {
  const params = new URL(location.href).searchParams;
  const op = params.get("op");
  const tag = params.get("tag");
  if (!op) return;

  if (op === "set") {
    document.cookie = `jrny_iso_${tag}=1; Secure; Path=/; Max-Age=3600; SameSite=Lax`;
    localStorage.setItem(`jrny_iso_${tag}`, "1");
  }

  if (op === "popup") {
    // An inline handler runs in the page itself, so window.open comes from the page, not the extension.
    document.body.insertAdjacentHTML(
      "afterbegin",
      `<button id="jrny-popup" style="font-size:28px;padding:20px;margin:16px"
         onclick="window.open('/html?op=read&amp;tag=popup', 'jrnypopup', 'width=500,height=600')">
         Open popup</button>`
    );
  }

  if (op === "webauthn") {
    // Passkey probe. The script and buttons are injected into the page itself, so WebAuthn runs
    // with the page's origin (https://httpbin.org) and a real user gesture. The outcome, or the
    // error's name and message, goes to document.title as "WA {...}".
    const script = document.createElement("script");
    script.textContent = `
      function jrnyReport(op, started, result) {
        document.title = "WA " + JSON.stringify(Object.assign({ op, ms: Date.now() - started }, result));
      }
      function jrnyRandom(n) { return crypto.getRandomValues(new Uint8Array(n)); }
      async function jrnyGet() {
        const started = Date.now();
        jrnyReport("get", started, { state: "pending" });
        try {
          const c = await navigator.credentials.get({ publicKey: {
            challenge: jrnyRandom(32), rpId: location.hostname, userVerification: "preferred", timeout: 60000 } });
          jrnyReport("get", started, { state: "ok", type: c && c.type, authenticatorAttachment: c && c.authenticatorAttachment });
        } catch (e) { jrnyReport("get", started, { state: "error", name: e.name, message: e.message }); }
      }
      async function jrnySupport() {
        const started = Date.now();
        try {
          jrnyReport("support", started, { state: "ok",
            uvpaa: await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable(),
            conditional: await PublicKeyCredential.isConditionalMediationAvailable() });
        } catch (e) { jrnyReport("support", started, { state: "error", name: e.name, message: e.message }); }
      }
      async function jrnyCreate() {
        const started = Date.now();
        jrnyReport("create", started, { state: "pending" });
        try {
          const c = await navigator.credentials.create({ publicKey: {
            challenge: jrnyRandom(32),
            rp: { id: location.hostname, name: "JRNY spike" },
            user: { id: jrnyRandom(16), name: "jrny-spike-test", displayName: "JRNY spike test" },
            pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
            authenticatorSelection: { residentKey: "required", userVerification: "preferred" },
            timeout: 60000 } });
          jrnyReport("create", started, { state: "ok", type: c && c.type, authenticatorAttachment: c && c.authenticatorAttachment });
        } catch (e) { jrnyReport("create", started, { state: "error", name: e.name, message: e.message }); }
      }`;
    document.documentElement.appendChild(script);
    document.body.insertAdjacentHTML(
      "afterbegin",
      `<div style="margin:16px">
         <p style="font-size:20px">Passkey probe on ${location.origin} (PublicKeyCredential: ${typeof PublicKeyCredential})</p>
         <button id="jrny-wa-get" style="font-size:28px;padding:20px" onclick="jrnyGet()">Sign in with passkey (get)</button>
         <button id="jrny-wa-create" style="font-size:28px;padding:20px" onclick="jrnyCreate()">Create passkey (create)</button>
         <button id="jrny-wa-support" style="font-size:28px;padding:20px" onclick="jrnySupport()">Check support</button>
       </div>`
    );
    return;
  }

  const cookies = document.cookie
    .split(";")
    .map((c) => c.trim().split("=")[0])
    .filter((n) => n.startsWith("jrny_iso_"));
  const ls = Object.keys(localStorage).filter((k) => k.startsWith("jrny_iso_"));
  document.title = "ISO " + JSON.stringify({ op, tag, cookies, ls });
})();
