# MikasRevs Phone 2 (MRPhone / MPhone)

A smartphone mod for **Minecraft 1.20.1 · Forge · Java 17**. Current version: **0.9.4 beta**.

[Türkçe](README_TR.md) · [Contributing](CONTRIBUTING.md) · [Changelog](CHANGELOG.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

## Features

- Player phone numbers, contacts, direct and group messages, notifications and call history.
- Voice and video calls, camera, gallery and social applications with server moderation.
- Marketplace listings and item trades with confirmations, inventory delivery and private trade chat.
- Bank integration with Lightman's Currency or Kuro's Economy, using their existing accounts without phone transfer fees.
- PIN lock, Do Not Disturb, wallpapers and a choice of real-world or Minecraft time.
- Six phone appearances, matching cases, call animations and eleven languages, including Simplified and Traditional Chinese.

The experimental **iPhone Duo** is hidden from Creative inventory in this release. Its source remains available for development; it shares MPhone's accounts and server systems.

## Installation

Install the release mod JAR on the Forge client and server. Use the same MRPhone version on both sides. Do not install this project's source ZIP as a mod.

| Optional integration | Development version | Purpose |
| --- | --- | --- |
| [MCEF](https://modrinth.com/mod/mcef) | Forge 2.1.6 for 1.20.1 | YouTube browser; client side |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Forge 1.20.1-2.6.20 | Microphone audio for calls; configure it on client and server |
| [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) | 1.20.1-2.3.0.5 | Existing bank accounts |
| [Kuro's Economy](https://modrinth.com/mod/kuros-economy) | Forge 3.2.6 for 1.20.1 | Existing bank balances |

Integrations are separate mods, not embedded in MRPhone. With both economy mods installed, the phone selects Lightman's Currency. Without either, the other phone features still work.

MCEF downloads native Chromium libraries on first launch. Let the download finish. A missing `mcef-libraries/windows_amd64/d3dcompiler_47.dll` indicates that the native installation is incomplete or unavailable; reinstall the native libraries using MCEF's official downloader. Do not download individual DLLs from unrelated sites.

## Build from source

Install a **64-bit JDK 17**, then open a terminal in the project directory. The included Gradle 8.8 wrapper downloads the build tools and dependencies; no separate Gradle installation is needed. The first build needs internet access. Python, Blender and Node.js are not required for the normal build because generated assets are included.

Windows PowerShell:

```powershell
./gradlew.bat build --console=plain
```

Linux/macOS:

```sh
sh ./gradlew build --console=plain
```

Output: `build/libs/MRPhone 2 0.9.4 beta.jar`. `assemble` also copies the separate official MCEF mod into `build/dependencies/`; it does not embed Chromium libraries. Test sources are excluded from the release JAR.

Import `build.gradle` as a Gradle project in IntelliJ IDEA, select JDK 17, then run `./gradlew.bat genIntellijRuns` if you need IDE launch configurations.

## Development and verification

```powershell
./gradlew.bat runClient
./gradlew.bat check
./gradlew.bat runGameTestServer -PphoneEconomy=none --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=lightmans --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=kuros --console=plain
./gradlew.bat runGameTestServer -PphoneEconomy=both --console=plain
```

`build` includes `verifyPhoneRegression`: persistence, packet boundaries, trade recovery, bank transactions, device layouts, models, animations, media and translation checks. Forge GameTests additionally exercise the server integrations. GitHub Actions runs the build and all four economy configurations. Hardware microphone quality, live video and external YouTube playback still need two real clients.

## Source layout and storage

| Directory | Role |
| --- | --- |
| `src/main/java/com/mikasrevs/newphone/client` | Screens, fonts, rendering and device interaction |
| `src/main/java/com/mikasrevs/newphone/server` | Shared services, accounts and world persistence |
| `src/main/java/com/mikasrevs/newphone/network` | Client/server synchronization and request validation |
| `src/main/java/com/mikasrevs/newphone/integration` | Voice chat and economy adapters |
| `src/main/java/com/mikasrevs/newphone/marketplace` | Listings, trade custody and recovery |
| `src/main/resources` | Shipped translations, models, textures, fonts and sounds |
| `src/test` | Regression checks, Forge GameTests and opt-in previews |
| `phone_models`, `art`, `Blockbench_Source`, `tools` | Editable assets and generation tools |

Phone data belongs to the player and world, not the device's appearance. Server data uses Minecraft world storage and recovery journals. This version requires no external MySQL server or REST service. Back up the **whole world**, including player data, phone journals and the selected economy mod's data.

Read [Contributing](CONTRIBUTING.md) for development guidance. Local `build/` output and test evidence are not included in source control.

## Licensing

This project is **source-available**, under the custom [MRPhone Source-Available License 1.0](LICENSE.txt). You may inspect, build and modify the code for private use. Publishing or redistributing the original mod, modified builds or project code requires the copyright holder's prior written permission, including uploads to CurseForge, Modrinth and other hosting services.

GitHub source forks and contributions remain subject to GitHub's public-repository terms. A source fork is not permission to publish an unofficial mod release. Modpacks may reference the official project and obtain its files through authorized platform features; independently hosting or bundling the mod requires permission.

This is not an OSI-approved open-source license. Bundled fonts and upstream components retain their separate licenses; see [third-party notices](THIRD_PARTY_NOTICES.md). Read the full project license before distributing a build.
