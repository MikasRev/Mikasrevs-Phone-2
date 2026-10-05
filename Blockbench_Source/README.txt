Modern MPhone Blockbench sources

Modern_MPhone_CLOSED.bbmodel : exact closed/off-screen geometry
Modern_MPhone_OPEN.bbmodel   : exact open/on-screen geometry

The Minecraft runtime open/close animation is intentionally implemented with vanilla item-model predicate frames (z_phone_boot_1..5). This keeps the mod compatible with the existing Forge 1.20.1 item system and does not require GeckoLib or a new renderer.
