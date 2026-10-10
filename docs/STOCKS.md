# Stock watchlist and market data

The Stocks feature is a read-only watchlist. It does not trade, connect to a broker, track holdings, value a portfolio or give recommendations, and it never changes accounts, transactions or balances.

## Provider

Quotes come from the [Finnhub](https://finnhub.io) REST API. It was chosen because it has documented endpoints for symbol search, quotes, company profiles, US market status and candles, and a free plan that only needs an API key. Alternatives considered:

- Twelve Data: good history endpoints, but its free tier is described as for evaluation and internal, non-display use.
- Alpha Vantage: a free key allows only about 25 requests per day, too few for a refreshing watchlist.
- Scraped or undocumented sources (for example Yahoo endpoints): not acceptable.

What was found about Finnhub's free plan when this was written (October 2026; check finnhub.io/pricing for the current terms before relying on any of it):

- An API key is required; the free key is meant for personal, non-commercial use, and redistribution of the data needs a paid plan.
- About 60 requests per minute; exceeding it returns HTTP 429.
- `quote`, `search`, `stock/profile2` and `stock/market-status` are available on the free plan; quotes for US stocks are described as real-time.
- Access to `stock/candle` (price history) on the free plan has changed over time and several users report HTTP 403 for US symbols. The app treats 403 as "not included in your plan" and shows that instead of a chart.
- Coverage of non-US exchanges depends on the plan.

Because the terms tie the key to its owner, the app does not ship a developer key. Each user enters their own key on the "Market data" screen.

## Endpoints used

| Purpose | Endpoint | When |
| --- | --- | --- |
| Validate key | `GET /stock/market-status?exchange=US` | when saving a key |
| Search | `GET /search?q=` | while typing, debounced 450 ms |
| Add to watchlist | `GET /stock/profile2?symbol=` | once per added symbol (name, exchange, currency) |
| Quotes | `GET /quote?symbol=` | one call per symbol per refresh |
| Session | `GET /stock/market-status?exchange=US` | once per refresh when the list has US symbols |
| History | `GET /stock/candle?symbol=&resolution=&from=&to=` | when a range is selected on the detail screen (cached in memory) |

The key is sent in the `X-Finnhub-Token` header, never in the URL. Resolutions: 1D uses 5-minute candles, 1W 30-minute candles, 1M/3M/1Y daily candles.

## Freshness labels

Labels come from the provider timestamp `t` of each quote, never from when the app fetched it:

| Label | Condition |
| --- | --- |
| Real-time | US session open and the price is at most 2 minutes old |
| Delayed | US session open and the price is 2 to 30 minutes old; or any price under 30 minutes old when the session state is unknown (non-US symbols) |
| Last close | US session closed and the price is at most 4 days old |
| Stale | anything older |
| Unavailable / API key required / API key rejected / Rate limited / Not included in plan | no quote, or the matching provider error |

A delayed feed therefore shows "Delayed" by construction. The app polls snapshots; it is not a tick-by-tick stream.

## Refresh behaviour

- Polling runs only while the Stocks screen is in the STARTED state (`repeatOnLifecycle`); leaving the screen or the app stops it. There is no background worker.
- While the US market is open: one cycle per minute (one request per symbol plus one market-status request). The watchlist is capped at 25 symbols to stay under 60 requests per minute.
- While the market is closed: no automatic polling; manual refresh still works.
- Unknown session (only non-US symbols): every 5 minutes.
- Errors back off exponentially up to 15 minutes. HTTP 429 pauses for the `Retry-After` value or at least one minute and stops the current cycle. Missing or rejected keys stop polling.

## Storage

- `watchlist` table: symbol (primary key, so duplicates are impossible), display symbol, name, exchange, trading currency, position, time added.
- `stock_quotes` table: the last quote per symbol with prices stored as decimal text, the provider timestamp, fetch time and provider name.
- History is held in memory only.

Deleting financial data keeps the watchlist; "Reset app completely" removes it. Removing a symbol deletes its cached quote.

## Data sent to Finnhub

The API key, watchlist symbols, search text and the device's IP address. No transactions, balances, merchants or other app data.

## Manual setup

1. Create an account at https://finnhub.io/register and copy the API key from the dashboard.
2. In the app: More > Stocks > Set up API key (or Settings > Market data), paste the key, tap "Check and save".
3. The key is encrypted with an Android Keystore AES-GCM key and stored in the app's preferences. "Remove saved key" deletes it.

## Tests

`StocksTest` covers quote, search, profile, candle and market-status parsing (including empty, malformed, null and zero responses), HTTP 401/403/429/5xx, timeouts and offline errors, the header-only key, freshness labels, refresh intervals, add/remove/reorder, duplicate prevention, the size cap, persistence across repository instances, missing-key behaviour, rate-limit handling, stale cached quotes and isolation from finance data. All use synthetic responses; no real key is involved.
