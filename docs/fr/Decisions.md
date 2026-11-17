# Décisions
Les règles que suit Firefly, et pourquoi. Chacune est vraie dans le code aujourd'hui.

## Même résultat, autre méthode
Vanilla calcule la lumière dans un ordre fixe, sur un seul thread. Firefly la calcule dans n'importe quel ordre, sur plusieurs threads. Une fois le travail fini, chaque bloc a la lumière qu'un recalcul complet des mêmes blocs donnerait.
Quand vanilla dépend de l'ordre de ses tâches, Firefly ne recopie pas cette dépendance. Les seuls écarts que Firefly garde sont les 18 erreurs de ciel sur 200 graines de test, que vanilla fait aussi sur les mêmes blocs.

## Ce que Firefly remplace
Firefly remplace seulement le moteur de lumière que le serveur crée pour chaque dimension. Le client garde le moteur vanilla, même en solo, donc un joueur sans Firefly se connecte normalement.
Toutes les méthodes publiques du moteur vanilla existent toujours et renvoient la même chose qu'en vanilla. Un mod qui change la lumière en appelant directement la méthode vanilla du parent, comme Axiom, arrive aussi chez Firefly.

## Un seul contrat avec les autres mods
`LightExecutor` est la seule API. Un autre mod, comme Leafs, la fournit par `ServiceLoader` pour prêter ses threads. Firefly ne sait jamais quel mod les prête.
Firefly se déclare incompatible avec Moonrise, ScalableLux, Starlight et Phosphor, qui remplacent le même moteur. Firefly ne se déclare jamais `starlight`, sinon Flashback appelle des classes de Starlight que Firefly n'a pas.

## Les threads
La lumière se calcule sur les threads de Firefly, jamais sur le thread serveur. Sur un serveur chargé, c'est le thread serveur qui limite tout.
Firefly ne touche pas à la priorité des threads et n'a aucune commande. Tous les réglages sont dans `config/firefly.json` ([Config.md](Config.md)).

## Une tâche et son carré
Une tâche s'occupe d'un chunk. Elle lit les 5x5 chunks autour de lui et n'écrit que dans les 3x3 du milieu. Elle réserve ces 3x3 chunks au moment où elle démarre, pas quand elle est mise en file, donc deux tâches qui ne se chevauchent pas tournent en même temps.

## Une lumière publiée ne change plus
Une tâche copie une section avant d'écrire dedans, puis publie la copie à la fin. Les autres threads lisent la lumière quand ils veulent, sans verrou, et ne voient jamais une lumière à moitié calculée.
La copie coûte quelques Ko de mémoire temporaire par chunk. En échange, les sections toutes noires ou toutes à 15 partagent un seul tableau, et un chunk garde moins de RAM qu'en vanilla.

## Pas de clignotement chez le joueur
Le serveur envoie la lumière seulement quand tout un groupe de tâches est fini, et seulement les sections dont les données ont changé.

## La génération
Vanilla n'éclaire un chunk neuf qu'une fois ses voisins initialisés. Firefly s'appuie sur cette règle, et éclaire un chunk généré en une seule tâche.
Tant qu'un chunk est en génération, Firefly lit sa lumière à 0, comme vanilla à côté d'un chunk initialisé.

## Les tâches de la file vanilla
Certains mods, comme Axiom et Flashback, ajoutent des tâches dans la file interne du moteur vanilla, une tâche "avant" et une tâche "après". Firefly envoie le travail des tâches "avant" dans le même passage, et ne lance une tâche "après" qu'une fois cette lumière publiée. Une tâche "après" veut dire "la lumière est prête", comme en vanilla.

## Axiom
Axiom ne prend son relight rapide que s'il voit le mod `starlight` ou `scalablelux`. Firefly modifie les trois endroits où Axiom fait ce test, donc Axiom demande à Firefly de recalculer les chunks modifiés. Firefly les recalcule en parallèle, sans bloquer le thread serveur.
Ce patch existe seulement parce qu'Axiom n'a pas de point d'entrée neutre. Ses mixins ne s'appliquent que si Axiom est installé.

## Limites connues
- Après un edit Axiom, la zone reste noire pendant environ un tick. Axiom envoie les chunks modifiés avant leur nouvelle lumière, et fait pareil sans Firefly.
- Sur un edit Axiom énorme, un chunk peut rester noir jusqu'à ce que le joueur se reconnecte. C'est rare, et la lumière du serveur est juste.
