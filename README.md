# Würstel Gigante (Forge 1.20.1)

Una cintura che porta un würstel gigante: 4 blocchi di lunghezza, ¼ di blocco di spessore, appeso sul davanti e sempre puntato dove guardi. Indossandola, i pugni a mano vuota diventano schiaffi di würstel morbidi e snodabili, ogni volta da un'angolazione diversa (destra, sinistra, dall'alto, dal basso, in diagonale), contro mob, giocatori e blocchi.

![Anteprima dello schiaffo col würstel](docs/preview.gif)

## Installazione

1. Installa **Minecraft Forge 1.20.1** (versione 47.x).
2. Scarica `wurstel-1.0.0.jar` dalla pagina [Releases](../../releases).
3. Mettilo nella cartella `mods` di Minecraft (anche sul server, in multiplayer).

## Come si usa

- **Würstel**: `bistecca di maiale cotta` + `filo` → 2 würstel (si possono anche mangiare).
- **Cintura Würstel**:
  ```
  cuoio   lingotto d'oro   cuoio
  würstel     würstel      würstel
  ```
- Indossa la cintura nello slot dei **pantaloni** (o tasto destro tenendola in mano).
- A mano vuota, dai pugni come al solito: invece del braccio parte lo schiaffo col würstel. Funziona su mob, giocatori (con PvP attivo) e per rompere i blocchi.
- La trovi anche in Creativa, nelle schede *Combattimento* e *Cibo e bevande*.

Il würstel si vede in terza persona, lo vedono gli altri giocatori e lo vedi anche in prima persona. Ondeggia mentre cammini e salti, e segue la testa con un po' di ritardo.

## Configurazione

- `config/wurstel-client.toml`: `showInFirstPerson`, `sounds`, `particles`.
- `saves/<mondo>/serverconfig/wurstel-server.toml`: `damageMultiplier` (default 2, cioè il doppio di un pugno) e `extraKnockback` (default 0.6).

## Compilare da soli

Serve Java 17 o più recente:

```
./gradlew build
```

Il jar finisce in `build/libs/`. Su GitHub la compilazione parte da sola a ogni push (GitHub Actions): ogni versione nuova di una mod viene pubblicata in una Release sua, senza toccare quelle vecchie.

## Altre mod

Questo repository può contenere più mod: il Würstel Gigante sta nella radice, ogni altra mod in una cartella `mods/<nome>/` con il suo jar e le sue Release.
