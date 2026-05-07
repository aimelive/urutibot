/* =========================================================
   UrutiBot - appointments management UI
   - My appointments list (USER + ADMIN)
   - Create appointment form
   - Admin: paginated list of all appointments + status update + cancel
   ========================================================= */
(function () {
  "use strict";

  const { api, state, on, util, toast } = window.UrutiBot;

  const STATUS_LABELS = {
    BOOKED: "Booked",
    COMPLETED: "Completed",
    CANCELLED: "Cancelled",
  };
  const STATUS_VARIANTS = {
    BOOKED: "info",
    COMPLETED: "success",
    CANCELLED: "danger",
  };

  /* ---------- helpers ---------- */
  function setLoading(listEl, on) {
    if (!listEl) return;
    const loading = listEl.querySelector("[data-list-loading]");
    if (loading) loading.hidden = !on;
  }
  function setEmpty(listEl, on) {
    const empty = listEl.querySelector("[data-list-empty]");
    if (empty) empty.hidden = !on;
  }
  function clearList(listEl) {
    listEl.querySelectorAll(".apt-card").forEach((c) => c.remove());
  }

  function renderAppointmentCard(appt, opts) {
    opts = opts || {};
    const isAdmin = !!opts.admin;
    const card = document.createElement("article");
    card.className = "apt-card";
    card.setAttribute("data-apt-id", appt.id);
    const status = appt.status || "BOOKED";
    const statusLabel = STATUS_LABELS[status] || status;
    const statusVar = STATUS_VARIANTS[status] || "info";

    card.innerHTML = `
      <header class="apt-card-head">
        <div class="apt-when">
          <strong>${util.escapeHtml(util.formatDateTime(appt.dateTime))}</strong>
          <span class="muted">${util.escapeHtml(util.relativeFromNow(appt.dateTime))}</span>
        </div>
        <span class="badge badge--${statusVar}">${statusLabel}</span>
      </header>
      <p class="apt-purpose">${util.escapeHtml(appt.purpose || "")}</p>
      ${
        isAdmin
          ? `<p class="apt-meta muted">${util.escapeHtml(appt.fullName || "")} · ${util.escapeHtml(appt.email || "")}</p>`
          : ""
      }
      <p class="apt-id muted">ID: <code>${util.escapeHtml(appt.id)}</code></p>
      <footer class="apt-card-foot">
        ${
          isAdmin
            ? `
          <select class="apt-status-select" data-apt-status>
            ${["BOOKED", "COMPLETED", "CANCELLED"]
              .map(
                (s) =>
                  `<option value="${s}" ${s === status ? "selected" : ""}>${STATUS_LABELS[s]}</option>`,
              )
              .join("")}
          </select>
          <button type="button" class="btn btn-secondary btn-sm" data-apt-update>Update</button>
        `
            : ""
        }
        ${
          status !== "CANCELLED"
            ? `<button type="button" class="btn btn-ghost-danger btn-sm" data-apt-cancel>Cancel</button>`
            : ""
        }
      </footer>
    `;
    return card;
  }

  function bindCardActions(card, opts) {
    opts = opts || {};
    const id = card.getAttribute("data-apt-id");
    const cancelBtn = card.querySelector("[data-apt-cancel]");
    if (cancelBtn) {
      cancelBtn.addEventListener("click", async () => {
        if (!confirm("Cancel this appointment?")) return;
        cancelBtn.disabled = true;
        try {
          await api.put("/api/appointments/" + id + "/cancel");
          toast("Appointment cancelled.", { variant: "success" });
          opts.onChanged && opts.onChanged();
        } catch (e) {
          toast(e.message || "Failed to cancel.", { variant: "error" });
          cancelBtn.disabled = false;
        }
      });
    }
    const updateBtn = card.querySelector("[data-apt-update]");
    const statusSel = card.querySelector("[data-apt-status]");
    if (updateBtn && statusSel) {
      updateBtn.addEventListener("click", async () => {
        const newStatus = statusSel.value;
        updateBtn.disabled = true;
        try {
          await api.patch("/api/appointments/" + id + "/status", {
            status: newStatus,
          });
          toast("Status updated.", { variant: "success" });
          opts.onChanged && opts.onChanged();
        } catch (e) {
          toast(e.message || "Failed to update.", { variant: "error" });
          updateBtn.disabled = false;
        }
      });
    }
  }

  /* ---------- My appointments ---------- */
  async function loadMyAppointments() {
    if (!state.isAuthenticated()) {
      window.UrutiBot.auth.openLogin();
      return;
    }
    const listEl = document.querySelector("[data-my-appointments-list]");
    if (!listEl) return;
    clearList(listEl);
    setEmpty(listEl, false);
    setLoading(listEl, true);
    try {
      const items = await api.get("/api/appointments/me");
      setLoading(listEl, false);
      if (!Array.isArray(items) || items.length === 0) {
        setEmpty(listEl, true);
        return;
      }
      items
        .sort((a, b) => new Date(b.dateTime) - new Date(a.dateTime))
        .forEach((a) => {
          const card = renderAppointmentCard(a, { admin: false });
          bindCardActions(card, { onChanged: loadMyAppointments });
          listEl.appendChild(card);
        });
    } catch (e) {
      setLoading(listEl, false);
      toast(e.message || "Failed to load appointments.", { variant: "error" });
    }
  }

  /* ---------- Admin appointments ---------- */
  const adminPager = { page: 0, size: 10, totalPages: 1 };

  async function loadAdminAppointments() {
    if (!state.hasRole("ADMIN")) return;
    const listEl = document.querySelector("[data-admin-appointments-list]");
    if (!listEl) return;
    clearList(listEl);
    setEmpty(listEl, false);
    setLoading(listEl, true);
    try {
      const resp = await api.get(
        "/api/appointments?page=" +
          adminPager.page +
          "&size=" +
          adminPager.size +
          "&sort=dateTime,desc",
      );
      setLoading(listEl, false);
      // Spring Data PagedModel envelope (VIA_DTO): { content, page: { totalPages, ... } }.
      // Fall back to top-level totalPages for legacy/non-paged responses.
      const pageMeta = resp.page || {};
      adminPager.totalPages = pageMeta.totalPages || resp.totalPages || 1;
      const text = document.querySelector("[data-admin-pager-text]");
      if (text)
        text.textContent =
          "Page " + (adminPager.page + 1) + " of " + adminPager.totalPages;
      const items = resp.content || [];
      if (items.length === 0) {
        setEmpty(listEl, true);
        return;
      }
      items.forEach((a) => {
        const card = renderAppointmentCard(a, { admin: true });
        bindCardActions(card, { onChanged: loadAdminAppointments });
        listEl.appendChild(card);
      });
    } catch (e) {
      setLoading(listEl, false);
      toast(e.message || "Failed to load appointments.", { variant: "error" });
    }
  }

  function bindAdminPager() {
    const prev = document.querySelector("[data-admin-prev]");
    const next = document.querySelector("[data-admin-next]");
    if (prev) {
      prev.addEventListener("click", () => {
        if (adminPager.page > 0) {
          adminPager.page -= 1;
          loadAdminAppointments();
        }
      });
    }
    if (next) {
      next.addEventListener("click", () => {
        if (adminPager.page < adminPager.totalPages - 1) {
          adminPager.page += 1;
          loadAdminAppointments();
        }
      });
    }
  }

  /* ---------- Create appointment ---------- */
  function bindCreateForm() {
    const form = document.querySelector("[data-create-apt-form]");
    if (!form) return;
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const errEl = form.querySelector("[data-create-apt-error]");
      if (errEl) errEl.hidden = true;
      const submit = form.querySelector("[data-create-apt-submit]");
      const fd = new FormData(form);
      const purpose = (fd.get("purpose") || "").toString().trim();
      const dateTime = (fd.get("dateTime") || "").toString();
      if (!purpose || !dateTime) {
        if (errEl) {
          errEl.textContent = "Both fields are required.";
          errEl.hidden = false;
        }
        return;
      }
      // datetime-local value is "YYYY-MM-DDTHH:mm" - backend accepts this.
      const body = { purpose, dateTime: dateTime + ":00" };
      submit.disabled = true;
      submit.querySelector(".spinner") && (submit.querySelector(".spinner").hidden = false);
      try {
        await api.post("/api/appointments", body);
        toast("Appointment booked.", { variant: "success" });
        form.reset();
        window.UrutiBot.auth.closeModal("create-appointment");
        loadMyAppointments();
      } catch (e) {
        if (errEl) {
          errEl.textContent = e.message || "Failed to create appointment.";
          errEl.hidden = false;
        }
      } finally {
        submit.disabled = false;
        submit.querySelector(".spinner") && (submit.querySelector(".spinner").hidden = true);
      }
    });
  }

  /* ---------- Wire-up actions ---------- */
  function init() {
    bindCreateForm();
    bindAdminPager();

    on("user-action", (action) => {
      if (action === "open-my-appointments") {
        window.UrutiBot.auth.openModal("my-appointments");
        loadMyAppointments();
      } else if (action === "open-admin") {
        adminPager.page = 0;
        window.UrutiBot.auth.openModal("admin-appointments");
        loadAdminAppointments();
      }
    });

    document.addEventListener("click", (e) => {
      const btn = e.target.closest && e.target.closest("[data-action]");
      if (!btn) return;
      if (btn.closest("[data-user-menu-popover]")) return;
      const action = btn.getAttribute("data-action");
      if (action === "open-my-appointments") {
        if (!state.isAuthenticated()) {
          window.UrutiBot.auth.openLogin();
          return;
        }
        window.UrutiBot.auth.openModal("my-appointments");
        loadMyAppointments();
      } else if (action === "open-create-appointment") {
        if (!state.isAuthenticated()) {
          window.UrutiBot.auth.openLogin();
          return;
        }
        window.UrutiBot.auth.openModal("create-appointment");
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

  window.UrutiBot.appointments = {
    loadMyAppointments,
    loadAdminAppointments,
    openCreate: () => window.UrutiBot.auth.openModal("create-appointment"),
  };
})();
