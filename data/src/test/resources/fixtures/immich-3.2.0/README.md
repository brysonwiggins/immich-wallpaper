# Immich 3.2.0 fixtures (owner's server, 2026-10-10)

Captured from a real server and scrubbed: file names, paths, people/album names, emails, GPS and place
names are replaced, and every UUID is replaced by a consistent fake ID (`00000000-0000-4000-8000-…`).

Known quirk: this capture predates the scrubber fix that leaves booleans alone, so in
`server-features.json` the boolean flags `ocr` and `email` show as `"REDACTED"`.

Used by `:data` ImmichClient tests.

`search-metadata.json` is **synthesized**, not captured. It takes the first five items of
`search-random.json` and wraps them in the documented `POST /search/metadata` response shape:
`{albums, assets: {total, count, items, facets, nextPage}}`.
