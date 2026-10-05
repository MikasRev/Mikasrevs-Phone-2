# Contributing

Use Java 17 and the included Gradle wrapper. Read [README.md](README.md) for development commands and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) before adding assets or dependencies.

This is a source-available project. Source forks and pull requests for contributions are supported under the project license and GitHub's terms. Publishing an original or modified mod release requires the copyright holder's prior written permission. Keep the project license and third-party notices in your fork. Contributions must be submitted under the repository's applicable license, and you must hold the necessary rights to them.

## Changes

1. Open an issue describing the problem or proposed feature, then work on a branch or fork.
2. Keep changes focused. Reuse shared phone accounts, server services and request validation when adding a device or screen.
3. Validate requests on the server. UI checks alone cannot protect items, money, permissions or player-owned media.
4. Add meaningful regression coverage when changing persistence, network formats, trades, payments or permissions. Ordinary wording and visual tweaks do not need implementation-mirroring tests.
5. Run `./gradlew.bat build --console=plain` (or `sh ./gradlew build --console=plain`). For bank/trade/server changes, also run the four `runGameTestServer -PphoneEconomy=...` configurations in the README. Test affected screens in the client.
6. Describe the behavior change and validation in the pull request. Mention any save migration or client/server compatibility change.

## Translations and assets

Translations live in `src/main/resources/assets/newphone/lang/`. Keep all eleven languages complete and preserve formatting placeholders. Optional translation audit tooling is in `tools/audit_phone_translations.cjs`; `check` also verifies translation coverage. CJK and UI fonts have their own SIL Open Font Licenses and source manifests beside the font files.

Commit the editable asset source and its generated runtime resource together. Keep `.obj`, `.mtl`, `.blend` and `.bbmodel` models; exclude editor backups and personal reference photos. Document the origin and license of new third-party assets. Asset-generation tools may require Blender, Python/fontTools or Node.js; those are not normal build dependencies.

Do not commit game worlds, logs, crash reports, `.env` files, private keys or downloaded native libraries. Use a sanitized excerpt in an issue when needed. Test preview worlds and logs remain under ignored `build/` directories.
