/** Reviewed editorial entries. Never infer a death from generated text or trending searches. */
export const editorialEntries = [{
  id: "dolly-parton-celebration-2026",
  kind: "tribute",
  artist: "Dolly Parton",
  title: "Celebrating Dolly Parton",
  description: "A life of songs, storytelling and generosity. Explore her music, from Jolene to 9 to 5.",
  sourceUrl: "https://www.dollyparton.com/",
  sourceLabel: "Dolly Parton · Official website",
  verifiedAt: "2026-09-07T00:00:00Z",
  startsAt: "2026-08-25T00:00:00Z",
  expiresAt: "2026-09-25T00:00:00Z",
  featuredSong: "Jolene",
  sponsored: false,
}];

export function activeEditorial(now = Date.now()) {
  return editorialEntries.filter(entry => Date.parse(entry.startsAt) <= now && now < Date.parse(entry.expiresAt));
}
