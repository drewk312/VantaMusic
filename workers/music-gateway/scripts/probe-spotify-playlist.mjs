import { importSpotifyPlaylistExtension } from "./src/providers/spotify-extension.ts";
import { importSpotifyPlaylist } from "./src/providers/spotify-web.ts";

const id = "37i9dQZF1DXcBWIGoYBM5M";
const ext = await importSpotifyPlaylistExtension(id, 5);
console.log("extension", { name: ext.name, n: ext.tracks.length, first: ext.tracks[0]?.title });

const viaWeb = await importSpotifyPlaylist(id, {} as never, 5);
console.log("viaWeb", { n: viaWeb.length, first: viaWeb[0]?.title });
