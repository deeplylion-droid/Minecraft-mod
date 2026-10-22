# Provare le mod in gioco (senza schermo)

Compilare non basta: le cose che si rompono davvero sono i clic, la sincronizzazione tra giocatori, cosa vede l'altro, i numeri. Per questo provo ogni mod con un **server dedicato e due client veri** che girano su schermi finti (Xvfb), li comando con **tasti e clic veri** (`xdotool`) e li faccio guardare da una **mod di test** che scrive cosa vede e scatta screenshot. Poi guardo le immagini.

Tutto quello che serve è in `harness/` (per Fabric 26.3), in `harness/forge/` (per Forge 1.20.1, sezione 8); per 1.21.1 vedi in fondo.

## Indice
1. Cosa serve nel container
2. Preparare la prova (`setup.sh`)
3. Scrivere e lanciare lo script (`mp_template.sh`)
4. La mod di test (`ctest`) e i suoi comandi
5. Ricette di prova che hanno funzionato
6. Guardare i risultati
7. Altre versioni e altri casi

## 1. Cosa serve nel container

```bash
apt-get install -y xvfb xdotool x11-utils mesa-vulkan-drivers vulkan-tools ffmpeg   # se mancano
python3 -c "import numpy, PIL"                                                       # per immagini e suoni
ls /home/user/jdk/   # JDK 25 per 26.3 (21 per 1.21.1, 17 per Forge): se manca, da api.adoptium.net
```
- OpenGL headless: Mesa llvmpipe (`LIBGL_ALWAYS_SOFTWARE=1`). In 26.3 il gioco chiede una visuale GLX sRGB che Xvfb non ha: lo risolve il mixin `GlBackendMixin` della mod di test.
- Vulkan headless: lavapipe (`mesa-vulkan-drivers`), `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd*.json` e `preferredGraphicsBackend:"vulkan"` in `options.txt`. Lo script fa partire Mario in Vulkan e Luigi in OpenGL, così si provano tutte e due le strade (l'utente gioca in Vulkan).
- Il suono nel sandbox non c'è (OpenAL non parte): non si può verificare l'audio, dillo all'utente.

## 2. Preparare la prova

```bash
SCRATCH=<la cartella scratchpad della sessione>
.claude/skills/mod-minecraft/harness/setup.sh mods/<mod> $SCRATCH/test
```
Crea in `$SCRATCH/test`:
- `srv/` e `cli2/`: copie del progetto Gradle della mod (server dedicato e client di Luigi). Il client di Mario gira dalla cartella della mod stessa (`mods/<mod>/run/`, ignorata da git).
- `srv/run/server.properties` (mondo piatto, **pacifico**, `online-mode=false`, niente whitelist), `ops.json` (Mario e Luigi operatori), `eula.txt`.
- `options.txt` dei client (italiano, GUI scala 2, niente schermate di benvenuto, `pauseOnLostFocus:false`).
- `ctest/`: la mod di test compilata con il jar della mod (`-PmodJar=...`), così può leggere le sue classi.

La prima volta Gradle scarica Minecraft e gli asset per ogni copia: ci vogliono minuti e la rete. Lo script usa sempre `--offline`, quindi in un container nuovo la prima prova va lanciata da una copia senza `--offline` (dopo `./gradlew downloadAssets` nella cartella della mod): altrimenti `runServer` si ferma perché mancano `dev-launch-injector` e `fabric-log4j-util`. Le volte dopo va bene offline. Se Maven Central risponde `429`, aggiungi ai comandi Gradle `-Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=2000`.

## 3. Scrivere e lanciare lo script

Copia `harness/mp_template.sh` dentro la cartella di prova (es. `$SCRATCH/test/mp1.sh`), imposta `MOD` (la cartella di prova è quella dello script), scrivi le fasi in fondo, poi:
```bash
cd $SCRATCH/test && SCRATCH=$SCRATCH ./mp1.sh all > driver1.log 2>&1 &
# ogni tanto:  tail -5 driver1.log
# per fermarlo:  .claude/skills/mod-minecraft/harness/stop.sh $SCRATCH/test
```
Lo script: copia il codice nelle due copie, sposta (non cancella) il mondo vecchio, avvia server, due Xvfb (`:77`, `:78`) e due client con `--quickPlayMultiplayer localhost:25565`, aspetta che entrambi siano dentro, prepara il mondo (pacifico, mezzogiorno fermo, niente pioggia, niente mob), esegue le fasi, chiude tutto e copia gli screenshot in `$T/out-<ora>/`.

Funzioni utili dentro lo script (`A` = Mario, `B` = Luigi):
- `cmd A "tp Luigi 4 -60 0" "xp set Mario 25 levels"`: comandi da operatore (Mario).
- `do_ B "orbit Mario 40 6 1"`: richieste alla mod di test (vedi sotto).
- `shot B nome`, `burst B nome 20 2` (20 foto ogni 2 tick, esatte), `status A`.
- `key A z` (tocco), `hold A space 0.15` o `down`/`up` (tasti tenuti), `rhold A 3` (tasto destro tenuto 3 secondi: cariche, archi), `slot A 1` (slot della barra), `guiclick A x y` (clic in coordinate GUI), `button A "Rinasci"` (centro di un bottone dall'ultima descrizione dello schermo), `respawn A`.
- `waitlog $LA "screen DeathScreen" 10`: aspetta una riga nuova (che contiene quel testo) nel log del client; `$LA`/`$LB`/`$LS` sono i log di Mario, Luigi e del server.

Regole imparate a caro prezzo:
- Non modificare lo script mentre gira: copialo e lancia la copia.
- Non usare `pkill -f` o `pgrep -f` con un testo che c'è nel tuo comando: usa `stop.sh` / `cleanup.sh`.
- `key` preme e rilascia subito: per salto e corsa usa `hold`.
- Gli slime del mondo piatto uccidono i giocatori di prova: il `server.properties` del modello è già in pacifico; se cambi mondo, controlla.
- Le coordinate intere di `/tp` e `/summon` vanno al centro del blocco (+0,5).
- Il client di Mario **compila dal sorgente della mod** quando parte (server e Luigi usano copie fatte all'avvio dello script): non modificare i sorgenti finché Mario non è entrato, e **non compilare nella cartella della mod mentre la prova gira** (il gioco carica le classi da `build/`). Per controllare che il codice nuovo compili intanto, copia `src`, `gradle*`, `build.gradle`, `settings.gradle`, `gradle.properties` in una cartella della scratchpad e compila lì (la prima volta Loom ci mette un paio di minuti). Una volta ho lanciato lo script con la build rotta perché il comando dopo `gradlew build` era in `&&` con un `ls`: controlla l'esito della build prima di lanciare.
- Tieni i giocatori in vita tra una fase e l'altra (`effect give @a minecraft:instant_health 1 4 true`, `saturation`, `resistance 1200 4` per le prove solo visive) e rialzali se sono morti (funzione `respawn` sul bottone "Rinasci"): in una prova un `damage` di troppo ha mandato Luigi "a terra" e tutto il resto è andato sprecato.
- Le ricariche restano da una fase all'altra: se una fase usa un potere, quella dopo non lo può riusare subito (in Filo Rosso la Cupola provata nella ruota non partiva più nella fase della Cupola).
- Filo Rosso: se mandi uno dei due lontano, dopo 1,5 secondi il filo lo riporta **dentro** l'altro (le foto mostrano una faccia da vicino e i clic colpiscono il partner). Per le prove a distanza allunga il filo (`gamerule filorosso:length 32`) e resta entro 36 blocchi.
- Un boss vivo continua ad attaccare tra un comando e l'altro dello script: la calata del Burattinaio lancia i giocatori a 10-15 blocchi, e una prova "il partner taglia il nodo" falliva per questo, non per la mod. Le meccaniche che non hanno bisogno del boss provale senza (il nodo si prova con la Maschera, `Puppets.prank`); con `NoAI` il boss non fa più niente, neanche gli attacchi forzati, perché le azioni stanno in `customServerAiStep`. E leggi lo stato poco prima di agire: `fr` impiega un secondo, e in 7 secondi di marionetta tre letture se ne mangiano metà.

## 4. La mod di test (`ctest`)

Scrive righe `[ctest] ...` nel log del client (`$T/out-*/mario.log`, `luigi.log`): schermate aperte con i bottoni e le loro coordinate, chat e action bar (risultati dei comandi, messaggi di morte, messaggi della mod), stato. Ogni tick legge `ftest_cmd.txt` nella cartella del gioco ed esegue le righe:

| Comando | Cosa fa |
|---|---|
| `cmd <comando>` | esegue un comando come quel giocatore |
| `look <yaw> <pitch>`, `face <giocatore>`, `faceat x y z`, `facetype <tipo>` | gira la telecamera |
| `orbit <giocatore> <angolo> <distanza> <altezza>` | si teletrasporta a quella distanza dal giocatore (0° = dietro di lui) e lo guarda: inquadrature ripetibili |
| `cam fp\|back\|front` | prima o terza persona |
| `shot <nome>`, `burst <nome> <quante> <ogni tick>` | screenshot, anche a tempo esatto |
| `hud on\|off`, `screen`, `close` | F1, descrive o chiude la schermata |
| `status` | riga `st`: posizione, velocità, vita, XP, effetti, altri giocatori |
| `ent <tipo>` | posizione, vita (se vivente) e dimensione di ogni entità di quel tipo (`entity.minecraft.pig`, anche proiettili) |
| `track <tick>` | altezza e velocità verticale a ogni tick (salti, spinte) |
| `particles` | quante particelle ci sono |

Per la mod che provi aggiungi comandi suoi in `run()` (es. per le chiappe `aim <giocatore>` guardava il centro delle chiappe, calcolato con le classi della mod). Le classi della mod sono disponibili perché `setup.sh` compila la mod di test con `-PmodJar`.

## 5. Ricette di prova che hanno funzionato

- **Danni**: maiali con 1000 di vita, fermi e muti, a distanze note; dopo il colpo `ent pig` e confronta con i conti (il danno con la caduta di distanza, i limiti del raggio). Ricorda il +0,5 delle coordinate intere.
  `summon minecraft:pig 3 -60 0 {NoAI:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:1000}],Health:1000f}`
- **Ender Dragon**: `summon minecraft:ender_dragon 8 -57 0 {NoAI:1b}`, leggi la vita prima e dopo (`ent ender_dragon`); poi `data merge entity @e[type=ender_dragon,limit=1] {NoAI:0b}` per lasciarlo morire davvero (animazione, sparisce, progresso "L'End liberato").
- **Giocatore colpito**: Luigi vicino a Mario, `status B` prima e dopo (vita, effetti, posizione per la spinta). In pacifico si cura in fretta: leggi subito. Attenzione: i danni dei tipi che scalano con la difficoltà (es. le esplosioni vanilla, `scaling` diverso da `never`) in pacifico sui giocatori valgono 0 e in facile sono dimezzati: per un requisito tipo "fa 30 cuori" misura sui mob e, sui giocatori, con `difficulty normal` e senza armatura, e scrivi nel `CLAUDE.md` contro cosa vale il numero.
- **Tempi esatti**: le pressioni con `sleep` sbagliano di qualche tick; se conta (cariche), fai scrivere alla mod di test i tick d'uso (`player.getTicksUsingItem()`) con un comando suo.
- **Spinte e salti**: `track 40`, poi `hold A space`, e calcola dal log l'altezza massima (es. salto normale 1,25 blocchi).
- **Schermate**: apri con il tasto vero, `screen` per sapere dove sono i bottoni, `guiclick` sulle coordinate, controlla nel log cosa è successo (in Disabilità 1.0.0 un test via riflessione passava, i clic veri no).
- **Visivo**: `orbit` per inquadrare sempre allo stesso modo, una foto per ogni livello o stato; `burst` per le animazioni (premi il tasto dopo aver avviato il burst). Guarda anche l'inventario (`key A e`), la prima persona, la terza persona davanti e dietro, di giorno e di notte se conta.
- **Boss**: mondo piatto, boss fermo con `NoAI` e un comando di debug della mod per forzare ogni attacco (`/filo boss attacco <nome>`).

## 6. Guardare i risultati

- `python3 scripts/sheet.py foglio.png 5 384 $(ls burst_*.png)`: un foglio di provini, poi aprilo con lo strumento di lettura delle immagini. Ritaglia con PIL quando il soggetto è piccolo.
- Controlla nei log: `grep "\[ctest\]" mario.log`, e gli errori veri (lo script già filtra il rumore: servizi Mojang irraggiungibili, OpenAL, ALSA).
- Se qualcosa si vede male, correggi e rifai solo la fase che serve (passa il nome della fase allo script: `./mp1.sh "fart dragon"`).

## 7. Altre versioni e altri casi

- **Un giocatore solo**: `runClient --args="--quickPlaySingleplayer <mondo>"`; per i comandi in singleplayer conviene eseguirli lato server con permessi da proprietario (la vecchia mod di test di Filo Rosso lo faceva con `server.getCommands().performPrefixedCommand(...withPermission(LevelBasedPermissionSet.OWNER))`).
- **Fabric 1.21.1** (Sotto la Bedrock, Jumpscare!): stesso schema, ma la mod di test va riscritta con i nomi vecchi (`Minecraft.screen`, `setScreen`, `GuiGraphics`, `ResourceLocation`) e non serve `GlBackendMixin`. Le mod di test compilate con Loom e mappature chiamano i metodi di Minecraft ereditati dalle classi della mod facendo prima il cast alla classe di Minecraft, altrimenti il remapping in dev fallisce.
- **Forge 1.20.1**: c'è un banco di prova suo, vedi la sezione 8 (usato per la Frusta Imperiale; Würstel e Yo-Yo erano stati verificati solo compilando e con anteprime offline).
- **Client di produzione** (come lo avvia l'utente): Knot con il jar compilato e il classpath preso dal JSON della versione; utile per scoprire problemi che in dev non ci sono (fatto per Disabilità e Filo Rosso).
- **Con le mod dell'utente** (Sodium, Iris, Simple Voice Chat, WorldEdit): mettile in `run/mods` del client e fai partire, per vedere conflitti di mixin o di tasti.

## 8. Forge 1.20.1 (`harness/forge/`)

Stesso schema (server dedicato, Mario e Luigi su Xvfb `:77`/`:78`, `xdotool`, mod di test che scrive `[ctest] ...` e legge `ftest_cmd.txt`), con queste differenze:
- `harness/forge/setup.sh /home/user/Minecraft-mod/mods/<mod> $SCRATCH/test-forge` prepara **tre copie** del progetto (`srv/`, `cli1/` per Mario, `cli2/` per Luigi): anche Mario gira da una copia, così nella cartella della mod non nasce niente. Usa il wrapper Gradle della radice e il JDK 17, compila la mod, la mod di test e scarica gli asset (`downloadAssets`).
- La mod di test (`harness/forge/ctest/`) è una mod Forge (`@Mod("ctest")`, `displayTest="IGNORE_ALL_VERSION"`) compilata con `jar` e **non** `build`: i client di sviluppo (`runClient`) usano i nomi di Mojang, il jar riofuscato non funzionerebbe. Va in `run/mods/` dei client. I comandi sono gli stessi della 26.3 (più `track` con x e z, e in più scrive i clic del mouse che arrivano al gioco: `mouse button 0 action 1`). I comandi della mod che provi vanno in `ctest/src/main/java/ctest/Extra.java` nella cartella di prova (esempio: `harness/forge/Extra-esempio-frusta.java`), chiamato per ogni comando che il driver non conosce; `setup.sh` rinfresca i file generici e lascia `Extra.java`.
- `mp_template.sh`: in ogni copia aggiunge al `build.gradle` le righe di `run_args.gradle` (nome del giocatore da `CT_USER`, `--quickPlayMultiplayer localhost:25565`, finestra 1280×720, `-Xmx2G`); sempre OpenGL (1.20.1 non ha Vulkan). Prepara il mondo con i gamerule di 1.20.1 (`doDaylightCycle`, `doWeatherCycle`, `doMobSpawning`). In più: `lclick`/`rclick` (tasti del mouse in gioco senza muovere il mouse, che girerebbe la telecamera), `hover` (muove il mouse in una schermata, per i tooltip), `last <A|B> <prefisso> [n]` (ultime righe del driver), `bnow` (raffica che parte subito: `do_` aspetta 0,7 s, quindi `burst B x 20 1; lclick A` fotografava prima del colpo).
- Comandi in 1.20.1: `summon minecraft:pig 0 -60 3 {NoAI:1b,Silent:1b,Attributes:[{Name:"generic.max_health",Base:1000}],Health:1000f}`, oggetti con `item replace entity Mario hotbar.3 with frusta:netherite_whip{Enchantments:[{id:"frusta:sottomissione",lvl:5s}]}`, libri con `StoredEnchantments`.
- Il mondo piatto di 1.20.1 ha l'erba a y = -61: i giocatori stanno a -60.

Trappole trovate qui:
- `spawn-animals=false` nel `server.properties` **cancella subito tutti gli animali**, anche quelli evocati (`ServerLevel.shouldDiscardEntity`): i maiali di prova sparivano. Il modello non lo mette più; per non far nascere mob basta `gamerule doMobSpawning false`.
- **Ender Dragon con `NoAI`**: in 1.20.1 le sue parti colpibili (testa, collo, corpo...) si spostano solo nel ramo con l'IA, quindi restano ferme a 0 0 0 e nessun colpo lo prende (il modello invece si vede al posto giusto). Evocalo **appollaiato**: `summon minecraft:ender_dragon 0 -60 10 {Rotation:[0f,0f],DragonPhase:6}`; in pacifico resta fermo circa 5 secondi con la testa verso -z, poi decolla. Per mirare alla testa serve un comando della mod di test che guardi `dragon.head.getBoundingBox().getCenter()`.
- Con F1 (`hud off`) vanilla non disegna l'oggetto in mano in prima persona: le foto in prima persona vanno fatte con `hud on`.
- llvmpipe disegna pochi fotogrammi al secondo: in una raffica ogni tick ci sono foto uguali di fila. Va bene, basta guardarle tutte.
- In uno script di prova non usare `wait` da solo: aspetta anche server e client lanciati in background (lo script resta fermo per un'ora). Usa `wait $PID` del solo comando che ti serve.
- Dopo un colpo che spinge (Schiocco Imperiale) il bersaglio non è più dov'era: rimettilo a posto prima della prova successiva.

