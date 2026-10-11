# Firefly
The vanilla light engine runs on a single thread. Firefly replaces the vanilla server light engine with a multithreaded one, and that's it. No API, except `LightExecutor`, which lets another mod lend its threads to Firefly. Firefly works with or without Leafs, and Leafs shares its chunk threads.
The mods **Moonrise** and **ScalableLux** are incompatible, they also replace the light engine.

## TOC
1. [Overview.md](Overview.md) - The concepts, and how Firefly works, in simple terms.
2. [Methodology.md](Methodology.md) - How to work in the repo, test, measure, debug.
3. [Layout.md](Layout.md) - The folders and their responsibility.
4. [Config.md](Config.md) - What each config option does.
5. [Benchmark.md](Benchmark.md) - The bench results, and where the numbers come from.
