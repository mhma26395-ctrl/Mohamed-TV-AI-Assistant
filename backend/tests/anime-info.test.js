import test from "node:test";
import assert from "node:assert/strict";
import { getAnimeInfo } from "../worker.js";

test("anime search returns normalized safe metadata and source timestamp", async () => {
  let requested = "";
  const fetcher = async url => {
    requested = String(url);
    return new Response(JSON.stringify({ data: [{
      mal_id: 1, title: "Example Anime", title_english: "Example",
      type: "TV", year: 2024, episodes: 12, score: 8.4,
      synopsis: "A short synopsis.", images: { jpg: { image_url: "https://img.example/a.jpg" } },
      url: "https://myanimelist.net/anime/1", genres: [{ name: "Drama" }]
    }] }), { status: 200, headers: { "content-type": "application/json" } });
  };
  const result = await getAnimeInfo("example", "ar-EG", fetcher);
  assert.match(requested, /q=example/);
  assert.match(requested, /sfw=true/);
  assert.equal(result.ok, true);
  assert.equal(result.type, "anime");
  assert.equal(result.anime[0].title, "Example Anime");
  assert.equal(result.anime[0].score, 8.4);
  assert.equal(result.anime[0].genres[0], "Drama");
  assert.ok(result.fetchedAt);
});

test("anime discovery uses popular endpoint when query is empty", async () => {
  let requested = "";
  const result = await getAnimeInfo("", "ar-EG", async url => {
    requested = String(url);
    return new Response(JSON.stringify({ data: [] }), { status: 200 });
  });
  assert.match(requested, /top\/anime/);
  assert.equal(result.count, 0);
});

test("anime provider rate limit is reported explicitly", async () => {
  await assert.rejects(
    () => getAnimeInfo("test", "en", async () => new Response("limited", { status: 429 })),
    /anime_provider_rate_limited/
  );
});
