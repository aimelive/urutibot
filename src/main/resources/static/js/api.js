/* =========================================================
   UrutiBot - shared client utilities
   - Persistent state (auth, visitorId, memoryId) in localStorage
   - Tiny event bus
   - Fetch wrapper with bearer-token + JSON handling
   - UUID + date helpers
   - Toast notifications
   Exposes: window.UrutiBot
   ========================================================= */
(function () {
  "use strict";

  const STORAGE = {
    AUTH: "urutibot.auth",
    VISITOR: "urutibot.visitorId",
    MEMORY: "urutibot.memoryId",
    THEME: "urutibot.theme",
  };

  /* ---------- UUID ---------- */
  function uuid() {
    if (window.crypto && typeof crypto.randomUUID === "function") {
      return crypto.randomUUID();
    }
    // RFC4122 v4 fallback
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  /* ---------- localStorage helpers ---------- */
  function readJson(key) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }
  function writeJson(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {}
  }
  function readStr(key) {
    try {
      return localStorage.getItem(key);
    } catch (e) {
      return null;
    }
  }
  function writeStr(key, value) {
    try {
      if (value == null) localStorage.removeItem(key);
      else localStorage.setItem(key, value);
    } catch (e) {}
  }

  /* ---------- Event bus ---------- */
  const listeners = new Map(); // event -> Set<fn>
  function on(event, fn) {
    if (!listeners.has(event)) listeners.set(event, new Set());
    listeners.get(event).add(fn);
    return () => off(event, fn);
  }
  function off(event, fn) {
    const set = listeners.get(event);
    if (set) set.delete(fn);
  }
  function emit(event, payload) {
    const set = listeners.get(event);
    if (!set) return;
    set.forEach((fn) => {
      try {
        fn(payload);
      } catch (e) {
        console.error("[bus] listener error for", event, e);
      }
    });
  }

  /* ---------- State ---------- */
  const state = {
    auth: readJson(STORAGE.AUTH), // {token, user:{id,email,fullName,roles[]}}
    visitorId: readStr(STORAGE.VISITOR),
    memoryId: readStr(STORAGE.MEMORY),
  };

  if (!state.visitorId) {
    state.visitorId = uuid();
    writeStr(STORAGE.VISITOR, state.visitorId);
  }
  if (!state.memoryId) {
    state.memoryId = uuid();
    writeStr(STORAGE.MEMORY, state.memoryId);
  }

  function getAuth() {
    return state.auth;
  }
  function isAuthenticated() {
    return !!(state.auth && state.auth.token);
  }
  function hasRole(role) {
    return (
      isAuthenticated() &&
      Array.isArray(state.auth.user && state.auth.user.roles) &&
      state.auth.user.roles.includes(role)
    );
  }
  function setAuth(auth) {
    state.auth = auth;
    if (auth) writeJson(STORAGE.AUTH, auth);
    else writeStr(STORAGE.AUTH, null);
    emit("auth-changed", state.auth);
  }
  function clearAuth() {
    setAuth(null);
  }

  function getVisitorId() {
    return state.visitorId;
  }
  function getMemoryId() {
    return state.memoryId;
  }
  function setMemoryId(id) {
    state.memoryId = id || uuid();
    writeStr(STORAGE.MEMORY, state.memoryId);
    emit("memory-changed", state.memoryId);
    return state.memoryId;
  }
  function rotateMemoryId() {
    return setMemoryId(uuid());
  }

  /* ---------- Fetch wrapper ---------- */
  async function request(path, opts) {
    opts = opts || {};
    const headers = Object.assign(
      { Accept: "application/json" },
      opts.headers || {},
    );
    const auth = getAuth();
    if (auth && auth.token && !opts.skipAuth) {
      headers["Authorization"] = "Bearer " + auth.token;
    }
    let body = opts.body;
    if (body && typeof body === "object" && !(body instanceof FormData) && !(body instanceof Blob)) {
      headers["Content-Type"] = headers["Content-Type"] || "application/json";
      body = JSON.stringify(body);
    }

    let resp;
    try {
      resp = await fetch(path, {
        method: opts.method || "GET",
        headers,
        body,
        signal: opts.signal,
      });
    } catch (err) {
      throw new ApiError("Network error. Please check your connection.", 0, err);
    }

    // 401 on a request that carried a token => token is invalid/expired
    if (resp.status === 401 && headers["Authorization"]) {
      clearAuth();
      emit("session-expired");
    }

    let data = null;
    const ct = resp.headers.get("content-type") || "";
    if (ct.includes("application/json")) {
      try {
        data = await resp.json();
      } catch (e) {
        data = null;
      }
    } else if (resp.status !== 204) {
      try {
        data = await resp.text();
      } catch (e) {
        data = null;
      }
    }

    if (!resp.ok) {
      const message = extractErrorMessage(data, resp.status);
      throw new ApiError(message, resp.status, data);
    }
    return data;
  }
  function extractErrorMessage(data, status) {
    if (!data) return "Request failed (" + status + ").";
    if (typeof data === "string") return data;
    if (Array.isArray(data.message)) return data.message.join(" ");
    if (data.message) return data.message;
    if (data.error) return data.error;
    return "Request failed (" + status + ").";
  }
  class ApiError extends Error {
    constructor(message, status, body) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.body = body;
    }
  }

  const api = {
    get: (path, opts) => request(path, Object.assign({}, opts, { method: "GET" })),
    post: (path, body, opts) =>
      request(path, Object.assign({}, opts, { method: "POST", body })),
    put: (path, body, opts) =>
      request(path, Object.assign({}, opts, { method: "PUT", body })),
    patch: (path, body, opts) =>
      request(path, Object.assign({}, opts, { method: "PATCH", body })),
    delete: (path, opts) =>
      request(path, Object.assign({}, opts, { method: "DELETE" })),
    raw: request,
  };

  /* ---------- Date / format ---------- */
  function formatDateTime(isoLike) {
    if (!isoLike) return "—";
    try {
      const d = new Date(isoLike);
      if (isNaN(d.getTime())) return String(isoLike);
      return d.toLocaleString(undefined, {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch (e) {
      return String(isoLike);
    }
  }
  function relativeFromNow(isoLike) {
    try {
      const d = new Date(isoLike);
      const diff = d.getTime() - Date.now();
      const abs = Math.abs(diff);
      const mins = Math.round(abs / 60000);
      const hrs = Math.round(abs / 3600000);
      const days = Math.round(abs / 86400000);
      const sign = diff >= 0 ? "in " : "";
      const suffix = diff >= 0 ? "" : " ago";
      if (mins < 60) return sign + mins + "m" + suffix;
      if (hrs < 24) return sign + hrs + "h" + suffix;
      return sign + days + "d" + suffix;
    } catch (e) {
      return "";
    }
  }
  function escapeHtml(s) {
    return String(s == null ? "" : s).replace(
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

  /* ---------- Toast ---------- */
  function toast(message, opts) {
    opts = opts || {};
    const root = document.querySelector("[data-toast-root]");
    if (!root) return;
    const el = document.createElement("div");
    el.className = "toast toast--" + (opts.variant || "info");
    el.setAttribute("role", "status");
    el.textContent = message;
    root.appendChild(el);
    requestAnimationFrame(() => el.classList.add("is-in"));
    const ttl = opts.duration || 4000;
    setTimeout(() => {
      el.classList.remove("is-in");
      el.classList.add("is-out");
      setTimeout(() => el.remove(), 250);
    }, ttl);
  }

  window.UrutiBot = {
    api,
    on,
    off,
    emit,
    uuid,
    state: {
      getAuth,
      isAuthenticated,
      hasRole,
      setAuth,
      clearAuth,
      getVisitorId,
      getMemoryId,
      setMemoryId,
      rotateMemoryId,
    },
    util: { formatDateTime, relativeFromNow, escapeHtml },
    toast,
    ApiError,
  };
})();
