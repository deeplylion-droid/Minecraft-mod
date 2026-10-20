# Texture, suoni, modelli e anteprime

Nessuna mod usa materiale preso da altre mod o da internet: tutto è generato con script Python (numpy, PIL, ffmpeg) che restano nella scratchpad (non nel repository). Nel `CLAUDE.md` della mod si scrive solo che gli asset sono generati e con che tecnica, così chi viene dopo sa che deve rigenerarli invece di cercarne la fonte. Esempi veri in `scripts/`.

## Indice
1. Texture
2. Suoni
3. Modelli
4. Icona della mod
5. Immagine di anteprima per il README
6. Testi (lang)

## 1. Texture

- Stile vanilla: pixel art alla risoluzione di Minecraft (16×16 oggetti e particelle; skin 64×64; entità grandi con texture più grandi, es. 512×512 per i mostri di Jumpscare). Ridimensiona sempre con `Image.NEAREST`.
- Mai tinta unita: ogni colore è una rampa di 4-6 toni (ombra, medio, luce) con un po' di rumore e piccole variazioni di tinta; luce che viene dall'alto a sinistra.
- **Ricolorazioni** (es. würstel di diamante e di netherite): gradient map sulla luminosità della texture originale, così restano forme e ombre.
- **Particelle e cose da tingere**: texture bianche/grigie con alpha, il colore lo dà il codice (`setColor`) o il colore dei vertici. Le particelle hanno il loro `particles/<nome>.json` con la lista delle texture (`"mod:nome_0"`...) in `textures/particle/`.
- **Parti che brillano** (occhi, crepe, il cuore di Recisione): seconda texture `_glow.png` disegnata con il render type "eyes" sopra quella normale.
- **Texture di altre cose del gioco**: si possono usare a runtime (le chiappe indossano il dietro delle gambe della skin del giocatore, l'albino di Disabilità usa una copia sbiancata della skin creata dal codice).
- **Mappe con immagini** (Sotto la Bedrock): file `.bin` di 128×128 byte con i colori delle mappe di Minecraft, generati convertendo un'immagine nella palette delle mappe.
- Esempio: `scripts/immagini_gas_e_icona.py` (sbuffi di gas 16×16 con bordi a due livelli di alpha e luce in alto a sinistra, più l'icona).

## 2. Suoni

- OGG Vorbis mono 44,1 kHz: genera i campioni con numpy e codificali con
  `ffmpeg -f s16le -ar 44100 -ac 1 -i - -c:a libvorbis -q:a 5 out.ogg` (i dati entrano da stdin).
- Sintesi, non registrazioni. Tecniche usate:
  - impulsi di flusso con jitter e shimmer (vibrazioni tipo labbra, scoregge), poi risonanze che si muovono nel tempo con un filtro a finestre (STFT) - `scripts/synth_scoregge.py`;
  - oscillatori, filtri formantici STFT, distorsione e riverbero sintetico (rumore che decade) per le urla di Jumpscare;
  - colpi brevi con rumore filtrato e inviluppo rapido per schiaffi e impatti; fruscii (whoosh) con rumore passa-banda che sale e scende;
  - toni lunghi e campane con parziali inarmoniche che decadono (campana di Recisione).
- Sempre: normalizza, una saturazione morbida (`tanh`) per il volume percepito, taglia la coda quando il suono è sotto circa -46 dB, dissolvenza breve alla fine, 2-4 varianti per ogni evento in `sounds.json` (con il `subtitle`).
- Controllo a occhio: nel sandbox non si sente niente, quindi guarda spettrogramma e volume con `scripts/spectro.py` (deve esserci quello che ti aspetti: durata, pause, frequenze). All'utente di' che l'audio non l'hai potuto ascoltare.
- Volume e distanza: `SoundEvent.createVariableRangeEvent` fa sentire il suono fino a 16 × volume blocchi; in `sounds.json` `attenuation_distance` lo allarga. I suoni che chi li subisce deve sentire forte (jumpscare) si suonano lato client con `SimpleSoundInstance.forUI`.

## 3. Modelli

- **Mostri e boss**: usa la skill `monster` (bozze con render, sceglie l'utente, poi `.bbmodel` con texture, glow, animazioni idle/walk/attack1/death e anteprime). Filo Rosso legge il `.bbmodel` direttamente con il suo `BbModel` (gruppi → ModelPart, animazioni di Blockbench rifatte uguali).
- **Modelli JSON semplici** (mostri di Jumpscare, accessori di Disabilità): un file `assets/<mod>/<cartella>/<nome>.json` con
  ```json
  {"texture_size": [512, 512], "unit": 0.015625,
   "parts": [{"name": "body", "parent": "", "pivot": [0, 20, 16], "rotation": [0, 0, 0],
              "cubes": [{"uv": [68, 250], "density": 1, "from": [-8, -5, -8], "size": [16, 12, 16], "inflate": 0.0}]}]}
  ```
  letto a runtime in `ModelPart` (box UV come vanilla, `density` = texel per unità, unità 1/64 di blocco) e caricato da un reload listener, così si può sostituire con un resource pack. Il modello e la sua texture si generano insieme con Python (la texture si dipinge sapendo dove cade l'UV di ogni faccia).
- **Geometria fatta nel codice** (würstel come tubo, yo-yo come superficie di rotazione, chiappe come sfere deformate): vertici emessi direttamente nel buffer, con UV e normali calcolate; utile quando la forma cambia di continuo.
- Prima di metterlo nel gioco conviene un renderer offline in Python identico al modello (fatto per Jumpscare e per lo yo-yo) per provare le inquadrature senza avviare Minecraft.

## 4. Icona della mod

`assets/<mod_id>/icon.png`, 128×128: disegnala a 64×64 in pixel art con PIL e ingrandiscila ×2 con `NEAREST`. Deve far capire la mod a colpo d'occhio (le chiappe in jeans con la nuvola verde, il filo rosso...). Controlla che si legga anche a 32×32.

## 5. Immagine di anteprima per il README

`docs/<mod>-preview.png`, larga circa 1800 px, fatta con screenshot veri del gioco (dalle prove): una fila di riquadri piccoli (livelli, varianti) e una di riquadri grandi (le scene migliori), ognuno con un'etichetta gialla su nero in italiano. `scripts/anteprima_readme.py` la compone; i ritagli si scelgono guardando i fogli di provini (`scripts/sheet.py`). Nel README va subito sotto il titolo della mod: `![descrizione](docs/<mod>-preview.png)`.

## 6. Testi (lang)

- `assets/<mod_id>/lang/it_it.json` e `en_us.json`, sempre tutti e due (l'utente e gli amici giocano in italiano).
- Chiavi tipiche: oggetti, tasti (`key.<mod>.<azione>` e la categoria `key.category.<mod>.main`), messaggi di morte (`death.attack.<tipo>`, con `%1$s` vittima e `%2$s` chi ha colpito), sottotitoli dei suoni (`subtitles.<mod>.<suono>`), messaggi della mod (consigli all'entrata, action bar).
- Tono: frasi brevi, italiano normale, un po' di ironia se la mod è comica; nei nomi dei comandi parole italiane senza accenti (`/disabilita`).
