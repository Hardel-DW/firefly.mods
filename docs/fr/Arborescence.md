# Arborescence
Un dossier a une responsabilité. Tout le code vit dans `common/src/main/java/fr/hardel/firefly/`. `fabric` et `neoforge` ne font rien de particulier.
- `engine/` - Le calcul de la lumière, une tâche par chunk.
- `storage/` - La lumière stockée sur chaque chunk.
- `server/` - Le moteur Firefly, et la lecture de la lumière.
- `schedule/` - Les threads, et `LightExecutor` pour utiliser ceux de Leafs.
- `mixin/` - Les mixins, dont le patch d'Axiom.

Les tests sont dans `common/src/test` et `neoforge/src/test`.
