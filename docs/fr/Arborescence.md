# Arborescence
Un dossier est une responsabilité. Tout le code vit dans `common/src/main/java/fr/hardel/firefly/`. `fabric` et `neoforge` font rien de bien spéciale.
- `engine/` - Le calcul de la lumière, une tâche par chunk.
- `storage/` - Le stockage de la lumière du chunks.
- `server/` - Le moteur firefly, Et la lecture de la lumière.
- `schedule/` - Les threads, et `LightExecutor` pour utiliser ceux de Leafs.
- `mixin/` - Les mixins tout simplement.

Les tests sont dans `common/src/test` et `neoforge/src/test`.
