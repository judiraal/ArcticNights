# Arctic Nights

Arctic Nights makes Minecraft's overworld climate depend on latitude, season, and weather instead of treating every region as the same comfortable center. It can lengthen or shorten days by z-coordinate, align the sun path, band worldgen temperature by latitude, and act as an outdoor climate authority for snow, ice, Weather2 visuals, and Cold Sweat WORLD temperature.

The mod is built for packs that want distance and climate to matter. In Immersive Wilderness it helps make warm starting regions, deep winter, exposed travel, storms, and polar expeditions feel like world rules rather than quest gates.

## Main Features

- Latitude-based day/night length and sun position.
- Optional latitude-banded biome temperature during world generation.
- Runtime outdoor climate authority for precipitation, snow and ice behavior, Weather2 visuals, and Cold Sweat outdoor WORLD temperature.
- Optional tag-driven spawn ecology for seasonal and climate-sensitive mob pressure.
- Optional structure-denial belt for packs that want civilization to be absent from specific latitude ranges.
- Optional snow accumulation, full-block snow melt, seasonal vegetation regrowth, and tornado block-grab adjustments.
- Config-gated maintenance commands and diagnostics for pack authors.

## Compatibility

Arctic Nights is a NeoForge mod for Minecraft 1.21.1. It has optional integrations for Serene Seasons, Cold Sweat, Weather2, and Iris. Without those mods, the relevant integrations are skipped.

The default config is conservative: the climate authority, spawn ecology, structure denial, maintenance commands, diagnostics, and tick-rate climate catch-up are disabled until a pack enables them deliberately.

## Pack Author Notes

For public packs, keep maintenance commands and diagnostics disabled unless you are actively investigating a world. Tick-rate climate catch-up can make loaded chunks repay missed snow and ice exposure faster by temporarily increasing the random tick speed passed into normal vanilla chunk ticking; tune it with profiling rather than treating the maximum budget as a universal default.
