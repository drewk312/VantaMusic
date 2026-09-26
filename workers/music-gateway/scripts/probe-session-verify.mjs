const COMMUNITY_MIN_SKEW_MS = 5 * 60 * 1000;
function communitySessionFromEnv(env) {
  const sessionId = env.COMMUNITY_SESSION_ID?.trim();
  const sessionSecret = env.COMMUNITY_SESSION_SECRET?.trim();
  if (!sessionId || !sessionSecret) return null;
  const expiresAt = env.COMMUNITY_SESSION_EXPIRES?.trim();
  if (expiresAt) {
    const parsed = Date.parse(expiresAt);
    if (Number.isFinite(parsed) && parsed - Date.now() < COMMUNITY_MIN_SKEW_MS) {
      return null;
    }
  }
  return {
    installId: env.COMMUNITY_INSTALL_ID?.trim() || "shared-gateway-install",
    sessionId,
    sessionSecret,
    expiresAt: expiresAt || "",
    appVersion: env.COMMUNITY_APP_VERSION?.trim() || "unknown",
    platform: env.COMMUNITY_PLATFORM?.trim() || "desktop",
  };
}
const env = { COMMUNITY_SESSION_ID: "abc", COMMUNITY_SESSION_SECRET: "def", COMMUNITY_SESSION_EXPIRES: "2026-09-11T16:13:49.000Z" };
console.log(communitySessionFromEnv(env));