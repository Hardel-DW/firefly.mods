# La lumière dans Minecraft
Chaque bloc a deux lumières, de 0 (noir) à 15 (plein jour).
- La lumière des blocs. Une torche donne 14, la glowstone 15.
- La lumière du ciel. Un bloc qui voit le ciel reçoit 15.

La lumière perd 1 par bloc. Une torche éclaire donc environ 14 blocs autour d'elle, et la pierre l'arrête. Le ciel descend à 15 jusqu'au sol, puis entre dans les grottes en perdant 1 par bloc, comme une torche.

Le monde est découpé en chunks de 16x16 blocs, et chaque chunk en sections de 16x16x16 blocs. Le jeu range la lumière par section, 2 Ko par section. Une section en plein ciel, loin de tout bloc, ne stocke rien.

# Vanilla
Le serveur calcule la lumière, la sauvegarde avec le chunk et l'envoie au joueur. Il n'a qu'un seul thread de lumière par dimension, donc tout le travail passe dedans à la suite, même sur une machine à 24 threads.
Un chunk neuf doit être éclairé avant qu'on puisse jouer dedans. Quand plusieurs joueurs explorent, la génération fait la queue derrière ce seul thread.

# Firefly
Firefly découpe le travail en tâches, une par chunk. Une torche éclaire moins d'un chunk, donc une tâche n'a besoin que des chunks autour du sien. Elle réserve les 3x3 chunks dans lesquels elle écrit, et lit les 5x5 autour.
Deux tâches éloignées tournent en même temps sur deux threads. Deux tâches qui se chevauchent s'attendent. Plus de threads, plus de chunks éclairés par seconde.

Une tâche calcule sur une copie, puis remplace la lumière d'un coup à la fin. Les autres threads lisent la lumière quand ils veulent et ne voient jamais un résultat à moitié fini.
Chaque chunk garde sa propre lumière, et la perd quand il se décharge. Une section toute noire ou toute à 15 ne garde pas ses 2 Ko, elle pointe vers un tableau unique partagé par tout le serveur.

Un chunk relu du disque garde sa lumière sauvegardée. Ses voisins ont pu changer pendant son absence, donc Firefly vérifie ses 4 bords et ne corrige que ce qui ne colle pas.

# Les threads
Seul, Firefly crée ses propres threads, un tiers des cœurs par défaut ([Config.md](Config.md)).
Avec Leafs, Firefly ne crée aucun thread. Ses tâches tournent sur les threads de chunks de Leafs.

Les règles derrière tout ça, et leurs raisons, sont dans [Decisions.md](Decisions.md).
