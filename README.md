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

Una mod misteriosa per **Fabric 1.21.1**: serve anche **Fabric API**. Jar: `belowbedrock-1.1.0.jar` nelle [Releases](../../releases). Va installata anche sul server, in multiplayer.

- **Piccone di diamante glitchato**: la ricetta del piccone di diamante, ma al contrario. La texture ogni tanto si glitcha e una lettera del nome non si legge mai.
  ```
              bastone
              bastone
  diamante    diamante    diamante
  ```
- È l'unico attrezzo che **rompe la bedrock** (circa 4 secondi a blocco), solo nel mondo normale e in quello sbiadito.
- **Buttati nel vuoto**: non muori. Cadi per 30-40 secondi nel buio, con il vento, un disco suonato troppo lento, un battito che accelera, voci e glitch. Poi diventa tutto nero...
- ...e ti svegli nel **mondo sbiadito**: è la mappa del tuo mondo (stesso seme, infinita come quella normale), ma senza colori, senza sole, senza animali né mostri, sempre di giorno, nella nebbia. E non ci cresce niente: niente alberi, niente erba, niente fiori, funghi o alghe; il terreno è terra nuda e nei villaggi i campi sono vuoti. E la tua casa non c'è.
- In giro trovi dettagli inquietanti: cartelli che sanno troppo, pezzi di muro in mezzo al nulla, tunnel 2x2, piramidi di sabbia nel mare, porte che non portano da nessuna parte, letti all'aperto, fuochi eterni, accampamenti abbandonati, bauli sepolti sotto un cartello "QUI", stanze vuote (gialle, bianche o camere da letto), pozzi dritti fino alla bedrock, figure ferme nella nebbia. E le **mappe**: 9 immagini di Lui, appese ai muri e nelle stanze o chiuse nei bauli, insieme al diario di chi è caduto prima di te.
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

### Jumpscare! (`mods/jumpscares/`)

![I cinque mostri in faccia, e quello che vede chi guarda](docs/jumpscares-preview.png)

Jumpscare da fare agli amici, per **Fabric 1.21.1**: serve anche **Fabric API**. Jar: `jumpscares-1.0.0.jar` nelle [Releases](../../releases). Va installata sul server e da tutti i giocatori (chi non ce l'ha non vede niente).

- **Cinque mostri 3D** che ti saltano in faccia, ognuno con entrata, urla ed effetti suoi:
  - **Il Clown**: salta fuori dal basso su una molla, come un pupazzo a molla, ride e poi ti urla in faccia con due file di denti a punta. Flash rosso e coriandoli.
  - **La Dama Bianca**: si avvicina a scatti tra le scariche di disturbo, con la testa piegata; poi la mascella le scivola giù e si apre un buco nero. Strillo da banshee, e dopo qualche secondo di Oscurità.
  - **Il Ragno**: cala dall'alto sul suo filo, occhi lucidi, zanne che colano veleno, zampe che ti avvolgono. Sibilo, ragnatele e Lentezza (resti paralizzato).
  - **Il Cadavere**: arriva a scatti come un pupazzo rotto, con un occhio fuori dall'orbita e la testa che si contorce. Ruggito marcio e Nausea.
  - **Il Demone**: sale dal basso tra le fiamme, corna, crepe di lava e la gola come una fornace. Ruggito profondo con un boato.
- Ogni jumpscare: il mostro arriva a pochi centimetri dalla telecamera, lo schermo trema, flash colorato, disturbo e vignetta; poi l'ultimo balzo dentro la sua bocca e tutto nero. L'HUD sparisce (e gli effetti si vedono anche con F1). Chi lo subisce fa un piccolo salto indietro.
- **Suoni fortissimi**: urla sintetizzate (suonate due volte insieme) e un colpo d'orchestra. Occhio alle cuffie.
- **Perle Spavento**, una per mostro: si lanciano come le perle di ender. Chi viene colpito, o è a meno di 2,5 blocchi da dove cade, si prende quel jumpscare; chi l'ha lanciata legge "Hai spaventato ...!". Lanciata ai tuoi piedi spaventa te, così la provi anche da solo. Funzionano anche nel distributore: una trappola dietro una piastra a pressione.
- **Ricette** (senza forma, danno 2 perle):
  - Clown: perla di ender + colorante rosso + colorante bianco
  - Dama Bianca: perla di ender + membrana di fantasma + colorante nero
  - Ragno: perla di ender + occhio di ragno + filo
  - Cadavere: perla di ender + carne marcia + osso
  - Demone: perla di ender + polvere di blaze + carica di fuoco
- **Comando** (da operatore, o con i trucchi attivi): `/jumpscare <giocatori> [clown|dama|ragno|cadavere|demone|casuale] [secondi]`. Con i secondi parte più tardi, così hai il tempo di fare l'indifferente. Il messaggio di conferma lo vedi solo tu.
- **Multiplayer**: chi è vicino vede il mostro saltare in faccia alla vittima, lo sente urlare... e a un certo punto il mostro gira la testa e fissa anche lui.
- Accessibilità: *Effetti di distorsione* attenua il tremolio, *Lampi nel cielo attenuati* attenua i flash.

### Disabilità (`mods/disabilities/`)

![La scelta obbligatoria e alcune disabilità, viste da dentro e da fuori](docs/disabilities-preview.png)

Per **Fabric 26.3** (l'ultima versione di Minecraft): serve **Fabric API 0.163.0+26.3** e **Java 25**. Jar: `disabilities-1.0.1.jar` nelle [Releases](../../releases) (la 1.0.0 non faceva cliccare le carte nella scelta: usate la 1.0.1). Va installata sul server **e da tutti i giocatori**: chi entra senza la mod viene rimandato indietro con un messaggio.

- **Alla prima entrata nel mondo è obbligatorio scegliere due disabilità** su 21, da una schermata che non si può chiudere (finché scegli non puoi essere ferito). Passando sopra una carta leggi cosa cambia per te e cosa vedono gli altri. Il pulsante *Casuale* sceglie per te.
- **Ognuno le sue**: due giocatori non possono avere la stessa disabilità (con 8 giocatori se ne usano 16 su 21). Le carte già prese sono segnate *PRESA*; alcune coppie non vanno insieme (cieco + miope, sedia a rotelle + stampelle, stampelle + un braccio solo...).
- Quando qualcuno sceglie, in chat compare per tutti: "*Mario ha scelto: ♿ Paraplegia e 🔇 Sordità*" (passa sopra i nomi per la descrizione).
- **Le icone delle disabilità** stanno dopo il nome di ognuno: sopra la testa, nella lista giocatori (Tab), in chat e nei messaggi di morte. Le tue sono anche in alto a sinistra.
- Gli effetti sulla vista restano anche nascondendo l'HUD (F1) e in terza persona: niente trucchi.

| Disabilità | Cosa cambia per te | Cosa vedono gli altri |
|---|---|---|
| Cecità | Vedi solo a 3 blocchi, cielo compreso; chi fa rumore si illumina per un attimo. Col **bastone bianco** (tasto destro) per qualche secondo vedi intorno | Occhiali scuri, il bastone che batte a terra mentre cammini |
| Acromatopsia | Tutto in bianco e nero, inventario compreso | Occhiali con le lenti rosa |
| Miopia grave | Oltre pochi blocchi è tutto sfocato | Occhiali spessi |
| Cieco da un occhio | Metà sinistra dello schermo buia | Benda sull'occhio |
| Albinismo | Di giorno all'aperto il sole ti abbaglia e, senza elmo, ti brucia | Capelli bianchi e pelle pallidissima (dalla tua skin); fumi al sole |
| Sordità | Nessun suono, neanche la musica | Apparecchi acustici |
| Mutismo | Non puoi scrivere in chat (né /msg, /me...): fai i **gesti** col tasto **G** (Ciao, Sì, No, Vieni qui, Aiuto, Pericolo, Grazie, Seguimi) | Il gesto con le braccia, un fumetto sopra la testa e una riga in chat per chi è vicino |
| Dislessia | Tutte le scritte hanno le lettere che si mescolano (menu, chat, cartelli, inventario) | I tuoi messaggi in chat arrivano con le lettere scambiate |
| Paraplegia | Sedia a rotelle: niente salti, niente scale a pioli, liane o impalcature; più basso. La vedi anche in prima persona | Seduto sulla sedia, le ruote girano mentre ti muovi |
| Gamba amputata | Stampelle: più lento, niente corsa, salti bassi; **per usare le mani devi fermarti** | Una gamba sola e le stampelle che dondolano |
| Braccio amputato | Niente mano secondaria; archi, balestre, tridenti e lance non si usano; scavi più piano | Un braccio solo |
| Nanismo | Alto la metà, braccia corte (arrivi meno lontano), più lento | Sei piccolissimo |
| Obesità | Più lento, fame doppia, dopo 4 secondi di corsa hai il fiatone, le cadute fanno più male; però ti spostano meno | Corporatura enorme, fiatone e sudore |
| Parkinson | La mira trema sempre; ogni tanto le gambe si bloccano e ti scivola di mano quello che tieni | Testa e braccia che tremano |
| Narcolessia | Ogni 3-6 minuti ti addormenti di colpo per qualche secondo (schermo nero), a volte anche quando vieni colpito. Un amico ti sveglia col tasto destro | Crolli a terra, russi, "Zzz" sopra la testa |
| Sindrome di Tourette | Tic: scatti della testa, salti, colpi nel vuoto (a volte colpisci davvero chi hai davanti), giravolte, ammiccamenti, versi e parole a caso | Vedono i tic, sentono i versi, leggono le parole in chat e nel fumetto ("PATATA!") |
| Amnesia | Niente F3 (coordinate), niente libro delle ricette e niente segnalini dei compagni; i nomi degli altri diventano "???"; ogni 5-9 minuti l'inventario si rimescola | Ti gratti la testa sotto un "?" |
| Vertigini | Il mondo ondeggia, vicino ai burroni gira tutto (e lo zoom impazzisce), cammini storto e ogni tanto inciampi | Barcolli e inciampi |
| Asma | Correre, saltare, nuotare e arrampicarti ti tolgono il fiato (le bollicine dell'aria); senza fiato vedi buio ai lati e rallenti. Hai un **inalatore** | Il respiro che fischia, la mano sul petto |
| Emofilia | Ogni ferita continua a sanguinare (e intanto non guarisci) finché non usi una **benda** | Gocce di sangue, la mano sulla ferita; puoi fasciarli tu (tasto destro con la benda) |
| Allergia | Vicino a fiori, erba, foglie e animali starnutisci: la testa scatta, gli occhi lacrimano e a volte ti cade quello che tieni | Starnuti fortissimi |

- **Oggetti** (in Creativa nella scheda *Strumenti e utilità*; il bastone e l'inalatore te li dà la mod, e te li ridà quando rinasci):
  - **Bastone bianco**: `bastone` + `bastone` + `colorante bianco`;
  - **Inalatore** (16 spruzzi): `lingotto di ferro` + `ampolla di vetro`;
  - **Benda** (×2): `carta` + `carta` + `filo`.
- **Comando** `/disabilita`:
  - per tutti: `/disabilita` (le tue), `/disabilita lista` (di tutti), `/disabilita info <disabilità>`;
  - da operatore: `scegli [giocatori]` (fa riscegliere), `assegna <giocatori> <prima> <seconda>`, `casuale [giocatori]`, `togli <giocatori>` (nessuna disabilità, per esempio per chi fa le riprese), `evento <sonno|sveglia|tic|starnuto|inciampo|blocco|dimenticanza|ferita|fiato> [giocatori]` per far succedere subito un episodio.
  - I nomi da usare: `cieco`, `acromatopsia`, `miope`, `monocolo`, `albino`, `sordo`, `muto`, `dislessico`, `sedia`, `stampelle`, `monco`, `nano`, `obeso`, `parkinson`, `narcolessia`, `tourette`, `amnesia`, `vertigini`, `asma`, `emofilia`, `allergia` (il tab li suggerisce).
- **Gamerule**: `/gamerule disabilities:unique false` (disabilità ripetibili), `/gamerule disabilities:reroll_on_death true` (quando muori riscegli), `/gamerule disabilities:episodes false` (mette in pausa sonni, tic, starnuti, blocchi, inciampi e dimenticanze).
- In live: la mod non può spegnere il vostro microfono su Discord, quindi chi è muto o sordo deve stare al gioco.
- **Se Minecraft si chiude da solo mentre carica** (con schede NVIDIA, codice `-1073741819` / `0xC0000005` nel log, senza errori Java): si rompe il driver video mentre il gioco prepara la grafica. Nelle impostazioni *Video* scegliete **Vulkan** come API grafica (o mettete `preferredGraphicsBackend:"vulkan"` in `options.txt`), oppure nel Pannello di controllo NVIDIA spegnete *Threaded optimization* per `javaw.exe`; con MultiMC conviene Prism Launcher.
