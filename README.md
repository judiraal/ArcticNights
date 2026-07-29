# Arctic Nights

Arctic Nights makes Minecraft's overworld climate depend on latitude, season, and weather instead of treating every region as the same comfortable center. It can lengthen or shorten days by z-coordinate, align the sun path, band worldgen temperature by latitude, and act as an outdoor climate authority for snow, ice, Weather2 visuals, and Cold Sweat WORLD temperature.

The mod is built for packs that want distance and climate to matter. In Immersive Wilderness it helps make warm starting regions, deep winter, exposed travel, storms, and polar expeditions feel like world rules rather than quest gates.

## Main Features

- Latitude-based day/night length and sun position.
- Optional latitude-banded biome temperature during world generation.
- Runtime outdoor climate authority for precipitation, snow and ice behavior, Weather2 visuals, and Cold Sweat outdoor WORLD temperature.
- Datapack-extensible cave temperature profiles that add cold, cool, warm, or hot ambient offsets without changing shared biome tags.
- Optional tag-driven spawn ecology for seasonal and climate-sensitive mob pressure.
- Optional structure-denial belt for packs that want civilization to be absent from specific latitude ranges.
- Optional snow accumulation, full-block snow melt, seasonal vegetation regrowth, and tornado block-grab adjustments.
- Optional world-age and season policy for naturally rolled Weather2 land tornadoes, without suppressing ordinary storms, hail, or operator test summons.
- Config-gated maintenance commands and diagnostics for pack authors.

## Compatibility

Arctic Nights is a NeoForge mod for Minecraft 1.21.1. It has optional integrations for Serene Seasons, Cold Sweat, Weather2, and Iris. Without those mods, the relevant integrations are skipped.

The default config is conservative: the climate authority, spawn ecology, structure denial, maintenance commands, diagnostics, and tick-rate climate catch-up are disabled until a pack enables them deliberately.

## Pack Author Notes

For public packs, keep maintenance commands and diagnostics disabled unless you are actively investigating a world. Tick-rate climate catch-up can make loaded chunks repay missed snow and ice exposure faster by temporarily increasing the random tick speed passed into normal vanilla chunk ticking; tune it with profiling rather than treating the maximum budget as a universal default.

The built-in cave profiles use `#arcticnights:cave_temperature/cold` (-10 C), `cool` (-3 C), `mild_cool` (-2 C), `warm` (+8 C), and `hot` (+12 C). Their offsets are added to the ordinary biome contribution, so latitude and season remain authoritative. Pack datapacks may append cave biomes to these tags; a biome should belong to at most one profile.

When spawn ecology is enabled, entities in `#arcticnights:upper_cave_require_cold` (the vanilla zombie family by default) cannot spawn naturally at enclosed positions from Y32 through Y64 unless the local spawn temperature is freezing or the biome has the dedicated cold cave profile. Open-sky surface positions, caves below Y32, and non-natural structure or spawner encounters retain their existing behavior.
