# Méthodologie
Comment continuer ce projet. Ces règles ont fait leurs preuves.

## La règle absolue
Firefly produit le même résultat que vanilla, avec un process différent. Vanilla calcule la lumière dans un ordre précis, sur un seul thread. Firefly la calcule dans le désordre, sur plusieurs threads, et chaque bloc finit avec la même lumière.
Un joueur, un mod ou la sauvegarde ne voit aucune différence.

> Si une lumière clignote, est fausse ou absente c'est un bug.

## Compatibilité
Un mod ne parle presque jamais au moteur de lumière. Il donne un niveau de 0 à 15 à son bloc, et le moteur fait le reste. Firefly garde quand même toutes les fonctions publiques du moteur vanilla, avec les même valeurs retourner, pour les rares mods qui les appellent.
Un mixin enveloppe l'appel vanilla au lieu de recopier la méthode, pour que les mixins des autres mods marchent encore.

## Corriger un bug
Toujours le même cycle. On reproduit le bug dans un test, il est rouge. On corrige, il passe au vert. On vérifie en jeu.
Un test se vérifie en cassant le code exprès. S'il reste vert, il ne teste rien.

## Tester
- `gradlew :common:test :fabric:test :neoforge:test` lance tous les tests, avec le vrai Minecraft.
- L'oracle génère des petits mondes depuis une seed, casse et pose des centaines de blocs, puis compare la lumière de Firefly à celle que vanilla calcule de zéro, bloc par bloc. `gradlew :fabric:test -PoracleFirstSeed=0 -PoracleWorlds=200` teste 200 seeds en une dizaine de minutes. Il trouve toujours les 18 mêmes erreurs de ciel, que vanilla à aussi.
- L'oracle tourne dans le désordre, pour prouver que l'ordre ne change rien.
- `gradlew :fabric:lightBench` chronomètre les calcul, sans serveur. Il voit des écarts de 2 à 5 %.
- Le banc `lightid` teste sur un vrai serveur ce que l'oracle ne voit pas, la sauvegarde donc la déco/reco ([Benchmark.md](Benchmark.md)).
- On compare toujours à une lumière calculée de zéro, jamais à celle d'un vieux monde, qui pourrait avoir des erreurs.

## Mesurer
- Un seul bench à la fois sur le pc, pour pas fauser les résultats.
- On alterne, trois runs chacune au moins, et on garde la médiane. Sur windows le pc peut dégrader les perfs de 15 à 20 % aléatoirement avec le temps (Je suspecte Vanguard) donc un redémarrage est souvent néccéssaire.
- Un profil JFR explique une valeur
- Un gain de RAM se garde même sans gain de vitesse. Chaque Mo gagné, c'est des joueurs en plus pour Leafs. Aprés faut voir si le rapport maintenace/gain est intérréssant 100 ligne pour quelque Mo brut qui ne scale pas c'est pas intérréssant.
