import test from "node:test";
import assert from "node:assert/strict";
import { getSportsLeadersInfo } from "../worker.js";

const slugs = { "egyptian-premier-league":"egyptian-premier-league", "saudi-pro-league":"saudi-professional-league", "english-premier-league":"english-premier-league", "spanish-la-liga":"spanish-la-liga" };
test("LIVE SportScore API: standings, scorers and assists for all supported leagues", { timeout: 120000 }, async t => {
  for (const [league, slug] of Object.entries(slugs)) {
    await t.test(league, async () => {
      const base = "https://sportscore.com/api/widget/";
      const query = "?sport=football&slug=" + encodeURIComponent(slug) + "&src=mohamed-tv-ai-assistant";
      for (const [stat, endpoint] of [["standings","standings"],["scorers","topscorers"],["assists","topscorers"]]) {
        const url = base + endpoint + "/" + query + (stat === "standings" ? "" : "&limit=20&stat=" + (stat === "assists" ? "assists" : "goals"));
        const response = await fetch(url, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(15000) });
        const body = await response.text();
        console.log("SPORTS_LIVE", league, stat, "HTTP", response.status, body.slice(0, 2200));
        // The public provider may temporarily rate-limit CI. Do not fail an APK build
        // because of an upstream 429; deterministic mocked tests cover parsing/routing.
        if (response.status === 429) {
          t.skip("SportScore temporarily rate-limited the live CI request (HTTP 429)");
          return;
        }
        assert.equal(response.ok, true, stat + " endpoint must return HTTP 2xx");
        const parsed = await getSportsLeadersInfo(league, stat);
        assert.equal(parsed.ok, true);
        assert.ok(parsed.rows.length > 0, stat + " returned no rows");
        assert.ok(parsed.rows.some(row => stat === "standings" ? row.team && row.team !== "غير معروف" : row.player && row.player !== "غير معروف"), stat + " names missing");
      }
    });
  }
});
