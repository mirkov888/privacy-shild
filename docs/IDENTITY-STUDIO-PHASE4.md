# Identity Studio — Phase 4: Local Profile Manager
## Implemented
- Save custom browser User-Agent profiles under the currently selected platform.
- Favorite built-in and custom profiles; favorites sort first in platform lists.
- Delete custom profiles without modifying built-in catalogue data.
- Export custom profiles as JSON and import validated JSON from the clipboard.
- Imported profiles receive new local IDs; built-in profiles cannot be overwritten.
- Validation: name 2–80 characters; User-Agent 40–512 characters, begins with Mozilla/5.0, and rejects line breaks.
- Import is capped at 256 KB and 100 profiles per operation.
## Privacy
- Profiles stay in app-private SharedPreferences.
- Export contains custom profiles and favorite IDs only, not device IDs, serials, IMEI, MAC addresses or attestation data.
- Deleting a custom profile clears its favorite state and selected-profile reference.
