# Light in Minecraft
Every block has two lights, from 0 (dark) to 15 (full daylight).
- Block light. A torch gives 14, glowstone 15.
- Sky light. A block that sees the sky gets 15.

Light loses 1 per block. A torch lights about 14 blocks around it, and stone stops it. The sky comes down at 15 to the ground, then enters caves losing 1 per block, like a torch.

The world is split into chunks of 16x16 blocks, and each chunk into sections of 16x16x16 blocks. The game stores light per section, 2 KB per section. A section high in the sky, far from any block, stores nothing.

# Vanilla
The server computes the light, saves it with the chunk and sends it to the player. It has a single light thread per dimension, so all the work goes through it one piece after another, even on a 24 thread machine.
A new chunk must be lit before anyone can play in it. When several players explore, generation waits in line behind that one thread.

# Firefly
Firefly splits the work into tasks, one per chunk. A torch lights less than a chunk, so a task only needs the chunks around its own. It reserves the 3x3 chunks it writes to, and reads the 5x5 around them.
Two tasks far apart run at the same time on two threads. Two tasks that overlap wait for each other. More threads means more chunks lit per second.

A task computes on a copy, then swaps the light in at the end. Other threads can read the light at any time and never see half of a result.
Each chunk keeps its own light, and loses it when it unloads. A section that is all dark or all 15 does not keep its 2 KB, it points to one array shared by the whole server.

A chunk read back from disk keeps its saved light. Its neighbours may have changed while it was away, so Firefly checks its 4 edges and fixes only what does not match.

# Threads
Alone, Firefly creates its own threads, a third of the cores by default ([Config.md](Config.md)).
With Leafs, Firefly creates no thread. Its tasks run on the Leafs chunk threads.

The rules behind all this, and why, are in [Decisions.md](Decisions.md).
