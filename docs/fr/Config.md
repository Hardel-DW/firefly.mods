# Config
Firefly crée `config/firefly.json` au premier démarrage. Un changement prend effet au redémarrage, Ce mods inclut aucune commande.
- `threads` : Le nombre de threads de lumière. `-1` par défaut, un tiers des cœurs. `0` est refusé. Avec Leafs, Firefly utilise les threads de Leafs et ignore `threads`.
- `shared_threads` : `true` par défaut. Avec `false`, Firefly garde ses propres threads même quand un mod comme Leafs lui prête les siens.