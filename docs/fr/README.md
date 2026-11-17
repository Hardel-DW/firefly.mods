# Firefly
Le moteur de lumière vanilla tourne sur un seul thread. Firefly remplace le moteur de lumière du serveur par un moteur multithread, et rien d'autre. Sa seule API est `LightExecutor`, qui permet à un autre mod de lui prêter ses threads. Firefly marche avec ou sans Leafs, et Leafs lui prête ses threads de chunks.
Moonrise et ScalableLux remplacent eux aussi le moteur de lumière, donc ils sont incompatibles avec Firefly.

## Sommaire
1. [Vulgarisation.md](Vulgarisation.md) - Comment marche la lumière, et comment Firefly la calcule, simplement.
2. [Decisions.md](Decisions.md) - Les règles que suit Firefly, pourquoi, et ses limites connues.
3. [Methodologie.md](Methodologie.md) - Comment travailler dans le repo, tester, mesurer, debugger.
4. [Arborescence.md](Arborescence.md) - Les dossiers et leur responsabilité.
5. [Config.md](Config.md) - Ce que fait chaque option de config.
6. [Benchmark.md](Benchmark.md) - Les résultats des benchs, et d'où viennent les chiffres.
