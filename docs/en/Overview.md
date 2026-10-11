# Light in Minecraft
Every block has two lights, from 0 (dark) to 15 (full daylight).
- Block light. A torch gives 14, glowstone 15.
- Sky light. A block that sees the sky gets 15.

Light loses 1 per block. A torch lights about 14 blocks around it. Stone stops it. The sky comes down at 15 to the ground, then enters caves losing 1 per block, like a torch.

The world is split into chunks of 16x16 blocks, and each chunk into sections of 16x16x16 blocks. The game stores light per section, 2 KB per section. A section high in the sky, far from any block, stores nothing.

# Vanilla
The server computes the light, saves it with the chunk and sends it to the player. It has a single light thread per dimension. Everything goes through it one after the other, even with 24 threads on the machine.
A generated chunk must be lit before it is playable. When several players explore, generation queues behind that thread.

# Firefly
## Parallel tasks
Firefly splits the work into tasks, one per chunk. A torch lights less than a chunk, so a task only touches the chunks around its own. It reserves a square of 5x5 chunks before it runs.
Two tasks far from each other run at the same time on two threads. Two tasks that touch wait for their turn. More threads, more chunks lit per second.

## Light lives on the chunk
Each chunk keeps its own light. When it unloads, its light goes with it.
A section that is all dark or all 15 does not keep its 2 KB. It points to a single array, shared by the whole server.

## Reading never waits
A task computes on a copy, then swaps the light in one go at the end. Published light never changes again. Other threads read it at any time, without waiting, and never see half computed light.

## Order does not matter
Each task prepares its area before computing. Whatever the order of the tasks, every block ends with the same light.

## Generation
Vanilla lights a new chunk in two steps. Firefly does everything in a single task, and generation no longer waits between the two.

## A reloaded chunk
A chunk read from disk keeps its light. Its neighbours may have changed while it was away, so Firefly checks its 4 edges and fixes only what does not match.

## The player
The server sends light to the player only once a group of tasks is done, and only the sections that changed. Light never flickers.

# Threads
Alone, Firefly creates its own threads, a third of the cores by default ([Config.md](Config.md)).
With Leafs, Firefly creates no thread. Its tasks run on the Leafs chunk threads, at low priority. When the machine is saturated, the game goes before the light.

# Compatibility
- A world moves from vanilla to Firefly and back, without recomputing anything.
- The client does not change. A player without Firefly joins normally.
- Flashback and NeoForge lights that depend on position work.
