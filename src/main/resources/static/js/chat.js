/* =========================================================
   UrutiBot - chat controller (auth-aware)
   - Generates / persists memoryId + visitorId via UrutiBot.state
   - Restores history from /api/chatbot/sessions/{memoryId}/messages
   - Streams replies via SSE from /api/chatbot/stream
   - Detects "requires authentication" cues in bot output and
     renders an inline auth CTA inside the offending bubble
   - On auth-success, retries the last user message so the
     just-blocked action goes through seamlessly
   - "New chat" rotates memoryId and resets the conversation
   ========================================================= */
(function () {
  "use strict";

  const { api, state, on, emit, util, toast } = window.UrutiBot;

  /* ---------- Theme toggle (legacy header button) ---------- */
  (function initTheme() {
    const root = document.documentElement;
    const btn = document.getElementById("theme-toggle");
    const STORAGE_KEY = "urutibot.theme";

    const apply = (pref) => {
      const actual =
        pref === "system"
          ? window.matchMedia("(prefers-color-scheme: dark)").matches
            ? "dark"
            : "light"
          : pref;
      root.setAttribute("data-theme", actual);
      root.setAttribute("data-theme-pref", pref);
    };
    const readPref = () => {
      const v = localStorage.getItem(STORAGE_KEY);
      return v === "light" || v === "dark" ? v : "system";
    };
    if (btn) {
      btn.addEventListener("click", () => {
        const order = ["light", "dark", "system"];
        const next = order[(order.indexOf(readPref()) + 1) % order.length];
        try {
          localStorage.setItem(STORAGE_KEY, next);
        } catch (e) {}
        apply(next);
        btn.setAttribute("aria-label", "Theme: " + next + ". Click to change.");
      });
    }
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const onMqChange = () => {
      if (readPref() === "system") apply("system");
    };
    if (mq.addEventListener) mq.addEventListener("change", onMqChange);
    else if (mq.addListener) mq.addListener(onMqChange);
  })();

  /* ---------- Markdown ---------- */
  function inlineMd(s) {
    s = s.replace(
      /\[([^\]]+)\]\((https?:\/\/[^\s)]+|mailto:[^\s)]+|tel:[^\s)]+)\)/g,
      (_, text, url) =>
        `<a href="${url}" target="_blank" rel="noopener noreferrer">${text}</a>`,
    );
    s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    s = s.replace(/(^|[\s(])_([^_]+)_(?=[\s.,!?)]|$)/g, "$1<em>$2</em>");
    s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
    return s;
  }
  function renderMarkdown(text) {
    const escaped = util.escapeHtml(text);
    const lines = escaped.split("\n");
    const out = [];
    let para = [];
    let list = null;
    const flushPara = () => {
      if (para.length) {
        out.push("<p>" + inlineMd(para.join(" ")) + "</p>");
        para = [];
      }
    };
    const closeList = () => {
      if (list) {
        out.push("</" + list + ">");
        list = null;
      }
    };
    for (const raw of lines) {
      const line = raw.trim();
      if (line === "") {
        flushPara();
        closeList();
        continue;
      }
      const ulMatch = /^[-*]\s+(.*)$/.exec(line);
      const olMatch = /^\d+\.\s+(.*)$/.exec(line);
      if (ulMatch) {
        flushPara();
        if (list !== "ul") {
          closeList();
          out.push("<ul>");
          list = "ul";
        }
        out.push("<li>" + inlineMd(ulMatch[1]) + "</li>");
      } else if (olMatch) {
        flushPara();
        if (list !== "ol") {
          closeList();
          out.push("<ol>");
          list = "ol";
        }
        out.push("<li>" + inlineMd(olMatch[1]) + "</li>");
      } else {
        closeList();
        para.push(line);
      }
    }
    flushPara();
    closeList();
    return out.join("");
  }

  /* ---------- Auth-required cue detection ---------- */
  // Bot tools return {requiresAuth:true} when the user is anonymous and
  // tries to use a privileged action; the LLM then phrases this back to
  // the user. We scan the rendered text for typical phrases and surface
  // an inline auth CTA so the user can sign in without leaving chat.
  const AUTH_CUES = [
    /\blog ?in\b/i,
    /\bsign ?in\b/i,
    /\bcreate (?:an )?account\b/i,
    /\bregister\b/i,
    /\blog into your account\b/i,
    /\bmust be (?:logged in|authenticated)\b/i,
    /\brequires? (?:authentication|an account)\b/i,
    /\bauthenticate\b/i,
  ];
  function looksLikeAuthRequired(text) {
    if (!text) return false;
    const sample = text.slice(0, 1200);
    let hits = 0;
    for (const re of AUTH_CUES) if (re.test(sample)) hits++;
    return hits >= 1;
  }

  /* ---------- Controller ---------- */
  function setupChat(root) {
    const conversation = root.querySelector("[data-chat-conversation]");
    const log = root.querySelector("[data-chat-log]");
    const errBox = root.querySelector("[data-chat-error]");
    const errMsg = root.querySelector("[data-chat-error-msg]");
    const retryBtn = root.querySelector("[data-chat-retry]");
    const form = root.querySelector("[data-chat-form]");
    const input = root.querySelector("[data-chat-input]");
    const sendBtn = root.querySelector("[data-chat-send]");
    const stopBtn = root.querySelector("[data-chat-stop]");
    const resetBtn = root.querySelector("[data-chat-reset]");
    const statusText = root.querySelector("[data-chat-status-text]");

    const GREETING_ANON =
      "Hello! I'm UrutiBot, your assistant from UrutiHub. " +
      "Ask me anything about our services, pricing, or working hours. " +
      "If you'd like to **book**, view, or cancel an appointment, you'll be asked to sign in first.";
    const GREETING_AUTH = (name) =>
      "Welcome back" +
      (name ? ", **" + name.split(" ")[0] + "**" : "") +
      "! I'm UrutiBot. Would you like to book a new appointment, " +
      "review your existing ones, or ask about our services?";

    const ctrl = {
      streaming: false,
      controller: null,
      pendingBot: null,
      lastUserMsg: null,
      lastWire: null,
      authRetryQueued: false,
    };

    /* ---------- bubble + typewriter ---------- */
    function appendUserBubble(text) {
      const div = document.createElement("div");
      div.className = "bubble bubble--user";
      div.textContent = text;
      log.appendChild(div);
      scrollLog();
      return div;
    }
    function appendBotBubble(text) {
      const div = document.createElement("div");
      div.className = "bubble bubble--bot";
      div.innerHTML = renderMarkdown(text);
      log.appendChild(div);
      scrollLog();
      return div;
    }
    function scrollLog() {
      log.scrollTop = log.scrollHeight;
    }

    function startBotBubble() {
      const wrap = document.createElement("div");
      wrap.className = "bubble bubble--bot bubble--streaming";
      wrap.setAttribute("aria-busy", "true");
      const textEl = document.createElement("div");
      textEl.className = "bubble-text";
      const typing = document.createElement("span");
      typing.className = "typing";
      typing.setAttribute("aria-label", "UrutiBot is typing");
      typing.innerHTML = "<span></span><span></span><span></span>";
      wrap.appendChild(textEl);
      wrap.appendChild(typing);
      log.appendChild(wrap);
      scrollLog();
      return {
        el: wrap,
        textEl,
        typing,
        raw: "",
        displayed: "",
        typerHandle: null,
        streamDone: false,
        aborted: false,
        pendingFinalize: null,
      };
    }

    const TYPER_TICK_MS = 22;
    function chunkSizeFor(remaining, streamDone) {
      let n = 1;
      if (remaining > 280) n = 12;
      else if (remaining > 120) n = 5;
      else if (remaining > 40) n = 2;
      if (streamDone && remaining > 8) n = Math.max(n, 3);
      return n;
    }
    function scheduleTyper(b) {
      if (b.typerHandle != null) return;
      b.typerHandle = setTimeout(() => typerTick(b), TYPER_TICK_MS);
    }
    function typerTick(b) {
      b.typerHandle = null;
      if (!b.el.isConnected) return;
      const remaining = b.raw.length - b.displayed.length;
      if (remaining <= 0) {
        if (b.streamDone && b.pendingFinalize) {
          const c = b.pendingFinalize;
          b.pendingFinalize = null;
          c();
        }
        return;
      }
      const step = chunkSizeFor(remaining, b.streamDone);
      b.displayed = b.raw.slice(0, b.displayed.length + step);
      b.textEl.innerHTML = renderMarkdown(b.displayed);
      scrollLog();
      b.typerHandle = setTimeout(() => typerTick(b), TYPER_TICK_MS);
    }
    function appendToken(b, token) {
      b.raw += token;
      scheduleTyper(b);
    }
    function finalizeBubble(b, aborted) {
      if (!b || !b.el) return;
      b.streamDone = true;
      b.aborted = aborted;
      const raw = b.raw;
      const commit = () => {
        if (b.typerHandle != null) {
          clearTimeout(b.typerHandle);
          b.typerHandle = null;
        }
        if (b.typing && b.typing.parentNode) {
          b.typing.remove();
          b.typing = null;
        }
        b.el.removeAttribute("aria-busy");
        b.el.classList.remove("bubble--streaming");
        if (raw.trim()) {
          b.el.innerHTML = renderMarkdown(raw);
          if (aborted) {
            const note = document.createElement("span");
            note.className = "bubble-stopped";
            note.textContent = "stopped";
            b.el.appendChild(note);
          }
          // Inline auth CTA when bot signaled login is required
          maybeAttachAuthCta(b.el, raw);
        } else {
          b.el.remove();
        }
      };
      if (aborted || b.displayed.length >= b.raw.length) commit();
      else {
        b.pendingFinalize = commit;
        scheduleTyper(b);
      }
    }

    function maybeAttachAuthCta(bubbleEl, text) {
      if (state.isAuthenticated()) return;
      if (!looksLikeAuthRequired(text)) return;
      // Only one CTA per bubble.
      if (bubbleEl.querySelector(".inline-auth")) return;
      window.UrutiBot.auth.renderInlineAuthPrompt(bubbleEl, {
        title: "Sign in to continue",
        message:
          "Booking, viewing, or cancelling appointments requires an UrutiHub account. Sign in or create one and I'll pick up right where we left off.",
      });
      ctrl.authRetryQueued = true; // queue retry of last user msg post-login
      scrollLog();
    }

    /* ---------- composer ---------- */
    function setStreaming(on) {
      ctrl.streaming = on;
      if (on) {
        input.setAttribute("disabled", "");
        sendBtn.hidden = true;
        stopBtn.hidden = false;
      } else {
        input.removeAttribute("disabled");
        sendBtn.hidden = false;
        stopBtn.hidden = true;
        setTimeout(() => input && input.focus(), 30);
      }
    }
    function showError(msg) {
      errMsg.textContent = msg;
      errBox.hidden = false;
    }
    function hideError() {
      errBox.hidden = true;
    }

    /* ---------- send / SSE ---------- */
    async function sendMessage(text) {
      ctrl.lastUserMsg = text;
      appendUserBubble(text);
      ctrl.lastWire = text;

      // Short-circuit: an anonymous user typing "I want to log in" gets the
      // CTA card straight away — no LLM call. Routing through the model
      // would produce contradictory output (it'd say "I can't sign you in"
      // right above the Sign in button), and a wordy answer when the action
      // is one click away.
      if (!state.isAuthenticated() && isLoginOnlyIntent(text)) {
        const bubble = appendBotBubble(
          "Sure! Sign in or create an account below — I'll pick up right where we left off.",
        );
        if (window.UrutiBot.auth && window.UrutiBot.auth.renderInlineAuthPrompt) {
          window.UrutiBot.auth.renderInlineAuthPrompt(bubble, {
            title: "Sign in to continue",
            message:
              "Booking, viewing, or cancelling appointments requires an UrutiHub account.",
          });
        }
        ctrl.authRetryQueued = true; // post-login local welcome will fire
        scrollLog();
        return;
      }

      // Short-circuit: an already-authenticated user typing "I want to log in"
      // (or the FR/RW equivalents) gets a local reply. The bot would say the
      // same thing — saves the LLM round-trip + ~2K tokens per occurrence.
      // The system prompt's auth_context already handles this correctly when
      // the message slips past the heuristic, so a miss is harmless.
      if (state.isAuthenticated() && isLoginOnlyIntent(text)) {
        const auth = state.getAuth();
        const firstName = (auth && auth.user && auth.user.fullName)
          ? auth.user.fullName.split(/\s+/)[0]
          : null;
        appendBotBubble(
          "You're already signed in" +
          (firstName ? " as **" + firstName + "**" : "") +
          ". Would you like to **book an appointment**, " +
          "**review your existing ones**, or ask about our services?",
        );
        return;
      }

      // Short-circuit: "log me out" — there's no LLM tool for this, so route
      // straight through the existing logout() which wipes server-side
      // history, clears the JWT, and emits auth-changed (the chat.js
      // listener then rotates memoryId + re-renders the anon greeting).
      if (state.isAuthenticated() && isLogoutIntent(text)) {
        appendBotBubble("Signing you out…");
        if (window.UrutiBot.auth && window.UrutiBot.auth.logout) {
          window.UrutiBot.auth.logout();
        }
        return;
      }

      await streamReply(text);
    }

    async function streamReply(text) {
      hideError();
      const bubble = startBotBubble();
      ctrl.pendingBot = bubble;
      setStreaming(true);

      const controller = new AbortController();
      ctrl.controller = controller;

      const auth = state.getAuth();
      const headers = {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      };
      if (auth && auth.token) headers.Authorization = "Bearer " + auth.token;

      try {
        const resp = await fetch("/api/chatbot/stream", {
          method: "POST",
          headers,
          body: JSON.stringify({
            memoryId: state.getMemoryId(),
            anonymousVisitorId: auth ? null : state.getVisitorId(),
            message: text,
          }),
          signal: controller.signal,
        });

        if (!resp.ok) {
          // 401 with a token attached => server rejected the JWT (expired
          // or invalid). Clear local auth state and trigger the global
          // session-expired handler in auth.js, which shows a toast and
          // opens the sign-in modal. Queue this message for re-send so
          // the user can resume right after re-authenticating.
          if (resp.status === 401 && auth) {
            state.clearAuth();
            ctrl.authRetryQueued = true;
            emit("session-expired");
            // Drop the half-rendered bot bubble; the toast is the cue.
            finalizeBubble(bubble, true);
            try {
              if (bubble && bubble.el && bubble.el.parentNode) {
                bubble.el.parentNode.removeChild(bubble.el);
              }
            } catch (e) {}
            return;
          }
          let msg = "Request failed (" + resp.status + ").";
          try {
            const data = await resp.json();
            if (data && Array.isArray(data.message))
              msg = data.message.join(" ");
            else if (data && data.message) msg = data.message;
          } catch (e) {}
          throw new Error(msg);
        }
        if (!resp.body || !resp.body.getReader) {
          throw new Error("Streaming not supported in this browser.");
        }
        await readSse(resp.body.getReader(), bubble);
      } catch (err) {
        if (err.name === "AbortError") {
          finalizeBubble(bubble, true);
        } else {
          finalizeBubble(bubble, false);
          showError(err.message || "Network error. Please try again.");
        }
      } finally {
        ctrl.controller = null;
        ctrl.pendingBot = null;
        setStreaming(false);
      }
    }

    async function readSse(reader, bubble) {
      const decoder = new TextDecoder("utf-8");
      let buffer = "";
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buffer.indexOf("\n\n")) !== -1) {
          const frame = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          const ev = parseFrame(frame);
          if (!ev) continue;
          if (ev.event === "token") {
            let token = "";
            try {
              token = JSON.parse(ev.data);
            } catch (e) {
              token = ev.data;
            }
            appendToken(bubble, token);
          } else if (ev.event === "done") {
            finalizeBubble(bubble, false);
            return;
          } else if (ev.event === "error") {
            let msg = "An error occurred while generating a response.";
            try {
              const j = JSON.parse(ev.data);
              if (j && j.message) msg = j.message;
            } catch (e) {}
            finalizeBubble(bubble, false);
            showError(msg);
            return;
          }
        }
      }
      finalizeBubble(bubble, false);
    }

    function parseFrame(frame) {
      const lines = frame.split("\n");
      let event = "message";
      const dataLines = [];
      for (const line of lines) {
        if (line.startsWith(":")) continue;
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:"))
          dataLines.push(line.slice(5).replace(/^ /, ""));
      }
      if (dataLines.length === 0 && event === "message") return null;
      return { event, data: dataLines.join("\n") };
    }

    /* ---------- restore history ---------- */
    async function restoreHistory() {
      const memoryId = state.getMemoryId();
      const auth = state.getAuth();
      const url =
        "/api/chatbot/sessions/" +
        encodeURIComponent(memoryId) +
        "/messages" +
        (auth ? "" : "?visitorId=" + encodeURIComponent(state.getVisitorId()));
      try {
        const resp = await api.get(url);
        // The endpoint returns Spring Data's paged envelope (PagedModel under
        // VIA_DTO, PageImpl on older builds); both expose the rows under
        // `content`. Tolerate a bare array in case the endpoint is ever
        // simplified to List<>.
        const messages = Array.isArray(resp) ? resp : (resp && resp.content) || [];
        if (messages.length === 0) {
          renderInitialGreeting();
          return;
        }
        log.innerHTML = "";
        messages.forEach((m) => {
          if (m.role === "USER") {
            const text = sanitizeUserContextPrefix(m.content || "");
            if (text.trim()) appendUserBubble(text);
          } else if (m.role === "ASSISTANT") {
            if ((m.content || "").trim()) appendBotBubble(m.content);
          }
        });
        scrollLog();
      } catch (e) {
        // 404 => no session yet (anonymous + nothing sent), or 403 => session
        // belongs to someone else (likely after token expiry hit a memoryId
        // that's now linked). In both cases fall back to a fresh greeting.
        if (e && (e.status === 404 || e.status === 403)) {
          if (e.status === 403) {
            state.rotateMemoryId();
          }
          renderInitialGreeting();
          return;
        }
        renderInitialGreeting();
      }
    }

    // The chat composer used to inject "(Context for the assistant- not part
    // of my question: my email is X.)" as a hidden first-turn prefix. We no
    // longer do this (auth context comes from the JWT), but old sessions may
    // still contain it — strip it so it never shows up in a restored bubble.
    function sanitizeUserContextPrefix(text) {
      const idx = text.indexOf("(Context for the assistant");
      if (idx === -1) return text;
      const closeIdx = text.indexOf(")", idx);
      if (closeIdx === -1) return text;
      return (
        text.slice(0, idx) + text.slice(closeIdx + 1)
      ).replace(/^\s+/, "");
    }

    function renderInitialGreeting() {
      log.innerHTML = "";
      const auth = state.getAuth();
      const greeting =
        auth && auth.user
          ? GREETING_AUTH(auth.user.fullName || auth.user.email)
          : GREETING_ANON;
      const bubble = startBotBubble();
      bubble.raw = greeting;
      finalizeBubble(bubble, false);
    }

    /* ---------- composer events ---------- */
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      sendCurrent();
    });
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        sendCurrent();
      }
    });
    input.addEventListener("input", () => {
      input.style.height = "auto";
      input.style.height = Math.min(input.scrollHeight, 140) + "px";
    });
    function sendCurrent() {
      if (ctrl.streaming) return;
      const text = (input.value || "").trim();
      if (!text) return;
      input.value = "";
      input.style.height = "auto";
      hideError();
      sendMessage(text);
    }

    stopBtn.addEventListener("click", () => {
      if (ctrl.controller) ctrl.controller.abort();
    });
    retryBtn.addEventListener("click", () => {
      hideError();
      if (ctrl.streaming) return;
      const replay = ctrl.lastWire || ctrl.lastUserMsg;
      if (replay) streamReply(replay);
    });

    resetBtn.addEventListener("click", () => {
      const ok = window.confirm(
        "Start a new conversation?\n\n" +
          "This permanently clears the current chat history. " +
          "It can't be undone.",
      );
      if (!ok) return;
      newConversation();
    });

    function newConversation() {
      if (ctrl.streaming && ctrl.controller) ctrl.controller.abort();

      // Drop the server-side row for the chat we're abandoning. Capture the
      // memoryId BEFORE rotating, and pass visitorId so anonymous sessions
      // can be authorized too. Fire-and-forget — any failure is non-fatal.
      const abandoned = state.getMemoryId();
      if (abandoned) {
        const visitorId = state.getVisitorId();
        const path = "/api/chatbot/sessions/" + encodeURIComponent(abandoned)
          + (visitorId ? "?visitorId=" + encodeURIComponent(visitorId) : "");
        api.delete(path).catch((e) =>
          console.warn("Failed to delete abandoned chat session:", e),
        );
      }

      state.rotateMemoryId();
      ctrl.lastUserMsg = null;
      ctrl.lastWire = null;
      ctrl.authRetryQueued = false;
      log.innerHTML = "";
      hideError();
      renderInitialGreeting();
    }

    /* ---------- auth events ---------- */
    function clearInlineAuthCtas() {
      // The user is now signed in — every "Sign in / Create account" CTA
      // attached to past bot bubbles is now stale and clicking it would be
      // confusing. Strip them all in one pass.
      log.querySelectorAll(".inline-auth").forEach((el) => el.remove());
    }

    // A short message whose only intent is "let me sign in" / "register me"
    // (English / French / Kinyarwanda). When it matches, there's no real
    // tool action to resume after login — we skip the LLM round-trip and
    // show a local welcome bubble instead.
    const LOGIN_ONLY_PATTERNS = [
      /^\s*(i\s+(want|need|would\s+like)\s+to\s+|let\s+me\s+|please\s+|can\s+i\s+|how\s+do\s+i\s+)?(log\s*[\-\s]?in|sign\s*[\-\s]?in|sign\s*up|signup|register|create\s+(an?\s+)?account)\s*[?.!]?\s*$/i,
      /^\s*(je\s+(veux|voudrais|souhaite)\s+|laisse[z]?\s+moi\s+|s'il\s+vous\s+pla[iî]t\s+)?(me\s+)?(connecter|inscrire|cr[eé]er\s+(un\s+)?compte|s'identifier)\s*[?.!]?\s*$/i,
      /^\s*(ndashaka\s+|nshaka\s+|reka\s+(n)?|nyemerera\s+)?(kwinjira|kwiyandikisha|gufungura\s+konti)\s*[?.!]?\s*$/i,
    ];
    function isLoginOnlyIntent(msg) {
      if (!msg) return false;
      const t = msg.trim();
      if (t.length > 80) return false; // long messages likely have other intent
      return LOGIN_ONLY_PATTERNS.some((re) => re.test(t));
    }

    // Counterpart for "log me out" / "sign me out" / FR / RW. When a signed-in
    // user types one of these we run the actual logout flow client-side
    // instead of routing through the LLM (which has no logout tool anyway).
    const LOGOUT_PATTERNS = [
      /^\s*(please\s+|can\s+you\s+|could\s+you\s+|now\s+)?(log\s*[\-\s]?(me\s+)?out|sign\s*[\-\s]?(me\s+)?out|signout|logout|end\s+(my\s+)?session)\s*[?.!]?\s*$/i,
      /^\s*(s'il\s+vous\s+pla[iî]t\s+)?(me\s+)?(d[eé]connect(er|e[rz]?)|fermer\s+(ma\s+)?session|d[eé]connexion)\s*[?.!]?\s*$/i,
      /^\s*(ndashaka\s+|nshaka\s+|reka\s+(n)?)?(gusohoka|kuvayo|kuvayo\s+muri\s+konti|gufunga\s+konti)\s*[?.!]?\s*$/i,
    ];
    function isLogoutIntent(msg) {
      if (!msg) return false;
      const t = msg.trim();
      if (t.length > 60) return false;
      return LOGOUT_PATTERNS.some((re) => re.test(t));
    }

    on("auth-success", () => {
      // Stale CTAs first.
      clearInlineAuthCtas();
      if (!ctrl.authRetryQueued || !ctrl.lastUserMsg) return;
      ctrl.authRetryQueued = false;

      const auth = state.getAuth();
      const firstName = (auth && auth.user && auth.user.fullName)
        ? auth.user.fullName.split(/\s+/)[0]
        : null;

      if (isLoginOnlyIntent(ctrl.lastUserMsg)) {
        // The user just wanted to log in — they did. No pending tool to
        // resume, so skip the API call and the tokens it would burn. Show
        // a local welcome bubble; the next message will pick up naturally.
        toast("You're signed in.", { variant: "success" });
        appendUserBubble(ctrl.lastUserMsg);
        appendBotBubble(
          "Welcome" + (firstName ? ", **" + firstName + "**" : "") +
          "! You're signed in. Would you like to **book an appointment**, " +
          "**review your existing ones**, or ask about our services?",
        );
        ctrl.lastUserMsg = null;
        ctrl.lastWire = null;
        return;
      }

      // Real pending action (book / cancel / list / etc.) — resume by
      // sending a contextual continuation. The model has the original
      // intent in memory and the new system prompt now says we're
      // authenticated, so it will call the appropriate tool.
      toast("Welcome back! Continuing your request…", { variant: "info" });
      if (ctrl.lastUserMsg) appendUserBubble(ctrl.lastUserMsg);
      streamReply("I've just signed in. Please continue from where we left off.");
    });
    on("auth-changed", () => {
      // When user logs out, rotate memoryId so we don't try to read
      // a session that's now linked to the previously-authed user.
      const auth = state.getAuth();
      if (!auth) {
        state.rotateMemoryId();
        log.innerHTML = "";
        renderInitialGreeting();
      }
    });

    /* ---------- boot ---------- */
    restoreHistory();
  }

  const card = document.querySelector(".chat-card");
  if (card) setupChat(card);
})();
