# Firefly
Le moteur de lumière est Monothread. Firefly remplace le moteur de lumière serveur vanilla par un moteur multithread, c'est tout. Aucune API, sauf `LightExecutor`, qui permet à un autre mod de prêter ses threads à Firefly. Firefly peut fonctionner avec ou sans Leafs, Leaf partage ces thread de chunks.
Les mods **Moonrise**, **ScalableLux** sont incompatibles, ils remplacent eux aussi le moteur de lumière.

## TOC
1. [Vulgarisation.md](Vulgarisation.md) - Vulgarisation des concepts, et le fonctionnement de Firefly, de manière simple.
2. [Methodologie.md](Methodologie.md) - Comment travailler dans le repo tester, mesurer, debuger.
3. [Arborescence.md](Arborescence.md) - Les dossiers et leurs responsabilité.
4. [Config.md](Config.md) - Ce que fait chaque option de config.
5. [Benchmark.md](Benchmark.md) - Les résultat du benchmark, Explication des chiffres.