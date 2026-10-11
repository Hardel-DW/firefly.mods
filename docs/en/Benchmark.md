# Benchmark
The benchmarks run on a Ryzen 5900X, 12 cores / 24 threads, 32 GB of RAM, in practice with ZGC, 12 GB of heap and 8 threads.
Every run starts from a fresh server, with the same reproducible process, on the vanilla, Starlight and Firefly engines. Each engine runs at least three times, alternating.

## Firefly aims for
- More chunks lit per second than other engines, and faster with more threads.
- Less RAM per chunk than vanilla.
- A placed block that changes the light in under a millisecond.
- Never making generation or players wait.
- Exactly the right light, with no flicker.

## The scenarios
- `light` generates 14 161 chunks without light, then lights them. It measures chunks lit per second, CPU and RAM per chunk.
- `latency` places then removes 2 000 light sources, one by one. It measures the time until the new light is visible.
- `lightid` compares the light of every section with a computation from scratch, in the overworld, the Nether and the End, before and after 400 edits. It measures correctness.
- `reflight` flies a bot under Leafs, with a `view distance` of 32, over an already generated world. It measures the time of the light step.

## Results
Firefly V0.1.0, Minecraft 26.3. For the `reflight` bench it runs with Leafs, 12 region threads and 12 chunk threads.
> When Leafs is installed, Firefly uses the Leafs chunk threads instead of its own.

| measure | Firefly | ScalableLux | vanilla |
|---|---|---|---|
| Chunks lit per second | 10 680 | 9 549 | 1 466 |
| CPU per chunk | 524 µs | 506 µs | ? |
| RAM allocated per chunk | 33 KB | 28 KB | ? |
| Placed block, p50 | 0.32 ms | 0.27 ms | 48 ms |
| RAM kept per chunk | 10.2 KB | 14.6 KB | 12.6 KB |
| `reflight`, p50 / p90 / p99 | 4.6 / 39 / 185 ms | 2.7 / 26 / 236 ms | ? |
| `lightid`, wrong sections | 0 | 0 | 0 |

## Where the gains come from
- Several threads instead of a single one per dimension. That is most of it.
- Generation no longer waits for the light to be initialized. About 30 % more chunks.
- A generated chunk has a single task instead of two.
- Computing the sky heights leaves the vanilla generation thread and moves into the task.
- Each thread reuses its working memory from one task to the next. Allocated RAM goes from 101 to 33 KB per chunk.
- Sections that are all dark or all 15 share a single array. Hence 10.2 KB per chunk.
- Only the sections that change are sent to the player.
