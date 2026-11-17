# Benchmark
The benchmarks run on a Ryzen 5900X, 12 cores / 24 threads, 32 GB of RAM, with ZGC, 12 GB of heap and 8 light threads.
Every run starts from a fresh server, with the same reproducible process. Each number is the median of at least three runs.

## Firefly aims for
- More chunks lit per second than other engines, and faster with more threads.
- Less RAM kept per chunk than vanilla.
- A placed block that changes the light in under a millisecond.
- Never making generation or players wait.
- Exactly the right light, with no flicker.

## The scenarios
- `light` generates 14 161 chunks without light, then lights them. It measures chunks lit per second, CPU and RAM per chunk.
- `latency` places then removes 2 000 light sources, one by one. It measures the time until the new light is visible.
- `lightid` compares the light of every section with a computation from scratch, in the overworld, the Nether and the End, before and after 400 edits. It measures correctness.
- `reflight` flies a bot under Leafs, with a `view distance` of 32, over an already generated world. It measures the time of the light step of the chunks read back from disk, and of the few chunks generated at the edge.

## Results
Firefly 0.2.2, Minecraft 26.3. `light` is the median of 7 runs, `latency` of 3 runs.
The ScalableLux and vanilla columns, and the `reflight` rows, come from an earlier series measured with Firefly 0.1.2. For `reflight`, Firefly runs with Leafs, 12 region threads and 12 chunk threads.
> When Leafs is installed, Firefly uses the Leafs chunk threads instead of its own. Leafs runs the light of a chunk before generating other chunks.

| measure | Firefly | ScalableLux | vanilla |
|---|---|---|---|
| Chunks lit per second | 11 200 | 9 907 | 1 523 |
| CPU per chunk | 479 µs | 518 µs | ? |
| RAM allocated per chunk | 33 KB | 28 KB | ? |
| Placed block, p50 / p99 | 0.25 / 0.44 ms | 0.27 / 0.48 ms | 49 / 51 ms |
| RAM kept per chunk | 9.5 KB | 14.6 KB | 12.6 KB |
| `reflight`, chunks read back, p50 / p90 / p99 | 0.47 / 1.2 / 8.7 ms | 0.84 / 4.9 / 67 ms | ? |
| `reflight`, chunks generated, p50 / p90 / p99 | 1.9 / 5.9 / 13 ms | 3.2 / 25 / 74 ms | ? |
| `lightid`, wrong sections | 0 | 0 | 0 |

Over 7 runs, `light` goes from 9 400 to 11 200 chunks per second, because the whole lighting lasts only 1.3 to 1.5 s. The CPU per chunk is steadier, from 479 to 534 µs.

## Where the gains come from
- Several threads instead of a single one per dimension. That is most of it.
- Generation does not wait for the light to be initialized. That gives about 30 % more chunks.
- A generated chunk has a single task instead of two.
- The sky heights are computed in the task, not on the vanilla generation thread.
- Each thread reuses its working memory from one task to the next, hence 33 KB allocated per chunk instead of 101.
- Sections that are all dark or all 15 share a single array, even after a reload. Hence 9.5 KB per chunk.
- A chunk read back only checks its edges, and skips blocks at 15 and opaque blocks.
- A task reserves only the 3x3 chunks it writes, and only when it starts.
- Only the sections that change are sent to the player.
