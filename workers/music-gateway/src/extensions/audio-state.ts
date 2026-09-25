import type { Env } from "../types";
import { handleExtensionAudio } from "./audio-proxy";

/** Streaming transforms need the Durable Object CPU budget, not the short edge request budget.
 * Each HTTP request gets its own instance; no audio or credentials are persisted.
 */
export class ExtensionAudio {
  constructor(_state: DurableObjectState, private readonly env: Env) {}
  fetch(request: Request): Promise<Response> {
    return handleExtensionAudio(request, this.env);
  }
}

export function routeExtensionAudio(request: Request, env: Env): Promise<Response> {
  if (!env.EXTENSION_AUDIO) return Promise.resolve(new Response("Audio processing unavailable", { status: 503 }));
  const stub = env.EXTENSION_AUDIO.get(env.EXTENSION_AUDIO.newUniqueId());
  // Forward the original request including byte ranges and cancellation. Return the
  // response body untouched: the edge must not execute the decryption transform.
  return stub.fetch(request);
}
