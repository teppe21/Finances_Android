# Security policy

## Reporting a vulnerability

Please do not report security problems in a public issue. Use GitHub's private reporting: open the repository's **Security** tab and choose **Report a vulnerability** (private vulnerability reporting must be enabled in the repository settings by the maintainer). Include the app version, Android version, steps to reproduce and the impact. Do not include real financial data.

This is a personal project maintained in spare time; there is no guaranteed response time or bug bounty.

## Scope

The app stores financial data only on the device, has no backend, and talks to the network only for exchange rates and, with your own API key, stock quotes. How the app lock, the PIN verifier and the API-key encryption work, and what they do not protect against, is described in [docs/SECURITY.md](docs/SECURITY.md) and [docs/PRIVACY.md](docs/PRIVACY.md).

Only the latest release receives fixes.
