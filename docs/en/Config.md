# Config
Firefly creates `config/firefly.json` on first start. A change applies on restart. The mod has no command.
- `threads` : The number of light threads. `-1` by default, a third of the cores. `0` is refused. With Leafs, Firefly uses the Leafs threads and ignores `threads`.
- `shared_threads` : `true` by default. With `false`, Firefly keeps its own threads even when a mod like Leafs lends its own.
