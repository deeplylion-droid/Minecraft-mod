# Würstel Gigante (Forge 1.20.1)

Una cintura che porta un würstel gigante: 4 blocchi di lunghezza, ¼ di blocco di spessore, appeso sul davanti e sempre puntato dove guardi. Indossandola, i pugni a mano vuota diventano schiaffi di würstel morbidi e snodabili, ogni volta da un'angolazione diversa (destra, sinistra, dall'alto, dal basso, in diagonale), contro mob, giocatori e blocchi.

![Anteprima dello schiaffo col würstel](docs/preview.gif)

## Installazione

1. Installa **Minecraft Forge 1.20.1** (versione 47.x).
2. Scarica `wurstel-1.1.0.jar` dalla pagina [Releases](../../releases).
3. Mettilo nella cartella `mods` di Minecraft (anche sul server, in multiplayer).

## Come si usa

- **Würstel**: `bistecca di maiale cotta` + `filo` → 2 würstel (si possono anche mangiare).
- **Cintura Würstel**:
  ```
  cuoio   lingotto d'oro   cuoio
  würstel     würstel      würstel
  ```
- Indossa la cintura nello slot dei **pantaloni** (o tasto destro tenendola in mano).
- A mano vuota, dai pugni come al solito: invece del braccio parte lo schiaffo col würstel. Funziona su mob, giocatori (con PvP attivo) e per rompere i blocchi. Gli schiaffi colpiscono sempre a piena forza, anche se clicchi in fretta.
- La trovi anche in Creativa, nelle schede *Combattimento* e *Cibo e bevande*.

Il würstel si vede in terza persona, lo vedono gli altri giocatori e lo vedi anche in prima persona. Ondeggia mentre cammini e salti, e segue la testa con un po' di ritardo.

## Würstel di diamante e di netherite

![Würstel normale, di diamante e di netherite](docs/materials.png)

- **Cintura Würstel di Diamante**, al banco da lavoro:
  ```
              diamante
  diamante    cintura     diamante
              diamante
  ```
- **Cintura Würstel di Netherite**, al tavolo da forgia: `modello di forgiatura` (potenziamento in netherite) + `cintura di diamante` + `lingotto di netherite`, come per le armature.

Gli incantesimi e il nome restano quando potenzi la cintura. Il würstel cambia colore (anche addosso al giocatore) e brilla se la cintura è incantata.

| Cintura | Danno degli schiaffi | Velocità di scavo | Blocchi che lasciano il drop | Armatura |
|---|---|---|---|---|
| Würstel | ×2 | ×1 | come la mano | 1 |
| Diamante | ×4 | ×8 | come un attrezzo di diamante (pietra, minerali, ossidiana...) | 2 |
| Netherite | ×8 | ×9 | come un attrezzo di netherite; non brucia nella lava | 3 |

Il danno è rispetto a un pugno; la velocità di scavo rispetto alla mano (8 e 9 sono le velocità del piccone di diamante e di netherite).

## Incantesimo Turgido (I–V)

- Va sulle cinture würstel: ogni livello **raddoppia** il danno degli schiaffi (I ×2, II ×4, III ×8, IV ×16, V ×32).
- Lo trovi sui libri incantati (tavolo per incantesimi, bibliotecari, bauli) e lo metti sulla cintura con l'incudine. Le cinture ora si possono anche incantare direttamente al tavolo per incantesimi.
- Due libri Turgido IV nell'incudine fanno un Turgido V.
- In Creativa i libri Turgido I–V sono nella scheda *Combattimento*.
- **Cintura di netherite + Turgido V** = schiaffi ×256: un solo schiaffo uccide l'**Ender Dragon**.

Nel suggerimento della cintura vedi il moltiplicatore di danno totale (con Turgido) e quello di scavo.

## Configurazione

- `config/wurstel-client.toml`: `showInFirstPerson`, `sounds`, `particles`.
- `saves/<mondo>/serverconfig/wurstel-server.toml`:
  - `[slap]`: `damageMultiplier` (default 2, il doppio di un pugno), `extraKnockback` (default 0.6), `miningSpeedMultiplier` (default 1) della cintura normale;
  - `[diamond]` e `[netherite]`: `damageMultiplier` (4 e 8) e `miningSpeedMultiplier` (8 e 9);
  - `[turgido]`: `damageMultiplierPerLevel` (default 2: ogni livello raddoppia il danno).

## Compilare da soli

Serve Java 17 o più recente (Java 21 per le mod Fabric 1.21.1):

```
./gradlew build
```

Il jar finisce in `build/libs/`. Su GitHub la compilazione parte da sola a ogni push (GitHub Actions): ogni versione nuova di una mod viene pubblicata in una Release sua, senza toccare quelle vecchie.

## Altre mod

Questo repository può contenere più mod: il Würstel Gigante sta nella radice, ogni altra mod in una cartella `mods/<nome>/` con il suo jar e le sue Release.

### Yo-Yo Arcobaleno (`mods/rainbow-yoyo/`)

![Anteprima dei trick dello yo-yo](docs/rainbow-yoyo-preview.gif)

Uno yo-yo arcobaleno gigante (1,25 blocchi), per Forge 1.20.1. Jar: `rainbowyoyo-1.0.0.jar` nelle [Releases](../../releases). Va installato anche sul server, in multiplayer.

- **Ricetta** (banco da lavoro):
  ```
  lana rossa   lana arancione       lana gialla
  filo         blocco di diamante   filo
  lana lime    lana azzurra         lana viola
  ```
- **Tasto destro (tieni premuto)**: lo lanci e gira in fondo al filo (8 blocchi), seguendo il mirino. Rilascia e torna in mano. Non ti rallenta.
- **Tasto sinistro**: colpo rapido dritto davanti a te.
- **Tasto sinistro con lo yo-yo fuori**: un trick.
  - guardando avanti: **Loop the Loop**, tre giri della morte giganti davanti a te;
  - guardando in alto: **Shoot the Moon**, sale di 10 blocchi e si schianta a terra con un'onda d'urto;
  - guardando in basso: **Walk the Dog**, rotola per terra come una ruota per 9 blocchi e torna;
  - accovacciato: **Around the World**, due giri orizzontali intorno a te, come una sega circolare.
- I quattro trick in una sola combo caricano la **Supernova Arcobaleno**: il clic successivo fa crescere lo yo-yo, che esplode in fuochi d'artificio colpendo tutto nel raggio di 7 blocchi.
- **Ogni colpo fa 40 cuori di danno** (80 punti), con messaggi di morte suoi.
- Scia luminosa arcobaleno, bordo che brilla al buio, particelle, suoni sintetizzati (lancio, ronzio che sale con la velocità, colpi, trick, schianto, Supernova). Il braccio segue il filo anche in prima persona.
- Config: `saves/<mondo>/serverconfig/rainbowyoyo-server.toml` (`damage`, `stringLength`) e `config/rainbowyoyo-client.toml` (`trail`, `particles`).

### Sotto la Bedrock (`mods/below-bedrock/`)

![Il mondo sbiadito e Lui](docs/below-bedrock-preview.png)

Una mod misteriosa per **Fabric 1.21.1**: serve anche **Fabric API**. Jar: `belowbedrock-1.0.0.jar` nelle [Releases](../../releases). Va installata anche sul server, in multiplayer.

- **Piccone di diamante glitchato**: la ricetta del piccone di diamante, ma al contrario. La texture ogni tanto si glitcha e una lettera del nome non si legge mai.
  ```
              bastone
              bastone
  diamante    diamante    diamante
  ```
- È l'unico attrezzo che **rompe la bedrock** (circa 4 secondi a blocco), solo nel mondo normale e in quello sbiadito.
- **Buttati nel vuoto**: non muori. Cadi per 30-40 secondi nel buio, con il vento, un disco suonato troppo lento, un battito che accelera, voci e glitch. Poi diventa tutto nero...
- ...e ti svegli nel **mondo sbiadito**: è la mappa del tuo mondo (stesso seme, infinita come quella normale), ma senza colori, senza sole, senza animali né mostri, sempre di giorno, nella nebbia. E la tua casa non c'è.
- In giro trovi dettagli inquietanti: cartelli che sanno troppo, alberi senza foglie, tunnel 2x2, piramidi di sabbia nel mare, porte in mezzo al nulla, letti nell'erba, fuochi eterni, accampamenti abbandonati, bauli sepolti sotto un cartello "QUI", stanze vuote (gialle, bianche o camere da letto), pozzi dritti fino alla bedrock, figure ferme nella nebbia. E le **mappe**: 9 immagini di Lui, appese agli alberi, nelle stanze e nei bauli, insieme al diario di chi è caduto prima di te.
- **Lui**: alto, pallido, occhi neri, braccia troppo lunghe, nessuna ombra. Non si può ferire.
  - ti guarda da lontano e sparisce se lo fissi;
  - compare con la coda dell'occhio;
  - si avvicina solo quando non lo guardi: quando è dietro di te, girati...;
  - ti imita come in uno specchio;
  - ti lascia dei regali (mappe, il diario, un libro con il tuo nome);
  - a volte ti insegue, e guardarlo non lo ferma.
- **Cose strane**, sempre più spesso più a lungo resti: passi dietro di te, porte che si aprono, torce che si spengono, versi di animali che qui non esistono, sussurri in chat ("Lui ti sussurra: ..."), "*il tuo nome* ha lasciato la partita", un jukebox che suona *11* nella nebbia, cartelli con il tuo nome, finti lag, blocchi sbagliati, la nebbia che si chiude, lo schermo che si strappa, la sua faccia per un istante.
- Qui non si dorme: i letti dicono che ci sono mostri vicino. Bussola e orologio impazziscono.
- **Uscire**: muori (torni al tuo letto), oppure scava fino alla bedrock del mondo sbiadito e buttati di nuovo. Una volta a casa, però, ogni tanto lo rivedi in lontananza.
- Progressi nascosti nella scheda *Minecraft*.
- Multiplayer: ognuno vive la sua storia, ma Lui lo vedono tutti.
- Per i video (da operatore):
  - `/faded him <watch|peek|stalk|mimic|gift|chase>`: fa apparire Lui adesso, in quella modalità;
  - `/faded event <nome>`: fa succedere subito una cosa strana (i nomi li suggerisce il tab, es. `footsteps`, `whisper`, `jukebox`, `fog`, `name_sign`);
  - `/faded fall`: ti butta subito nel vuoto, senza scavare;
  - l'uovo generatore (scheda *Uova generatrici*) o `/summon belowbedrock:inhabitant`: Lui resta fermo a fissarti.
- Gamerule: `/gamerule fadedHauntings false` (niente eventi e niente Lui), `/gamerule fadedSightings false` (non ti segue più a casa).
