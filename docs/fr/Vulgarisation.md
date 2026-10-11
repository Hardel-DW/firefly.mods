# La lumière dans Minecraft
Chaque bloc a deux lumières, de 0 (noir) à 15 (plein jour).
- La lumière des blocs. Une torche donne 14, la glowstone 15.
- La lumière du ciel. Un bloc qui voit le ciel reçoit 15.

La lumière perd 1 par bloc. Une torche éclaire donc 14 blocs autour d'elle. La pierre l'arrête. Le ciel descend à 15 jusqu'au sol, puis entre dans les grottes en perdant 1 par bloc, comme une torche.

Le monde est découpé en chunks de 16x16 blocs, et chaque chunk en sections de 16x16x16 blocs. Le jeu range la lumière par section, 2 Ko par section. Une section en plein ciel, loin de tout bloc, ne garde rien.

# Vanilla
Le serveur calcule la lumière, la sauvegarde avec le chunk et l'envoie au joueur. Il a un seul thread de lumière par dimension. Tout passe dedans à la suite, même avec 24 threads sur la machine.
Un chunk généré doit être éclairé avant d'être jouable. Quand plusieurs joueurs explorent, la génération fait la queue derrière ce thread.

# Firefly
## Des tâches en parallèle
Firefly découpe le travail en tâches, une par chunk. Une torche éclaire moins d'un chunk, donc une tâche ne touche que les chunks autour du sien. Elle réserve un carré de 5x5 chunks avant de tourner.
Deux tâches loin l'une de l'autre tournent en même temps sur deux threads. Deux tâches qui se touchent attendent leur tour. Plus de threads, plus de chunks éclairés par seconde.

## La lumière vit sur le chunk
Chaque chunk garde sa lumière. Quand il se décharge, sa lumière part avec lui.
Une section toute noire ou toute à 15 ne garde pas ses 2 Ko. Elle pointe vers un tableau unique, partagé par tout le serveur.

## On lit sans attendre
Une tâche calcule sur une copie, puis remplace la lumière d'un coup à la fin. Une lumière publiée ne change plus. Les autres threads la lisent donc à tout moment, sans attendre, et ne voient jamais une lumière à moitié calculée.

## L'ordre ne compte pas
Chaque tâche prépare sa zone avant de calculer. Peu importe l'ordre des tâches, chaque bloc finit avec la même lumière.

## La génération
Vanilla éclaire un chunk neuf en deux étapes. Firefly fait tout en une seule tâche, et la génération n'attend plus entre les deux.

## Un chunk rechargé
Un chunk relu du disque garde sa lumière. Ses voisins ont pu changer pendant son absence, donc Firefly vérifie ses 4 bords et corrige seulement ce qui ne colle pas.

## Le joueur
Le serveur envoie la lumière au joueur seulement quand un groupe de tâches est fini, et seulement les sections qui ont changé. La lumière ne clignote pas.

# Les threads
Seul, Firefly crée ses threads, un tiers des cœurs par défaut ([Config.md](Config.md)).
Avec Leafs, Firefly ne crée aucun thread. Ses tâches passent sur les threads de chunks de Leafs, en priorité basse. Quand la machine sature, le jeu passe avant la lumière.

# Compatibilité
- Un monde passe de vanilla à Firefly et retour, sans rien recalculer.
- Le client ne change pas. Un joueur sans Firefly se connecte normalement.
- Flashback et les lumières de NeoForge qui dépendent de la position marchent.
