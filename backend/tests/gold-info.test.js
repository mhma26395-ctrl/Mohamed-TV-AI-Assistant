import test from "node:test";
import assert from "node:assert/strict";
import { getGoldInfo } from "../worker.js";

test("gold price falls back to independent EGP-per-gram source when primary provider fails", async () => {
  const calls = [];
  const fetcher = async url => {
    calls.push(String(url));
    if (String(url).includes("gold-api.com")) return new Response("unavailable", { status: 503 });
    return new Response(JSON.stringify({
      xau: { price: 5000, currency: "EGP", unit: "gram" },
      data_state: { status: "fresh", age_seconds: 20 },
      price_as_of: "2026-11-10T12:00:00Z",
      source: "xaus.com"
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  const result = await getGoldInfo("EGP", fetcher);
  assert.equal(result.ok, true);
  assert.equal(result.currency, "EGP");
  assert.equal(result.pricePerGram["24K"], 5000);
  assert.equal(result.pricePerGram["21K"], 4375);
  assert.equal(result.pricePerGram["18K"], 3750);
  assert.equal(result.source, "XAUS spot gold + currency conversion");
  assert.equal(result.stale, false);
  assert.equal(calls.length, 2);
});

test("gold fallback marks stale quotes and rejects excessively old cached prices", async () => {
  const staleFetcher = async url => String(url).includes("gold-api.com")
    ? new Response("unavailable", { status: 503 })
    : new Response(JSON.stringify({
        xau: { price: 5000 }, data_state: { status: "stale", age_seconds: 120 }
      }), { status: 200 });
  const stale = await getGoldInfo("EGP", staleFetcher);
  assert.equal(stale.ok, true);
  assert.equal(stale.stale, true);

  const oldFetcher = async url => String(url).includes("gold-api.com")
    ? new Response("unavailable", { status: 503 })
    : new Response(JSON.stringify({
        xau: { price: 5000 }, data_state: { status: "stale", age_seconds: 30000 }
      }), { status: 200 });
  await assert.rejects(() => getGoldInfo("EGP", oldFetcher), /gold_providers_unavailable/);
});

test("gold fallback never invents a quote when both providers fail", async () => {
  const fetcher = async () => new Response("unavailable", { status: 503 });
  await assert.rejects(() => getGoldInfo("EGP", fetcher), /gold_providers_unavailable/);
});
