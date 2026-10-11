# Benchmark
Les benchmarks tournent sur un Ryzen 5900X, 12 cœurs / 24 threads, 32 Go de RAM, en pratique ont utilise ZGC avec 12 Go de RAM. et 8 threads.
Chaque run part d'un serveur vierge, même process reproductible, avec les moteurs vanilla, Starlight et Firefly. Chaque moteur tourne au moins trois fois, en alternance.

## Firefly vise
- Éclairer plus de chunks par seconde que les autres moteurs, et plus vite avec plus de threads.
- Moins de RAM par chunk que vanilla.
- Changer la lumière d'un bloc posé en moins d'une milliseconde.
- Ne jamais faire attendre la génération ni les joueur.
- Donner exactement la bonne lumière, sans clignotement.

## Les scénarios
- `light` génère 14 161 chunks sans lumière, puis les éclaire. Il mesure les chunks éclairés par seconde, le CPU et la RAM par chunk.
- `latency` pose puis retire 2 000 sources de lumière, une par une. Il mesure le temps avant que la nouvelle lumière soit visible.
- `lightid` compare la lumière de chaque section à un calcul de zéro, dans l'overworld, le Nether et l'End, avant et après 400 modifications. Il mesure la justesse.
- `reflight` fait voler un bot sous Leafs, avec 32 de `distance d'affichage`, sur un monde déjà généré. Il mesure le temps de l'étape de lumière.

## Résultats
Firefly V0.1.0, Minecraft 26.3. Pour le bench `reflight` il tourne avec Leafs, 12 threads de régions et 12 threads de chunks.
> Quand leaf est installer Firefly utilise les thread de chunk de leaf au lieux des siens.

| mesure | Firefly | ScalableLux | vanilla |
|---|---|---|---|
| Chunks éclairés par seconde | 10 680 | 9 549 | 1 466 |
| CPU par chunk | 524 µs | 506 µs | ? |
| RAM allouée par chunk | 33 Ko | 28 Ko | ? |
| Bloc posé, p50 | 0,32 ms | 0,27 ms | 48 ms |
| RAM gardée par chunk | 10,2 Ko | 14,6 Ko | 12,6 Ko |
| `reflight`, p50 / p90 / p99 | 4,6 / 39 / 185 ms | 2,7 / 26 / 236 ms | ? |
| `lightid`, sections fausses | 0 | 0 | 0 |

## D'où viennent les gains
- Plusieurs threads au lieu d'un seul par dimension. C'est l'essentiel.
- La génération n'attend plus que la lumière soit initialisée. Environ 30 % de chunks en plus.
- Un chunk généré n'a qu'une tâche au lieu de deux.
- Le calcul des hauteurs du ciel quitte le thread de génération de vanilla, et passe dans la tâche.
- Chaque thread réutilise sa mémoire de travail d'une tâche à l'autre. La RAM allouée passe de 101 à 33 Ko par chunk.
- Les sections toutes noires ou toutes à 15 partagent un seul tableau. D'où 10,2 Ko par chunk.
- Seules les sections qui changent partent au joueur.
