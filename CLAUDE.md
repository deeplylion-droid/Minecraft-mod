# Note per Claude

Repository di mod per **Minecraft Forge 1.20.1** fatte con Claude per deeplylion-droid. L'utente scrive in italiano e usa spesso il telefono: rispondi in italiano, in modo breve.

## Regole del repository

- **Non cancellare mai niente**: né mod esistenti, né file, né Release o tag su GitHub. L'utente l'ha chiesto esplicitamente.
- Il **Würstel Gigante** resta nella radice del repository (`src/`, `build.gradle`, `gradle.properties`).
- Ogni **nuova mod** va in una cartella sua, `mods/<nome-mod>/`, come progetto Gradle autonomo: `settings.gradle`, `build.gradle`, `gradle.properties` (con `mod_id`, `mod_name`, `mod_version`, `minecraft_version`, `forge_version`), `src/`. Usa il Gradle wrapper della radice: `./gradlew -p mods/<nome-mod> build`. Copia la struttura del Würstel (stesso build.gradle).
- Quando aggiungi una mod, aggiungila alla sezione "Altre mod" del README.
- Per aggiornare una mod alza `mod_version` nel suo `gradle.properties`: la CI pubblica una nuova Release senza toccare le vecchie.
- Se l'utente non dice altro, le mod sono per Forge 1.20.1 (47.x), mappature `official`, Java 17.

## Compilazione e Release

- `.github/workflows/build.yml` compila la radice e ogni `mods/*/`, carica i jar come artifact e crea una Release `<mod_id>-v<versione>` per ogni jar non ancora pubblicato (controlla i nomi degli asset delle Release esistenti; non cancella mai nulla).
- La prima Release del Würstel ha il tag `v1.0.0` (asset `wurstel-1.0.0.jar`), creata prima di questa convenzione.
- Toolchain: ForgeGradle `[6.0,6.2)`, Gradle wrapper 8.8, Forge `1.20.1-47.2.0`.
- Nel sandbox di Claude i server di Forge e Mojang possono essere bloccati: in quel caso fai il push e controlla l'esito della build di GitHub Actions tramite l'API di GitHub. I commit che toccano solo documentazione possono usare `[skip ci]`.

## Würstel Gigante (radice, `mod_id` = `wurstel`)

Cintura (oggetto `wurstel:wurstel_belt`, ArmorItem negli slot dei pantaloni) che porta un würstel 3D lungo 4 blocchi e spesso ¼ di blocco, attaccato alla fibbia e puntato dove guarda la testa. A mano vuota, i pugni (contro entità e blocchi) diventano schiaffi di würstel morbidi, da angolazioni sempre diverse. C'è anche il cibo `wurstel:wurstel`.

- `client/anim/WurstelAnimState.java`: animazione pura (senza classi di Minecraft). Radice spinta da una molla verso il copione dello schiaffo (carica 1,2 tick, colpo 1,8 tick), flessione morbida che reagisce all'accelerazione, ritardo lungo il würstel, oscillazioni per passi, salti e giri di testa. Le costanti in cima regolano velocità, ampiezze e morbidezza; `SLAP_ANGLES` sono le 8 angolazioni; `IMPACT_TICKS` sincronizza suono e particelle.
- `client/geom/WurstelGeometry.java`: tubo liscio con estremità arrotondate (lunghezza `LENGTH`, raggio `RADIUS`), texture `textures/entity/wurstel.png` (32×128, u intorno, v lungo).
- `client/render/WurstelLayer.java` + `WurstelRenderer.java`: layer aggiunto a giocatori, supporti per armature, zombie, scheletri, piglin (`ClientModEvents`). Punto di attacco `BUCKLE_*` in pixel del modello; compensa la scala 0.9375 dei giocatori. In prima persona il würstel viene disegnato nel mondo in `RenderLevelStageEvent.AFTER_ENTITIES`. Nell'inventario viene accorciato (proiezione ortografica).
- `client/anim/WurstelAnimManager.java`: a fine tick client rileva gli swing (`swinging && swingTime == 0`, mano principale vuota), avvia lo schiaffo, suona whoosh/slap e crea particelle; azzera `attackAnim`/`oAttackAnim` di chi indossa la cintura, così il braccio non tira il pugno (prima e terza persona).
- `event/SlapEvents.java` (server): moltiplica il danno a mano vuota (`damageMultiplier`, default 2) e aggiunge knockback (`extraKnockback`, default 0.6). Config in `config/WurstelConfig.java` (client: `showInFirstPerson`, `sounds`, `particles`).
- Suoni sintetizzati in `assets/wurstel/sounds/` (slap1-4, whoosh1-2, squish1), OGG Vorbis mono.
- **Non ancora provata in gioco**: è stata verificata compilando con il vero Forge e con anteprime renderizzate (`docs/preview.gif`). Se l'utente segnala problemi, parti da lì.
