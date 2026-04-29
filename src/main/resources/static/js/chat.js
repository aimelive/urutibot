(function () {
  "use strict";

  /* =========================================================
       Theme toggle (light → dark → system)
       Pre-paint script in <head> already set data-theme.
       This handles user clicks and OS-preference changes while
       in 'system' mode.
       ========================================================= */
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

  /* =========================================================
       Tiny markdown renderer (paragraphs, **bold**, lists, links).
       HTML is escaped FIRST to prevent XSS.
       ========================================================= */
  function escapeHtml(s) {
    return s.replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  }
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
    const escaped = escapeHtml(text);
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

  /* =========================================================
       Session persistence
       ========================================================= */
  const STORAGE_KEY = "urutibot.session";

  function loadSession() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!parsed || !parsed.memoryId) return null;
      return parsed;
    } catch (e) {
      return null;
    }
  }
  // Persistence policy: the memoryId (email) and a short trailing message
  // history are kept in localStorage indefinitely, so a returning visitor
  // skips the email gate and resumes the same server-side conversation
  // memory. The user can wipe both with "Start new conversation".
  function saveSession(s) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    } catch (e) {}
  }
  function clearSession() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (e) {}
  }

  function emailValid(s) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s);
  }

  /* =========================================================
       Chat controller - wired against the single chat card on
       the page. The chat card IS the page, not a widget.
       ========================================================= */
  function setupChat(root) {
    const prechat = root.querySelector("[data-chat-prechat]");
    const prechatErr = root.querySelector("[data-chat-prechat-error]");
    const emailInput = root.querySelector("[data-chat-email]");
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
    const emailDisplay = root.querySelector("[data-chat-email-display]");
    const emailSep = root.querySelector("[data-chat-email-sep]");

    const GREETING =
      "Hello! I'm UrutiBot, your assistant from UrutiHub. " +
      "Would you like to book an appointment with our team, " +
      "or do you have a question about our services or pricing?";

    const state = {
      memoryId: null,
      history: [], // [{role:'user'|'bot', text}]
      streaming: false,
      controller: null,
      pendingBot: null,
      lastUserMsg: null,
    };

    // ---- restore session if present ----
    const saved = loadSession();
    if (saved && emailValid(saved.memoryId)) {
      state.memoryId = saved.memoryId;
      state.history = Array.isArray(saved.history) ? saved.history : [];
      updateHeader();
      showConversation();
      renderHistory();
    } else {
      updateHeader();
      // First-time visitor - focus email field
      setTimeout(() => emailInput && emailInput.focus(), 80);
    }

    // Reflect the current memoryId in the chat card header.
    function updateHeader() {
      if (state.memoryId) {
        emailDisplay.textContent = state.memoryId;
        emailDisplay.hidden = false;
        if (emailSep) emailSep.hidden = false;
        resetBtn.hidden = false;
      } else {
        emailDisplay.textContent = "";
        emailDisplay.hidden = true;
        if (emailSep) emailSep.hidden = true;
        resetBtn.hidden = true;
      }
    }

    function showConversation() {
      prechat.hidden = true;
      conversation.hidden = false;
      setTimeout(() => input && input.focus(), 50);
    }
    function showPrechat() {
      conversation.hidden = true;
      prechat.hidden = false;
      setTimeout(() => emailInput && emailInput.focus(), 50);
    }

    function renderHistory() {
      log.innerHTML = "";
      state.history.forEach((m) => appendMessage(m.role, m.text));
      log.scrollTop = log.scrollHeight;
    }

    function appendMessage(role, text) {
      const div = document.createElement("div");
      div.className = "bubble bubble--" + (role === "user" ? "user" : "bot");
      if (role === "user") {
        div.textContent = text;
      } else {
        div.innerHTML = renderMarkdown(text);
      }
      log.appendChild(div);
      log.scrollTop = log.scrollHeight;
      return div;
    }

    function addUserMessage(text) {
      state.history.push({ role: "user", text });
      persist();
      return appendMessage("user", text);
    }

    function addBotMessage(text) {
      state.history.push({ role: "bot", text });
      persist();
      return appendMessage("bot", text);
    }

    // Play a fully-known message (e.g. the local greeting) through the
    // streaming + typewriter path, no network round-trip. Pre-fills `raw`
    // and goes straight to finalize - which persists history immediately
    // and defers the visual commit until the typer has drained.
    function typeBotMessage(text) {
      const bubble = startBotBubble();
      bubble.raw = text;
      finalizeBotBubble(bubble, false);
    }

    function startBotBubble() {
      const wrap = document.createElement("div");
      wrap.className = "bubble bubble--bot bubble--streaming";
      wrap.setAttribute("aria-busy", "true");

      // Streaming bubble has two slots: a text container that grows with
      // tokens, and a trailing typing indicator that stays visible until
      // finalize. Before the first token, the text slot is empty so the
      // dots read as "thinking"; after, they read as "still generating".
      const textEl = document.createElement("div");
      textEl.className = "bubble-text";

      const typing = document.createElement("span");
      typing.className = "typing";
      typing.setAttribute("aria-label", "UrutiBot is typing");
      typing.innerHTML = "<span></span><span></span><span></span>";

      wrap.appendChild(textEl);
      wrap.appendChild(typing);
      log.appendChild(wrap);
      log.scrollTop = log.scrollHeight;
      return {
        el: wrap,
        textEl,
        typing,
        raw: "", // characters received from the network
        displayed: "", // characters painted to the DOM (typewriter cursor)
        typerHandle: null, // setTimeout id for the typewriter loop
        streamDone: false, // SSE `done` (or terminal error/abort) seen
        aborted: false,
        persisted: false, // bot turn pushed to state.history
        pendingFinalize: null, // commit fn deferred until typer drains
      };
    }

    function persist() {
      const tail = state.history.slice(-30);
      saveSession({ memoryId: state.memoryId, history: tail });
    }

    function setStreaming(on) {
      state.streaming = on;
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

    function showError(message) {
      errMsg.textContent = message;
      errBox.hidden = false;
    }
    function hideError() {
      errBox.hidden = true;
    }

    // ---- pre-chat submit ----
    prechat.addEventListener("submit", (e) => {
      e.preventDefault();
      const value = (emailInput.value || "").trim();
      if (!emailValid(value)) {
        prechatErr.hidden = false;
        prechatErr.textContent = "Please enter a valid email address.";
        emailInput.setAttribute("aria-invalid", "true");
        return;
      }
      prechatErr.hidden = true;
      emailInput.removeAttribute("aria-invalid");
      state.memoryId = value;
      state.history = [];
      persist();
      updateHeader();
      showConversation();
      renderHistory();

      // Warm static greeting in the bot's voice - single source of truth.
      // Avoids an extra round-trip and pollutes server-side memory with a
      // non-user "hello" we'd then have to filter. Routed through the
      // streaming path (raw pre-filled, no network) so the typewriter
      // animation runs identically to a real reply.
      typeBotMessage(GREETING);
    });

    // ---- composer ----
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
      if (state.streaming) return;
      const text = (input.value || "").trim();
      if (!text) return;
      input.value = "";
      input.style.height = "auto";
      hideError();
      sendMessage(text);
    }

    async function sendMessage(text) {
      state.lastUserMsg = text;
      addUserMessage(text);

      // The bot only sees `memoryId` as an opaque conversation key, not as
      // the user's email. So on the first user turn of this client session
      // we silently include the email as inline context- once it lands in
      // the server-side chat memory (keyed by memoryId), the bot can answer
      // "check my appointments" etc. without re-asking. The displayed
      // bubble shows only the text the user typed.
      const userTurns = state.history.filter((m) => m.role === "user").length;
      const wire =
        userTurns === 1
          ? "(Context for the assistant- not part of my question: my email is " +
            state.memoryId +
            ". Please use this email for any appointment lookups, bookings, or cancellations without asking me to repeat it.)\n\n" +
            text
          : text;

      state.lastWire = wire;
      await streamReply(wire);
    }

    async function streamReply(text) {
      const bubble = startBotBubble();
      state.pendingBot = bubble;
      setStreaming(true);

      const controller = new AbortController();
      state.controller = controller;

      try {
        const resp = await fetch("/api/chatbot/stream", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "text/event-stream",
          },
          body: JSON.stringify({
            memoryId: state.memoryId,
            message: text,
          }),
          signal: controller.signal,
        });

        if (!resp.ok) {
          let msg = "Request failed (" + resp.status + ").";
          try {
            const data = await resp.json();
            if (data && Array.isArray(data.message))
              msg = data.message.join(" ");
            else if (data && data.message) msg = data.message;
          } catch (e) {
            /* ignore parse errors */
          }
          throw new Error(msg);
        }

        if (!resp.body || !resp.body.getReader) {
          throw new Error("Streaming not supported in this browser.");
        }

        await readSse(resp.body.getReader(), bubble);
      } catch (err) {
        if (err.name === "AbortError") {
          finalizeBotBubble(bubble, true);
        } else {
          finalizeBotBubble(bubble, false);
          showError(err.message || "Network error. Please try again.");
        }
      } finally {
        state.controller = null;
        state.pendingBot = null;
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
            finalizeBotBubble(bubble, false);
            return;
          } else if (ev.event === "error") {
            let msg = "An error occurred while generating a response.";
            try {
              const j = JSON.parse(ev.data);
              if (j && j.message) msg = j.message;
            } catch (e) {}
            finalizeBotBubble(bubble, false);
            showError(msg);
            return;
          }
        }
      }
      // Stream closed without `done` - finalize whatever we have.
      finalizeBotBubble(bubble, false);
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

    function appendToken(bubble, token) {
      // Network -> buffer. The typewriter loop is the single writer to the
      // DOM, so token bursts queue up here instead of flashing all at once.
      bubble.raw += token;
      scheduleTyper(bubble);
    }

    // Tunable typewriter pacing. Adaptive chunk size keeps short replies
    // feeling typed while preventing long responses from dragging on -
    // when the buffer is large (network delivered a burst), each tick
    // emits more characters to catch up.
    const TYPER_TICK_MS = 22;
    function chunkSizeFor(remaining, streamDone) {
      let n = 1;
      if (remaining > 280) n = 12;
      else if (remaining > 120) n = 5;
      else if (remaining > 40) n = 2;
      // Once the stream is closed, drain a bit more aggressively so the
      // user isn't left watching a typewriter long after the data arrived.
      if (streamDone && remaining > 8) n = Math.max(n, 3);
      return n;
    }

    function scheduleTyper(bubble) {
      if (bubble.typerHandle != null) return;
      bubble.typerHandle = setTimeout(() => typerTick(bubble), TYPER_TICK_MS);
    }

    function typerTick(bubble) {
      bubble.typerHandle = null;
      // Bubble removed from DOM (e.g. user hit "New chat") - drop the loop.
      if (!bubble.el.isConnected) return;

      const remaining = bubble.raw.length - bubble.displayed.length;
      if (remaining <= 0) {
        // Caught up. If the network stream is also done, run the deferred
        // visual commit (clears the typing dots, swaps in final markdown).
        if (bubble.streamDone && bubble.pendingFinalize) {
          const commit = bubble.pendingFinalize;
          bubble.pendingFinalize = null;
          commit();
        }
        return;
      }

      const step = chunkSizeFor(remaining, bubble.streamDone);
      bubble.displayed = bubble.raw.slice(0, bubble.displayed.length + step);
      bubble.textEl.innerHTML = renderMarkdown(bubble.displayed);
      log.scrollTop = log.scrollHeight;

      bubble.typerHandle = setTimeout(() => typerTick(bubble), TYPER_TICK_MS);
    }

    function finalizeBotBubble(bubble, aborted) {
      if (!bubble || !bubble.el) return;
      bubble.streamDone = true;
      bubble.aborted = aborted;

      const raw = bubble.raw;

      // Persist to history *now*, when the network turn ends - not when the
      // typewriter finishes painting. Otherwise a follow-up user message
      // sent during playback would slot ahead of this bot turn in history.
      if (raw.trim() && !bubble.persisted) {
        bubble.persisted = true;
        state.history.push({ role: "bot", text: raw });
        persist();
      }

      const commit = () => {
        if (bubble.typerHandle != null) {
          clearTimeout(bubble.typerHandle);
          bubble.typerHandle = null;
        }
        if (bubble.typing && bubble.typing.parentNode) {
          bubble.typing.remove();
          bubble.typing = null;
        }
        bubble.el.removeAttribute("aria-busy");
        bubble.el.classList.remove("bubble--streaming");

        if (raw.trim()) {
          bubble.el.innerHTML = renderMarkdown(raw);
          if (aborted) {
            const note = document.createElement("span");
            note.className = "bubble-stopped";
            note.textContent = "stopped";
            bubble.el.appendChild(note);
          }
        } else {
          // Nothing arrived (user aborted before first token, etc).
          bubble.el.remove();
        }
      };

      // On abort, snap to whatever's been received - don't make the user
      // wait for a typewriter to finish painting text they just cancelled.
      // Otherwise, let the typer drain to the end before swapping in the
      // final markdown (and removing the trailing dots).
      if (aborted || bubble.displayed.length >= bubble.raw.length) {
        commit();
      } else {
        bubble.pendingFinalize = commit;
        scheduleTyper(bubble);
      }
    }

    // ---- stop ----
    stopBtn.addEventListener("click", () => {
      if (state.controller) state.controller.abort();
    });

    // ---- retry ----
    retryBtn.addEventListener("click", () => {
      hideError();
      if (state.streaming) return;
      // Replay the exact wire payload of the failed attempt- preserves the
      // first-turn email context if the original send never reached the server.
      const replay = state.lastWire || state.lastUserMsg;
      if (replay) streamReply(replay);
    });

    // ---- reset ----
    resetBtn.addEventListener("click", () => {
      // The reset is destructive: it wipes the message history and the
      // memoryId from localStorage and the current view. Make this
      // explicit before doing it so the click is intentional.
      const ok = window.confirm(
        "Start a new conversation?\n\n" +
          "This permanently clears the current chat history. " +
          "It can't be undone.",
      );
      if (!ok) return;
      if (state.streaming && state.controller) state.controller.abort();
      state.history = [];
      state.memoryId = null;
      state.lastUserMsg = null;
      state.lastWire = null;
      log.innerHTML = "";
      hideError();
      clearSession();
      // Clear email field so the user re-confirms
      if (emailInput) emailInput.value = "";
      updateHeader();
      showPrechat();
    });
  }

  // Bootstrap once the DOM is parsed (script is deferred, so we can run now).
  const card = document.querySelector(".chat-card");
  if (card) setupChat(card);
})();
