---
name: monster
description: Modelli horror low-poly in stile Minecraft (boss e mob) - .bbmodel Blockbench con texture dettagliata, glow, animazioni (idle, walk, attack1, death) e anteprime obbligatorie. Usare quando si crea o si rifà un mostro, un mob o un boss.
---

# monster.md - Modelli horror low-poly

Istruzioni per creare un modello 3D horror (boss o mob) in stile Minecraft: pochi poligoni, texture molto dettagliata. Si produce un `.bbmodel` (Blockbench) con texture `.png`, texture glow, animazioni e anteprime. Rispondere nella lingua dell'utente (di norma italiano), con tono diretto.

Il livello di qualita' atteso e' alto: volumi complessi e vari, texture ricche con sfumature, dettagli credibili. Niente copie di personaggi, modelli o texture esistenti.

## Principio guida
Lo stile NON e' fisso. La descrizione del mostro (tipo, colore principale, volto, dettagli) la da' sempre l'utente; questo documento decide solo tecnica, qualita' e coerenza. Non aggiungere di default dettagli che l'utente non ha chiesto (denti, occhi, colore), salvo gli elementi fissi sotto.

Generi coperti, anche mescolati: creepy realistico (umanoidi dal corpo sbagliato), carne e corpo deformato, bestie demoniache, incubo lovecraftiano (tentacoli, occhi multipli, masse informi). Proporzioni: umanoide alterato (arti troppo lunghi, testa piccola o assente), bestia, o massa con molti arti, secondo la richiesta.

## Elementi fissi (se hanno senso col mostro)
- Silhouette riconoscibile in controluce: un elemento forte (corna, tentacoli, braccia extra, colonna vertebrale esposta...), scelto in base alla descrizione.
- Occhi vuoti e luminosi (niente pupille, colore accento con glow). Se non ha occhi, un'altra zona luminosa.
- Gore medio: macchie di sangue, crepe, ferite, ossa o carne in vista; mai splatter totale. Denti grandi e insanguinati solo se richiesti o coerenti.
- Zone luminose (glow) per occhi, ferite e venature, con un colore accento diverso dal principale.
- Usura: crepe, macchie di sangue, bende, ruggine o muffa se coerenti.
- Nessun divieto di contenuto a priori; se una richiesta e' al limite, chiedere.

## Colore
- Il colore principale lo da' l'utente; se manca, sceglierlo coerente col mostro e dichiararlo.
- Mai tinta unita: ogni colore e' una rampa di 4-6 toni (ombra, medio, luce) con piccoli spostamenti di tinta (ombre verso viola/blu o rosso scuro, luci piu' calde). Aggiungere rumore leggero, macchie e sfumature tra le zone.
- 3-5 tinte dominanti piu' 1 colore accento per il glow. Nero e bianco solo come dettaglio (bocca, denti, ossa).
- Il colore deve avere senso col mostro (carne marcia = rossi, viola, giallo-verde malati; ossa = avorio sporco; ombre = grigio-blu).
- Texture piu' ricca per i boss grandi (centinaia di tonalita'); piu' pulita e a pochi colori (circa 30) per i mob piccoli.

## Valori di riferimento (per tarare qualita' e budget)
| tipo | cubi | altezza | texture | texel per unita' | colori |
|---|---|---|---|---|---|
| umanoide creepy | ~55 | ~68 px (4,3 blocchi) | 256 (UV 128) | ~1,0 | ~30, pixel puliti |
| demone enorme con tentacoli | ~155 | ~150 px (9,5 blocchi) | 256 (UV 128) | ~0,8 | ~30, rampe scure |
| bestia a piu' teste | ~90 | 4 zampe | 512 (UV 512) | ~1,35 | ~700, molto ricca |

(1 blocco = 16 px. La texture e' spesso 2x la risoluzione UV; i texel per unita' sono calcolati sulla texture reale.)

## Regole tecniche
- Budget cubi: boss 60-120, mob piccolo 20-50. Sono tetti di partenza: si superano se l'utente lo chiede (dichiarandone il costo in prestazioni).
- Dimensioni: i boss sono grandi, almeno 3 blocchi (48 px) di altezza, spesso 4-9. Mob piccoli sotto i 2 blocchi.
- Texture: boss grandi 256x256 (UV 128) o 512 se serve molto dettaglio; mob piccoli 64-128. Densita' di texel tra 0,7 e 1,4 per unita': piu' alta sul volto e nelle zone viste da vicino, piu' bassa su schiena e parti coperte.
- Formato: Blockbench `bedrock` o `free`, `box_uv: false`, UV per faccia, cubi ruotati dove serve forma organica (di norma 40-70% dei cubi ha rotazione). Se una faccia e' ruotata, `uv_rotation`.
- Ossa a gerarchia vanilla: `all > body > chest > head / leftarm / rightarm`, `leftleg / rightleg`, bracci a 2-3 segmenti (`bone`, `bone2`...). Tentacoli, code e colonne vertebrali: catene di 6-8 ossa annidate. Aggiungere sempre l'osso `hitbox` alla radice (cubo che racchiude il corpo a riposo).
- Fronte verso -Z. Pivot delle ossa sulle giunture.
- Animazioni (solo queste, salvo richiesta): `idle` (loop), `walk` (loop), `attack1`, `death`. Interpolazione catmullrom. Come devono essere fatte: vedi le sezioni "Qualita' delle animazioni", "Movimento" e "Attacco".
- Un solo stadio del modello; ulteriori varianti o fasi solo se l'utente le chiede dopo.
- Texture glow separata `<nome>_glow.png`: solo le zone luminose, il resto trasparente.

## Qualita' delle animazioni
- Sempre molto fluide e coerenti: niente pose a scatti, niente rotazioni rigide del corpo intero, niente inclinazioni in avanti al posto di un attacco.
- Curve morbide (catmullrom), con anticipazione (la preparazione prima del colpo), azione veloce, rilascio e ritorno lento alla posa di riposo. Il peso del mostro si vede: i boss grandi sono lenti e pesanti, i mob piccoli scattanti.
- Parti secondarie in ritardo rispetto al corpo (code, tentacoli, mantelli, catene di ossa): ogni osso della catena parte con uno sfasamento crescente, cosi' ondeggiano invece di muoversi in blocco.
- I loop (`idle`, `walk`) devono chiudersi senza stacchi: primo e ultimo fotogramma uguali, durata coerente con la velocita' reale.
- Usare tutte le ossa del modello: se ci sono tentacoli, braccia extra o testa, devono partecipare a ogni animazione.
- Coerenza con lo stile e l'anatomia: l'animazione segue come e' fatto il mostro, mai un movimento generico da zombie applicato a tutti.
- Controllare il risultato guardando i fotogrammi (sequenza e GIF) prima di consegnare; se si nota uno scatto o una compenetrazione, correggerla.

## Movimento (camminata)
Il tipo di movimento deve essere coerente con il mostro: se fluttua, si sposta fluttuando in avanti (ondeggio lento, nessun passo a terra); se ha tentacoli, striscia o avanza coi tentacoli; se e' una bestia, cammina a quattro zampe con peso; se e' un umanoide deforme, andatura storta e innaturale (arti che si trascinano, scatti controllati); se e' enorme, passi lenti e pesanti.
- Se l'utente non ha specificato il movimento: proporre **3 metodi di movimento** diversi e adatti a quel mostro, come **domanda a scelta multipla cliccabile** (in Claude Code lo strumento di domande a scelta, AskUserQuestion; altrove l'equivalente; se non esiste, 3 opzioni numerate A/B/C). Ogni opzione ha un nome corto e una frase di spiegazione; segnare quella consigliata come prima.
- Se l'utente lo ha specificato, non chiedere: usare quello.
- Il movimento scelto deve valere anche per `idle` (es. un mostro che fluttua ha un idle di galleggiamento).

## Attacco
L'attacco deve nascere dal corpo del mostro, mai una semplice inclinazione scattosa in avanti. Esempi: tentacoli che frustano o trafiggono, braccia lunghe che spazzano o afferrano, morso con testa che scatta in avanti e fauci che si aprono, corna che incornano, coda che colpisce, onda di energia dalle zone luminose. Preparazione visibile, colpo, ritorno.
- Se l'utente non ha specificato come attacca: scegliere cio' che e' coerente col mostro e proporre **3 opzioni di attacco** diverse come domanda a scelta multipla cliccabile (stesso strumento del movimento; altrimenti A/B/C), con nome corto, una frase di spiegazione e la consigliata per prima. Esempio per un mostro con tentacoli: frusta a ventaglio, perforazione frontale, presa e stritolamento.
- Se l'utente lo ha specificato, non chiedere: usare quello.
- Una sola domanda per movimento e una per attacco, poste insieme nello stesso momento (all'inizio del metodo, prima delle bozze di concetto), cosi' l'utente decide tutto in un colpo.

## Formato del file .bbmodel (da scrivere direttamente a mano in JSON)
Il `.bbmodel` e' un JSON che Blockbench apre direttamente. Il file finale deve aprirsi senza errori e con la texture gia' applicata. Struttura minima richiesta (basta che Blockbench lo apra: non serve replicare un file in particolare):
- Radice: `meta` (`{"format_version": "4.5", "model_format": "bedrock", "box_uv": false}`), `name`, `resolution` (`{"width": 128, "height": 128}` = risoluzione UV), `elements`, `outliner`, `textures`, `animations`.
- Ogni cubo in `elements`: `{"name", "box_uv": false, "from": [x,y,z], "to": [x,y,z], "origin": [x,y,z] (pivot di rotazione), "rotation": [rx,ry,rz] (opzionale), "faces": {"north","east","south","west","up","down": {"uv": [u1,v1,u2,v2], "texture": 0}}, "type": "cube", "uuid": "<uuid>"}`. Le facce che non servono si omettono. Unita' in pixel (16 = 1 blocco), Y verso l'alto.
- `outliner`: albero di gruppi/ossa `{"name", "origin": [x,y,z] (pivot), "rotation": opzionale, "uuid", "children": [...]}`. In `children` i cubi sono indicati con il loro `uuid` (stringa) e le ossa figlie come oggetti annidati.
- `textures`: `[{"name": "<nome>.png", "mode": "bitmap", "id": "0", "uuid": "<uuid>", "source": "data:image/png;base64,<PNG in base64>"}]`. La texture **va incorporata come base64** nel `source`, cosi' il modello si apre gia' texturizzato; consegnare comunque anche il `.png` a parte.
- `animations`: `[{"uuid", "name": "idle", "loop": "loop" oppure "once", "length": <secondi>, "animators": {"<uuid del gruppo>": {"name": "<nome osso>", "type": "bone", "keyframes": [{"channel": "rotation" | "position" | "scale", "data_points": [{"x": 0, "y": 0, "z": 0}], "time": <secondi>, "interpolation": "catmullrom" | "linear", "uuid": "<uuid>"}]}}}]`. Posizione in pixel, rotazione in gradi.
- Tutti gli `uuid` sono stringhe uniche (generabili con `uuid.uuid4()`).
- Dopo averlo scritto: ricaricare il file con Python (`json.load`) e verificare che ogni uuid dei `children` esista in `elements`, che ogni `uv` stia nei limiti della texture, che i cubi con facce abbiano `texture: 0`, e che il PNG incorporato si decodifichi. Fare un render con uno script proprio per vedere il modello prima di consegnare.
- Dire sempre all'utente: "apri il file in Blockbench (File > Open Model)" e che le anteprime sono render dello script, non di Blockbench, quindi piccole differenze di ombre sono normali.

## Metodo
1. L'utente descrive il mostro (tema, colore, dettagli). Se manca qualcosa che cambia molto, fare una sola domanda; altrimenti decidere e dichiarare la scelta.
2. Se movimento o attacco non sono specificati, porre le domande cliccabili descritte sopra (insieme, in un solo momento). Poi fare 3 bozze di concetto diverse (forma e silhouette, non solo colore), ognuna in 1-2 frasi e con un render di anteprima. L'utente sceglie e si rifinisce solo quella.
3. Costruire il modello e le animazioni (nel movimento e nell'attacco scelti) con uno script Python deterministico (stesso seed, stesso risultato): cubi, UV e texture generati da codice (Pillow, numpy).
4. Verifica visiva obbligatoria prima di consegnare: guardare davvero le anteprime e controllare cubi senza texture, facce nere, UV sovrapposte, aree vuote, proporzioni.
5. Consegna: bbmodel, png, glow, animazioni, anteprime e un file di istruzioni con i nomi di ossa e animazioni. Dire onestamente cosa non e' stato verificato in un motore reale.
6. Le modifiche chieste dopo toccano solo cio' che e' stato chiesto; il resto resta identico.

## Anteprime (obbligatorie a ogni consegna)
Non consegnare mai un modello senza render. Metterle in una cartella `anteprime/` e allegarle alla risposta:
- Render fermo da piu' angoli (davanti, lato, dietro, tre quarti) su sfondo scuro neutro, in un'unica immagine.
- GIF di `idle`, `walk` e `attack1` (e `death`) con un giocatore di Minecraft accanto per la scala; loop corti e fluidi.
- Sequenza dell'attacco in un PNG con 6-8 fotogrammi affiancati.
- Texture ingrandita (nearest neighbor x8-12) con la texture glow accanto, piu' un'immagine del modello con e senza glow su sfondo scuro.
- Dettaglio del volto ingrandito.
- Per le bozze di concetto: una immagine per bozza, stesso angolo e stessa luce, affiancate.
- Dopo ogni modifica rifare solo le anteprime toccate.
- Segnalare onestamente cio' che il render non mostra (ombre, trasparenze, comportamento in un motore reale).

## Cosa NON fare
- Niente copie di personaggi, modelli o texture esistenti.
- Niente pattern a griglia, texture piatte, colori saturi senza sfumature.
- Niente effetti troppo densi su aree grandi.
- Non consegnare senza anteprime.
