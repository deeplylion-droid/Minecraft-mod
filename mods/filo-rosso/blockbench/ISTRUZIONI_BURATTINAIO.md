# Il Burattinaio

Il super boss di Filo Rosso (dalla 1.1.0), fatto con la skill `monster`
(`.claude/skills/monster/SKILL.md`): bozza scelta "A - Il Burattinaio", movimento "Sospeso",
attacco "Fili che comandano". È quello che teneva i fili di Recisione.

Una figura altissima (circa 11 blocchi dal fondo del cappotto alla testa, 17 con le bamboline che
pendono sotto, e i fili salgono per altri 11) in un cappotto nero lungo e sfilacciato, che non tocca mai le assi: resta appesa a tre fili rossi che salgono dalla testa nel buio, perché anche
lui è una marionetta. Una maschera di porcellana crepata con tre occhi vuoti rossi e lacrime di
sangue, colletto con un filo d'oro, lacci rossi sul petto. Braccia lunghissime: in ogni mano un
bilancino da marionetta di legno scuro con quattro fili, e in fondo a ognuno una bambolina pallida.
Colore principale nero-prugna, porcellana avorio sporco, legno e ottone, sangue; accento luminoso
rosso (occhi, fili, cuciture).

## File

- `burattinaio.bbmodel`: il modello. **Apri il file in Blockbench (File > Open Model)**: formato
  Bedrock, UV per faccia, texture già dentro. Salvato come progetto "4.10", così si apre giusto sia in
  Blockbench 4 sia in Blockbench 5.
- `burattinaio.png`: texture 512×512 (risoluzione UV 256). `burattinaio_glow.png`: solo le parti che
  brillano (occhi, lacrime, fili, cuciture del petto), il resto trasparente.
- `anteprime_burattinaio/`: render fatti con uno script (non con Blockbench), quindi piccole
  differenze di ombre sono normali.
  - `1_angoli.png` davanti, tre quarti, lato, dietro, con un giocatore per la scala; `2_idle.gif`,
    `2_walk.gif`, `2_attack1.gif`, `2_death.gif`; `3_attacco_sequenza.png` l'attacco in 8 fotogrammi;
    `4_texture.png` texture e glow, `4_texture_maschera_x2.png` la parte della maschera ingrandita;
    `5_glow.png` con e senza glow; `6_volto.png` la maschera da vicino.
  - Le tre bozze di partenza sono nella scratchpad della sessione, non nel repository.
- La mod legge lo stesso modello da
  `src/main/resources/assets/filorosso/geometry/burattinaio.bbmodel` (copia senza la texture dentro)
  e usa `textures/entity/burattinaio.png` e `burattinaio_glow.png`. Se lo modifichi in Blockbench,
  salva e copia il file lì (e le texture, se le cambi): **non rinominare le ossa** elencate sotto, la
  mod ne muove alcune per nome.

## Numeri

116 cubi (più `hitbox`, 56×264×56 px), 83 ossa; quasi tutte le parti lunghe (fili, lembi del
cappotto, dita) sono ossa con una rotazione di riposo, così ogni giuntura si muove da sola. Circa 2200
colori, densità più alta sulla maschera. Le animazioni hanno circa 10.000 keyframe catmullrom: i fili,
i lembi del cappotto e le bamboline sono stati simulati come pendoli (con il vento e il peso), poi
salvati come keyframe.

## Ossa

```
all
├── body                         bacino, da cui pende il cappotto
│   ├── robe1 ... robe10         i 10 lembi del cappotto, in cerchio
│   │   └── fray1_1, fray1_2 ... le frange sfilacciate in fondo a ogni lembo
│   └── chest                    busto, colletto, lacci, spalle
│       ├── rightupstring, leftupstring       fili che salgono dalle spalle
│       ├── neck
│       │   └── head             la testa (cappuccio scuro)
│       │       ├── mask         la maschera di porcellana (cade nella morte)
│       │       └── headstring1 > headstring1b, headstring2 > ..., headstring3 > ...   i fili che lo reggono
│       ├── rightarm > rightarm2 > righthand                                         braccio in 3 segmenti
│       │   righthand: rightfinger1 > rightfinger1b, rightfinger2 > ..., rightfinger3 > ..., rightthumb,
│       │              rightcontrol (il bilancino)
│       │                └── rightstring1 > rightstring1b, rightstring2 > ..., rightstring3 > ...,
│       │                    rightstring4 > rightstring4b > rightdoll (la bambolina)
│       └── leftarm ... (uguale, "left")
└── hitbox                       cubo nascosto che racchiude il corpo a riposo
```

Fronte verso -Z, pivot sulle giunture.

## Animazioni

| nome | durata | tipo | cosa fa |
|---|---|---|---|
| `idle` | 4,0 s | loop | appeso, sale e scende piano e ruota appena; le mani muovono i bilancini fuori tempo una dall'altra, le dita lavorano; a 2,5 s la testa scatta di lato come se qualcuno avesse tirato il suo filo; fili, lembi e bamboline ondeggiano in ritardo |
| `walk` | 3,0 s | loop | "sospeso": scivola in avanti inclinato, con un'onda lenta su e giù, braccia e bilancini trascinati indietro, il cappotto che sventola |
| `attack1` | 2,4 s | una volta | "fili che comandano": il bilancino destro sale (0-0,9 s), la testa cerca la sua marionetta, poi frusta giù (**1,1 s**: i fili arrivano sulla vittima) e tira tre volte (1,35 / 1,66 / 1,8 s); il sinistro si alza per l'equilibrio |
| `death` | 3,6 s | una volta | trema, i suoi fili si spezzano (0,4 s), cade sulle assi come una marionetta con un piccolo rimbalzo, il busto si piega in avanti, le braccia si allargano a terra, la maschera si stacca e cade davanti a lui, a faccia in su |

Nella mod (`client/render/BurattinaioModel.java`):
- `idle` sempre, `walk` quando si sposta, `attack1` è l'attacco `fili` (al tick 22 i fili prendono
  la vittima), `death` quando muore; poi si disfa pezzo per pezzo in polvere nera.
- Gli altri attacchi sono pose fatte nel codice sopra l'idle, con le stesse ossa: `calata` (braccia
  su, poi giù sulle assi al tick 20), `pioggia` (braccia alzate verso il buio), `buio` (braccia
  aperte che tremano), l'arrivo dal buio (appeso molle, poi si sveglia con uno scatto) e `salto`
  (sparisce e riappare). La testa segue chi guarda a scatti; quando viene colpito la maschera scatta
  indietro; ogni tanto un fotogramma "salta" (un pezzo sparisce, la testa si torce).

## Cosa non è stato verificato

- Il file non è stato aperto in un vero Blockbench qui: è scritto seguendo il formato del codice
  sorgente di Blockbench e ricontrollato con uno script (uuid, UV dentro la texture, facce con
  texture 0, PNG incorporato, loop chiusi).
- Le anteprime sono render dello script: luce simile a quella delle entità di Minecraft, niente
  ombre portate né nebbia.
