# Minecraft 26.3 + Fabric: API e schemi veri delle mod

Le mod 26.3 del repository sono `mods/chiappe-colossali/` (la più piccola: è il modello da copiare), `mods/disabilities/` e `mods/filo-rosso/`. Gli esempi sono copiati da lì (sotto ogni blocco c'è il file, dalla radice del repository; `// ...` = righe tolte) o controllati nei sorgenti decompilati di 26.3. Se un nome non torna, cercalo con `grep -rn` nei sorgenti di Minecraft (jar `-sources` di Loom scompattato nella scratchpad) o con `javap -cp <jar> <classe>` sui jar di Fabric API in `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.

## Indice
1. Toolchain e file del progetto
2. Nomi nuovi
3. Tasti e mouse (SDL3)
4. Registrare: item, suoni, entità, danni, gamerule
5. Dati: attachment e SavedData
6. Pacchetti
7. Disegnare entità: render state, modelli, layer, pose
8. Nel mondo, HUD, schermate
9. Particelle, post effect, risorse
10. Comandi ed eventi
11. Camera, culling, inventario e altri mixin
12. Trappole
13. Cose non ancora usate nel repository

## 1. Toolchain e file del progetto
- Minecraft 26.3 **non è offuscato**: niente mappature né remap, dipendenze con `implementation` (non `modImplementation`), nomi di Mojang.
- Loom **1.19.1** (plugin `net.fabricmc.fabric-loom`), Gradle **9.8.1** con il **wrapper dentro la cartella della mod** (copia `gradlew`, `gradlew.bat`, `gradle/wrapper/` da `mods/chiappe-colossali/`). Compila con `cd mods/<x> && ./gradlew build` → `build/libs/<mod_id>-<versione>.jar`. La CI usa il wrapper della cartella se c'è, con il Java di `java_version`.
- Java **25**, loader **0.19.5**, Fabric API **0.163.0+26.3**. `gradle.properties`: `minecraft_version=26.3`, `loader_version=0.19.5`, `fabric_version=0.163.0+26.3`, `loom_version=1.19.1`, `mod_loader=fabric`, `java_version=25`, poi `mod_id`, `mod_name`, `mod_version`, `maven_group`, `mod_authors`.
- `splitEnvironmentSourceSets()`: codice e mixin client in `src/client/java` e `src/client/resources`, che il server dedicato non vede. Il codice comune non importa classi client: se un item deve fare un effetto sul client usa un hook che il client riempie (`Sided.sonar` in `mods/disabilities/src/main/java/com/disabilities/Sided.java`).

```groovy
plugins {
    // Minecraft 26.x is not obfuscated: no mappings and no remapping, plain `implementation`
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}
version = project.mod_version
group = project.maven_group
base { archivesName = project.mod_id }
loom {
    // Client code lives in src/client, so the dedicated server never sees it.
    splitEnvironmentSourceSets()
    mods { "${mod_id}" { sourceSet sourceSets.main; sourceSet sourceSets.client } }
}
repositories {}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
processResources {
    def version = project.version
    inputs.property "version", version
    filesMatching("fabric.mod.json") { expand "version": version }
}
// Minecraft 26.3 runs on Java 25
tasks.withType(JavaCompile).configureEach { it.options.release = 25; it.options.encoding = 'UTF-8' }
java { sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
```
(da mods/chiappe-colossali/build.gradle, uguale nelle tre mod; compattato su meno righe: copia il file vero. `settings.gradle`: `pluginManagement` con i repository `https://maven.fabricmc.net/`, `mavenCentral()`, `gradlePluginPortal()`, e `rootProject.name`.)

```json
{
  "schemaVersion": 1, "id": "filorosso", "version": "${version}", "name": "Filo Rosso",
  "icon": "assets/filorosso/icon.png", "environment": "*",
  "entrypoints": { "main": ["com.filorosso.FiloRosso"], "client": ["com.filorosso.client.FiloRossoClient"] },
  "mixins": [ "filorosso.mixins.json", { "config": "filorosso.client.mixins.json", "environment": "client" } ],
  "depends": { "fabricloader": ">=0.19.5", "minecraft": "~26.3", "java": ">=25", "fabric-api": "*" }
}
```
(da mods/filo-rosso/src/main/resources/fabric.mod.json, compattato; ci sono anche `description`, `authors`, `contact`, `license`.) Mixin comuni in `src/main/resources/<id>.mixins.json` con `"mixins": [...]`, client in `src/client/resources/<id>.client.mixins.json` con `"client": [...]`; tutti e due con `"required": true`, `"minVersion": "0.8"`, `"package"`, `"compatibilityLevel": "JAVA_25"`, `"injectors": {"defaultRequire": 1}`. Chiappe ha solo quello client. MixinExtras c'è già (`@WrapWithCondition` in `mods/filo-rosso/src/client/java/com/filorosso/client/mixin/MinecraftStartMixin.java`).

Ogni mod ha `public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }` nella classe principale, e `onInitialize()` chiama `ModSounds.init()`, `ModItems.init()`, `Net.init()`...: metodi vuoti o quasi che caricano la classe e quindi registrano i campi statici (mods/chiappe-colossali/src/main/java/com/chiappe/Chiappe.java).

## 2. Nomi nuovi
| Cosa | 26.3 | Prima (1.21.1, in `mods/below-bedrock`, `mods/jumpscares`) |
|---|---|---|
| Identificatore | `Identifier.fromNamespaceAndPath(ns, p)`, `Identifier.withDefaultNamespace(p)` | `ResourceLocation` |
| Disegno in GUI | `GuiGraphicsExtractor`, metodi `extractRenderState(...)` | `GuiGraphics`, `render(...)` |
| Schermate | `mc.gui.setScreen(s)`, `mc.gui.screen()` | — |
| HUD vanilla | `mc.gui.hud` (classe `Hud`): `setOverlayMessage(c, false)`, `isHidden()` (F1), `toggle()`, `getBossOverlay()`, `getChat()` | per disegnare: `HudRenderCallback` |
| F3 | `mc.debugEntries.isOverlayVisible()` / `setOverlayVisible(false)` | — |
| Render type | `RenderTypes.entityCutout/entityCutoutCull/entityTranslucent/eyes(tex)` (pacchetto `net.minecraft.client.renderer.rendertype`) | `RenderType.eyes(...)` |
| Disegnare | differito: `SubmitNodeCollector.submitModel/submitCustomGeometry/submitNameTag` | `buffers.getBuffer(...)` |
| Nel mondo | `LevelRenderEvents.COLLECT_SUBMITS` | `WorldRenderEvents.AFTER_ENTITIES` |
| Luce | `LightCoordsUtil.FULL_BRIGHT`, `LightCoordsUtil.getLightCoords(level, pos)` | `LightTexture.FULL_BRIGHT`, `LevelRenderer.getLightColor` |
| Ferire | `hurtServer(ServerLevel, DamageSource, float)` | `hurt(DamageSource, float)` |
| Mandare la spinta al client | `syncVelocity = true` | `hurtMarked = true` |
| Action bar dal server | `player.sendOverlayMessage(c)` | `displayClientMessage(c, true)` |
| Lato | `level.isClientSide()` (il campo ora è privato) | `level.isClientSide` (campo) |
| Uso di un item | `InteractionResult use(Level, Player, InteractionHand)` → `InteractionResult.SUCCESS` | `InteractionResultHolder.sidedSuccess(...)` |
| Permessi | `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` | `source.hasPermission(2)` |
| Gamerule | `GameRuleBuilder` → `/gamerule <ns>:<nome>`; vanilla `minecraft:advance_time`, `minecraft:spawn_mobs`... | `GameRuleRegistry.register("fadedHauntings", ...)` |
| Pacchetti | `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()` | `playS2C()` |
| Tab creative | `CreativeModeTabEvents.modifyOutputEvent(tab)` | `ItemGroupEvents.modifyEntriesEvent` |
| Reload listener | `ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id, l)` | `ResourceManagerHelper` |
| Entità vanilla | `EntityTypes.LIGHTNING_BOLT` (`EntityType` non ha più le costanti) | — |
| Ricarica tra due colpi | `LivingEntity.damageCooldownTime` | — |
| È giorno | `level.isBrightOutside()` | — |
| Faccia in GUI | `PlayerFaceExtractor.extractRenderState(g, skin, x, y, size, color)` | — |
| Giocatori nel render | entità `Avatar`, `AvatarRenderer<?>`, `AvatarRenderState`, `net.minecraft.client.model.player.PlayerModel` | — |
| Font in un testo | `Style.EMPTY.withFont(new FontDescription.Resource(id))` | — |
| Backend grafici | `com.mojang.renderpearl` (`GlBackend`, `VulkanBackend`), opzione `PreferredGraphicsApi` | — |

## 3. Tasti e mouse (SDL3)
- `InputConstants.KEY_*` sono **scancode SDL3**: A=4 … Z=29 (B=5, G=10, I=12, J=13, O=18, U=24, Y=28, Z=29), `KEY_1`…`KEY_9` = 30…38 ma **`KEY_0` = 39**, Invio=40, Esc=41, Tab=43, Spazio=44, F1=58, F3=60, F5=62. `KeyEvent(key, keycode, modifiers)`: `key()` è lo scancode (confrontalo con `KEY_*`), `keycode()` il keycode (`KEYCODE_A = 97`...). Utili: `event.isEscape()`, `isConfirmation()`, `MIO_TASTO.matches(event)`.
- Mouse: `MOUSE_BUTTON_LEFT = 1`, `MIDDLE = 2`, `RIGHT = 3`. **Mai confrontare con 0**: in Disabilità 1.0.0 le carte non si cliccavano per questo.
- Tasti di vanilla 26.3: **G = `key.quickActions`**, O = `key.friends`, P = `key.socialInteractions`, L progressi, C/X barre salvate, F mano secondaria, Q butta, T chat. Iris usa R/K/O, Simple Voice Chat V/G/M/N/H. Le mod usano Z (Chiappe) e J/Y/U/I (Filo Rosso); Disabilità usa G, scelta prima di saperlo.

```java
public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(FiloRosso.id("main"));
public static final KeyMapping MENU = key("menu", InputConstants.KEY_J);
private static KeyMapping key(String name, int key) {
    return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.filorosso." + name, InputConstants.Type.KEYBOARD, key, CATEGORY));
}
// nel tick del client (ClientTickEvents.END_CLIENT_TICK), compattato:
while (MENU.consumeClick()) { /* ... */ mc.gui.setScreen(new CoupleScreen()); }
```
(da mods/filo-rosso/src/client/java/com/filorosso/client/FiloRossoClient.java; nel lang `"key.category.filorosso.main"` e `"key.filorosso.menu"`. Il nome del tasto da mostrare: `MENU.getTranslatedKeyMessage()`.)

## 4. Registrare: item, suoni, entità, danni, gamerule
```java
public static final Item INHALER = register("inhaler", InhalerItem::new, new Item.Properties().durability(16));
private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Disabilities.id(name));
    return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
}
// in init(): CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> { output.accept(WHITE_CANE); ... });
```
(da mods/disabilities/src/main/java/com/disabilities/item/ModItems.java.)
- `setId(key)` va fatto prima di costruire l'item, altrimenti "Item id not set". Componenti: `.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)`, `.rarity(Rarity.EPIC)` (mods/filo-rosso/.../item/ModItems.java).
- Nell'item: `public InteractionResult use(Level level, Player player, InteractionHand hand)`, lavoro vero solo se `level instanceof ServerLevel`, poi `stack.hurtAndBreak(1, player, hand)`, `player.getCooldowns().addCooldown(stack, COOLDOWN)`, `return InteractionResult.SUCCESS` (InhalerItem.java).
- Asset: `assets/<ns>/items/<nome>.json` = `{"model": {"type": "minecraft:model", "model": "<ns>:item/<nome>"}}` più `models/item/<nome>.json` (`"parent": "minecraft:item/generated"`, `"textures": {"layer0": "<ns>:item/<nome>"}`). Ricette in `data/<ns>/recipe/` (singolare), ingredienti come stringhe (`"minecraft:gold_block"`), `"result": {"id": "filorosso:broken_heart", "count": 1}`. Loot di un mob: `data/<ns>/loot_table/entities/<nome>.json`.
- Suoni: `Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))` (mods/chiappe-colossali/src/main/java/com/chiappe/ModSounds.java); `assets/<ns>/sounds.json` con `"fart_atomic": {"subtitle": "subtitles.chiappe.fart_atomic", "sounds": [{"name": "chiappe:fart_atomic1", "attenuation_distance": 24}, ...]}`, file in `assets/<ns>/sounds/*.ogg`. Sul server `level.playSound(null, x, y, z, suono, SoundSource.PLAYERS, volume, pitch)`; nelle schermate `mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F))`. Alcuni `SoundEvents` sono `Holder.Reference`: per `playSound` serve `.value()` (`SoundEvents.GENERIC_EAT.value()`, mods/filo-rosso/.../server/Joining.java).

```java
public static final EntityType<Recisione> RECISIONE = register("recisione",
        EntityType.Builder.<Recisione>of(Recisione::new, MobCategory.MONSTER).sized(2.0F, 9.6F).eyeHeight(9.0F).fireImmune().clientTrackingRange(16));
public static final EntityType<Missile> MISSILE = register("missile",
        EntityType.Builder.<Missile>of(Missile::new, MobCategory.MISC).sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(2).noSave());
private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
    ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FiloRosso.id(name));
    return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
}
// in init(): FabricDefaultAttributeRegistry.register(RECISIONE, Recisione.createBossAttributes());
```
(da mods/filo-rosso/src/main/java/com/filorosso/entity/ModEntities.java.)
- Attributi: `Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400.0).add(Attributes.STEP_HEIGHT, 3.0)` (Recisione.java), `Zombie.createAttributes()...` (Servant.java). Far nascere: `ModEntities.RECISIONE.create(level, EntitySpawnReason.TRIGGERED)`, `setPos(...)`, `level.addFreshEntity(boss)`; un mob normale anche `snapTo(x, y, z, yaw, 0.0F)` e `finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null)` (server/Boss.java, server/Servants.java).
- Firme da sovrascrivere in 26.3 (Recisione, Missile, Grasp): `hurtServer(ServerLevel, DamageSource, float)`, `causeFallDamage(double, float, DamageSource)`, `removeWhenFarAway(double)`, `canUsePortal(boolean)`, `customServerAiStep(ServerLevel)`, `doHurtTarget(ServerLevel, Entity)`, `tickDeath()`, `defineSynchedData(SynchedEntityData.Builder)` con `builder.define(DATA_ACTION, NONE)`, `readAdditionalSaveData(ValueInput)` / `addAdditionalSaveData(ValueOutput)`, `startSeenByPlayer/stopSeenByPlayer(ServerPlayer)` per la barra `ServerBossEvent`. Proiettile: `extends ThrowableProjectile` con `getDefaultGravity()`, `onHit(HitResult)`, `canHitEntity(Entity)`.

Tipi di danno: `data/<ns>/damage_type/fart.json` = `{"exhaustion": 0.1, "message_id": "chiappe.fart", "scaling": "never"}`, più eventuali tag in `data/minecraft/tags/damage_type/` (`bypasses_armor.json` con `"replace": false, "values": [...]`).
```java
public static final ResourceKey<DamageType> FART = ResourceKey.create(Registries.DAMAGE_TYPE, Chiappe.id("fart"));
// ...
DamageSource source = level.damageSources().source(ModDamage.FART, p);
if (!e.hurtServer(level, source, damage * k)) { continue; }
```
(da mods/chiappe-colossali/src/main/java/com/chiappe/server/ModDamage.java e FartAttack.java, compattato.) Anche `source(key)` e `source(key, diretta, proprietario)` (`source(ModDamage.VOID, this, this.getOwner())` in Missile.java); le versioni con entità sono pubbliche grazie a fabric-transitive-access-wideners. Messaggi di morte (`DamageSource.getLocalizedDeathMessage`): con un'entità come fonte la chiave è `death.attack.<message_id>` con `%1$s` vittima e `%2$s` chi ha colpito (`.item` solo se tiene un oggetto rinominato); senza entità `death.attack.<message_id>`, o `.player` se c'è un kill credit.

```java
public static final GameRule<Integer> LENGTH = GameRuleBuilder.forInteger(6).range(2, 32).category(GameRuleCategory.PLAYER)
        .buildAndRegister(FiloRosso.id("length"));
public static final GameRule<Boolean> EVENTS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER)
        .buildAndRegister(FiloRosso.id("events"));
// lettura: server.overworld().getGameRules().get(ModRules.LENGTH)   (oppure server.getGameRules().get(...))
```
(da mods/filo-rosso/src/main/java/com/filorosso/server/ModRules.java.) Nel lang `gamerule.<ns>.<nome>` e `gamerule.<ns>.<nome>.description`.

## 5. Dati: attachment e SavedData
```java
/** saved, kept on death, known by every client */
public static final AttachmentType<Link> LINK = AttachmentRegistry.create(FiloRosso.id("link"),
        b -> b.persistent(Link.CODEC).copyOnDeath().syncWith(Link.STREAM_CODEC, AttachmentSyncPredicate.all()));
/** not saved, known by every client */
public static final AttachmentType<Status> STATUS = AttachmentRegistry.create(FiloRosso.id("status"),
        b -> b.syncWith(Status.STREAM_CODEC, AttachmentSyncPredicate.all()));
public static Link link(Entity entity) { return entity.getAttachedOrElse(LINK, Link.NONE); }
```
(da mods/filo-rosso/src/main/java/com/filorosso/data/ModData.java, compattato.)
- Si scrive sul server con `player.setAttached(ModData.STATUS, new Status(...))`: ogni `setAttached` manda il valore ai client scelti dal predicato (`all()`, `targetOnly()`, `allButTarget()`); il client legge con `getAttached` / `getAttachedOrElse`. I valori sono record immutabili: si sostituiscono, non si modificano. Anche sul mondo: `server.overworld().getAttachedOrElse(ModData.TAKEN, Taken.EMPTY)` / `setAttached` (Disabilità, solo `persistent`).
- Rete: `StreamCodec.composite(ByteBufCodecs.VAR_LONG, Status::downedUntil, ByteBufCodecs.VAR_INT, Status::revive, Status::new)` (data/Status.java). Salvataggio: `RecordCodecBuilder.create(i -> i.group(UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(Link::partner), Codec.INT.optionalFieldOf("color", 0).forGetter(Link::color), ...).apply(i, Link::new))` (data/Link.java): `optionalFieldOf` con un default, così i mondi vecchi si caricano. Disabilità salva le scelte per nome, non per numero (condition/Picks.java).

```java
public static final SavedDataType<CoupleBook> TYPE = new SavedDataType<>(FiloRosso.id("couples"), CoupleBook::new, CODEC, null);
public static CoupleBook get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(TYPE);
}
// dopo ogni modifica: book.setDirty();
```
(da mods/filo-rosso/src/main/java/com/filorosso/data/CoupleBook.java: `extends SavedData`, `CODEC` fatto con `RecordCodecBuilder`, quarto argomento (`DataFixTypes`) `null`.)

## 6. Pacchetti
```java
public record FartFx(int entity, int level) implements CustomPacketPayload {
    public static final Type<FartFx> TYPE = new Type<>(Chiappe.id("fart_fx"));
    public static final StreamCodec<ByteBuf, FartFx> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FartFx::entity, ByteBufCodecs.VAR_INT, FartFx::level, FartFx::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
// senza campi: public static final StreamCodec<ByteBuf, Fart> CODEC = StreamCodec.unit(INSTANCE);
public static void init() {   // dal main: serve sia al client sia al server
    PayloadTypeRegistry.serverboundPlay().register(Fart.TYPE, Fart.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(FartFx.TYPE, FartFx.CODEC);
}
```
(da mods/chiappe-colossali/src/main/java/com/chiappe/net/Net.java, compattato.) Liste e mappe: `Candidate.CODEC.apply(ByteBufCodecs.list())`, `ByteBufCodecs.map(java.util.HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.VAR_INT)`, `ByteBufCodecs.optional(...)` (mods/filo-rosso/.../net/Net.java, data/Link.java).
```java
ServerPlayNetworking.registerGlobalReceiver(Net.Fart.TYPE, (payload, context) -> fart(context.player()));
// ...
Net.FartFx fx = new Net.FartFx(p.getId(), lv);
ServerPlayNetworking.send(p, fx);
for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
    if (viewer != p && ServerPlayNetworking.canSend(viewer, Net.FartFx.TYPE)) { ServerPlayNetworking.send(viewer, fx); }
}
// client (ChiappeClient.java):
ClientPlayNetworking.registerGlobalReceiver(Net.FartFx.TYPE, (payload, context) ->
        FartFx.play(context.client(), payload.entity(), payload.level()));
ClientPlayNetworking.send(Net.Fart.INSTANCE);
```
(da mods/chiappe-colossali/src/main/java/com/chiappe/server/FartAttack.java, compattato.) `PlayerLookup.tracking(entity)` = chi vede l'entità (lei esclusa); anche `all(server)`, `around(level, pos, raggio)`. Il ricevitore client gira sul thread del client: può toccare `mc.level` e le schermate. Se arriva una schermata mentre ne è aperta un'altra, mettila in coda e aprila nel tick quando `mc.gui.screen() == null` (`pendingChoice`, `PROPOSALS` in FiloRossoClient.java).

## 7. Disegnare entità: render state, modelli, layer, pose
Il render è in due tempi: `extractRenderState(entity, state, partial)` copia dall'entità in un `*RenderState` quello che serve; `submit(state, poseStack, collector, camera)` mette in coda modelli e geometria nel `SubmitNodeCollector`. Minecraft disegna dopo e chiama `model.setupAnim(state)` in quel momento, con lo stato salvato: nel submit non c'è l'entità, e le pose vanno in `setupAnim(state)`.
```java
public class GraspRenderer extends EntityRenderer<Grasp, GraspRenderer.State> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/grasp.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/grasp_glow.png"));
    public static class State extends EntityRenderState { public float rise; /* ... */ }
    private final Model model;   // static class Model extends EntityModel<State>, sotto
    public GraspRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new Model(CubeModels.load(context.getResourceManager(), FiloRosso.id("geometry/grasp.json")));
        this.shadowRadius = 0.0F;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(Grasp entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.rise = entity.rise(partial);
    }
    @Override public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(-1.25F, -1.25F, 1.25F);   // ...
        collector.submitModel(this.model, state, poseStack, this.model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
                state.outlineColor);
        collector.submitModel(this.model, state, poseStack, GLOW, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
// client: EntityRendererRegistry.register(ModEntities.GRASP, GraspRenderer::new);
```
(da mods/filo-rosso/src/client/java/com/filorosso/client/render/GraspRenderer.java, compattato; `FULL_BRIGHT = 0xF000F0`.)
- `submitModel(model, state, poseStack, renderType, luce, overlay, tinta (-1 = nessuna), uvMapping (null), outline)`. I `ModelPart` hanno la Y in giù: in un `EntityRenderer` semplice scala `(-s, -s, s)`.
- Il modello: `static class Model extends EntityModel<State>` con `super(baked.root(), RenderTypes::entityCutout)` e `setupAnim(State s)` che chiama prima `super.setupAnim(s)` (rimette tutte le parti a riposo) e poi le gira (`this.fingers[i].xRot = Mth.lerp(s.close, -30.0F, 28.0F) * D`, con `D = Mth.DEG_TO_RAD`).
- Mob con parti che brillano: `extends MobRenderer<Recisione, RecisioneRenderState, RecisioneModel>`, `super(context, model, 1.2F)` (ombra), `this.addLayer(new EyesLayer<RecisioneRenderState, RecisioneModel>(this) { public RenderType renderType() { return GLOW; } })`, `getTextureLocation(state)`, e se serve `setupRotations(state, poseStack, bodyRot, scale)`, `getFlipDegrees()`, `getBoundingBoxForCulling(entity, partial)` (RecisioneRenderer.java). `RecisioneRenderState extends LivingEntityRenderState`, che ha già `ageInTicks`, `walkAnimationPos/Speed`, `deathTime`, `hasRedOverlay`. Un mob come uno vanilla: `ServantRenderer extends ZombieRenderer` con un'altra texture.
- Modelli JSON a box UV (`CubeModels.bake`): `MeshDefinition` → `getRoot().addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(inflate), 1.0F / density, 1.0F / density), PartPose.offsetAndRotation(...))` → `LayerDefinition.create(mesh, texW, texH).bakeRoot()`. Modelli Blockbench `.bbmodel` (UV per faccia, animazioni): riusa `mods/filo-rosso/src/client/java/com/filorosso/client/render/BbModel.java` (`BbModel.load(manager, id, "hitbox")`, `bb.bone("head")`, `bb.animation("idle").apply(secondi, peso)`), che crea `new ModelPart(List<ModelPart.Cube>, Map<String, ModelPart>)` con `ModelPart.Polygon`, poi `setInitialPose(...)` e `resetPose()`; da Blockbench a ModelPart la conversione è `(-x, -y, z)`.

Layer sui giocatori:
```java
LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
    if (renderer instanceof AvatarRenderer<?> avatar) { helper.register(new ButtLayer(avatar)); }
});
// public class ButtLayer extends RenderLayer<AvatarRenderState, PlayerModel>, costruttore (RenderLayerParent<AvatarRenderState, PlayerModel> renderer):
@Override
public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
    ButtVisual v = state.getData(ButtVisual.KEY);
    if (v == null || v.skin == null || state.isInvisible || state.isSpectator) { return; }
    int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);   // ...
    poseStack.pushPose();
    this.getParentModel().root().translateAndRotate(poseStack);
    this.getParentModel().body.translateAndRotate(poseStack);
    poseStack.translate(0.0F, 12.0F / 16.0F, 2.0F / 16.0F);
    collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(v.skin), (pose, buffer) -> {
        // ... per ogni vertice (4 per quad):
        buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, vv).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    });
    poseStack.popPose();
}
```
(da mods/chiappe-colossali/src/client/java/com/chiappe/client/ChiappeClient.java e ButtLayer.java, accorciato.) La skin del giocatore: `state.skin.body().texturePath()` (Cheeks.java). Un modello intero su un osso: `getParentModel().getHead().translateAndRotate(poseStack)` e `collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model.texture), light, overlay, -1, null, state.outlineColor)` con `AccessoryModel extends Model<AvatarRenderState>` (mods/disabilities/.../render/ConditionLayer.java).

I dati per il layer passano dal render state:
```java
public static final RenderStateDataKey<ButtVisual> KEY = RenderStateDataKey.create(() -> "chiappe:cheeks");
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void chiappe$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        Cheeks.extract(entity, state, partial);   // state.setData(ButtVisual.KEY, v), oppure null
    }
}
```
(da mods/chiappe-colossali/src/client/java/com/chiappe/client/ButtVisual.java e mixin/AvatarRendererMixin.java.) `setData`/`getData` vengono da `FabricRenderState`, che Fabric aggiunge a ogni `EntityRenderState`. Il descrittore completo serve perché nel bytecode ci sono anche i metodi bridge (`AvatarRenderer` ha tre `extractRenderState`, `PlayerModel` tre `setupAnim`). Altri punti di `AvatarRenderer` (mods/disabilities/.../mixin/AvatarRendererMixin.java): `getRenderOffset(AvatarRenderState)` RETURN (abbassa il corpo), `setupRotations(AvatarRenderState, PoseStack, float, float)` TAIL (dondola), `submitNameDisplay(...)` TAIL con `collector.submitNameTag(poseStack, attachment, 0, testo, !state.isDiscrete, state.lightCoords, camera)` (fumetto sopra il nome).

Pose del giocatore:
```java
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void disabilities$pose(AvatarRenderState state, CallbackInfo ci) {
        Poses.apply((PlayerModel) (Object) this, state);
    }
}
// Poses.apply: Visuals v = s.getData(Visuals.KEY); if (v.has(Condition.OBESITY)) { m.body.xScale = 1.55F; m.rightArm.zRot += 0.18F; m.head.y += 0.6F; }
```
(da mods/disabilities/src/client/java/com/disabilities/client/mixin/PlayerModelMixin.java e render/Poses.java.) Parti: `head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, con `xRot/yRot/zRot`, `x/y/z`, `xScale/yScale/zScale`, `visible`. Il modello è uno solo per tutti i giocatori: quello che cambia per uno passa dal suo stato.

## 8. Nel mondo, HUD, schermate
```java
LevelRenderEvents.COLLECT_SUBMITS.register(ThreadRenderer::submit);
private static void submit(LevelRenderContext context) {
    Vec3 camera = context.levelState().cameraRenderState.pos;
    PoseStack poseStack = context.poseStack();
    // ...
    context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
        // vertici relativi alla telecamera: p.subtract(camera)
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    });
}
```
(da mods/filo-rosso/src/client/java/com/filorosso/client/render/ThreadRenderer.java, accorciato.) Luce: `LightCoordsUtil.getLightCoords(level, BlockPos.containing(p))`; tick parziale: `mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)`. Un modello nel mondo come se fosse sul giocatore (sedia in prima persona): `poseStack.translate(x - camera.x, ...)`, `rotateDegrees(Axis.YP, 180.0F - bodyRot)`, `scale(-1, -1, 1)`, `scale(0.9375F, ...)`, `translate(0, -1.501F, 0)` come fa vanilla, poi `context.submitNodeCollector().submitModel(...)` (mods/disabilities/.../render/FirstPersonAids.java).

HUD, due modi. Elemento di Fabric, che sparisce con F1 come il mirino: `HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Chiappe.id("fart_cooldown"), CooldownHud::extract)` con `static void extract(GuiGraphicsExtractor g, DeltaTracker delta)` che usa `g.guiWidth()`, `g.guiHeight()`, `g.fill(...)` (mods/chiappe-colossali/src/client/java/com/chiappe/client/ChiappeClient.java e CooldownHud.java). Quello che deve restare anche con F1 (effetti sullo schermo, "a terra") si disegna da un mixin su `Hud`:
```java
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void filorosso$couple(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) { CoupleHud.draw(graphics, delta); }
    }
}
```
(da mods/filo-rosso/src/client/java/com/filorosso/client/mixin/HudMixin.java, compattato; Disabilità usa anche `HEAD`, per stare sotto l'HUD vanilla. Dentro nascondi a mano quello che non deve vedersi, con `mc.gui.hud.isHidden()` o `mc.debugEntries.isOverlayVisible()`.)
```java
g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), texW, texH, texW, texH, color);
g.pose().pushMatrix();
g.pose().translate(w / 2.0F, h / 2.0F - 20 + bob);
g.pose().scale(3.0F, 3.0F);
g.centeredText(mc.font, Component.literal(z), 0, 0, ARGB.color(k, 0xFFAAC8FF));
g.pose().popMatrix();
g.fill(0, 0, w, h, ARGB.black(k * 0.97F));
```
(da mods/disabilities/src/client/java/com/disabilities/client/hud/Overlays.java.) `blit(pipeline, tex, x, y, u, v, larghezza, altezza, srcLarg, srcAlt, texLarg, texAlt, colore)` stira il pezzo `(u, v, srcLarg, srcAlt)` su `larghezza × altezza`. `pose()` è un `Matrix3x2fStack` (2D). Altri: `text(font, comp, x, y, colore)`, `fillGradient(x0, y0, x1, y1, c1, c2)`, `outline(x, y, w, h, colore)`. Colori ARGB: un testo con alfa 0 non viene disegnato, quindi `0xFFRRGGBB` o `ARGB.color(alfa, rgb)`, `ARGB.white(a)`, `ARGB.black(a)`.

Schermate (`extends Screen`, mods/filo-rosso/src/client/java/com/filorosso/client/screen/ProposalScreen.java): costruttore `super(Component.translatable("filorosso.proposal.title"))`; in `init()` i bottoni con `this.addRenderableWidget(Button.builder(testo, b -> this.answer(false)).bounds(x, y, 120, 20).build())`; `isPauseScreen()` → `false` (multiplayer); `tick()`; `extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)` disegna (`g.fill`, `g.outline`, `g.centeredText(this.font, ...)`) e finisce con `super.extractRenderState(g, mouseX, mouseY, a)`, che disegna i bottoni. Input:
```java
@Override
public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    Condition c = this.cardAt(event.x(), event.y());
    if (c != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
        this.toggle(c);
        return true;
    }
    return super.mouseClicked(event, doubleClick);
}
@Override
public boolean keyPressed(KeyEvent event) {
    if (event.isEscape()) { return true; }   // e shouldCloseOnEsc() → false: la scelta non si chiude
    return super.keyPressed(event);   // ...
}
```
(da mods/disabilities/src/client/java/com/disabilities/client/screen/ChooserScreen.java, compattato.) Altro: `isInGameUi()` (GestureScreen.java); faccia di un giocatore con `PlayerFaceExtractor.extractRenderState(g, skin, x, y, size, color)` e skin da `connection.getPlayerInfo(id).getSkin()` (filo-rosso/.../screen/Screens.java); togliere bottoni vanilla con `ScreenEvents.AFTER_INIT` + `Screens.getWidgets(screen).removeIf(...)` (DisabilitiesClient.java).

## 9. Particelle, post effect, risorse
```java
public static final SimpleParticleType GAS = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Chiappe.id("gas"),
        FabricParticleTypes.simple(true));   // true = overrideLimiter
// client: ParticleProviderRegistry.getInstance().register(ModParticles.GAS, GasParticle.Provider::new);
public class GasParticle extends SingleQuadParticle {
    GasParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = xd;   // ... friction, gravity, lifetime, quadSize, setColor, setAlpha, roll
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    public static class Provider implements ParticleProvider<SimpleParticleType> {   // costruttore (SpriteSet sprites)
        @Override public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                                 double xd, double yd, double zd, RandomSource random) {
            return new GasParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
```
(da mods/chiappe-colossali/src/main/java/com/chiappe/ModParticles.java, ChiappeClient.java e GasParticle.java, compattato.) `assets/chiappe/particles/gas.json` = `{"textures": ["chiappe:gas_0", ..., "chiappe:gas_3"]}`, immagini in `textures/particle/gas_N.png`. Farle nascere sul client: `mc.particleEngine.createParticle(ModParticles.GAS, x, y, z, vx, vy, vz)` (restituisce la particella), `level.addParticle(...)`, `level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, ...)`; dal server `level.sendParticles(tipo, x, y, z, quante, dx, dy, dz, velocità)`; polvere colorata `new DustParticleOptions(0x6a0a10, 1.6F)` (colore RGB intero).

Post effect: `assets/<ns>/post_effect/<nome>.json` (id `<ns>:<nome>`), shader in `assets/<ns>/shaders/post/<nome>.fsh`.
```json
{ "targets": { "swap": {} },
  "passes": [
    { "vertex_shader": "minecraft:core/screenquad", "fragment_shader": "disabilities:post/grayscale",
      "inputs": [ { "sampler_name": "In", "target": "minecraft:main" } ], "output": "swap",
      "uniforms": { "GrayConfig": [ { "name": "Amount", "type": "float", "value": 1.0 } ] } },
    { "vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/blit",
      "inputs": [ { "sampler_name": "In", "target": "swap" } ], "output": "minecraft:main",
      "uniforms": { "BlitConfig": [ { "name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0] } ] } } ] }
```
(da mods/disabilities/src/main/resources/assets/disabilities/post_effect/grayscale.json, compattato.) Lo shader: `#version 330`, `#extension GL_ARB_separate_shader_objects : require`, `uniform sampler2D InSampler;` (il sampler `"In"`), `layout(location = 0) in vec2 texCoord;`, `layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };`, `layout(std140) uniform GrayConfig { float Amount; };`, `layout(location = 0) out vec4 fragColor;` (shaders/post/grayscale.fsh). Con `"use_depth_buffer": true` arriva `DepthSampler` (e `DepthSize` in `SamplerInfo`): la profondità è **rovesciata**, 1 = vicino, distanza ≈ `0.05 / depth` (myopia.fsh). Shader vanilla riusabili: `minecraft:post/box_blur` (`BlurConfig`: `BlurDir`, `Radius`; tears.json), `minecraft:post/blit`.
```java
@Inject(method = "update", at = @At("TAIL"))
private void disabilities$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
    GameRenderer self = (GameRenderer) (Object) this;
    if (Me.has(Condition.MYOPIA)) { self.getRequestedPostEffects().add(DISABILITIES_MYOPIA); }   // sul mondo, sotto la GUI
}
/** Achromatopsia: black and white over everything, inventory included (after the GUI is drawn). */
@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
private void disabilities$grayscale(CallbackInfo ci) {
    // ... (in try/catch: se fallisce una volta, si spegne)
    PostChain chain = this.minecraft.getShaderManager().getPostChain(DISABILITIES_GRAYSCALE, LevelTargetBundle.MAIN_TARGETS);
    if (chain != null) { chain.process(this.mainRenderTarget, this.resourcePool); }   // @Shadow @Final RenderTarget e CrossFrameResourcePool
}
```
(da mods/disabilities/src/client/java/com/disabilities/client/mixin/GameRendererMixin.java, compattato.)

Risorse lette dalla mod (modelli JSON sostituibili con un resource pack): `public final class Accessories implements ResourceManagerReloadListener` con `onResourceManagerReload(ResourceManager manager)` che fa `manager.getResource(Disabilities.id("accessories/" + name + ".json"))` → `resource.get().openAsReader()` → `JsonParser.parseReader(reader)`, registrata nel client con `ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Accessories.ID, Accessories.INSTANCE)` (mods/disabilities/.../render/Accessories.java, DisabilitiesClient.java). I renderer di Filo Rosso invece leggono il loro modello nel costruttore da `context.getResourceManager()`. Font con icone: `assets/<ns>/font/<nome>.json` con provider `bitmap` e caratteri U+E000…, usati con `Component.literal(glifo).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(FiloRosso.id("icons"))).withColor(rgb))` (mods/filo-rosso/src/main/java/com/filorosso/util/Text.java).

## 10. Comandi ed eventi
```java
CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> register(dispatcher));
private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    java.util.function.Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    dispatcher.register(Commands.literal("filo")
            .executes(c -> mine(c.getSource()))
            .then(Commands.literal("lega").requires(op)
                    .then(Commands.argument("primo", EntityArgument.player())
                            .then(Commands.argument("secondo", EntityArgument.player())
                                    .executes(c -> tie(c.getSource(), EntityArgument.getPlayer(c, "primo"), EntityArgument.getPlayer(c, "secondo")))))));
}
```
(da mods/filo-rosso/src/main/java/com/filorosso/server/FiloCommand.java, accorciato.) Risposta: `source.sendSuccess(() -> Component.translatable(...), false)`; errore: `throw new SimpleCommandExceptionType(Component.translatable(key, args)).create()`; suggerimenti: `SuggestionProvider<CommandSourceStack>` con `SharedSuggestionProvider.suggest(nomi, builder)`; giocatore: `source.getPlayerOrException()`.

Eventi usati (argomenti come nel codice):
- `ServerTickEvents.END_SERVER_TICK` `(server)`: Filo Rosso chiude tutto in `try/catch (RuntimeException e)` e registra l'errore invece di far cadere il server. `ServerLifecycleEvents.SERVER_STARTED` `(server)`.
- `ServerPlayConnectionEvents.JOIN` `(handler, sender, server)` / `DISCONNECT` `(handler, server)`, il giocatore è `handler.player`. `ServerPlayerEvents.AFTER_RESPAWN` `(oldPlayer, newPlayer, alive)` (`alive` falso = è morto).
- `ServerLivingEntityEvents.ALLOW_DAMAGE` `(entity, source, amount)` → `false` annulla; `AFTER_DAMAGE` `(entity, source, baseTaken, taken, blocked)`; `ALLOW_DEATH` `(entity, source, amount)` → `false` = non muore (il "a terra" di Revive.java). `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` `(level, killer, killed, source)`; `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` `(player, origin, destination)`.
- `UseEntityCallback` / `AttackEntityCallback` `(player, level, hand, entity, hit)`, `UseItemCallback` `(player, level, hand)`, `UseBlockCallback` `(player, level, hand, hit)` → `InteractionResult.PASS/SUCCESS/FAIL`.
- `ServerMessageEvents.ALLOW_CHAT_MESSAGE` `(message, sender, type)` → `false` blocca; `ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE, (sender, message) -> Component)` cambia il testo (la dislessia, Speech.java).
- Client: `ClientTickEvents.END_CLIENT_TICK` `(mc)`; `ClientPlayConnectionEvents.JOIN` `(handler, sender, client)` / `DISCONNECT` `(handler, client)` (qui si azzera tutto lo stato statico del client); `ClientLifecycleEvents.CLIENT_STARTED` `(client)`; `ClientSendMessageEvents.ALLOW_CHAT` `(message)` / `ALLOW_COMMAND` `(command)`; `ScreenEvents.AFTER_INIT` `(client, screen, width, height)`.

## 11. Camera, culling, inventario e altri mixin
```java
/** Screen shake of the big farts, added where vanilla tilts the view when you get hurt. */
@Inject(method = "bobHurt", at = @At("TAIL"))   // @Mixin(GameRenderer.class)
private void chiappe$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
    Shake.apply(poseStack);
}
// Shake.apply: float amount = strength * left * mc.options.screenEffectScale().get().floatValue();
//              poseStack.rotateDegrees(Axis.ZP, amount * (Mth.sin(t * 2.1F) + 0.5F * Mth.sin(t * 5.3F + 1.0F)));
```
(da mods/chiappe-colossali/src/client/java/com/chiappe/client/mixin/GameRendererMixin.java e Shake.java; tick parziale lì dentro: `cameraState.cameraEntityPartialTicks`.) FOV: mixin su `Camera`, `@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)` con `(float partialTicks, CallbackInfoReturnable<Float> cir)` → `cir.setReturnValue(cir.getReturnValueF() * factor)` (mods/disabilities/.../mixin/CameraMixin.java).

Distanza della terza persona, decisa dal server: `AttributeInstance camera = p.getAttribute(Attributes.CAMERA_DISTANCE); camera.removeModifier(CAMERA); camera.addTransientModifier(new AttributeModifier(CAMERA, extra, AttributeModifier.Operation.ADD_VALUE));` (mods/chiappe-colossali/src/main/java/com/chiappe/server/LevelSync.java). Stesso schema per `SCALE`, `MOVEMENT_SPEED`, `JUMP_STRENGTH`, `BLOCK_INTERACTION_RANGE`, `SAFE_FALL_DISTANCE`... con `addOrUpdateTransientModifier` e `Operation.ADD_MULTIPLIED_TOTAL` (mods/disabilities/.../server/Body.java).

Culling e inventario:
```java
@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)   // @Mixin(EntityRenderer.class)
private void chiappe$bigCheeks(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
                               CallbackInfoReturnable<Boolean> cir) {
    AABB box = player.getInterpolatedBoundingBox(partialTicks).inflate(2.4 * r);   // ... più la distanza massima
    cir.setReturnValue(culler.isVisible(box));
}
@Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))   // @Mixin(InventoryScreen.class); a RETURN rimette false
private static void chiappe$start(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float offsetY,
                                  float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
    Cheeks.inGui = true;   // nel riquadro dell'inventario le chiappe vengono rimpicciolite
}
```
(da mods/chiappe-colossali/src/client/java/com/chiappe/client/mixin/EntityRendererMixin.java e InventoryScreenMixin.java, accorciato.) Per un renderer tuo basta `getBoundingBoxForCulling` (RecisioneRenderer.java); per un proiettile visto da lontano `shouldRenderAtSqrDistance(double)` nell'entità (Missile.java).

Spingere un giocatore dal server (il movimento dei giocatori è del client): `p.setDeltaMovement(v); p.connection.send(new ClientboundSetEntityMotionPacket(p)); p.syncVelocity = true;` (`fling` in mods/chiappe-colossali/src/main/java/com/chiappe/server/FartAttack.java); per i mob basta `e.push(vec)`.

Altri bersagli di mixin che funzionano:
- `DebugScreenOverlay.extractRenderState(GuiGraphicsExtractor)` HEAD cancellabile e `KeyboardHandler.keyPress(long, int, KeyEvent)` HEAD con `options.keyDebugOverlay.matches(event)`: F3 bloccato (Disabilità).
- `FogRenderer.setupFog(Camera, int, DeltaTracker, float, ClientLevel)` RETURN → `FogData` (`environmentalStart/End`, `renderDistanceStart/End`, `skyEnd`, `cloudEnd`, `color`); `Camera.extractRenderState(CameraRenderState, DeltaTracker)` TAIL con `cameraState.entityRenderState.doesMobEffectBlockSky = true` (niente cielo) (Disabilità).
- `SoundEngine.play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;` HEAD → `PlayResult.NOT_STARTED` (sordo); `Font.prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;` con `@ModifyVariable(at = @At("HEAD"), argsOnly = true)`: passa di lì tutto il testo disegnato, menu compresi (dislessia).
- `KeyboardInput.tick` TAIL, in un mixin che `extends ClientInput`: `this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), false, k.shift(), false)` (niente salto né corsa, Filo Rosso). `MouseHandler.turnPlayer(double)` HEAD cancellabile; `Minecraft.startAttack()` / `continueAttack(boolean)` / `startUseItem()` HEAD cancellabili (Disabilità).
- Comuni: `@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)` su `LivingEntity` con firma `(float amount, ServerLevel level, DamageSource source)`, più `heal` HEAD (Filo Rosso); `Player.getDisplayName()` RETURN per un'icona dopo il nome (sopra la testa, in chat, nei messaggi di morte) e `PlayerTabOverlay.getNameForDisplay(PlayerInfo)` per la lista Tab; `Player.updatePlayerPose` HEAD con `setPose(Pose.SWIMMING)` (a terra); `ServerGamePacketListenerImpl.getMaximumFlyingTicks(Entity)` RETURN → `Integer.MAX_VALUE` (niente kick per volo a chi è appeso).

## 12. Trappole
- **Mouse**: `MOUSE_BUTTON_LEFT == 1`, mai 0. Prova le schermate con clic veri (`xdotool`), non chiamando i metodi: in Disabilità 1.0.0 i test passavano e le carte non si cliccavano.
- **G** è di vanilla (`key.quickActions`); `KEY_0` vale 39, non `KEY_1 - 1`.
- **Render differito**: nel `submit` non c'è l'entità e il modello si mette in posa dopo, con `setupAnim(state)`. Quello che serve passa dal render state (`RenderStateDataKey` riempito con un mixin TAIL su `extractRenderState`); le pose dei giocatori vanno in `PlayerModel.setupAnim`.
- **Mixin**: con `defaultRequire: 1` un bersaglio sbagliato fa crashare all'avvio: dopo ogni mixin nuovo avvia il gioco una volta. Per i metodi generici o sovraccarichi (`extractRenderState`, `setupAnim`, `submitNameDisplay`, `prepareText`, `SoundEngine.play`) scrivi il descrittore completo.
- **HUD**: gli elementi di `HudElementRegistry` spariscono con F1; per gli effetti che non si devono poter togliere usa il mixin su `Hud` (ed escludi `LevelLoadingScreen`). Un testo con alfa 0 non si vede.
- **Il modello del giocatore è disegnato a 15/16** (0,9375): le misure in blocchi vanno divise per 0,9375 (`MODEL_SCALE` in ButtLayer, i -4/16 × 0,9375 della sedia).
- **`entityCutoutCull`** scarta le facce girate al contrario: tieni l'ordine dei vertici che guarda fuori (commento in `ButtLayer.sphere`).
- **`tickCount` del server e del client non coincidono**: misura il tempo di un'azione da quando ogni lato vede cambiare il dato (`onSyncedDataUpdated`), e scrivi ogni volta un valore nuovo (`DATA_ACTION_START`), così la stessa azione due volte di fila riparte anche sui client (Recisione.java).
- **Ricarica dei colpi**: nei 10 tick dopo un colpo (`damageCooldownTime > 10`) un colpo nuovo conta solo per quanto supera il precedente; per colpi multipli voluti metti prima `p.damageCooldownTime = 0` (la frusta di Recisione).
- **Danno perso davvero**: per la vita condivisa Filo Rosso salva la vita in un mixin HEAD su `hurtServer` e la confronta in `AFTER_DAMAGE`, così conta quello che resta dopo armatura, resistenza e assorbimento (SharedLife.java).
- **Ender Dragon**: danno pieno solo su testa e collo (`dragon.hurt(level, dragon.head, source, danno)`), sulle altre parti `danno / 4 + min(danno, 1)`; lo ferisce solo una fonte con un giocatore o un tipo nel tag `minecraft:always_hurts_ender_dragons`. Ha 200 di vita.
- **Client senza mod**: manda pacchetti solo se `ServerPlayNetworking.canSend(...)`. Le mod che senza client non hanno senso aspettano fino a 100 tick dal JOIN che `canSend` diventi vero, poi `player.connection.disconnect(Component.literal("Questo mondo usa la mod ...: installala (con Fabric API) per entrare. ..."))` (Joining.java, Chooser.java).
- **Movimento**: il server non muove i giocatori. La fisica del filo è sul client (ogni client muove solo il suo giocatore), con al massimo 0,9 blocchi di correzione per tick e 10 tick di attesa dopo un teletrasporto: senza questi limiti un teletrasporto lanciava il partner (ThreadPhysics.java). Chi è appeso va escluso dal controllo del volo (`getMaximumFlyingTicks`) e dal danno da caduta (`resetFallDistance()`).
- **Grafica su Windows**: con certi driver NVIDIA il gioco crasha in OpenGL mentre carica (`0xC0000005`). Filo Rosso fa partire "Predefinita" in Vulkan (`PreferredGraphicsApiMixin`, `GraphicsFallback`, `MinecraftStartMixin`, più `ClientLifecycleEvents.CLIENT_STARTED.register(client -> GraphicsFallback.started())`): copia quei pezzi se una mod nuova deve essere sicura per l'utente.
- **Post shader**: la profondità è rovesciata (1 = vicino); un effetto che deve coprire anche l'inventario va applicato dopo `GuiRenderer.endFrame()`, non con `getRequestedPostEffects()`.
- **Attachment**: sono record immutabili, e senza un `setAttached` di un valore nuovo i client non vedono niente. Gli altri giocatori lo vedono solo con `AttachmentSyncPredicate.all()` (o `allButTarget()`); l'XP degli altri giocatori in vanilla non arriva ai client, per questo Chiappe copia il livello in un attachment.


## 13. Cose non ancora usate nel repository (verifica nei sorgenti)

Queste non ci sono in nessuna mod 26.3: prima di scriverle, cerca la firma vera nei sorgenti decompilati (`grep -rn "nome" <sorgenti>/common/net/minecraft/...`) e prendi come modello una classe vanilla simile.
- **Uso tenuto premuto** (archi, tridente, cariche): `Item.use` → `player.startUsingItem(hand)`, `getUseDuration`, `getUseAnimation` (`ItemUseAnimation`), `onUseTick`, `releaseUsing`; guarda `BowItem` e `TridentItem`. Lo yo-yo (Forge) evitava `startUsingItem` perché rallenta il giocatore.
- **Armi e attrezzi**: `Item.Properties` con i componenti di arma/attrezzo e i modificatori di attributo; modello item con parent `minecraft:item/handheld`; guarda come vanilla registra le spade in `Items`.
- **Proiettili**: `Filo Rosso` ha `entity/Missile` (ThrowableProjectile senza gravità) e `MissileRenderer`; per la mira guarda `Projectile.shootFromRotation`, per un dato sincronizzato `SynchedEntityData.defineId` + `onSyncedDataUpdated`, per una dimensione che cambia `getDimensions` + `refreshDimensions()`.
- **Esplosioni**: `ServerLevel.explode(...)` con `ExplosionDamageCalculator` e `Level.ExplosionInteraction` (rispetta `minecraft:mob_griefing`); il danno vanilla delle esplosioni scala con la difficoltà: se il danno è un requisito, fallo tu con un tipo di danno `scaling: never` e usa l'esplosione solo per effetto e blocchi.
- **Suoni in loop sul client**: `AbstractTickableSoundInstance`; in 26.3 non c'è ancora, l'unico esempio è `YoyoSpinSound` dello yo-yo (Forge 1.20.1, `mods/rainbow-yoyo/src/main/java/com/rainbowyoyo/client/sound/`): controlla la firma nei sorgenti 26.3.
