"use strict";
// Debug builds only: lets instrumented tests run a script in a page's content script world
// (PageBridge.evaluate). This file, and this manifest's 'unsafe-eval', replace nothing in the
// release extension (src/main/assets/okapi), which can't run code it is sent.
okapiQuestions.eval = (message) => (0, eval)(message.code);
