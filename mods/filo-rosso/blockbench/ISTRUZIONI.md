# Recisione, la Marionetta

Il boss di Filo Rosso, rifatto con la skill `monster` (`.claude/skills/monster/SKILL.md`).
Una marionetta gigante di corda nera intrecciata (circa 10 blocchi, 14 con i fili): testa a gabbia di
ferro arrugginito con tre occhi rossi vuoti dentro, un nodo di filo rosso al posto del cuore (con
crepe che brillano), braccia fino alle ginocchia con dita lunghe e bende sporche alle giunture.
I fili che la reggevano sono stati tagliati, ma salgono ancora dalle spalle: sono la frusta con cui
attacca. Colore principale nero-viola con luci calde, poi grigio delle bende, ferro e ruggine, sangue;
accento luminoso rosso.

## File

- `recisione.bbmodel`: il modello. **Apri il file in Blockbench (File > Open Model)**: formato
  Bedrock, UV per faccia, texture già dentro. È salvato come progetto "4.10", così si apre giusto sia
  in Blockbench 4 sia in Blockbench 5 (che converte da solo i keyframe al suo formato nuovo).
- `recisione.png`: texture 256×256 (risoluzione UV 128). `recisione_glow.png`: solo le parti che
  brillano (occhi, crepe del cuore, filo rosso, estremità tagliate), il resto trasparente.
- `anteprime/`: render fatti con uno script (non con Blockbench), quindi piccole differenze di
  ombre sono normali.
  - `0_bozze.png` le tre bozze di partenza (A la Lira, B la Marionetta, scelta, C il Velo);
    `1_angoli.png` davanti, tre quarti, lato, dietro; `2_idle.gif`, `2_walk.gif`, `2_attack1.gif`,
    `2_death.gif` con un giocatore accanto per la scala; `3_attacco_sequenza.png` l'attacco in 8
    fotogrammi; `4_texture_x8.png` texture e glow ingranditi; `5_glow.png` con e senza glow;
    `6_volto.png` la testa da vicino.
- La mod legge lo stesso modello da
  `src/main/resources/assets/filorosso/geometry/recisione.bbmodel` (copia senza la texture dentro) e
  usa le texture in `assets/filorosso/textures/entity/`. Se lo modifichi in Blockbench, salva e
  copia il file lì (e le texture, se le cambi): **non rinominare le ossa** elencate sotto, la mod
  ne muove alcune per nome (gli altri attacchi, lo sguardo, i glitch).

## Numeri

116 cubi (più `hitbox`), 56% ruotati, 60 ossa, densità 1,0-1,4 texel per pixel del modello (1,4 sul
volto), circa 2200 colori. Le animazioni hanno circa 4500 keyframe catmullrom.

## Ossa

```
all
├── body                       bacino (nodo) e anche
│   └── chest                  busto: 4 corde intrecciate, cuore, legature, spalle, occhielli di ferro
│       ├── neck
│       │   └── head           gabbia di ferro storta, nodo scuro, 3 occhi
│       │       └── headstring > headstring2 > headstring3 > headstring4     filo tagliato sopra la testa
│       ├── rightarm > rightarm2 > righthand                                  braccio in 3 segmenti
│       │   righthand: rightfinger1 > rightfinger1b, rightfinger2 > rightfinger2b,
│       │              rightfinger3 > rightfinger3b, rightwire > rightwire2  (filo tagliato al polso)
│       ├── leftarm ... (uguale, "left")
│       ├── rightstring > rightstring2 > ... > rightstring7                   filo lungo dalla spalla (96 px)
│       ├── rightstringb > rightstringb2 > rightstringb3 > rightstringb4      filo corto dietro (42 px)
│       └── leftstring ..., leftstringb ... (uguali)
├── rightleg > rightleg2 > rightfoot                                          gamba in 3 segmenti
├── leftleg > leftleg2 > leftfoot
└── hitbox                     cubo nascosto che racchiude il corpo a riposo (36×158×20 px)
```

Fronte verso -Z, pivot sulle giunture. I fili e le dita sono catene di ossa con una rotazione di
riposo: ogni cubo è dritto nel suo osso e le rotazioni lo piegano, così ogni giuntura si muove da sola.

## Animazioni

| nome | durata | tipo | cosa fa |
|---|---|---|---|
| `idle` | 4,0 s | loop | dondola come appesa a fili che non ci sono; i fili salgono e ondeggiano come alghe, un'onda che corre lungo ognuno; le dita si piegano una dopo l'altra; a 2,5 s la testa scatta di lato e torna piano |
| `walk` | 2,4 s | loop | passi lenti e storti: il piede destro si trascina, il ginocchio sinistro cede quando ci carica il peso; braccia e fili in ritardo. Un giro = 2 passi = 60 px (3,75 blocchi) |
| `attack1` | 2,0 s | una volta | frusta dei fili: si carica (0-0,5 s, fili arricciati all'indietro), i fili destri sferzano giù davanti (colpo a **0,70 s**), poi i sinistri (**0,83 s**), si piega con il colpo, poi torna lentamente |
| `death` | 3,2 s | una volta | i fili tirano un'ultima volta, si spezzano (0,42 s) e cadono; crolla seduta a terra, il busto si piega in avanti, la testa ciondola, braccia e fili si posano a terra (simulati come corde) |

Nella mod: `idle` sempre, `walk` in base alla strada fatta (i piedi non scivolano), `attack1` è
l'attacco `frusta` (le due sferzate fanno danno ai tick 14 e 17), `death` quando muore; dopo il
crollo si disfa in polvere nera e i fili diventano cuori. Gli altri attacchi (radici, missili, presa,
campana, salto, uscita dal terreno) sono pose fatte nel codice sopra l'idle, con le stesse ossa.

## Cosa non è stato verificato

- Il file non è stato aperto in un vero Blockbench qui: è scritto seguendo il formato del codice
  sorgente di Blockbench e ricontrollato con uno script (uuid, UV dentro la texture, facce con
  texture 0, PNG incorporato, loop chiusi).
- Le anteprime sono render dello script: luce simile a quella delle entità di Minecraft, niente
  ombre portate né nebbia.
