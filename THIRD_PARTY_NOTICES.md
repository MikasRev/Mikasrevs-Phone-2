# Third-party notices

This document identifies upstream components; it does not replace their licenses or grant rights to their names, logos or trademarks.

## Bundled fonts

The UI and Chinese fonts are derivatives of Noto Sans and Noto Sans CJK, distributed under **SIL Open Font License 1.1**. Their renamed family names, upstream revisions, generation details and copyright notices are recorded in:

- [Font NOTICE](src/main/resources/assets/newphone/font/NOTICE.txt)
- [Noto Sans license](src/main/resources/assets/newphone/font/OFL-NotoSans.txt)
- [Noto Sans CJK license](src/main/resources/assets/newphone/font/OFL-NotoSansCJK.txt)

Keep these notices and licenses with source and binary redistributions. The project's own license does not replace them.

## Build scaffolding

This project began from the Minecraft Forge MDK. The original MDK license and credits have been preserved:

- [Forge MDK license and upstream notices](licenses/Forge-MDK-LICENSE.txt)
- [Forge MDK credits](licenses/Forge-MDK-CREDITS.txt)

The **Gradle wrapper** is upstream Gradle code under Apache License 2.0. See [Gradle's license](https://github.com/gradle/gradle/blob/v8.8.0/LICENSE) and the notices in `gradlew` and `gradlew.bat`. A copy is included in [licenses/Gradle-Apache-2.0.txt](licenses/Gradle-Apache-2.0.txt).

Minecraft, its official mappings, Forge and the downloaded build dependencies retain their upstream terms. Source publication does not redistribute a Minecraft installation or game worlds.

## Separate optional mods

MCEF, Simple Voice Chat, Lightman's Currency and Kuro's Economy are separate projects. Their JARs are resolved from their upstream repositories during development and are not embedded in the MRPhone JAR. `build/dependencies/` may contain a separate official MCEF JAR for local distribution convenience; it is excluded from the source repository. Follow the respective upstream licenses when redistributing those mods.

## Project assets

Editable phone models and asset-generation sources are included. Font notices remain separate as described above. Locally supplied device reference photos under `art/iphone_duo/references/`, duplicate archives and editor backup files are excluded from the publication package. When adding images, sounds or models, record third-party origins and permissions rather than assuming the code license covers them.
