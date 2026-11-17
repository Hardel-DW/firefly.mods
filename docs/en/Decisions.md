# Decisions
The rules Firefly follows, and why. Each one is true in the code today.

## Same result, different process
Vanilla computes light in a fixed order, on one thread. Firefly computes it in any order, on several threads. Once the work is done, every block has the light a full recomputation of the same blocks gives.
Where vanilla depends on the order of its tasks, Firefly does not copy that dependency. The only gaps Firefly keeps are the 18 sky errors out of 200 test seeds that vanilla makes on the same blocks.

## What Firefly replaces
Firefly replaces only the light engine the server creates for each dimension. The client keeps the vanilla engine, even in singleplayer, so a player without Firefly joins normally.
Every public method of the vanilla engine still exists and returns what vanilla returns. A mod that changes light through the parent vanilla method, like Axiom, also reaches Firefly.

## One contract with other mods
`LightExecutor` is the only API. Another mod, like Leafs, provides it through `ServiceLoader` to lend its threads. Firefly never knows which mod lends them.
Firefly declares itself incompatible with Moonrise, ScalableLux, Starlight and Phosphor, which replace the same engine. Firefly never declares itself `starlight`, because Flashback then calls Starlight classes that Firefly does not have.

## The threads
Light is computed on Firefly's threads, never on the server thread. The server thread is the slowest resource of a busy server.
Firefly sets no thread priority and has no command. The only settings are in `config/firefly.json` ([Config.md](Config.md)).

## A task and its square
A task works on one chunk. It reads the 5x5 chunks around it and writes only the 3x3 in the middle. It reserves those 3x3 chunks when it starts, not when it is queued, so tasks that do not overlap run at the same time.

## Published light never changes
A task copies a section before writing to it, then publishes the copy at the end. Other threads read light at any time without a lock and never see half computed light.
The copy costs a few short lived KB per chunk. In exchange, sections that are all dark or all 15 share one array, and a chunk keeps less RAM than in vanilla.

## The player sees no flicker
The server sends light only when a whole group of tasks is done, and only the sections whose data changed.

## Generation
Vanilla lights a new chunk only once its neighbours have started their light. Firefly relies on that rule, and lights a generated chunk in a single task.
While a chunk is still generating, Firefly reads its light as 0, like vanilla next to an initialized chunk.

## Tasks from the vanilla queue
Some mods, like Axiom and Flashback, add tasks to the internal queue of the vanilla engine, a "before" task and an "after" task. Firefly sends the work of the "before" tasks in the same pass, and runs an "after" task only once that light is published. An "after" task means "the light is done", as in vanilla.

## Axiom
Axiom picks its fast relight only when it sees the mod `starlight` or `scalablelux`. Firefly patches the three places where Axiom reads that check, so Axiom asks Firefly to relight the edited chunks. Firefly relights them in parallel, without blocking the server thread.
This patch exists only because Axiom has no neutral hook. The mixins apply only when Axiom is installed.

## Known limits
- After an Axiom edit, the area stays black for about a tick. Axiom sends the edited chunks before their new light. It does the same without Firefly.
- On a huge Axiom edit, a chunk can stay black until the player reconnects. It is rare, and the server's light is correct.
