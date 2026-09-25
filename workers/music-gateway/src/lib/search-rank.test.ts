import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { inferSearchIntent, rankTracks, extractArtistsAndAlbums } from "./search-rank.js";
import type { GatewayTrack, ProviderId } from "../types";
import { chooseCatalogSuggestion, dedupeTracks } from "../providers/search.js";
import { isAllowedVocalTrack } from "./vocal-recording.js";

function track(overrides: Partial<GatewayTrack> = {}): GatewayTrack {
  return {
    id: "1",
    title: "Song",
    artist: "Artist",
    provider: "deezer" as ProviderId,
    format: "flac",
    explicit: false,
    ...overrides,
  };
}

describe("search-rank", () => {
  it("uses consensus suggestions with artist context and rejects ambiguous completions", () => {
    assert.equal(
      chooseCatalogSuggestion("bohemian rapsody", [
        "bohemian rhapsody",
        "bohemian rhapsody karaoke",
        "bohemian rhapsody queen",
        "bohemian rhapsody movie",
      ]),
      "bohemian rhapsody queen"
    );
    assert.equal(
      chooseCatalogSuggestion("smels like teen spirit", [
        "smells like teen spirit",
        "smells like teen spirit nirvana",
        "smells like teen spirit guitar",
      ]),
      "smells like teen spirit nirvana"
    );
    assert.equal(
      chooseCatalogSuggestion("hotel calfornia", [
        "hotel california",
        "hotel california eagles",
        "hotel california karaoke",
        "hotel california solo lesson",
      ]),
      "hotel california eagles"
    );
    assert.equal(
      chooseCatalogSuggestion("rollin in the deep", [
        "rolling in the deep",
        "rolling in the deep karaoke",
        "rolling in the deep adele",
        "rolling in the deep lyrics",
        "rolling in the deep live",
        "rolling in the deep lil wayne",
      ]),
      "rolling in the deep adele"
    );
    assert.equal(
      chooseCatalogSuggestion("bohemian rapsody", [
        "bohemian rhapsody",
        "bohemian rhapsody queen",
        "bohemian rhapsody flash mob",
        "bohemian rhapsody piano tutorial",
      ]),
      "bohemian rhapsody queen"
    );
    assert.equal(
      chooseCatalogSuggestion("wonder all", [
        "wonder mall",
        "wonder all scenes",
        "wonder all song",
        "wonder all we do",
      ]),
      "wonder all"
    );
  });

  it("infers misspelled songs across the catalog without title-specific rules", () => {
    const cases = [
      ["wonder all", "Wonderwall", "Oasis"],
      ["hotel calfornia", "Hotel California", "Eagles"],
      ["bohemian rapsody", "Bohemian Rhapsody", "Queen"],
      ["blinding lites", "Blinding Lights", "The Weeknd"],
      ["smels like teen spirit", "Smells Like Teen Spirit", "Nirvana"],
      ["shape of yuo", "Shape of You", "Ed Sheeran"],
    ] as const;

    for (const [query, title, artist] of cases) {
      const discovery = [
        track({ id: `${query}-original`, title, artist, album: "Original Album", isrc: `ISRC-${query}` }),
        track({ id: `${query}-collision`, title: query, artist: query === "bohemian rapsody" ? "BOHEMIAN RAPSODY" : "Exact Text Collision", album: "Obscure Match", isrc: `COLLISION-${query}` }),
        track({ id: `${query}-cover`, title, artist: "Karaoke Cover Band" }),
        track({ id: `${query}-noise`, title: "Something Else", artist: "Another Artist" }),
      ];
      const intent = inferSearchIntent(query, discovery);
      assert.equal(intent.kind, "track", query);
      assert.equal(intent.title, title.toLowerCase(), query);
      assert.equal(intent.artist, artist, query);
      assert.equal(intent.retrievalQuery.toLowerCase(), `${title} ${artist}`.toLowerCase(), query);

      const ranked = rankTracks(query, discovery.slice().reverse(), intent);
      assert.equal(ranked[0].artist, artist, query);
    }
  });

  it("does not treat a popular song title as an artist named after the song", () => {
    const intent = inferSearchIntent("Happy", [
      track({ id: "swap", title: "Happy (From Despicable Me 2)", artist: "Happy", album: "Despicable Me 2" }),
      track({ id: "movie", title: "Despicable Me 2", artist: "Happy", album: "Despicable Me 2" }),
      track({ id: "pharrell-ost", title: "Happy", artist: "Pharrell Williams", album: "Despicable Me 2", isrc: "USUM71311296", releaseYear: 2013 }),
      track({ id: "pharrell", title: "Happy", artist: "Pharrell Williams", album: "G I R L", isrc: "USUM71311296", releaseYear: 2013 }),
      track({ id: "pharrell-2", title: "Happy", artist: "Pharrell Williams", album: "G I R L" }),
    ]);
    assert.equal(intent.kind, "track");
    assert.equal(intent.title, "happy");
    assert.equal(intent.artist, "Pharrell Williams");

    const ranked = rankTracks("Happy", [
      track({ id: "swap", title: "Happy (From Despicable Me 2)", artist: "Happy", album: "Despicable Me 2" }),
      track({ id: "pharrell", title: "Happy", artist: "Pharrell Williams", album: "G I R L", isrc: "USUM71311296", releaseYear: 2013 }),
    ], intent);
    assert.equal(ranked[0].artist, "Pharrell Williams");
  });

  it("preserves artist searches, requested variants, and low-confidence queries", () => {
    const artistIntent = inferSearchIntent("Oasis", [
      track({ title: "Wonderwall", artist: "Oasis" }),
      track({ title: "Live Forever", artist: "Oasis" }),
    ]);
    assert.equal(artistIntent.kind, "artist");
    assert.equal(artistIntent.retrievalQuery, "Oasis");

    const liveIntent = inferSearchIntent("wonderwall live", [
      track({ title: "Wonderwall (Live)", artist: "Oasis" }),
      track({ title: "Wonderwall", artist: "Oasis" }),
    ]);
    assert.equal(liveIntent.kind, "track");
    assert.match(liveIntent.retrievalQuery.toLowerCase(), /live/);

    const unknown = inferSearchIntent("unknown deep cut", [
      track({ title: "Unknown Dub", artist: "Caleo" }),
      track({ title: "Cut Me So Deep", artist: "The Decoders" }),
    ]);
    assert.equal(unknown.kind, "open");
    assert.equal(unknown.retrievalQuery, "unknown deep cut");
  });

  it("rejects unrequested covers generically while allowing explicit cover searches", () => {
    const cover = track({
      title: "Hotel California [flamenco guitar solo cover]",
      artist: "LucasGitanoFamily",
    });

    assert.equal(isAllowedVocalTrack(cover, "hotel calfornia"), false);
    assert.equal(
      isAllowedVocalTrack(
        track({ title: "Hotel California Cover", artist: "Tribute Band" }),
        "hotel california cover"
      ),
      true
    );
  });

  it("ranks exact title matches higher", () => {
    const query = "bad guy billie eilish";
    const tracks = [
      track({ title: "Bad Guy", artist: "Billie Eilish" }),
      track({ title: "bad guy (remix)", artist: "Billie Eilish" }),
      track({ title: "Bad Guy", artist: "Some Cover Band" }),
    ];
    const ranked = rankTracks(query, tracks);
    assert.equal(ranked[0].artist, "Billie Eilish");
    assert.equal(ranked[0].title, "Bad Guy");
  });

  it("lets exact title-and-artist evidence beat an upstream cover", () => {
    const intent = inferSearchIntent("creep radiohead", [
      track({ id: "cover", title: "Creep (Acoustic Live)", artist: "Francesco Parodi" }),
      track({ id: "noise", title: "Creep", artist: "Cover Collective" }),
      track({ id: "original", title: "Creep", artist: "Radiohead", album: "Pablo Honey" }),
    ]);

    assert.equal(intent.kind, "track");
    assert.equal(intent.title, "creep");
    assert.equal(intent.artist, "Radiohead");
    assert.equal(rankTracks("creep radiohed", [
      track({ id: "cover", title: "Creep (Acoustic Live)", artist: "Francesco Parodi" }),
      track({ id: "original", title: "Creep", artist: "Radiohead", album: "Pablo Honey" }),
    ], intent)[0].id, "original");
  });

  it("uses catalog intent to prioritize the original when the query omits the artist", () => {
    const query = "Spirit in the Sky";
    const tracks = [
      track({
        id: "cover",
        title: "Spirit In the Sky",
        artist: "Bo Donaldson & The Heywoods",
        provider: "qobuz",
        audioQuality: "24-bit / 48 kHz FLAC",
        isrc: "USBT21630071",
      }),
      track({
        id: "original",
        title: "Spirit In The Sky",
        artist: "Norman Greenbaum",
        provider: "qobuz",
        audioQuality: "24-bit / 192 kHz FLAC",
        isrc: "USC4R2335155",
      }),
      track({
        id: "cover-2",
        title: "Spirit in the Sky",
        artist: "KEiiNO",
        audioQuality: "16-bit / 44.1 kHz FLAC",
        isrc: "QZ4JJ1823592",
      }),
    ];

    const intent = inferSearchIntent(query, [tracks[1], tracks[0], tracks[2]]);
    const ranked = rankTracks(query, tracks, intent);
    assert.equal(ranked[0].artist, "Norman Greenbaum");
    assert.equal(ranked[0].id, "original");
  });

  it("uses original release evidence when several artists have the same exact title", () => {
    const intent = inferSearchIntent("everybody wants to rule the world", [
      track({ id: "later", title: "Everybody Wants To Rule The World", artist: "Lorde", releaseYear: 2013 }),
      track({ id: "cover", title: "Everybody Wants To Rule The World", artist: "Later Cover", releaseYear: 2021 }),
      track({ id: "original", title: "Everybody Wants To Rule The World", artist: "Tears For Fears", releaseYear: 1985 }),
    ]);

    assert.equal(intent.artist, "Tears For Fears");
  });

  it("uses repeated catalog identity to resist a one-off earlier same-title recording", () => {
    const intent = inferSearchIntent("shared title", [
      track({ id: "popular-1", title: "Shared Title", artist: "Primary Artist", releaseYear: 2022 }),
      track({ id: "popular-2", title: "Shared Title", artist: "Primary Artist", releaseYear: 2023 }),
      track({ id: "earlier", title: "Shared Title", artist: "Alternate Artist", releaseYear: 1996 }),
    ]);

    assert.equal(intent.artist, "Primary Artist");
  });

  it("preserves the intended artist credit when providers share an ISRC", () => {
    const tracks = dedupeTracks([
      track({ id: "deezer", title: "Uptown Funk", artist: "Mark Ronson", provider: "deezer", isrc: "GBARL1401524" }),
      track({ id: "qobuz", title: "Uptown Funk", artist: "Bruno Mars", provider: "qobuz", isrc: "GBARL1401524" }),
    ], "Mark Ronson");

    assert.equal(tracks.length, 1);
    assert.equal(tracks[0].artist, "Mark Ronson");
  });

  it("does not let a cover title impersonate an explicitly requested artist", () => {
    const ranked = rankTracks("La Grange ZZ Top", [
      track({ id: "cover", title: "La Grange (ZZ Top)", artist: "Matteo Leonetti" }),
      track({ id: "live", title: "La Grange", artist: "ZZ Top", album: "RAW", duration: 281, releaseYear: 2022 }),
      track({ id: "studio", title: "La Grange", artist: "ZZ Top", album: "Tres Hombres", duration: 230, releaseYear: 1973 }),
    ]);

    assert.equal(ranked[0].artist, "ZZ Top");
    assert.equal(ranked[0].id, "studio");
  });

  it("prefers the canonical album master over a later compilation entry", () => {
    const ranked = rankTracks("Sharp Dressed Man ZZ Top", [
      track({ id: "compilation", title: "Sharp Dressed Man", artist: "ZZ Top", album: "Rancho Texicano: The Very Best of ZZ Top", duration: 253, releaseYear: 2003 }),
      track({ id: "canonical", title: "Sharp Dressed Man", artist: "ZZ Top", album: "Eliminator", duration: 258, releaseYear: 1983 }),
      track({ id: "cover", title: "Sharp Dressed Man (ZZ Top Cover)", artist: "The Roman Screamroad Dream Highway", duration: 247 }),
    ]);

    assert.equal(ranked[0].id, "canonical");
  });

  it("rejects an unrequested live-album recording with an exact title", () => {
    const ranked = rankTracks("Smells Like Teen Spirit Nirvana", [
      track({ id: "live", title: "Smells Like Teen Spirit", artist: "Nirvana", album: "Live In Rio '93", duration: 365, releaseYear: 2022 }),
      track({ id: "studio", title: "Smells Like Teen Spirit", artist: "Nirvana", album: "Nevermind (Deluxe Edition)", duration: 301, releaseYear: 1991 }),
    ]);

    assert.equal(ranked[0].id, "studio");
  });

  it("deduplicates artists and albums", () => {
    const tracks = [
      track({ title: "T1", artist: "A1", album: "Album" }),
      track({ title: "T2", artist: "A1", album: "Album" }),
      track({ title: "T3", artist: "A2" }),
    ];
    const { artists, albums } = extractArtistsAndAlbums(tracks);
    assert.equal(artists.length, 2);
    assert.equal(albums.length, 1);
  });

  it("treats Beyonce as Beyoncé and ranks her studio catalog above karaoke, mixes, and credits", () => {
    const discovery = [
      track({
        id: "karaoke",
        title: "Texas Hold 'Em (Originally Performed by Beyoncé)",
        artist: "The Backing Tracks",
        provider: "qobuz",
        album: "Karaoke Hits",
      }),
      track({
        id: "tabata",
        title: "Texas Hold Em Tabata",
        artist: "Tabata Songs",
        album: "Beyoncé Workout",
      }),
      track({
        id: "credits",
        title: "Texas Hold 'Em",
        artist: "Beyoncé, Beyoncé Knowles, Executive Producer, ComposerLyricist",
        provider: "qobuz",
      }),
      track({
        id: "mix",
        title: "Sex Hero Trey Songs , August alsina, usher type Beyonce",
        artist: "DJ Mix 2024",
        provider: "soundcloud",
      }),
      track({ id: "halo", title: "Halo", artist: "Beyoncé", album: "I Am... Sasha Fierce", isrc: "USSM10804559", releaseYear: 2008 }),
      track({ id: "crazy", title: "Crazy In Love", artist: "Beyoncé", album: "Dangerously In Love", isrc: "USSM10305373", releaseYear: 2003 }),
      track({ id: "texas", title: "Texas Hold 'Em", artist: "Beyoncé", album: "Cowboy Carter", isrc: "USSM12401112", releaseYear: 2024 }),
    ];

    const intent = inferSearchIntent("Beyonce", discovery);
    assert.equal(intent.kind, "artist");
    assert.equal(intent.artist, "Beyoncé");

    const ranked = rankTracks("Beyonce", discovery, intent);
    assert.equal(ranked[0].artist, "Beyoncé");
    assert.ok(["Halo", "Crazy In Love", "Texas Hold 'Em"].includes(ranked[0].title ?? ""));
    assert.ok(ranked.findIndex((item) => item.id === "texas") < ranked.findIndex((item) => item.id === "karaoke"));
    assert.ok(ranked.findIndex((item) => item.id === "halo") < ranked.findIndex((item) => item.id === "mix"));

    const { artists } = extractArtistsAndAlbums(ranked, intent);
    assert.equal(artists[0]?.name, "Beyoncé");
    assert.equal(artists.some((artist) => /backing|tabata|composerlyricist|dj mix/i.test(artist.name)), false);
  });

  it("rejects karaoke impersonators and type-beat mixes from catalog search", () => {
    assert.equal(
      isAllowedVocalTrack(
        track({
          title: "Halo Originally Performed By Beyoncé",
          artist: "The Backing Tracks",
          album: "Karaoke Versions",
        }),
        "Beyonce"
      ),
      false
    );
    assert.equal(
      isAllowedVocalTrack(
        track({
          title: "Sex Hero Trey Songs , August alsina, usher type Beyonce",
          artist: "SoundCloud User",
        }),
        "Beyonce"
      ),
      false
    );
    assert.equal(
      isAllowedVocalTrack(track({ title: "Texas Hold 'Em", artist: "Beyoncé", album: "Cowboy Carter" }), "Beyonce"),
      true
    );
  });
});
