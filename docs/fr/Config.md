# Config
Firefly crée `config/firefly.json` au premier démarrage. Un changement prend effet au redémarrage, Ce mods inclut aucune commande.
- `threads` : Le nombre de threads de lumière. `-1` par défaut, un tiers des cœurs. Une valeur invalide, comme `0`, reprend la valeur par défaut. Avec Leafs, Firefly utilise les threads de Leafs et ignore `threads`.
- `shared_threads` : `true` par défaut. Avec `false`, Firefly garde ses propres threads même quand un mod comme Leafs lui prête les siens.