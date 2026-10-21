# Le mod fatte finora

In ordine di tempo. Per ognuna: cosa ha chiesto l'utente, cosa c'è, cosa è emerso parlando con lui, stato delle prove. I dettagli tecnici (classi, file, numeri) sono nella sezione della mod nel `CLAUDE.md`; le istruzioni per chi gioca (ricette, tasti, comandi) nel `README.md`.

| Mod | Cartella | `mod_id` | Toolchain | Versione |
|---|---|---|---|---|
| Würstel Gigante | radice | `wurstel` | Forge 1.20.1 | 1.1.0 (la prima Release ha il tag `v1.0.0`) |
| Yo-Yo Arcobaleno | `mods/rainbow-yoyo/` | `rainbowyoyo` | Forge 1.20.1 | 1.0.0 |
| Sotto la Bedrock | `mods/below-bedrock/` | `belowbedrock` | Fabric 1.21.1 | 1.1.0 |
| Jumpscare! | `mods/jumpscares/` | `jumpscares` | Fabric 1.21.1 | 1.0.0 |
| Disabilità | `mods/disabilities/` | `disabilities` | Fabric 26.3 | 1.0.1 |
| Filo Rosso | `mods/filo-rosso/` | `filorosso` | Fabric 26.3 | 1.0.0 |
| Chiappe Colossali | `mods/chiappe-colossali/` | `chiappe` | Fabric 26.3 | 1.0.0 |
| Frusta Imperiale | `mods/frusta-imperiale/` | `frusta` | Forge 1.20.1 | 1.0.1 |
| Infezione Zombie | `mods/infezione-zombie/` | `infezione` | Forge 1.20.1 | 1.0.0 |

C'è anche la skill `monster` (`.claude/skills/monster/`), fatta per l'utente: modelli horror low-poly in Blockbench con tre bozze, texture, glow, animazioni e anteprime.

## Würstel Gigante (Forge 1.20.1)

- **Richiesta**: una cintura che porta un würstel 3D gigante (4 blocchi, spesso ¼) attaccato alla fibbia e puntato dove guardi; a mano vuota i pugni diventano schiaffi di würstel morbidi, da angolazioni sempre diverse.
- **1.1.0**: cinture di diamante e netherite (danno ×4/×8, scavo e drop come gli attrezzi) e l'incantesimo **Turgido** I-V (×2 a livello). Requisito: **netherite + Turgido V uccide l'Ender Dragon con uno schiaffo** (8 × 32 = 256 > 200).
- **Ricette** (le ha chieste tutte una volta): würstel = bistecca di maiale cotta + filo (senza forma, 2 würstel); cintura = cuoio, lingotto d'oro, cuoio sopra tre würstel; diamante = 4 diamanti a croce intorno alla cintura (gli incantesimi restano); netherite = tavolo da forgia con modello di potenziamento + cintura di diamante + lingotto di netherite.
- **Stato**: la 1.0.0 l'ha provata in gioco e "funziona benissimo". La 1.1.0 è stata verificata solo compilando. Una volta ha chiesto "ripassami l'ultima versione, quella col würstel di netherite": gli ho rimandato `wurstel-1.1.0.jar` dopo aver controllato che fosse davvero l'ultima.

## Yo-Yo Arcobaleno (Forge 1.20.1)

- **Richiesta**: yo-yo arcobaleno gigante (1,25 blocchi), animazione di lancio ricca, trick, arma da **40 cuori** (80 di danno), suoni, modello 3D integrato col personaggio, multiplayer.
- Tasto destro tenuto = lancio e sleeper; sinistro = colpo o trick (Loop the Loop, Shoot the Moon, Walk the Dog, Around the World), i quattro in combo caricano la Supernova.
- **Stato**: verificata compilando e con un'anteprima offline delle traiettorie; non provata in gioco.

## Sotto la Bedrock (Fabric 1.21.1)

- **Richiesta**: mod "misteriosa" per video YouTube, tutta in stile vanilla: piccone di diamante glitchato (ricetta capovolta) che rompe la bedrock; nel vuoto non si muore ma si cade 30-40 secondi; poi una dimensione segreta che è l'overworld sbiadito, infinito, con dettagli liminali e inquietanti, mappe con immagini di un umanoide che ci vive, l'entità stessa con comportamenti, eventi glitch, multiplayer.
- **1.1.0**, richiesta dopo: nel mondo sbiadito **non cresce niente** (niente alberi, erba, fiori, funghi, alghe; campi dei villaggi vuoti).
- Comando `/faded` per i video (`him <modalità>`, `event <nome>`, `fall`).
- **Stato**: provata con server dedicato e client headless guidato da una mod di test; non ancora in gioco dall'utente.

## Jumpscare! (Fabric 1.21.1)

- **Richiesta**: jumpscare super spaventosi e rumorosi per spaventare gli amici in multiplayer, tremolio dello schermo, urla, facce orribili; un comando e delle "perle" (una per mostro) che spaventano chi colpiscono; il mostro deve essere un'**entità 3D che ti salta in faccia, non un'immagine 2D**; **cinque** diversi.
- Clown, Dama Bianca, Ragno, Cadavere, Demone; `/jumpscare <giocatori> [mostro] [secondi]`; chi non ha la mod non vede niente.
- **Stato**: provata con client headless e in multiplayer vero (6 spaventi su 6); audio non verificato.

## Disabilità (Fabric 26.3)

- **Richiesta**: per una live in vanilla con 8 amici, sull'ultima versione: alla prima entrata si devono **scegliere obbligatoriamente due disabilità** tra una ventina, il più possibile **impattanti e visibili** per chi le ha e per chi guarda.
- 21 disabilità (cieco col bastone, sedia a rotelle, Parkinson, narcolessia, Tourette, muto con gesti, dislessia, albino...), accessori 3D visti dagli altri, effetti sullo schermo, `/disabilita`.
- **1.0.1**: nella 1.0.0 le carte della scelta non si cliccavano (solo *Casuale*): in 26.3 il tasto sinistro del mouse vale 1, non 0. Da allora le schermate si provano con clic veri.
- Crash dell'utente all'avvio (driver NVIDIA in OpenGL): risolto con Vulkan. **Usata in live** con gli amici.

## Filo Rosso (Fabric 26.3)

- **Richiesta**: dopo Disabilità (una ragazza del gruppo non voleva giocarla in live), una mod per una vanilla da più live, "legati a coppie" espansa e romantica: filo di **6 blocchi**, all'inizio **ognuno sceglie a chi legarsi**, **vita condivisa**, boss finale **gigante mostruoso umanoide** (non un ragno), **enigmatico, non bambinesco, alla The Broken Script**.
- Filo con fisica sul client, livelli del legame e abilità (Y, U, I), destini per live, eventi ogni 20 minuti, imprese con classifica, a terra e rianimazione, Spaiati e schegge di cuore.
- Il primo boss ("Lo Spezzacuori", rosa con cuore e forbici) all'utente faceva schifo. Rifatto con la skill `monster`: ha scelto la bozza **"B - La Marionetta"**, movimento "passi lenti e storti", attacco "frusta di tentacoli". Ora è **Recisione**, marionetta di corda nera alta 10 blocchi (modello `.bbmodel` in `mods/filo-rosso/blockbench/`).
- **Stato**: provata con server e due client veri (anche Vulkan e un client di produzione); non ancora in gioco dall'utente. Una volta ha chiesto una spiegazione breve "per spiegarla agli altri": gli ho dato un elenco di 9 punti.

## Chiappe Colossali (Fabric 26.3)

- **Richiesta**: "aggiungi le chiappe ai modelli di Minecraft, delle grosse chiappone che ad ogni livello di XP diventano più grandi. A livello 25 [...] davvero colossali: qualcosa come **10 blocchi di diametro per chiappa**. [...] un **attacco scoreggia** che colpisce ad area intorno al player e fa più danno con i livelli di XP. A livello 25 **one shotta Ender Dragon**." Poi ha corretto: "Fermo non 1.20, 26.3".
- Chiappe con la texture dei pantaloni della skin, molle che le fanno ballare, al 25 appoggiate a terra col giocatore nella fessura; scoreggia col tasto **Z** (la G in 26.3 è di vanilla), raggio 2,8-10 blocchi, danno 2-159 fino al 24 e 1000 al 25; il drago al 24 resta a 41/200, al 25 muore; scoreggia atomica con fungo e schermo che trema.
- Prima versione: le chiappe sembravano due palle attaccate alla schiena; le ho rifatte come ellissoidi sovrapposti con ombra nella fessura, e quelle grandi appoggiate a terra.
- **Stato**: provata con server e due client veri (danni misurati, drago, razzo in aria, messaggi di morte); non ancora in gioco dall'utente, audio non verificato.

## Frusta Imperiale (Forge 1.20.1)

- **Richiesta** (la prima con il nuovo collaboratore): "una frusta che io possa usare per frustare i miei amici [...] imperiosa sia nell'aspetto da ferma, che nell'animazione e nel suono di schiocco, che deve **troneggiare potente su ogni altro suono** di minecraft [...] **pelle nera**, che si possa anche upgradare in **diamante e netherite**, con anche un libro di enchant personalizzato di **Sottomissione (I-V)**. La frusta in **netherite con Sottomissione V deve oneshottare l'enderdragon**. [...] Memorabile. Come dimensione deve estendersi almeno **4-6 blocchi**." Questa volta la versione l'ha detta lui: Forge 1.20.1.
- Scelte (domande a opzioni, ha preso sempre la consigliata): tutti e tre i comandi (sinistro frustata, destro tenuto rotazione + Schiocco Imperiale, accovacciato + destro presa che tira a sé), da ferma "strascico a terra", Sottomissione = danno ×2 a livello + in ginocchio.
- Lunga 5 / 5,5 / 6 blocchi; danno 4 / 6 / 8, con Sottomissione V 128 / 192 / 256: solo la netherite V uccide il drago. Lo schiocco abbassa per un attimo tutti gli altri suoni (`SoundDucker`).
- **Stato**: prima mod Forge provata con server e due client veri (nato per lei il banco di prova `harness/forge/`): numeri, drago, presa, ginocchio, PvP verificati. L'utente l'ha provata: "tutto bellissimo", tranne la frusta che durante la rotazione si staccava dal manico (corretto nella **1.0.1**: guarda sempre da vicino dove la frusta esce dall'impugnatura, anche nei primi tick delle mosse e in prima persona). Audio non verificato.

## Infezione Zombie (Forge 1.20.1)

- **Richiesta**: "Nuova mod Minecraft forge 1.20.1. La userei in un modpack epidemia zombie a fini di video su YouTube. Mi serve che io possa contrarre infezione zombie, che abbia **5 stadi di infezione, ben visibili nella GUI**. Anche sul **personaggio** si deve vedere bene che sta succedendo qualcosa. La skin su cui verrà applicata è **lyonWGF**." Poi: "Sappi che la serie è **horror, dark e matura**", "**bolle 3D** sopra la skin negli stadi più avanzati", "una bella **barra in alto a sinistra** con il progresso dell'infezione, animata, **stile genoma che si corrompe**".
- Scelte (domande a opzioni, sempre la consigliata): stile "Cadavere" (tre bozze disegnate sulla sua skin vera, scaricata e trasformata in Python), contagio solo col comando, stadio 5 = zombie giocabile (si torna umani morendo), antidoto fino allo stadio 4.
- Cosa c'è: skin trasformata per stadio sul client (pelle grigia, vene dal morso, occhi, sangue, vestiti strappati; funziona con qualsiasi skin), bolle 3D che pulsano e scoppiano, occhi che brillano al buio, pose e sintomi visti da tutti (tosse, spasmi, convulsioni, svenimenti), genoma animato, cuori infetti, vene ai bordi, titoli; zombie amici degli zombie che brucia al sole; `/infezione` con `sintomo` per far partire un sintomo durante le riprese.
- Mi ha chiesto "tra quanto mi mandi il jar?" mentre rifinivo: gli ho mandato subito il jar che funzionava, poi ho continuato con i ritocchi. Quando chiede il jar e la mod è già provata, mandalo e di' cosa manca.
- **Stato**: provata con server e due client veri (cinque giri di prova); non ancora provata in gioco dall'utente, audio non verificato.

