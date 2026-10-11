# Methodology
How to carry on this project. These rules have proven themselves.

## The absolute rule
Firefly gives the same result as vanilla, with a different process. Vanilla computes light in a precise order, on a single thread. Firefly computes it in any order, on several threads, and every block ends with the same light.
A player, a mod or the save sees no difference.

> A light that flickers, is wrong or is missing is a bug.

## Compatibility
A mod almost never talks to the light engine. It gives its block a level from 0 to 15, and the engine does the rest. Firefly still keeps every public function of the vanilla engine, returning the same values, for the few mods that call them.
A mixin wraps the vanilla call instead of copying the method, so the mixins of other mods still work.

## Fixing a bug
Always the same cycle. Reproduce the bug in a test, it is red. Fix it, it turns green. Check in game.
A test is checked by breaking the code on purpose. If it stays green, it tests nothing.

## Testing
- `gradlew :common:test :fabric:test :neoforge:test` runs every test, with the real Minecraft.
- The oracle generates small worlds from a seed, breaks and places hundreds of blocks, then compares the light of Firefly with the light vanilla computes from scratch, block by block. `gradlew :fabric:test -PoracleFirstSeed=0 -PoracleWorlds=200` tests 200 seeds in about ten minutes. It always finds the same 18 sky errors, which vanilla has too.
- The oracle also runs the tasks in random order, to prove the order changes nothing.
- `gradlew :fabric:lightBench` times the computation alone, without a server. It sees gaps of 2 to 5 %.
- The `lightid` bench tests on a real server what the oracle cannot see, the save, so a leave and rejoin ([Benchmark.md](Benchmark.md)).
- Always compare with light computed from scratch, never with the light of an old world, which may hold errors.

## Measuring
- One bench at a time on the machine, so results are not skewed.
- Alternate, at least three runs each, and keep the median. On Windows the machine can lose 15 to 20 % of its performance at random over time (Vanguard is suspected), so a reboot is often needed.
- A JFR profile explains a number.
- A RAM gain is kept even without a speed gain. Every MB saved means more players for Leafs. The maintenance cost still has to be worth it, 100 lines for a few MB that do not scale is not.
