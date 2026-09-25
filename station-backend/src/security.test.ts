import { describe, expect, it } from "vitest";
import { app, dispatch, normalizeRequestedCount } from "./server";

describe("station request security bounds", () => {
  it("clamps excessive model track counts to the server cap", () => {
    expect(normalizeRequestedCount(10_000, 30, 5, 30)).toEqual({ value: 30, clamped: true });
    expect(normalizeRequestedCount(10_000, 20, 5, 20)).toEqual({ value: 20, clamped: true });
  });

  it("rejects non-integer or non-finite requested counts", () => {
    expect(normalizeRequestedCount("30", 30, 5, 30)).toBeNull();
    expect(normalizeRequestedCount(Number.POSITIVE_INFINITY, 30, 5, 30)).toBeNull();
    expect(normalizeRequestedCount(7.5, 30, 5, 30)).toBeNull();
  });

  it("requires a partner token before protected RPC methods execute", async () => {
    const response = await dispatch(
      { jsonrpc: "2.0", method: "station.createStation", params: { seedText: "jazz" }, id: 1 },
      { userId: null, partnerToken: null, firebaseAuthenticated: false, ip: "127.0.0.1", userAgent: "test" }
    );
    expect(response.error?.code).toBe(1003);
  });

  it("validates declared parameter types before invoking handlers", async () => {
    const response = await dispatch(
      { jsonrpc: "2.0", method: "auth.partnerLogin", params: { deviceModel: 42, appVersion: "1.0" }, id: 1 },
      { userId: null, partnerToken: null, firebaseAuthenticated: false, ip: "127.0.0.1", userAgent: "test" }
    );
    expect(response.error?.code).toBe(1002);
  });

  it("rejects oversized JSON-RPC batches", async () => {
    const server = app.listen(0, "127.0.0.1");
    await new Promise<void>(resolve => server.once("listening", resolve));
    try {
      const address = server.address();
      if (!address || typeof address === "string") throw new Error("test_server_address_unavailable");
      const requests = Array.from({ length: 21 }, (_, id) => ({
        jsonrpc: "2.0",
        method: "system.ping",
        id,
      }));
      const response = await fetch(`http://127.0.0.1:${address.port}/jsonrpc`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requests),
      });
      expect(response.status).toBe(400);
    } finally {
      await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    }
  });

  it("rejects batched partner-token issuance", async () => {
    const server = app.listen(0, "127.0.0.1");
    await new Promise<void>(resolve => server.once("listening", resolve));
    try {
      const address = server.address();
      if (!address || typeof address === "string") throw new Error("test_server_address_unavailable");
      const requests = Array.from({ length: 2 }, (_, id) => ({
        jsonrpc: "2.0",
        method: "auth.partnerLogin",
        params: { deviceModel: "test", appVersion: "1.0" },
        id,
      }));
      const response = await fetch(`http://127.0.0.1:${address.port}/jsonrpc`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requests),
      });
      const body = await response.json() as Array<{ error?: { code?: number } }>;
      expect(body).toHaveLength(2);
      expect(body.every(item => item.error?.code === 1007)).toBe(true);
    } finally {
      await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    }
  });
});
