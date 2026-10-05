# App icon sources

The full-size PNG files in this directory are the editable icon sources. Runtime textures are 64 × 64 RGBA files in `src/main/resources/assets/newphone/textures/gui/phone/apps/`.

To resize an updated Marketplace icon, run from the project root:

```powershell
./tools/resize_phone_app_icon.ps1 -SourcePath ./art/app_icons/marketplace_source.png -DestinationPath ./src/main/resources/assets/newphone/textures/gui/phone/apps/marketplace.png
```

Preserve transparency when editing source images. Commit the source PNG and its runtime texture together.
