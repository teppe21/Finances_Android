# Security

## App lock

- **PIN**: 4 to 8 digits. All-same digits and simple runs (1234, 9876) are refused. There is no default PIN.
- **Verifier**: PBKDF2-HMAC-SHA256, 120,000 iterations, 16-byte random salt, 256-bit output. The output is then HMAC-SHA256'd with a non-exportable Android Keystore key, so a copy of the preferences file cannot be brute-forced off the device. Stored as `pbkdf2-sha256$<iterations>$<salt>$<hash>$k`. If the Keystore is unusable the verifier is stored unkeyed (`$n`).
- **Migration**: 2.0.0 stored `SHA-256(salt:pin)`. That hash is still accepted once and replaced with the new verifier on the next successful unlock.
- **Rate limiting**: after five wrong PINs, a lockout of 30 seconds that doubles with each further failure up to 15 minutes. The counter and lockout time are persisted, so restarting the app does not reset them.
- **Biometrics**: optional, through `BiometricPrompt` with `BIOMETRIC_WEAK`; the PIN is the fallback. Biometric unlock is a gate only; it is not bound to a cryptographic key. A lock created by 2.0.0 in biometric-only mode stays locked and falls back to the device screen lock until a PIN is set.
- **Lifecycle**: the unlocked state is held in the `Application`. Rotation keeps it, process death resets it. Leaving the app locks immediately, except for activities the app starts itself (document picker, gallery, notification settings, browser), which may take up to five minutes. Nothing is drawn until preferences are loaded, so financial screens never flash before the gate.
- **Screen capture**: `FLAG_SECURE` is set while the lock is enabled.
- **Forgot PIN**: the only recovery is a full reset, which erases all data.

What this does not protect against: someone who knows or observes the PIN, a rooted device, or malware with accessibility access. Room data is not encrypted at rest beyond Android's file-based encryption.

## Secrets

- The Finnhub key is encrypted with an AES-256-GCM Keystore key before being written to DataStore and is excluded from backup. It is only sent in a request header to Finnhub and never logged or put in a URL.
- No API keys, keystores or passwords are in the repository. Release signing reads environment variables or a git-ignored `keystore.properties`.
- Keystore keys may or may not be hardware-backed depending on the device; the app does not depend on it.

## Notification data

Raw notification text exists only in memory during processing. It is not written to the database (the v3→v4 and v4→v5 migrations removed text stored by older versions), not logged and not sent anywhere.

## Reporting

Report security issues privately to the repository owner rather than in a public issue.
