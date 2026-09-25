import type { CommunitySession } from "../providers/community-session";

export function shouldAdoptSession(initial: CommunitySession, stored?: CommunitySession): boolean {
  // A new verified installation supersedes a rejected session even if the old
  // session's automatic refresh gave it a later expiry date.
  return !stored || initial.installId !== stored.installId ||
    Date.parse(initial.expiresAt) > Date.parse(stored.expiresAt);
}
