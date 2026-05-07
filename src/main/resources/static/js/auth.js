/* =========================================================
   UrutiBot - authentication UI + flow
   - Renders login / register forms (modal AND inline-in-chat)
   - Calls /api/auth/login, /api/auth/register, /api/auth/me
   - Persists JWT and user via UrutiBot.state.setAuth
   - Updates header (anonymous vs authenticated) on auth-changed
   ========================================================= */
(function () {
  "use strict";

  const { api, state, on, emit, util, toast, ApiError } = window.UrutiBot;

  /* ---------- Modal helpers ---------- */
  function openModal(name) {
    const m = document.querySelector('[data-modal="' + name + '"]');
    if (!m) return;
    m.hidden = false;
    document.body.classList.add("modal-open");
    setTimeout(() => {
      const focusable = m.querySelector("input, button, select, textarea");
      if (focusable) focusable.focus();
    }, 30);
    emit("modal-opened", name);
  }
  function closeModal(name) {
    const m = name
      ? document.querySelector('[data-modal="' + name + '"]')
      : null;
    if (m) {
      m.hidden = true;
    } else {
      // close any open
      document.querySelectorAll(".modal:not([hidden])").forEach((el) => (el.hidden = true));
    }
    if (!document.querySelector(".modal:not([hidden])")) {
      document.body.classList.remove("modal-open");
    }
    emit("modal-closed", name || "*");
  }
  // Click on backdrop / close button closes modal.
  // Uses .closest() so a click on the SVG icon *inside* the close button
  // still resolves to the [data-modal-close] element — t.matches alone
  // would return false for the icon and silently no-op.
  document.addEventListener("click", (e) => {
    const t = e.target;
    if (!t || typeof t.closest !== "function") return;
    const closer = t.closest("[data-modal-close]");
    if (!closer) return;
    const modal = closer.closest(".modal");
    if (modal) {
      modal.hidden = true;
      emit("modal-closed", modal.getAttribute("data-modal") || "*");
    }
    if (!document.querySelector(".modal:not([hidden])")) {
      document.body.classList.remove("modal-open");
    }
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeModal();
  });

  /* ---------- Auth modal: tab switching ---------- */
  function activateTab(which) {
    const modal = document.querySelector('[data-modal="auth"]');
    if (!modal) return;
    modal.querySelectorAll("[data-auth-tab]").forEach((b) => {
      const active = b.getAttribute("data-auth-tab") === which;
      b.classList.toggle("is-active", active);
      b.setAttribute("aria-selected", active ? "true" : "false");
    });
    modal.querySelectorAll("[data-auth-form]").forEach((f) => {
      f.hidden = f.getAttribute("data-auth-form") !== which;
    });
    const subtitle = modal.querySelector("[data-auth-subtitle]");
    const title = modal.querySelector(".modal-title");
    if (which === "register") {
      if (title) title.textContent = "Create your account";
      if (subtitle)
        subtitle.textContent = "Join UrutiHub to book and manage appointments.";
    } else {
      if (title) title.textContent = "Welcome back";
      if (subtitle)
        subtitle.textContent = "Sign in to book, view, or cancel appointments.";
    }
    modal.querySelectorAll("[data-auth-error]").forEach((p) => (p.hidden = true));
  }

  /* ---------- Submit helpers ---------- */
  function setBusy(btn, busy) {
    if (!btn) return;
    btn.disabled = !!busy;
    const label = btn.querySelector(".btn-label");
    const sp = btn.querySelector(".spinner");
    if (label) label.style.opacity = busy ? "0.5" : "1";
    if (sp) sp.hidden = !busy;
  }
  function showFormError(form, message) {
    const err = form.querySelector(".form-error");
    if (!err) return;
    err.textContent = message;
    err.hidden = false;
  }
  function clearFormError(form) {
    const err = form.querySelector(".form-error");
    if (err) err.hidden = true;
  }

  async function handleLoginSubmit(form) {
    clearFormError(form);
    const fd = new FormData(form);
    const body = {
      email: (fd.get("email") || "").toString().trim(),
      password: (fd.get("password") || "").toString(),
    };
    if (!body.email || !body.password) {
      showFormError(form, "Email and password are required.");
      return;
    }
    const submitBtn = form.querySelector("[data-auth-submit]");
    setBusy(submitBtn, true);
    try {
      const resp = await api.post("/api/auth/login", body, { skipAuth: true });
      persistAuthResponse(resp);
      closeModal("auth");
      toast("Signed in as " + (resp.email || resp.fullName || "user"), {
        variant: "success",
      });
      emit("auth-success", resp);
    } catch (e) {
      showFormError(form, e.message || "Sign in failed.");
    } finally {
      setBusy(submitBtn, false);
    }
  }

  async function handleRegisterSubmit(form) {
    clearFormError(form);
    const fd = new FormData(form);
    const body = {
      fullName: (fd.get("fullName") || "").toString().trim(),
      email: (fd.get("email") || "").toString().trim(),
      password: (fd.get("password") || "").toString(),
      phone: (fd.get("phone") || "").toString().trim() || null,
    };
    if (!body.fullName || !body.email || !body.password) {
      showFormError(form, "Please fill in all required fields.");
      return;
    }
    if (body.password.length < 8) {
      showFormError(form, "Password must be at least 8 characters.");
      return;
    }
    const submitBtn = form.querySelector("[data-auth-submit]");
    setBusy(submitBtn, true);
    try {
      const resp = await api.post("/api/auth/register", body, { skipAuth: true });
      persistAuthResponse(resp);
      closeModal("auth");
      toast("Welcome, " + (resp.fullName || resp.email) + "!", {
        variant: "success",
      });
      emit("auth-success", resp);
    } catch (e) {
      showFormError(form, e.message || "Registration failed.");
    } finally {
      setBusy(submitBtn, false);
    }
  }

  function persistAuthResponse(resp) {
    state.setAuth({
      token: resp.token,
      tokenType: resp.tokenType || "Bearer",
      expiresInMs: resp.expiresInMs || 0,
      user: {
        id: resp.userId,
        email: resp.email,
        fullName: resp.fullName,
        roles: Array.isArray(resp.roles) ? resp.roles : [],
      },
    });
  }

  /* ---------- Bootstrap form bindings ---------- */
  function bindForms() {
    const modal = document.querySelector('[data-modal="auth"]');
    if (!modal) return;

    modal.querySelectorAll("[data-auth-tab]").forEach((b) => {
      b.addEventListener("click", () =>
        activateTab(b.getAttribute("data-auth-tab")),
      );
    });
    modal.querySelectorAll("[data-auth-switch]").forEach((a) => {
      a.addEventListener("click", (e) => {
        e.preventDefault();
        activateTab(a.getAttribute("data-auth-switch"));
      });
    });

    const loginForm = modal.querySelector('[data-auth-form="login"]');
    const registerForm = modal.querySelector('[data-auth-form="register"]');
    if (loginForm) {
      loginForm.addEventListener("submit", (e) => {
        e.preventDefault();
        handleLoginSubmit(loginForm);
      });
    }
    if (registerForm) {
      registerForm.addEventListener("submit", (e) => {
        e.preventDefault();
        handleRegisterSubmit(registerForm);
      });
    }
  }

  /* ---------- Header / user-menu sync ---------- */
  function refreshHeader() {
    const isAuth = state.isAuthenticated();
    const auth = state.getAuth();
    const anonBox = document.querySelector("[data-auth-anon]");
    const userBox = document.querySelector("[data-auth-user]");
    if (anonBox) anonBox.hidden = isAuth;
    if (userBox) userBox.hidden = !isAuth;

    if (isAuth && auth && auth.user) {
      const u = auth.user;
      const initials = (u.fullName || u.email || "U")
        .trim()
        .split(/\s+/)
        .map((p) => p[0])
        .slice(0, 2)
        .join("")
        .toUpperCase();
      const av = document.querySelector("[data-user-avatar]");
      if (av) av.textContent = initials || "U";
      const name = document.querySelector("[data-user-name]");
      if (name) name.textContent = (u.fullName || u.email || "").split("@")[0];
      const popName = document.querySelector("[data-user-menu-name]");
      if (popName) popName.textContent = u.fullName || "";
      const popEmail = document.querySelector("[data-user-menu-email]");
      if (popEmail) popEmail.textContent = u.email || "";
      const popRoles = document.querySelector("[data-user-menu-roles]");
      if (popRoles) popRoles.textContent = (u.roles || []).join(" · ");
      // Role-gated items
      document.querySelectorAll("[data-role-required]").forEach((el) => {
        const role = el.getAttribute("data-role-required");
        el.hidden = !state.hasRole(role);
      });
    }
    // Inline chat email display: show user's email next to bot name
    const emailDisplay = document.querySelector("[data-chat-email-display]");
    const emailSep = document.querySelector("[data-chat-email-sep]");
    if (isAuth && auth && auth.user && auth.user.email) {
      if (emailDisplay) {
        emailDisplay.textContent = auth.user.email;
        emailDisplay.hidden = false;
      }
      if (emailSep) emailSep.hidden = false;
    } else {
      if (emailDisplay) {
        emailDisplay.textContent = "";
        emailDisplay.hidden = true;
      }
      if (emailSep) emailSep.hidden = true;
    }
  }

  /* ---------- User menu dropdown ---------- */
  function bindUserMenu() {
    const trigger = document.querySelector("[data-user-menu-trigger]");
    const popover = document.querySelector("[data-user-menu-popover]");
    if (!trigger || !popover) return;

    function close() {
      popover.hidden = true;
      trigger.setAttribute("aria-expanded", "false");
    }
    function toggle() {
      const open = popover.hidden;
      popover.hidden = !open;
      trigger.setAttribute("aria-expanded", open ? "true" : "false");
    }
    trigger.addEventListener("click", (e) => {
      e.stopPropagation();
      toggle();
    });
    document.addEventListener("click", (e) => {
      if (!popover.hidden && !popover.contains(e.target) && e.target !== trigger) {
        close();
      }
    });
    popover.querySelectorAll("[data-action]").forEach((b) => {
      b.addEventListener("click", () => {
        const a = b.getAttribute("data-action");
        close();
        emit("user-action", a);
      });
    });
  }

  /* ---------- Logout ---------- */
  async function logout() {
    // Wipe the caller's chat history server-side BEFORE clearing the JWT —
    // the endpoint requires the bearer token to identify the user. Both
    // user-linked and anonymous-on-this-device sessions are removed.
    // Fire-and-forget: a network failure shouldn't block sign-out.
    try {
      const visitorId = state.getVisitorId();
      const path = "/api/chatbot/sessions/mine"
        + (visitorId ? "?visitorId=" + encodeURIComponent(visitorId) : "");
      await api.delete(path);
    } catch (e) {
      // Stale auth or network error — proceed with local logout regardless.
      console.warn("Failed to clear server-side chat history on logout:", e);
    }
    state.clearAuth();
    toast("Signed out.", { variant: "info" });
  }

  /* ---------- Page-level action delegation ---------- */
  function bindGlobalActions() {
    document.addEventListener("click", (e) => {
      const btn = e.target.closest && e.target.closest("[data-action]");
      if (!btn) return;
      // Only consider buttons NOT inside the user-menu popover here
      // (those are already handled there + emitted as user-action).
      if (btn.closest("[data-user-menu-popover]")) return;
      const action = btn.getAttribute("data-action");
      handleAction(action);
    });
    on("user-action", handleAction);
  }
  function handleAction(action) {
    switch (action) {
      case "open-login":
        activateTab("login");
        openModal("auth");
        break;
      case "open-register":
        activateTab("register");
        openModal("auth");
        break;
      case "logout":
        logout();
        break;
      case "toggle-theme": {
        const order = ["light", "dark", "system"];
        const cur = (localStorage.getItem("urutibot.theme") || "system");
        const next = order[(order.indexOf(cur) + 1) % order.length];
        try {
          localStorage.setItem("urutibot.theme", next);
        } catch (e) {}
        const actual =
          next === "system"
            ? window.matchMedia("(prefers-color-scheme: dark)").matches
              ? "dark"
              : "light"
            : next;
        document.documentElement.setAttribute("data-theme", actual);
        document.documentElement.setAttribute("data-theme-pref", next);
        break;
      }
      // Other actions handled by appointments / chat modules
    }
  }

  /* ---------- Validate stored token on boot ---------- */
  async function validateToken() {
    if (!state.isAuthenticated()) return;
    try {
      const me = await api.get("/api/auth/me");
      // Sync the latest user data
      const auth = state.getAuth();
      if (auth) {
        auth.user = {
          id: me.id,
          email: me.email,
          fullName: me.fullName,
          roles: Array.isArray(me.roles) ? me.roles : [],
        };
        state.setAuth(auth);
      }
    } catch (e) {
      // 401 handled in api.js (clears auth + emits session-expired)
    }
  }

  /* ---------- Inline-in-chat auth widget ---------- */
  // Builds a compact auth card to render inside a chat bubble when the
  // bot signals authentication is required. Resolves to a promise once
  // the user signs in/registers (or cancels via modal close).
  function renderInlineAuthPrompt(container, opts) {
    opts = opts || {};
    const wrap = document.createElement("div");
    wrap.className = "inline-auth";
    wrap.innerHTML = `
      <div class="inline-auth-head">
        <strong>${util.escapeHtml(opts.title || "Sign in to continue")}</strong>
        <p>${util.escapeHtml(opts.message || "Booking, listing, or cancelling appointments requires an account.")}</p>
      </div>
      <div class="inline-auth-actions">
        <button type="button" class="btn btn-primary btn-sm" data-inline-auth="login">Sign in</button>
        <button type="button" class="btn btn-secondary btn-sm" data-inline-auth="register">Create account</button>
      </div>
    `;
    container.appendChild(wrap);

    wrap.querySelector('[data-inline-auth="login"]').addEventListener("click", () => {
      activateTab("login");
      openModal("auth");
    });
    wrap.querySelector('[data-inline-auth="register"]').addEventListener("click", () => {
      activateTab("register");
      openModal("auth");
    });
    return wrap;
  }

  /* ---------- Password visibility toggle ----------
     Wraps every <input type="password"> in a positioned container and
     drops in an eye / eye-off button that flips the input's type. Runs
     on init AND on modal-opened so dynamically-revealed forms (the
     register tab, password fields inside any modal) get enhanced too. */
  const EYE_OPEN = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>';
  const EYE_OFF = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M3 3l18 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M10.6 6.2A10.6 10.6 0 0 1 12 6c6.5 0 10 6 10 6a17.7 17.7 0 0 1-3.2 3.9M6.2 7.6A17.7 17.7 0 0 0 2 12s3.5 6 10 6c1.4 0 2.6-.2 3.7-.6" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9.5 9.5a3 3 0 0 0 4.2 4.2" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>';

  function enhancePasswordInputs(root) {
    (root || document)
      .querySelectorAll('input[type="password"]:not([data-pw-enhanced])')
      .forEach((input) => {
        input.setAttribute("data-pw-enhanced", "true");
        const wrap = document.createElement("span");
        wrap.className = "pw-wrap";
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "pw-toggle";
        btn.setAttribute("aria-label", "Show password");
        btn.setAttribute("aria-pressed", "false");
        btn.innerHTML = EYE_OPEN;
        btn.addEventListener("click", () => {
          const showing = input.type === "text";
          input.type = showing ? "password" : "text";
          btn.setAttribute("aria-pressed", showing ? "false" : "true");
          btn.setAttribute(
            "aria-label",
            showing ? "Show password" : "Hide password",
          );
          btn.innerHTML = showing ? EYE_OPEN : EYE_OFF;
        });
        wrap.appendChild(btn);
      });
  }

  /* ---------- Init ---------- */
  function init() {
    bindForms();
    bindUserMenu();
    bindGlobalActions();
    refreshHeader();
    validateToken();
    enhancePasswordInputs();
    on("modal-opened", () => enhancePasswordInputs());
    on("auth-changed", refreshHeader);
    on("session-expired", () => {
      toast("Your session expired. Please sign in again.", { variant: "error" });
      activateTab("login");
      openModal("auth");
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

  // Public surface
  window.UrutiBot.auth = {
    openLogin: () => {
      activateTab("login");
      openModal("auth");
    },
    openRegister: () => {
      activateTab("register");
      openModal("auth");
    },
    closeModal,
    openModal,
    logout,
    renderInlineAuthPrompt,
  };
})();
