# Layout
One folder is one responsibility. All the code lives in `common/src/main/java/fr/hardel/firefly/`. `fabric` and `neoforge` do nothing special.
- `engine/` - The light computation, one task per chunk.
- `storage/` - The light stored on each chunk.
- `server/` - The Firefly engine, and reading the light.
- `schedule/` - The threads, and `LightExecutor` to use the Leafs ones.
- `mixin/` - The mixins, simply.

Tests live in `common/src/test` and `neoforge/src/test`.
