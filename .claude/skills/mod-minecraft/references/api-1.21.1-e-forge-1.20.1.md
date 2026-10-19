# Forge 1.20.1 e Fabric 1.21.1: toolchain e schemi di codice

Le due toolchain più vecchie del repository: servono per aggiornare Würstel Gigante e Yo-Yo Arcobaleno (Forge 1.20.1) o Sotto la Bedrock e Jumpscare! (Fabric 1.21.1), o per fare una mod nuova per quelle versioni. Gli esempi sono copiati dal codice vero: sotto ogni blocco c'è il file (dalla radice del repository), `// ...` vuol dire righe tolte e i commenti in italiano dentro i blocchi sono miei. Le note di ogni mod sono nel `CLAUDE.md`; per 26.3 leggi `api-26.3.md` (lì molti nomi cambiano).

## Indice
1. Forge 1.20.1: toolchain
2. Forge 1.20.1: schemi usati
3. Fabric 1.21.1: toolchain
4. Fabric 1.21.1: schemi usati
5. Trappole (e cosa cambia tra 1.20.1 e 1.21.1)

## 1. Forge 1.20.1: toolchain
- ForgeGradle `[6.0,6.2)`, Forge `1.20.1-47.2.0`, mappature `official` (nomi Mojang), Java 17, **wrapper Gradle 8.8 della radice**: nessuna mod Forge ha un `gradlew` suo.
- Würstel nella radice (`wurstel` 1.1.0), Yo-Yo in `mods/rainbow-yoyo/` (`rainbowyoyo` 1.0.0): i due `build.gradle` sono identici. Per una mod nuova copia `build.gradle` e `settings.gradle` dello yo-yo (in `pluginManagement` il maven `https://maven.minecraftforge.net/`, più il plugin `org.gradle.toolchains.foojay-resolver-convention` 0.8.0 che scarica Java 17 se manca) e cambia `rootProject.name` e `gradle.properties`.
- Comandi, con `JAVA_HOME` su un JDK 17 (Gradle 8.8 gira al massimo su Java 22: non con il JDK 25 delle mod 26.3):
```
./gradlew build                            # Würstel -> build/libs/wurstel-1.1.0.jar
./gradlew -p mods/rainbow-yoyo build       # -> mods/rainbow-yoyo/build/libs/rainbowyoyo-1.0.0.jar
./gradlew -p mods/rainbow-yoyo runClient   # o runServer, nella cartella run/ (ignorata da git)
```
- La prima build scarica Forge e Minecraft da `maven.minecraftforge.net` e dai server Mojang: se il sandbox li blocca, fai il push e leggi l'esito della build di GitHub Actions con l'API di GitHub. La CI fa `JAVA_HOME="${!java_home_var}" ./gradlew -p "$dir" build --stacktrace --no-daemon`, con Java 17 se manca `java_version`, e senza `mod_loader=fabric` intitola la Release "(Forge 1.20.1)" (`.github/workflows/build.yml`).
```groovy
plugins {
    // ... ('eclipse', 'idea')
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}
// ... (version = mod_version, group = mod_group_id, base { archivesName = mod_id })
java.toolchain.languageVersion = JavaLanguageVersion.of(17)
minecraft {
    mappings channel: mapping_channel, version: mapping_version
    // ... (copyIdeResources, runs: workingDirectory project.file('run'), client {}, server { args '--nogui' })
}
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
}
// ... (jar: manifest con 'Implementation-Version' : project.jar.archiveVersion, poi finalizedBy 'reobfJar')
```
(`build.gradle`, identico a `mods/rainbow-yoyo/build.gradle`; `reobfJar` riscrive il jar con i nomi del gioco vero)
```properties
minecraft_version=1.20.1
forge_version=47.2.0
mapping_channel=official
mapping_version=1.20.1
mod_id=rainbowyoyo
mod_name=Yo-Yo Arcobaleno
mod_version=1.0.0
mod_group_id=com.rainbowyoyo
mod_authors=deeplylion-droid
```
(`mods/rainbow-yoyo/gradle.properties`, più `org.gradle.jvmargs=-Xmx3G` e `org.gradle.daemon=false`: niente `mod_loader` né `java_version`, quindi la CI usa Forge e Java 17)
```toml
modLoader="javafml"
loaderVersion="[47,)"
[[mods]]
modId="rainbowyoyo"
version="${file.jarVersion}"
displayName="Yo-Yo Arcobaleno"
# ... (authors, description, poi [[dependencies.rainbowyoyo]] per "forge" "[47,)" e "minecraft" "[1.20.1,1.21)")
```
(`mods/rainbow-yoyo/src/main/resources/META-INF/mods.toml`) `${file.jarVersion}` è l'`Implementation-Version` del manifest: niente `processResources`, per una versione nuova basta alzare `mod_version`. `pack.mcmeta` ha `"pack_format": 15`.

## 2. Forge 1.20.1: schemi usati
### Registrazione
```java
public WurstelMod() {
    IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
    ModItems.ITEMS.register(modBus);
    ModSounds.SOUNDS.register(modBus);
    ModEnchantments.ENCHANTMENTS.register(modBus);
    ModRecipes.SERIALIZERS.register(modBus);
    modBus.addListener(WurstelMod::addToCreativeTabs);
    ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WurstelConfig.CLIENT_SPEC);
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WurstelConfig.SERVER_SPEC);
}
// registry/ModItems.java:
public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WurstelMod.MODID);
public static final RegistryObject<Item> WURSTEL_BELT = ITEMS.register("wurstel_belt",
        () -> new WurstelBeltItem(WurstelArmorMaterial.WURSTEL, new Item.Properties().stacksTo(1)));
```
(`src/main/java/com/wurstel/WurstelMod.java`, `registry/ModItems.java`; lo yo-yo registra anche `ModEntities.ENTITIES` e chiama `YoyoNetwork.register()`)
- Uguale per `ForgeRegistries.SOUND_EVENTS` (`() -> SoundEvent.createVariableRangeEvent(new ResourceLocation(WurstelMod.MODID, name))`), `ENCHANTMENTS` (`TurgidoEnchantment::new`), `RECIPE_SERIALIZERS` (`BeltUpgradeRecipe.Serializer::new`) ed `ENTITY_TYPES` (`EntityType.Builder.<YoyoEntity>of(YoyoEntity::new, MobCategory.MISC).sized(1.25F, 1.25F)` ... `.noSave().noSummon().build("rainbow_yoyo")` in `mods/rainbow-yoyo/.../registry/ModEntities.java`). L'oggetto vero si prende con `.get()`. Schede creative: `BuildCreativeModeTabContentsEvent` sul bus della mod, `if (event.getTabKey() == CreativeModeTabs.COMBAT) { event.accept(ModItems.WURSTEL_BELT); ... }`.
- Suoni: `assets/wurstel/sounds.json` (`"slap": { "subtitle": "subtitles.wurstel.slap", "sounds": ["wurstel:slap1", ...] }`) e i file `assets/wurstel/sounds/*.ogg` (Vorbis mono). Loop che segue un'entità: `YoyoSpinSound extends AbstractTickableSoundInstance` (`looping = true`, `canStartSilent()` vero, posizione e volume aggiornati in `tick()`), avviato con `Minecraft.getInstance().getSoundManager().play(new YoyoSpinSound(yoyo))`.
### Config
```java
Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
// ... SERVER = server.getLeft(); SERVER_SPEC = server.getRight(); poi nel costruttore Server(ForgeConfigSpec.Builder builder):
builder.push("slap");
damageMultiplier = builder
        .comment("Multiplier applied to bare-hand damage when slapping with the wurstel belt (1 = like a punch).")
        .defineInRange("damageMultiplier", 2.0, 0.0, 100.0);
```
(`src/main/java/com/wurstel/config/WurstelConfig.java`, poi `builder.pop()`) Si legge con `WurstelConfig.SERVER.extraKnockback.get()`; CLIENT finisce in `config/wurstel-client.toml`, SERVER in `saves/<mondo>/serverconfig/wurstel-server.toml`.
### Eventi
```java
@Mod.EventBusSubscriber(modid = WurstelMod.MODID)      // bus FORGE, client e server
public final class SlapEvents {
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // ...
        event.setAmount(amount * WurstelBeltItem.getDamageMultiplier(belt));
    }
```
(`src/main/java/com/wurstel/event/SlapEvents.java`) Nella stessa classe: `PlayerEvent.BreakSpeed` (`event.setNewSpeed(event.getNewSpeed() * multiplier)`, gira su client e server), `PlayerEvent.HarvestCheck` (`event.setCanHarvest(true)` se `TierSortingRegistry.isCorrectTierForDrops(harvestTier, state)`), `LivingKnockBackEvent` (`event.setStrength(...)`).
- Client sul bus della mod: `@Mod.EventBusSubscriber(modid = ..., bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)` per `EntityRenderersEvent.RegisterRenderers` (`event.registerEntityRenderer(ModEntities.YOYO.get(), YoyoRenderer::new)`) e `EntityRenderersEvent.AddLayers`:
```java
for (String skin : event.getSkins()) {
    EntityRenderer<? extends Player> renderer = event.getSkin(skin);
    attach(renderer);
}
```
(`src/main/java/com/wurstel/client/ClientModEvents.java`; lo stesso con `event.getRenderer(type)` per supporti, zombie, scheletri, piglin. `attach()` fa `living.addLayer(new WurstelLayer(living))` se il renderer è un `LivingEntityRenderer` con `HumanoidModel`; `WurstelLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M>` usa `getParentModel().body` e `.head`)
- Client sul bus FORGE (`value = Dist.CLIENT`): `TickEvent.ClientTickEvent` (guarda `event.phase`), `RenderLevelStageEvent` (quando `event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES`, con `event.getPoseStack()`, `getCamera()` e `getPartialTick()`, Würstel e yo-yo disegnano nel mondo quello che in prima persona non si vedrebbe), `RenderHandEvent` (`setCanceled(true)`), `RenderLivingEvent.Pre/Post`, `InputEvent.InteractionKeyMappingTriggered`, `ClientPlayerNetworkEvent.LoggingOut` (svuota le mappe statiche) (`client/ClientEvents.java` delle due mod).
### Rete (SimpleChannel, client -> server)
```java
public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(RainbowYoyoMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
public static void register() {
    CHANNEL.messageBuilder(YoyoActionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(YoyoActionPacket::encode)
            .decoder(YoyoActionPacket::decode)
            .consumerMainThread(YoyoActionPacket::handle)
            .add();
}
```
(`mods/rainbow-yoyo/src/main/java/com/rainbowyoyo/network/YoyoNetwork.java`) `YoyoActionPacket` è un `record` con `encode`/`decode` su `FriendlyByteBuf` e `handle(Supplier<NetworkEvent.Context> context)` che usa `context.get().getSender()`; si manda con `CHANNEL.sendToServer(packet)`. Verso il client lo yo-yo non manda pacchetti: usa `SynchedEntityData` (`defineSynchedData()` con `this.entityData.define(DATA_PHASE, ...)`) e `level().broadcastEntityEvent(this, EVENT_HIT)`, letto in `handleEntityEvent(byte)`.
### Item 3D, posa del braccio, codice solo client
```java
@Override   // in YoyoItem (codice comune)
public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new YoyoClientExtensions());
}
// YoyoClientExtensions implements IClientItemExtensions (e getCustomRenderer() restituisce YoyoItemRenderer.get()):
public static final HumanoidModel.ArmPose POSE =
        HumanoidModel.ArmPose.create("RAINBOWYOYO_STRING", false, YoyoClientExtensions::poseArm);
@Override
public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
    return hand == InteractionHand.MAIN_HAND && entity instanceof Player ? POSE : null;
}
```
(`mods/rainbow-yoyo/src/main/java/com/rainbowyoyo/item/YoyoItem.java` e `client/YoyoClientExtensions.java`; `poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm)` scrive `xRot/yRot/zRot` del braccio)
- BEWLR: `YoyoItemRenderer extends BlockEntityWithoutLevelRenderer`, creato pigro con `super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels())`, disegna in `renderByItem(stack, ItemDisplayContext, poseStack, buffers, light, overlay)`. Il modello dell'item **deve** avere `"parent": "builtin/entity"` (più i `display`: `assets/rainbowyoyo/models/item/rainbow_yoyo.json`).
- Parti luminose: secondo passaggio con `RenderType.eyes(GLOW_TEXTURE)` e `LightTexture.FULL_BRIGHT` (`client/render/YoyoModelRenderer.java`). Luccichio degli incantesimi: `ItemRenderer.getArmorFoilBuffer(buffers, renderType(texture(belt)), false, belt.hasFoil())` (`src/main/java/com/wurstel/client/render/WurstelRenderer.java`).
- Codice client chiamato da codice comune: `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.startSpinSound(this));`, con le chiamate client raccolte in `client/ClientHooks.java` (`entity/YoyoEntity.java`).
### Dati: tipo di danno, ricette, incantesimo
- `data/rainbowyoyo/damage_type/yoyo.json`: `{ "exhaustion": 0.1, "message_id": "rainbowyoyo.yoyo", "scaling": "when_caused_by_living_non_player" }`, nei lang `death.attack.rainbowyoyo.yoyo` (più `.player` e `.item`). Chiave `ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RainbowYoyoMod.MODID, "yoyo"))`; la fonte si crea con `level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ModDamageTypes.YOYO)` e `new DamageSource(type.get(), this, owner)` (diretta = lo yo-yo, colpevole = il giocatore), altrimenti `damageSources().playerAttack(owner)` (`mods/rainbow-yoyo/src/main/java/com/rainbowyoyo/entity/YoyoEntity.java`).
```java
public class BeltUpgradeRecipe extends ShapedRecipe {
    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = super.assemble(container, registryAccess);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof WurstelBeltItem && stack.hasTag()) {
                result.setTag(stack.getTag().copy());
                // ...
```
(`src/main/java/com/wurstel/recipe/BeltUpgradeRecipe.java`; il `Serializer` passa `fromJson`/`fromNetwork`/`toNetwork` a `RecipeSerializer.SHAPED_RECIPE`) Si registra solo il serializer: `getType()` resta quello del banco da lavoro e il JSON è un `crafting_shaped` con `"type": "wurstel:belt_upgrade"` (`data/wurstel/recipes/diamond_wurstel_belt.json`).
- Il passo netherite è vanilla: `"type": "minecraft:smithing_transform"` con `"template": { "item": "minecraft:netherite_upgrade_smithing_template" }`, `"base": { "item": "wurstel:diamond_wurstel_belt" }`, `"addition": { "item": "minecraft:netherite_ingot" }`, `"result": { "item": "wurstel:netherite_wurstel_belt" }` (`data/wurstel/recipes/netherite_wurstel_belt_smithing.json`; ogni ricetta ha il suo advancement in `data/wurstel/advancements/recipes/`).
- Categoria d'incantesimo per un item nuovo: `EnchantmentCategory.create("WURSTEL_BELT", item -> item instanceof WurstelBeltItem)` e `super(Rarity.RARE, WURSTEL_BELT, new EquipmentSlot[]{EquipmentSlot.LEGS})`; livello con `belt.getEnchantmentLevel(ModEnchantments.TURGIDO.get())` (`src/main/java/com/wurstel/enchantment/TurgidoEnchantment.java`).

## 3. Fabric 1.21.1: toolchain
- Loom `1.7.4` (plugin `fabric-loom`) con il **wrapper Gradle 8.8 della radice**, mappature Mojang (`loom.officialMojangMappings()`), loader 0.16.14, Fabric API `0.116.17+1.21.1`, Java 21. MixinExtras (`@WrapOperation`, `@ModifyExpressionValue`, `@Local`) è già nel loader: nessuna dipendenza in più.
- `build.gradle` e `settings.gradle` di `mods/below-bedrock/` e `mods/jumpscares/` sono identici (cambia `rootProject.name`); `settings.gradle` ha in `pluginManagement` il maven `https://maven.fabricmc.net/`, `mavenCentral()` e `gradlePluginPortal()`.
- Comandi, con `JAVA_HOME` su un JDK 21 (con `options.release = 21` Java 17 non basta): `./gradlew -p mods/below-bedrock build` (jar in `build/libs/belowbedrock-1.1.0.jar`, più `build/devlibs/*-dev.jar` con i nomi Mojang) e `./gradlew -p mods/jumpscares runClient --args="--quickPlaySingleplayer <mondo>"` (o `runServer`).
```properties
minecraft_version=1.21.1
loader_version=0.16.14
fabric_version=0.116.17+1.21.1
mod_loader=fabric
java_version=21
mod_id=belowbedrock
mod_name=Sotto la Bedrock
mod_version=1.1.0
maven_group=com.belowbedrock
mod_authors=deeplylion-droid
```
(`mods/below-bedrock/gradle.properties`, più `org.gradle.jvmargs=-Xmx3G`, `org.gradle.daemon=false`, `org.gradle.parallel=true`; `mod_loader` e `java_version` li legge la CI)
```groovy
plugins {
    id 'fabric-loom' version '1.7.4'
}
// ... (version = project.mod_version, group = project.maven_group, base { archivesName = project.mod_id })
loom {
    // Client code lives in src/client, so the dedicated server never sees it.
    splitEnvironmentSourceSets()
    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
    accessWidenerPath = file("src/main/resources/${mod_id}.accesswidener")
}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}
// ... (JavaCompile: it.options.release = 21, encoding 'UTF-8'; java { source/targetCompatibility = JavaVersion.VERSION_21 })
```
(`mods/below-bedrock/build.gradle`, identico a `mods/jumpscares/build.gradle`)
- `fabric.mod.json`: `"version": "${version}"`, `"environment": "*"`, entrypoint `main` (`com.belowbedrock.BelowBedrock`) e `client` (`com.belowbedrock.client.BelowBedrockClient`), `"mixins": ["belowbedrock.mixins.json", { "config": "belowbedrock.client.mixins.json", "environment": "client" }]`, `"accessWidener"`, `"depends": { "fabricloader": ">=0.16.0", "minecraft": "~1.21.1", "java": ">=21", "fabric-api": "*" }`. Il `.accesswidener` contiene solo `accessWidener v2 named`: i campi privati si leggono con `@Shadow`. Niente `pack.mcmeta`.
- File dei mixin: `"package"`, `"compatibilityLevel": "JAVA_21"`, liste `"mixins"` (comuni, in `src/main/resources`) e `"client"` (in `src/client/resources`), `"injectors": { "defaultRequire": 1 }`: un bersaglio sbagliato fa crashare all'avvio, quindi dopo ogni mixin nuovo fai partire il gioco.

## 4. Fabric 1.21.1: schemi usati
- Ingresso: `BelowBedrock implements ModInitializer`, `onInitialize()` chiama `ModItems.register()`, `ModEntities.register()`... (i campi statici si registrano quando la classe viene caricata, `register()` può anche essere vuoto). Client: `JumpscaresClient implements ClientModInitializer`. Id: `ResourceLocation.fromNamespaceAndPath(MODID, path)` in un metodo `id(path)`.
```java
public static final Item GLITCHED_PICKAXE = Registry.register(BuiltInRegistries.ITEM, BelowBedrock.id("glitched_pickaxe"),
        new GlitchedPickaxeItem(new Item.Properties().attributes(PickaxeItem.createAttributes(Tiers.DIAMOND, 1.0F, -2.8F))));
// in register():
ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
        .register(entries -> entries.addAfter(Items.DIAMOND_PICKAXE, GLITCHED_PICKAXE));
```
(`mods/below-bedrock/src/main/java/com/belowbedrock/registry/ModItems.java`) Allo stesso modo `BuiltInRegistries.ENTITY_TYPE` (+ `FabricDefaultAttributeRegistry.register(INHABITANT, Inhabitant.createAttributes())`), `SOUND_EVENT` (`SoundEvent.createVariableRangeEvent(id)`), `LOOT_FUNCTION_TYPE`. Renderer: `EntityRendererRegistry.register(ModEntities.SCARE_PEARL, ThrownItemRenderer::new)`.
### Pacchetti
```java
public record ScarePayload(int kind, long seed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ScarePayload> TYPE = new CustomPacketPayload.Type<>(Jumpscares.id("scare"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScarePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScarePayload::kind,
            ByteBufCodecs.VAR_LONG, ScarePayload::seed,
            ScarePayload::new);
    // ... (type() restituisce TYPE)
}
PayloadTypeRegistry.playS2C().register(ScarePayload.TYPE, ScarePayload.CODEC);      // net/ScareNet.java
ClientPlayNetworking.registerGlobalReceiver(ScarePayload.TYPE,                       // client/JumpscaresClient.java
        (payload, context) -> ScareClient.start(ScareKind.byId(payload.kind()), payload.seed()));
```
(`mods/jumpscares/src/main/java/com/jumpscares/net/ScarePayload.java`) Invio: `if (!ServerPlayNetworking.canSend(victim, ScarePayload.TYPE)) { return false; }` e poi `ServerPlayNetworking.send(victim, new ScarePayload(kind.ordinal(), seed))`.
- In 1.21.1 i nomi sono `PayloadTypeRegistry.playS2C()` / `playC2S()` (in 26.3 cambiano). Le due mod usano solo `playS2C`; per client -> server servono `playC2S().register(...)` e `ServerPlayNetworking.registerGlobalReceiver(TYPE, handler)` (ci sono in questa Fabric API, controllato nel jar). Il ricevitore del client gira già sul thread di render.
### Attachment, dimensione, dati salvati
- `AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(BelowBedrock.id("visited"))`, poi `player.getAttached(type)` / `player.setAttached(ModAttachments.VISITED, true)` (`registry/ModAttachments.java`, `fall/VoidFall.java`). Restano sul server: il builder ha anche `syncWith(StreamCodec, AttachmentSyncPredicate)`, qui non usato.
- `data/belowbedrock/dimension/faded.json`: `"type": "belowbedrock:faded"` e `"generator": { "type": "minecraft:noise", "settings": "minecraft:overworld", "biome_source": { "type": "minecraft:multi_noise", "preset": "minecraft:overworld" } }` (stesso seme = copia del mondo). `dimension_type/faded.json`: `"natural": false` (bussola e orologio girano, niente portali), `"fixed_time": 6000`, `"effects": "belowbedrock:faded"`, che il client registra con `DimensionRenderingRegistry.registerDimensionEffects(BelowBedrock.id("faded"), new FadedEffects())` (`FadedEffects extends DimensionSpecialEffects`, cielo `SkyType.NONE`). Nel codice: `ResourceKey.create(Registries.DIMENSION, BelowBedrock.id("faded"))` e `level.dimension() == LEVEL` (`world/Faded.java`).
- Dati del mondo: `new SavedData.Factory<>(Ids::new, Ids::load, null)` con `load(CompoundTag, HolderLookup.Provider)` e `save(...)`, preso con `overworld.getDataStorage().computeIfAbsent(Ids.FACTORY, "belowbedrock_maps")` (`world/FadedMaps.java`). Advancement senza trigger vanilla: `"criteria": { "done": { "trigger": "minecraft:impossible" } }` e `player.getAdvancements().award(holder, "done")` (`registry/ModAdvancements.java`).
- Eventi server usati: `ServerTickEvents.END_SERVER_TICK`, `ServerLivingEntityEvents.ALLOW_DAMAGE` (ritorna `false` per annullare il danno del vuoto), `ServerPlayerEvents.AFTER_RESPAWN`, `ServerPlayConnectionEvents.DISCONNECT`, `UseBlockCallback.EVENT`, `ServerEntityEvents.ENTITY_LOAD`, `ServerLifecycleEvents.SERVER_STOPPED`.
### Mixin usati

| Mixin | Bersaglio | Perché |
|---|---|---|
| `BlockBehaviourMixin` | `getDestroyProgress` HEAD | la bedrock si scava solo col piccone glitchato |
| `ChunkGeneratorMixin` | `applyBiomeDecoration`: `@WrapOperation` su `PlacedFeature.placeWithBiomeCheck`, `@Inject` TAIL | salta `VEGETAL_DECORATION` e `disk_grass`, poi `FadedCleaner` e `FadedDecorator` |
| `FeaturePoolElementMixin` | `place` HEAD | niente alberi e fiori nei villaggi (resta `Feature.BLOCK_PILE`) |
| `WorldGenRegionMixin` | `addFreshEntity` HEAD | nessun `Mob` creato dal worldgen |
| `NaturalSpawnerMixin`, `BaseSpawnerMixin`, `TrialSpawnerMixin` | `spawnForChunk` / `serverTick` / `tickServer` HEAD | mondo sbiadito vuoto |
| `WorldDimensionsMixin` | `checkStability` HEAD, `@ModifyExpressionValue` su `List.size()` in `bake` | il mondo non viene segnato "sperimentale" |
| BB `GameRendererMixin` | `render`, dopo `LevelRenderer.doEntityOutline()` | post-shader dopo il mondo e prima dell'HUD |
| `FogRendererMixin`, `ReceivingLevelScreenMixin` | `setupFog` RETURN, `render` HEAD | nebbia; schermata di caricamento nera |
| JS `GameRendererMixin` | `renderLevel` dopo `LevelRenderer.renderLevel(...)` (descrittore completo), `bobHurt` TAIL, `getFov` RETURN, `renderItemInHand` HEAD | mostro in faccia, tremolio, pugno di FOV, mano nascosta |
| `GuiMixin` | `Gui.render` HEAD, `cancellable = true` | via l'HUD durante lo spavento, al suo posto flash, disturbo, nero |

BB = `mods/below-bedrock/src/{main,client}/java/com/belowbedrock/**/mixin/`, JS = `mods/jumpscares/src/client/java/com/jumpscares/client/mixin/`. I metodi aggiunti hanno il prefisso della mod (`belowbedrock$haze`, `jumpscares$shake`), i campi nuovi sono `@Unique`.
### Grafica e suono sul client
- Post-shader: la catena è in `assets/belowbedrock/shaders/post/screen.json` (passaggio `"name": "belowbedrock_screen"` da `minecraft:main` a `swap`, poi `blit` indietro), ma il **programma** (`belowbedrock_screen.json` con `"vertex": "sobel"` + `belowbedrock_screen.fsh`) sta sempre in `assets/minecraft/shaders/program/`. Ogni uniform va dichiarato nel JSON del programma. Uso: `new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), CHAIN)`, `chain.resize(width, height)` quando cambia la finestra, `postChain.setUniform("Fade", fadeNow)`, `postChain.process(frameTicks)`, poi `getMainRenderTarget().bindWrite(true)`; al reload delle risorse `chain.close()` (`src/client/java/com/belowbedrock/client/ScreenFx.java`).
- HUD: `HudRenderCallback.EVENT.register(HudFx::render)` con `render(GuiGraphics graphics, DeltaTracker delta)` disegna sopra l'HUD (Fabric lo chiama a TAIL di `Gui.render`); per nascondere l'HUD vanilla Jumpscare! usa `GuiMixin` a HEAD con `ci.cancel()`. `Gui.render` viene chiamato anche con F1 (controllato nel bytecode di 1.21.1).
- Nel mondo: `WorldRenderEvents.AFTER_ENTITIES.register(ScareRenderer::renderWitnesses)`, dal `WorldRenderContext` `context.tickCounter().getGameTimeDeltaPartialTick(true)`, `context.camera().getPosition()`, `context.world()`, poi `mc.renderBuffers().bufferSource()` ed `endBatch(...)` (`client/scare/ScareRenderer.java`).
- Suoni solo per sé: `Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1.0F));` (canale MASTER, senza attenuazione: `client/scare/ScareClient.java`). Per chi sta intorno: `level.playSound(victim, victim.getX(), victim.getEyeY(), victim.getZ(), ModSounds.scream(kind), SoundSource.HOSTILE, 2.5F, 1.0F)`, dove il primo argomento è chi **non** lo sente (`scare/Scares.java`). Per un solo giocatore dal server: `player.connection.send(new ClientboundSoundEntityPacket(...))` (`below-bedrock/.../util/SoundFx.java`).
- Modelli da JSON ricaricati con le risorse (un resource pack li può sostituire): `ScareModels implements SimpleSynchronousResourceReloadListener`, con `getFabricId()` = `Jumpscares.id("scare_models")` e in `onResourceManagerReload(ResourceManager manager)`, per ogni mostro, `ResourceLocation file = Jumpscares.id("scares/" + kind.id + ".json")` letto con `manager.getResource(file)`; parti costruite con `cubes.texOffs(...).addBox(..., new CubeDeformation(inflate), 1.0F / density, 1.0F / density)` e `LayerDefinition.create(mesh, (int) size[0], (int) size[1]).bakeRoot()`; registrato con `ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ScareModels.INSTANCE)` (`mods/jumpscares/src/client/java/com/jumpscares/client/scare/ScareModels.java`).
### Comandi, gamerule, loot, proiettili
```java
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
        Commands.literal("jumpscare")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("giocatori", EntityArgument.players())
                        // ... .executes(...), poi "mostro" (StringArgumentType.word() con .suggests) e "secondi"
```
(`mods/jumpscares/src/main/java/com/jumpscares/scare/ScareCommand.java`; la risposta va solo a chi scrive: `source.sendSuccess(() -> ..., false)`)
- Gamerule: `GameRuleRegistry.register("fadedHauntings", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true))` (`registry/ModGameRules.java`).
- Funzione di loot: `CreepyMapFunction extends LootItemConditionalFunction` con `RecordCodecBuilder.mapCodec(instance -> commonFields(instance).and(Codec.STRING.optionalFieldOf("picture").forGetter(...)).apply(instance, CreepyMapFunction::new))`, registrata con `new LootItemFunctionType<>(CreepyMapFunction.CODEC)` e usata come `"functions": [{ "function": "belowbedrock:creepy_map" }]` (`loot/CreepyMapFunction.java`, `registry/ModLoot.java`, `data/belowbedrock/loot_table/chests/`). Bauli in worldgen: `RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot)`.
```java
public class ScarePearlItem extends Item implements ProjectileItem {
    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        ScarePearl pearl = new ScarePearl(level, position.x(), position.y(), position.z());
        // ... pearl.setItem(stack); return pearl;
```
(`mods/jumpscares/src/main/java/com/jumpscares/item/ScarePearlItem.java`; `use()` crea `new ScarePearl(level, player)` e `shootFromRotation(...)`; il distributore la lancia dopo `DispenserBlock.registerProjectileBehavior(pearl)` in `ModItems.register()`; l'entità `ScarePearl extends ThrowableItemProjectile`, disegnata con `ThrownItemRenderer`)

## 5. Trappole
Forge 1.20.1:
- La config SERVER esiste solo con un mondo aperto: nei tooltip controlla `WurstelConfig.SERVER_SPEC.isLoaded()` prima di leggerla.
- Un server dedicato non traduce i nomi della mod: `YoyoItem.getName` con `FMLEnvironment.dist != Dist.CLIENT` restituisce il `Component.translatable` invece del testo.
- Forge azzera la ricarica dell'attacco solo dopo il colpo: in `LivingHurtEvent` `player.getAttackStrengthScale(0.5F)` vale ancora e il Würstel divide per `0.2F + charge * charge * 0.8F` per colpire sempre pieno. Se cambi i numeri ricontrolla il drago (8 × 32 = 256 contro 200 di vita).
- Un'armatura indistruttibile (durabilità 0) non si incanta: `WurstelBeltItem.isEnchantable` è forzato a `true`.
- Disegno in `AFTER_ENTITIES`: chiudi il batch (`buffers.endBatch(...)`) e, se incantato, anche `RenderType.armorEntityGlint()` dopo. I giocatori sono disegnati in scala 0.9375 (da compensare); l'anteprima dell'inventario si riconosce con `RenderSystem.getProjectionMatrix().m33() > 0.5F` (ortografica).
- Niente pugno del braccio: a fine tick client `living.attackAnim = 0.0F; living.oAttackAnim = 0.0F;` (`WurstelAnimManager`).
- Yo-yo: niente `startUsingItem` (rallenta come un arco); col tasto tenuto vanilla ripete `use` ogni 4 tick e il server lo usa come battito (`HOLD_TICKS`), il client manda `RELEASE`. I clic d'attacco si consumano a inizio tick (`mc.options.keyAttack.consumeClick()`), prima di vanilla, e `InteractionKeyMappingTriggered` si annulla con `setSwingHand(false)`.
- Le eccezioni nel tick dello yo-yo vengono loggate e l'entità tolta (`discard()`) invece di far crashare. La matematica di `motion/` non usa classi di Minecraft e si prova con `javac` da sola.
- Con `PROTOCOL::equals` sui due lati, client e server devono avere la mod con la stessa versione del canale.

Fabric 1.21.1:
- Mod di test compilate con Loom contro il jar `-dev` della mod (`compileOnly files(...)`): i metodi di Minecraft ereditati dalle classi della mod si chiamano dopo il cast alla classe di Minecraft, altrimenti il remapping fallisce (nella mod di test, fuori dal repository: `((Entity) him).distanceTo(...)`).
- Una dimensione in più fa segnare il mondo come "sperimentale": lo evita `WorldDimensionsMixin`.
- Cartelli in worldgen: `setText()` va in NPE (avviserebbe un mondo che non esiste ancora). Codifica `SignText` con `SignText.DIRECT_CODEC` in un tag `front_text` e chiama `sign.loadWithComponents(tag, level.registryAccess())` (`world/FadedDecorator.java`).
- In worldgen non si creano mappe: le cornici nascono con una mappa segnaposto (custom data `belowbedrock_picture`) sostituita in `ServerEntityEvents.ENTITY_LOAD` (`world/FadedWorld.java`). Il decoratore è deterministico per chunk e scrive solo nel chunk e nei vicini.
- Prima di `teleportTo` in un'altra dimensione carica il chunk d'arrivo: `addRegionTicket(TicketType.PORTAL, ...)` 100 tick prima (`fall/VoidFall.java`).
- Pacchetti solo a chi ha la mod (`ServerPlayNetworking.canSend`): chi non ce l'ha entra lo stesso ma non vede niente.
- `SimpleSoundInstance.forUI(sound, pitch)` ha volume **0.25**: usa `forUI(sound, pitch, volume)` (prima il pitch, poi il volume; controllato nel jar).
- Tremolio in `bobHurt` TAIL scalato con `options.screenEffectScale()`, flash attenuato con `options.hideLightningFlash()`.
- Ricette senza forma: controlla che non coincidano con una vanilla (la perla Demone ha 3 ingredienti perché perla + polvere di blaze è l'occhio di ender).

Se porti codice da una versione all'altra:

| Cosa | Forge 1.20.1 | Fabric 1.21.1 |
|---|---|---|
| Id | `new ResourceLocation(WurstelMod.MODID, name)` | `ResourceLocation.fromNamespaceAndPath(MODID, path)`, `ResourceLocation.withDefaultNamespace("disk_grass")` |
| Registri | `DeferredRegister` + `RegistryObject.get()` | `Registry.register(BuiltInRegistries.X, id, obj)` in campi statici |
| Dati dell'entità | `defineSynchedData()` + `this.entityData.define(...)` | `defineSynchedData(SynchedEntityData.Builder builder)` + `builder.define(...)` |
| Dati dell'item | NBT: `stack.hasTag()`, `result.setTag(...)` | componenti: `stack.set(DataComponents.MAP_ID, id)`, `CustomData.update(...)` |
| Tooltip | `appendHoverText(stack, @Nullable Level level, tooltip, flag)` | `appendHoverText(stack, TooltipContext context, tooltip, flag)` |
| Cartelle `data/` | `recipes/`, `advancements/` | `recipe/`, `advancement/`, `loot_table/` (`damage_type/` in tutte e due) |
| JSON | `"result": { "item": ... }`, `"items": ["minecraft:netherite_ingot"]` | `"result": { "id": ..., "count": 1 }`, `"items": "minecraft:ender_pearl"` |
| Danno | `registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ModDamageTypes.YOYO)` + `new DamageSource(...)` | `player.damageSources().source(TAKEN, him)` |
| Tick parziale | `Minecraft.getInstance().getFrameTime()`, `event.getPartialTick()` | `deltaTracker.getGameTimeDeltaPartialTick(true)` (`DeltaTracker` arriva nei mixin e negli eventi) |

In 1.21.1 gli incantesimi sono file JSON e `ArmorMaterial` è un registro, non un'interfaccia: Turgido e le cinture del Würstel non si portano così come sono.
