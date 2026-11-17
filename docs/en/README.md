# Firefly
The vanilla light engine runs on a single thread. Firefly replaces the vanilla server light engine with a multithreaded one, and nothing else. Its only API is `LightExecutor`, which lets another mod lend its threads to Firefly. Firefly works with or without Leafs, and Leafs lends its chunk threads.
Moonrise and ScalableLux also replace the light engine, so they are incompatible with Firefly.

## TOC
1. [Overview.md](Overview.md) - How light works, and how Firefly computes it, in simple terms.
2. [Decisions.md](Decisions.md) - The rules Firefly follows, why, and its known limits.
3. [Methodology.md](Methodology.md) - How to work in the repo, test, measure, debug.
4. [Layout.md](Layout.md) - The folders and their responsibility.
5. [Config.md](Config.md) - What each config option does.
6. [Benchmark.md](Benchmark.md) - The bench results, and where the numbers come from.
