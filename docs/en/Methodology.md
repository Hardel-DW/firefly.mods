# Methodology
How to work on this project.

> A light that flickers, is wrong or is missing is a bug.

## Compatibility
A mod almost never talks to the light engine. It gives its block a level from 0 to 15, and the engine does the rest.
A mixin wraps the vanilla call instead of copying the method, so the mixins of other mods still work.

## Fixing a bug
Always the same cycle. Reproduce the bug in a test, it is red. Fix it, it turns green. Check in game.
Check a test by breaking the code on purpose. If it stays green, it tests nothing.

## Testing
- `gradlew :common:test :fabric:test :neoforge:test` runs every test, with the real Minecraft.
- The oracle generates small worlds from a seed, breaks and places hundreds of blocks, then compares the light of Firefly block by block with the light vanilla computes from scratch. `gradlew :fabric:test -PoracleFirstSeed=0 -PoracleWorlds=200` tests 200 seeds in about ten minutes, and always finds the same 18 sky errors vanilla makes too.
- The oracle also runs the tasks in random order, to prove the order changes nothing.
- `gradlew :fabric:lightBench` times the computation alone, without a server. It sees gaps of 2 to 5 %.
- The `lightid` bench tests on a real server what the oracle cannot see, the save, so a leave and rejoin ([Benchmark.md](Benchmark.md)).
- Always compare with light computed from scratch, never with the light of an old world, which may hold errors.
- A mod no test loads, like Axiom, is checked on a real server with its real jar.

## Measuring
- One bench at a time on the machine, so results are not skewed.
- Alternate, at least three runs each, and keep the median. On Windows the machine sometimes loses 15 to 20 % of its performance over time. If the numbers look odd, reboot.
- A JFR profile explains a number.
- A RAM gain is kept even without a speed gain, because every MB saved makes room for players. It still has to be worth the upkeep. 100 lines for a few MB that do not grow with the world is not worth it.
