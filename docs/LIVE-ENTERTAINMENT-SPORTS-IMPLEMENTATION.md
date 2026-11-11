# Live entertainment and sports lookup — implementation plan

This branch adds a provider-backed foundation for the requested entertainment and football features without changing the production branch.

## Provider choices

- TMDB for film and television search/discovery/trending. Configure the API Read Access Token as the encrypted Worker secret `TMDB_ACCESS_TOKEN`. Do not put it in the APK or repository. TMDB's developer API requires attribution and its published FAQ says commercial use needs a separate license; the project owner must verify that use is permitted before public/commercial distribution.
- Jikan v4 for anime discovery/search. It is a public, unofficial MyAnimeList data API. Respect its rate limits and expose source/freshness information.
- API-Football for supported leagues and cups, including CAF competitions where coverage exists for the requested season. Configure `API_FOOTBALL_KEY` as an encrypted Worker secret. Use the provider's leagues endpoint to verify coverage and current season; never hard-code unsupported tournament IDs.

## Required user-facing behavior

- All data comes from provider responses, not model memory.
- Return source, provider timestamp (or retrieval time), locale, and coverage metadata.
- An empty fixture list must be phrased as "the provider returned no fixtures" unless league coverage is verified for the selected season.
- Never call an unavailable/cached result live. Distinguish live, scheduled, finished, postponed, and cancelled states.
- Timezone defaults to Africa/Cairo unless the user selects another IANA timezone.
- Do not expose provider keys to the TV client.

## API contract extension

POST /v1/info types:
- `entertainment`: `category` = movie | tv | anime | all; optional `query`, `genre`, `language`, `year`, `sort`.
- `sports`: allowlisted `league` or `team`, `date` (YYYY-MM-DD), `timezone` (IANA).
- `sports_catalog`: discover supported leagues/cups by country/name and surface verified coverage.

## Before production release

1. Add Cloudflare encrypted secrets: `TMDB_ACCESS_TOKEN`, `API_FOOTBALL_KEY`.
2. Verify the project's licensing/attribution obligations and the availability of required competitions/seasons on the selected subscription plan.
3. Add tests with mocked providers for valid results, empty results, stale/offline behavior, invalid inputs, provider errors, rate limits, pagination, and Arabic/English queries.
4. Update TV intent parsing and structured response UI; do not rely on the LLM to classify a question that must use live data.
5. Deploy to staging and verify through authenticated device sessions.
6. Run backend tests, Android build/lint, and physical TV/remote validation before a release APK.
