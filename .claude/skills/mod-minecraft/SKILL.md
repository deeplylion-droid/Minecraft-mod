---
name: mod-minecraft
description: Come lavorare alle mod Minecraft del repository deeplylion-droid/Minecraft-mod nel modo in cui Claude le ha fatte finora con questo utente - le mod già fatte (Würstel Gigante, Yo-Yo Arcobaleno, Sotto la Bedrock, Jumpscare!, Disabilità, Filo Rosso, Chiappe Colossali), le tre toolchain (Forge 1.20.1, Fabric 1.21.1, Fabric 26.3) e le API di 26.3, le prove con un server e due client veri, texture/suoni/modelli generati, documentazione, Release e consegna del jar. Usare sempre quando si crea una mod nuova, si aggiorna o si corregge una mod, la si prova, si prepara una Release, si manda un jar o si risponde a domande su queste mod (ricette, comandi, come funziona), anche se l'utente non nomina la skill.
---

# Mod Minecraft per deeplylion-droid

Questa skill riassume come ho lavorato finora in questo repository: chi è l'utente, cosa abbiamo già fatto, come faccio una mod nuova dall'idea al jar consegnato, e gli errori che ho già pagato. Le note tecniche di ogni mod stanno nel `CLAUDE.md` del repository (leggilo sempre: è la fonte più dettagliata); qui c'è il metodo.

File di supporto (leggili quando servono, non tutti subito):
- `references/mod-fatte.md` - le 7 mod: cosa ha chiesto l'utente, cosa è venuto fuori, cosa gli è piaciuto o no, stato delle prove.
- `references/api-26.3.md` - Minecraft 26.3 + Fabric: nomi nuovi, schemi di codice veri presi dalle mod. Da leggere prima di scrivere codice 26.3.
- `references/api-1.21.1-e-forge-1.20.1.md` - le due toolchain più vecchie, per aggiornare quelle mod.
- `references/test-in-gioco.md` - come provo le mod: server dedicato + due client veri headless, input veri con `xdotool`, mod di test che scatta screenshot. I file pronti sono in `harness/`.
- `references/asset.md` - come genero texture, suoni, icone, modelli e immagini di anteprima. Script di esempio in `scripts/`.

## L'utente

- Scrive in **italiano**, spesso dal **telefono**: rispondi in italiano, breve, parole semplici, niente gergo. I numeri importanti in una tabellina.
- Gioca con gli amici (live con circa 8 persone, video YouTube): vuole mod **d'impatto**, che si vedano e si sentano, divertenti o spaventose, che funzionino in **multiplayer**.
- Nella richiesta mette spesso un numero preciso ("10 blocchi di diametro", "40 cuori", "one shotta l'Ender Dragon", "filo di 6 blocchi"): sono requisiti, non esempi. Verificali con una prova vera e scrivili nella sezione della mod in `CLAUDE.md`, così chi viene dopo non li rompe.
- Corregge subito se sbagli strada (es. "Fermo non 1.20, 26.3"): fermati, cambia, non discutere. Il `CLAUDE.md` dice Forge 1.20.1 come default, ma **le ultime tre mod sono tutte Fabric 26.3** e l'utente mi ha corretto quando ho usato 1.20.1: se la versione non è detta, chiedila con una domanda a opzioni (consigliata 26.3).
- Gusti: nell'horror vuole cose enigmatiche, alla *The Broken Script*, non bambinesche (un boss rosa con cuore e forbici "faceva schifo"). Per mostri e boss usa la skill `monster` (`.claude/skills/monster`): tre bozze con render, sceglie lui.
- Le scelte vere (aspetto del boss, tipo di attacco, versione) chiedile con domande a opzioni e una consigliata; il resto decidilo tu e dillo in una riga.
- Il suo PC: Windows, RTX 4090, MultiMC o Prism Launcher. Con 26.3 ha avuto crash nativi del driver NVIDIA in OpenGL (`0xC0000005`): soluzione Vulkan (le mod 26.3 recenti lo scelgono da sole su Windows). Se crasha di nuovo, chiedi il file `hs_err_pid*.log` dell'istanza.
- Mod che usa con gli amici in 26.3: Sodium, Iris (tasti R, K, O), Simple Voice Chat (V, G, M, N, H), WorldEdit. Scegli tasti liberi anche da quelle: in 26.3 vanilla usa la **G** per le azioni rapide; liberi Z, B, J, Y, U, I (J/Y/U/I li usa Filo Rosso).
- Richieste piccole tipiche, e come le ho gestite:
  - "ripassami l'ultima versione di X": controlla `mod_version` e le Release (tag `<mod_id>-v<versione>`), manda quel jar e di' che versione è.
  - "fammi vedere le crafting recipe": elencale in testo (sono nel `README.md`).
  - "tempo per finire?": stima onesta di cosa manca.
  - "spiegami X in breve per spiegarlo agli altri": un elenco corto pronto da incollare.
- Quando lavori a lungo, ogni tanto un messaggio di una riga ("Sto facendo X"): non vede cosa succede finché non scrivi.

## Regole del repository (da non discutere)

- **Non cancellare mai niente**: né mod, né file, né Release, né tag. Anche le cose vecchie che non usi più restano (es. la vecchia geometria di Recisione).
- Lavora solo sul branch indicato nella sessione; mai push su `main`; niente PR se non le chiede.
- Ogni mod nuova sta in `mods/<nome>/` come progetto Gradle autonomo, va aggiunta alla sezione "Altre mod" del `README.md` e ha la sua sezione nel `CLAUDE.md`. Il Würstel resta nella radice.
- Per pubblicare una versione nuova alza `mod_version` nel `gradle.properties` della mod: la CI (`.github/workflows/build.yml`) compila tutto e, **solo su `main`**, crea la Release `<mod_id>-v<versione>` per ogni jar non ancora pubblicato, senza toccare le vecchie.
- Commit in inglese, titolo "`<Mod>: <cosa>`" e un corpo che spiega cosa cambia per chi gioca; aggiungi in fondo le righe di attribuzione che ti dà la sessione (così come sono); per il resto niente nomi di modelli nei commit e nei file. Commit di sola documentazione con `[skip ci]`.

## Come faccio una mod nuova

1. **Capisci la richiesta.** Rileggi i numeri e le parole chiave dell'utente. Chiedi solo quello che cambia il lavoro (versione, una scelta di design vera). Per mostri e boss: skill `monster`.
2. **Lista dei compiti** (4-6 passi): struttura, logica del server, grafica del client, asset, prove, documentazione e consegna.
3. **Struttura**: copia la mod più recente della **stessa toolchain** (per 26.3: `mods/chiappe-colossali/`, la più piccola) con `settings.gradle`, `build.gradle`, `gradle.properties`, e per 26.3 anche `gradlew`, `gradlew.bat` e `gradle/wrapper/`. Cambia `mod_id`, `mod_name`, `maven_group`, `mod_version=1.0.0`, `fabric.mod.json`, il pacchetto Java (`com.<modid>`).
4. **Codice**, nell'ordine in cui conviene:
   - la matematica pura in classi senza Minecraft (dimensioni, danni, traiettorie, tempi): si prova da sola e si cambia in un punto (es. `Butt`, `YoyoMotion`, `ScareTimeline`);
   - il server decide tutto (danni, stato, sincronizzazione con attachment e pacchetti); il client disegna e anima;
   - mixin pochi e precisi, con il descrittore completo quando il metodo è generico o sovraccarico;
   - testi in `it_it.json` e `en_us.json`; comandi e nomi pensati per lui in italiano (`/filo`, `/disabilita`, `/jumpscare`).
5. **Asset** generati con Python (texture PIL/numpy, suoni sintetizzati con numpy + `ffmpeg -c:a libvorbis`, modelli JSON o `.bbmodel`): vedi `references/asset.md`. Gli script restano nella scratchpad, non nel repository; nel `CLAUDE.md` scrivi che gli asset sono generati.
6. **Compila** (`references/api-*.md` per i comandi). Se Forge o Mojang sono bloccati dalla rete, fai il push e leggi l'esito della build su GitHub Actions.
7. **Prova davvero** (`references/test-in-gioco.md`): server + due client, input veri, screenshot, numeri controllati (danni su mob con tanta vita, distanze, tempi). **Guarda le immagini** e correggi quello che si vede male: le chiappe della prima versione sembravano due palle, le ho rifatte sovrapposte, con la fessura e appoggiate a terra.
8. **Documenta**: sezione nel `README.md` (in italiano, per chi gioca: cosa fa, tasti, ricette, comandi, tabellina dei numeri, immagine `docs/<mod>-preview.png`) e sezione nel `CLAUDE.md` (cosa ha chiesto, come è fatta e in che file, come è stata provata, cosa non è stato verificato: "Non ancora provata in gioco dall'utente: se segnala problemi, parti da lì").
9. **Consegna**: commit, push, controlla che la build di GitHub Actions sia verde (strumenti GitHub MCP: `actions_list`/`actions_get`, log con `get_job_logs`), manda il jar locale (`build/libs/`) e l'anteprima con lo strumento che invia file all'utente (`SendUserFile`), e chiudi con un messaggio breve: cosa fa, come si usa, cosa hai verificato e cosa no (l'audio nel sandbox non si sente). La Release su GitHub nasce solo quando il branch arriva su `main` (lo fa l'utente, o una PR se la chiede): diglielo se ti chiede "dov'è la Release". Se la build locale non va, il jar è anche nell'artifact `mods` della build di Actions.

Per **aggiornare** una mod: leggi la sua sezione nel `CLAUDE.md`, correggi, riprova la parte toccata in gioco, alza `mod_version`, aggiorna `README.md` e `CLAUDE.md` (es. la 1.0.1 di Disabilità spiega il bug dei clic), consegna il jar nuovo.

## Toolchain, in breve

| | Forge 1.20.1 | Fabric 1.21.1 | Fabric 26.3 |
|---|---|---|---|
| Mod | Würstel (radice), Yo-Yo | Sotto la Bedrock, Jumpscare! | Disabilità, Filo Rosso, Chiappe Colossali |
| Java | 17 | 21 | 25 |
| Gradle | wrapper della radice (8.8) | wrapper della radice | **wrapper suo** nella cartella (9.8.1) |
| Plugin | ForgeGradle `[6.0,6.2)`, Forge 47.2.0, mappature `official` | Loom 1.7.4, mappature Mojang | Loom 1.19.1 (`net.fabricmc.fabric-loom`), niente mappature (non offuscato) |
| `gradle.properties` | `minecraft_version`, `forge_version`... | `mod_loader=fabric`, `java_version=21` | `mod_loader=fabric`, `java_version=25`, `loom_version` |
| Compilare | `./gradlew -p mods/<x> build` | `./gradlew -p mods/<x> build` | `cd mods/<x> && ./gradlew build` |

- Le mod Fabric usano `splitEnvironmentSourceSets()`: il codice client sta in `src/client/` (il server non lo vede mai).
- Ogni toolchain vuole il suo Java: il wrapper Gradle 8.8 della radice non gira con Java 25, quindi per Forge e Fabric 1.21.1 usa il JDK 17 o 21; per 26.3 il 25. Il `java` di default del container spesso è un altro: imposta sempre `JAVA_HOME` a mano.
- In un container nuovo mancano i JDK: Temurin da `https://api.adoptium.net/v3/binary/latest/<17|21|25>/ga/linux/x64/jdk/hotspot/normal/eclipse` (scompattalo in `/home/user/jdk/` e usa `JAVA_HOME=...`). Servono anche i maven di Fabric, Forge e Mojang per scaricare le dipendenze la prima volta; dopo, `--offline` è più veloce.
- Per leggere il codice di Minecraft 26.3: il jar dei sorgenti che Loom crea in `.gradle/loom-cache/minecraftMaven/...-sources.jar` (dopo `genSources`) va scompattato nella scratchpad e cercato con grep; per le classi di Fabric API, `javap -cp <jar> <classe>`.

## Provare: le regole che contano

Il dettaglio è in `references/test-in-gioco.md`. In breve: `harness/setup.sh mods/<mod> <scratchpad>/test` prepara server, secondo client e mod di test; copi `harness/mp_template.sh` in quella cartella, scrivi le fasi (comandi, tasti, inquadrature, screenshot) e lo lanci in background. Le cose che hanno fatto la differenza:
- Prova con **input veri** (tasti e clic con `xdotool` sulla finestra del gioco), non chiamando metodi via riflessione: in Disabilità 1.0.0 i test passavano ma le carte non si cliccavano.
- Prova in **multiplayer vero** (server dedicato + due client): metà dei bug sono di sincronizzazione o di cosa vede l'altro giocatore.
- Misura i numeri: per i danni usa mob con 1000 di vita (`/summon minecraft:pig ... {NoAI:1b,attributes:[{id:"minecraft:max_health",base:1000}],Health:1000f}`) e leggi la vita dal client; per il drago leggi la vita prima e dopo.
- Dopo aver aggiunto un mixin, fai partire il gioco almeno una volta: con `defaultRequire: 1` un bersaglio sbagliato fa crashare all'avvio.

## Trappole che ho già pagato

- **Mai `pkill -f` o `pgrep -f` con un testo che compare nel tuo stesso comando**: uccide la tua shell. Metti i kill in uno script a parte (`harness/stop.sh`, `harness/cleanup.sh`).
- **Non modificare uno script bash mentre gira** (bash lo legge un pezzo alla volta): copialo e modifica la copia.
- Mondo piatto di prova: gli slime uccidono i giocatori di prova. Metti `difficulty=peaceful` nel `server.properties` **prima** che il mondo nasca. `/kill` sugli slime li divide in slime più piccoli.
- `xdotool key` preme e rilascia nello stesso tick: va bene per i tasti a clic (`consumeClick`, es. la scoreggia), non per quelli tenuti (salto, corsa): usa `keydown`, `sleep 0.15`, `keyup`.
- Le coordinate intere di `/tp` e `/summon` finiscono al centro del blocco (+0,5): tienine conto nelle distanze.
- In pacifico e a sazietà piena i giocatori si curano in fretta: misura i danni sui giocatori subito, o meglio sui mob.
- 26.3: i tasti del mouse seguono SDL3, `InputConstants.MOUSE_BUTTON_LEFT == 1`; la G è di vanilla.
- Danni e difficoltà: i tipi di danno con `scaling` diverso da `never` (es. esplosioni vanilla) sui giocatori cambiano con la difficoltà (0 in pacifico). Per le armi della mod usa un tipo di danno suo con `"scaling": "never"`, così il numero chiesto dall'utente vale sempre.
- Messaggi di morte: con un danno che ha un'entità come fonte la chiave usata è `death.attack.<id>` con due argomenti (vittima, chi ha colpito); `.player` serve solo quando c'è un "kill credit" senza entità.
- Ender Dragon: i danni contano pieni solo su testa e collo (`dragon.hurt(level, dragon.head, source, danno)`); sulle altre parti diventano danno/4 + 1. Ha 200 di vita.
- Nel client senza mod: manda i pacchetti solo a chi li sa ricevere (`ServerPlayNetworking.canSend`); per le mod che non hanno senso senza client, il server butta fuori chi entra senza (Disabilità, Filo Rosso).
- Dopo una modifica grafica controlla anche l'anteprima del giocatore nell'inventario (la chiappa di 10 blocchi riempiva il riquadro).
- Container nuovo: la prima build può fallire con `429 Too Many Requests` da Maven Central (troppi download insieme), non per il codice. Rilancia con `-Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=2000` (Gradle riprova con attese crescenti).
- Container nuovo: `mp_template.sh` avvia server e client con `--offline`, ma la prima volta mancano le librerie di avvio (`dev-launch-injector`, `fabric-log4j-util`) e il server non parte. Lancia `./gradlew downloadAssets` nella cartella della mod e la prima prova da una copia dello script senza `--offline` (`sed -i 's/ --offline//g'`).
