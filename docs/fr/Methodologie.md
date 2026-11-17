# Méthodologie
Comment travailler sur ce projet.

> Une lumière qui clignote, fausse ou absente, c'est un bug.

## Compatibilité
Un mod ne parle presque jamais au moteur de lumière. Il donne un niveau de 0 à 15 à son bloc, et le moteur fait le reste.
Un mixin enveloppe l'appel vanilla au lieu de recopier la méthode, pour que les mixins des autres mods marchent encore.

## Corriger un bug
Toujours le même cycle. On reproduit le bug dans un test, il est rouge. On corrige, il passe au vert. On vérifie en jeu.
On vérifie un test en cassant le code exprès. S'il reste vert, il ne teste rien.

## Tester
- `gradlew :common:test :fabric:test :neoforge:test` lance tous les tests, avec le vrai Minecraft.
- L'oracle génère des petits mondes depuis une seed, casse et pose des centaines de blocs, puis compare bloc par bloc la lumière de Firefly à celle que vanilla calcule de zéro. `gradlew :fabric:test -PoracleFirstSeed=0 -PoracleWorlds=200` teste 200 seeds en une dizaine de minutes, et trouve toujours les 18 erreurs de ciel que vanilla fait aussi.
- L'oracle lance aussi les tâches dans un ordre aléatoire, pour prouver que l'ordre ne change rien.
- `gradlew :fabric:lightBench` chronomètre le calcul seul, sans serveur. Il voit des écarts de 2 à 5 %.
- Le banc `lightid` teste sur un vrai serveur ce que l'oracle ne voit pas, la sauvegarde, donc une déco/reco ([Benchmark.md](Benchmark.md)).
- On compare toujours à une lumière calculée de zéro, jamais à celle d'un vieux monde, qui peut contenir des erreurs.
- Un mod qu'aucun test ne charge, comme Axiom, se vérifie sur un vrai serveur avec son vrai jar.

## Mesurer
- Un seul bench à la fois sur le PC, pour ne pas fausser les résultats.
- On alterne, au moins trois runs chacun, et on garde la médiane. Sous Windows, le PC perd parfois 15 à 20 % de perfs au fil du temps. Si les chiffres sont bizarres, on redémarre le PC.
- Un profil JFR explique un chiffre.
- Un gain de RAM se garde même sans gain de vitesse, parce que chaque Mo gagné fait de la place pour des joueurs. Il faut quand même que ça vaille la maintenance. 100 lignes pour quelques Mo qui ne grandissent pas avec le monde, ça ne vaut pas le coup.
