import test from "node:test";
import assert from "node:assert/strict";
import { getCurrencyInfo, getSportsInfo } from "../worker.js";

test("currency lookup uses requested pair and returns dated reference rate", async () => {
  let requestedUrl = "";
  const fetcher = async url => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({ date: "2026-11-11", rates: { EGP: 49.75 } }), {
      status: 200, headers: { "content-type": "application/json" }
    });
  };
  const result = await getCurrencyInfo("usd", "egp", fetcher);
  assert.equal(requestedUrl, "https://api.frankfurter.app/latest?from=USD&to=EGP");
  assert.equal(result.ok, true);
  assert.equal(result.rate, 49.75);
  assert.equal(result.date, "2026-11-11");
  assert.match(result.note, /Reference rate/);
});

test("currency lookup rejects invalid codes without calling provider", async () => {
  let called = false;
  const result = await getCurrencyInfo("USDD", "EGP", async () => { called = true; });
  assert.deepEqual(result, { ok: false, error: "invalid_currency" });
  assert.equal(called, false);
});

test("currency lookup never invents a missing currency pair", async () => {
  const result = await getCurrencyInfo("USD", "EGP", async () => new Response(JSON.stringify({ rates: {} }), { status: 200 }));
  assert.equal(result.ok, false);
  assert.equal(result.error, "currency_pair_unavailable");
});

test("sports lookup maps the Egyptian league, preserves requested date and parses matches", async () => {
  let requestedUrl = "";
  const fetcher = async url => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({ events: [{
      id: "match-1", date: "2026-11-12T17:00Z",
      competitions: [{
        competitors: [
          { homeAway: "home", team: { displayName: "Al Ahly" }, score: "2" },
          { homeAway: "away", team: { displayName: "Zamalek" }, score: "1" }
        ],
        status: { type: { detail: "Full Time", state: "post" } }, venue: { fullName: "Cairo Stadium" }
      }]
    }] }), { status: 200, headers: { "content-type": "application/json" } });
  };
  const result = await getSportsInfo("egyptian-premier-league", "20261112", "ar", fetcher);
  assert.equal(requestedUrl, "https://site.api.espn.com/apis/site/v2/sports/soccer/egy.1/scoreboard?dates=20261112");
  assert.equal(result.ok, true);
  assert.equal(result.leagueCode, "egy.1");
  assert.equal(result.count, 1);
  assert.equal(result.matches[0].homeTeam, "Al Ahly");
  assert.equal(result.matches[0].awayScore, "1");
  assert.equal(result.matches[0].status, "Full Time");
});

test("sports lookup rejects unknown leagues without calling provider", async () => {
  let called = false;
  const result = await getSportsInfo("unknown-league", "20261112", "ar", async () => { called = true; });
  assert.deepEqual(result, { ok: false, error: "unsupported_league" });
  assert.equal(called, false);
});

test("sports lookup reports provider HTTP failures instead of returning fabricated fixtures", async () => {
  await assert.rejects(
    () => getSportsInfo("english-premier-league", "20261112", "ar", async () => new Response("unavailable", { status: 503 })),
    /sports_provider_http_503/
  );
});

test("sports lookup builds a valid Cairo-local date when none is supplied", async () => {
  let requestedUrl = "";
  await getSportsInfo("saudi-pro-league", null, "ar", async url => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({ events: [] }), { status: 200 });
  });
  const match = requestedUrl.match(/scoreboard\?dates=(\d{8})$/);
  assert.ok(match, "expected ESPN date parameter in YYYYMMDD format");
  assert.equal(match[1].length, 8);
});
