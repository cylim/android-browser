"use strict";
// Debug builds only: lets instrumented tests run a script in a page's content script world
// (PageBridge.evaluate). This file, and this manifest's 'unsafe-eval', replace nothing in the
// release extension (src/main/assets/okapi), which can't run code it is sent.
okapiTestQuestions.eval = (message, answer) => {
  try {
    Promise.resolve((0, eval)(message.code)).then((v) => answer(message, true, v), (e) => answer(message, false, String(e)));
  } catch (e) {
    answer(message, false, String(e));
  }
};
