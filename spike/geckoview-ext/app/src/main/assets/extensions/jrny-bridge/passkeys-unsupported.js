// Passkeys are not supported yet (see spike RESULTS.md, "Passkeys"): no credential provider
// trusts this app as a browser, so real WebAuthn calls end in an opaque SecurityError.
// Catch them in every page and frame, in every Profile, and reject with NotSupportedError
// instead, which sites treat as "this browser has no passkeys" and fall back to passwords.
// Runs at document_start, before page scripts. Uses exportFunction rather than an injected
// <script>, so a page's Content Security Policy can't block it.
(() => {
  const page = window.wrappedJSObject;
  if (!page || !page.CredentialsContainer || !page.PublicKeyCredential) return;
  const MESSAGE = "Passkeys are not supported in this browser yet.";

  function report(op) {
    browser.runtime.sendMessage({ type: "passkey-blocked", op, origin: location.origin }).catch(() => {});
  }
  function notSupported(op) {
    report(op);
    return page.Promise.reject(new page.DOMException(MESSAGE, "NotSupportedError"));
  }

  // navigator.credentials.get/create: reject passkey requests, pass everything else through.
  const proto = page.CredentialsContainer.prototype;
  for (const op of ["get", "create"]) {
    const original = proto[op];
    exportFunction(
      function (options) {
        if (options && options.publicKey) return notSupported(op);
        return original.call(this, options);
      },
      proto,
      { defineAs: op }
    );
  }

  // Feature detection: report no platform authenticator and no passkey autofill, so sites
  // hide "Sign in with a passkey" instead of offering a button that can only fail.
  const pkc = page.PublicKeyCredential;
  const resolveFalse = () => page.Promise.resolve(false);
  exportFunction(resolveFalse, pkc, { defineAs: "isUserVerifyingPlatformAuthenticatorAvailable" });
  exportFunction(resolveFalse, pkc, { defineAs: "isConditionalMediationAvailable" });
  if (typeof pkc.getClientCapabilities === "function") {
    exportFunction(
      () => page.Promise.resolve(cloneInto({
        conditionalCreate: false, conditionalGet: false, hybridTransport: false,
        passkeyPlatformAuthenticator: false, userVerifyingPlatformAuthenticator: false,
      }, page)),
      pkc,
      { defineAs: "getClientCapabilities" }
    );
  }
})();
