/* =========================================================
   UrutiBot - main bootstrap (currently a thin entry point;
   each module self-initializes on DOMContentLoaded).
   Kept as a separate file so future cross-cutting concerns
   (analytics, feature flags, error telemetry) have a place
   to land without touching individual modules.
   ========================================================= */
(function () {
  "use strict";
  // No-op for now. UrutiBot.api / .auth / .appointments / .chat
  // all self-init when their scripts load.
})();
