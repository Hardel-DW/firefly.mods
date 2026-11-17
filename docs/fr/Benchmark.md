# Benchmark
Les benchs tournent sur un Ryzen 5900X, 12 cœurs / 24 threads, 32 Go de RAM, avec ZGC, 12 Go de heap et 8 threads de lumière.
Chaque run part d'un serveur neuf, avec le même process reproductible. Chaque chiffre est la médiane d'au moins trois runs.

## Ce que vise Firefly
- Éclairer plus de chunks par seconde que les autres moteurs, et aller plus vite avec plus de threads.
- Garder moins de RAM par chunk que vanilla.
- Changer la lumière d'un bloc posé en moins d'une milliseconde.
- Ne jamais faire attendre la génération ni les joueurs.
- Donner exactement la bonne lumière, sans clignotement.

## Les scénarios
- `light` génère 14 161 chunks sans lumière, puis les éclaire. Il mesure les chunks éclairés par seconde, le CPU et la RAM par chunk.
- `latency` pose puis retire 2 000 sources de lumière, une par une. Il mesure le temps avant que la nouvelle lumière soit visible.
- `lightid` compare la lumière de chaque section à un calcul de zéro, dans l'overworld, le Nether et l'End, avant et après 400 modifications. Il mesure la justesse.
- `reflight` fait voler un bot sous Leafs, avec une `view distance` de 32, sur un monde déjà généré. Il mesure le temps de l'étape de lumière des chunks relus du disque, et des quelques chunks générés au bord.

## Résultats
Firefly 0.2.2, Minecraft 26.3. `light` est la médiane de 7 runs, `latency` de 3 runs.
Les colonnes ScalableLux et vanilla, et la ligne `reflight`, viennent d'une série plus ancienne, mesurée avec Firefly 0.1.2. Pour `reflight`, Firefly tourne avec Leafs, 12 threads de régions et 12 threads de chunks.
> Quand Leafs est installé, Firefly utilise les threads de chunks de Leafs au lieu des siens. Leafs fait passer la lumière d'un chunk avant la génération des autres chunks.

| mesure | Firefly | ScalableLux | vanilla |
|---|---|---|---|
| Chunks éclairés par seconde | 11 200 | 9 907 | 1 523 |
| CPU par chunk | 479 µs | 518 µs | ? |
| RAM allouée par chunk | 33 Ko | 28 Ko | ? |
| Bloc posé, p50 / p99 | 0,25 / 0,44 ms | 0,27 / 0,48 ms | 49 / 51 ms |
| RAM gardée par chunk | 9,5 Ko | 14,6 Ko | 12,6 Ko |
| `reflight`, chunks relus, p50 / p90 / p99 | 0,47 / 1,2 / 8,7 ms | 0,84 / 4,9 / 67 ms | ? |
| `reflight`, chunks générés, p50 / p90 / p99 | 1,9 / 5,9 / 13 ms | 3,2 / 25 / 74 ms | ? |
| `lightid`, sections fausses | 0 | 0 | 0 |

Sur 7 runs, `light` va de 9 400 à 11 200 chunks par seconde, parce que tout l'éclairage dure à peine 1,3 à 1,5 s. Le CPU par chunk est plus stable, de 479 à 534 µs.

## D'où viennent les gains
- Plusieurs threads au lieu d'un seul par dimension. C'est l'essentiel.
- La génération n'attend pas que la lumière soit initialisée. Ça fait environ 30 % de chunks en plus.
- Un chunk généré n'a qu'une tâche au lieu de deux.
- Le calcul des hauteurs du ciel se fait dans la tâche, pas sur le thread de génération de vanilla.
- Chaque thread réutilise sa mémoire de travail d'une tâche à l'autre, d'où 33 Ko alloués par chunk au lieu de 101.
- Les sections toutes noires ou toutes à 15 partagent un seul tableau, même après un rechargement. D'où 9,5 Ko par chunk.
- Un chunk relu ne vérifie que ses bords, et saute les blocs à 15 et les blocs opaques.
- Une tâche ne réserve que les 3x3 chunks dans lesquels elle écrit, et seulement au moment où elle démarre.
- Seules les sections qui changent partent au joueur.
