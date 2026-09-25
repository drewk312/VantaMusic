const PERFORMER_ROLES = new Set([
  "mainartist",
  "main artist",
  "associatedperformer",
  "associated performer",
  "featured artist",
  "featuredartist",
  "featuring",
  "performer",
  "vocalist",
  "backgroundvocal",
  "background vocal",
]);

const CREDIT_ONLY_ROLES = new Set([
  "composer",
  "lyricist",
  "composerlyricist",
  "songwriter",
  "producer",
  "executiveproducer",
  "executive producer",
  "writer",
  "arranger",
  "mixer",
  "editor",
  "programmer",
  "mastering",
  "masteringengineer",
  "mastering engineer",
  "recordingengineer",
  "recording engineer",
  "assistantengineer",
  "assistant engineer",
  "mixingengineer",
  "mixing engineer",
  "workarranger",
  "work arranger",
  "interprete",
  "interprète",
  "musicpublisher",
  "music publisher",
  "publisher",
  "2ndengineer",
  "2nd engineer",
  "secondengineer",
  "second engineer",
  "editedby",
  "edited by",
  "coproducer",
  "co producer",
  "assistantproducer",
  "associatedproducer",
]);

function roleKey(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, "");
}

function isPerformerRole(value: string): boolean {
  const lower = value.trim().toLowerCase();
  return PERFORMER_ROLES.has(lower) || PERFORMER_ROLES.has(roleKey(value));
}

function isCreditOnlyRole(value: string): boolean {
  const lower = value.trim().toLowerCase();
  return CREDIT_ONLY_ROLES.has(lower) || CREDIT_ONLY_ROLES.has(roleKey(value));
}

function looksLikeCamelRole(value: string): boolean {
  return /^(?:\d+(?:st|nd|rd|th))?[A-Z][a-z]+(?:[A-Z][a-z]+)+$/.test(value.trim());
}

function isPublisherLike(value: string): boolean {
  const n = value.toLowerCase();
  return n.includes("publishing") || n.includes("publisher") || n.includes("rights management");
}

function isRoleOrCredit(value: string): boolean {
  if (isPerformerRole(value) || isCreditOnlyRole(value)) return true;
  if (looksLikeCamelRole(value)) return true;
  if (isPublisherLike(value)) return true;
  if (/^(edited by|mixed by|produced by|written by)$/i.test(value.trim())) return true;
  if (/^\d+(st|nd|rd|th)\s+/i.test(value) && /engineer|editor|assistant/i.test(value)) return true;
  return false;
}

function pushUnique(names: string[], name: string): void {
  if (!names.some((existing) => existing.toLowerCase() === name.toLowerCase())) {
    names.push(name);
  }
}

/** First billed name, with Qobuz role / publisher tokens stripped. */
export function cleanArtistCredit(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  const parts = trimmed.split(",").map((part) => part.trim()).filter(Boolean);
  if (parts.length === 0) return "";
  if (parts.length === 1) return isRoleOrCredit(parts[0]) ? "" : parts[0];
  const names = parts.filter((part) => !isRoleOrCredit(part));
  return names[0] ?? "";
}

/** Primary + featured names from a Qobuz track payload. Drops credit-only roles. */
export function qobuzArtistLine(item: Record<string, unknown>): string {
  const performer = item.performer as { name?: string } | undefined;
  const primary = cleanArtistCredit(performer?.name ?? "");
  const performersRaw = typeof item.performers === "string" ? item.performers : "";
  const names: string[] = [];
  const dashSeparated = performersRaw.includes(" - ");
  const chunks = dashSeparated ? performersRaw.split(" - ") : [performersRaw];
  for (const chunk of chunks) {
    const parts = chunk.split(",").map((part) => part.trim()).filter(Boolean);
    if (parts.length === 0) continue;

    if (!dashSeparated && parts.some(isRoleOrCredit)) {
      const firstName = parts.find((part) => !isRoleOrCredit(part));
      if (firstName) pushUnique(names, firstName);
      continue;
    }

    const maybeName = parts[0];
    const roles = parts.slice(1);
    if (!maybeName || isRoleOrCredit(maybeName)) continue;
    const roleTokens = roles.filter((part) => isRoleOrCredit(part));
    if (roles.length > 0 && roleTokens.length === 0) {
      for (const name of parts) {
        if (!isRoleOrCredit(name)) pushUnique(names, name);
      }
      continue;
    }
    const hasPerformerRole = roles.some(isPerformerRole);
    const creditOnly = roles.length > 0 && roles.every((part) => isRoleOrCredit(part) && !isPerformerRole(part));
    if (creditOnly && !hasPerformerRole) continue;
    pushUnique(names, maybeName);
  }
  if (names.length === 0) return primary || "Unknown Artist";
  const head = primary || names[0];
  const featured = names.filter((name) => name.toLowerCase() !== head.toLowerCase());
  return featured.length === 0 ? head : `${head}, ${featured.join(", ")}`;
}
