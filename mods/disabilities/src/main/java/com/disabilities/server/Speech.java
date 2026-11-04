package com.disabilities.server;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.gesture.Gesture;
import com.disabilities.net.Net;
import com.disabilities.util.Scrambler;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

/**
 * Mute players cannot chat (or /msg, /me...): they make gestures instead, which everyone nearby
 * sees. Dyslexic players' messages arrive with swapped letters.
 */
public final class Speech {
    private Speech() {
    }

    /** Commands that are talking. */
    private static final Set<String> TALKING = Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm");
    /** How far a gesture can be seen. */
    private static final double GESTURE_RANGE = 40.0;
    private static final java.util.Map<java.util.UUID, Long> LAST_GESTURE = new java.util.HashMap<>();

    public static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, type) -> {
            if (isMute(sender)) {
                sender.sendOverlayMessage(Component.translatable("disabilities.msg.mute").withStyle(ChatFormatting.RED));
                return false;
            }
            return true;
        });
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE, (sender, message) -> {
            if (sender != null && ModData.has(sender, Condition.DYSLEXIA)) {
                return Component.literal(Scrambler.typos(message.getString(), sender.getRandom().nextLong()));
            }
            return message;
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.MakeGesture.TYPE, (payload, context) -> gesture(context.player(), payload.gesture()));
        ServerPlayNetworking.registerGlobalReceiver(Net.TriedToSpeak.TYPE, (payload, context) -> triedToSpeak(context.player()));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) -> afterChat(sender, message.decoratedContent()));
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_TRY = new java.util.HashMap<>();

    /** A mute player tried to talk: everybody around sees and hears them try. */
    private static void triedToSpeak(ServerPlayer player) {
        if (!isMute(player) || ModData.lying(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_TRY.get(player.getUUID());
        if (last != null && now - last < 30) {
            return;
        }
        LAST_TRY.put(player.getUUID(), now);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), com.disabilities.sound.ModSounds.MMH,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        Ailments.bubble(player, Component.translatable("disabilities.bubble.mmh").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 50);
    }

    /** After a chat message: dyslexic players' words also float over their head; deaf players around say "Eh?". */
    private static void afterChat(ServerPlayer sender, Component content) {
        if (ModData.has(sender, Condition.DYSLEXIA)) {
            Ailments.bubble(sender, content.copy().withStyle(ChatFormatting.WHITE), 70);
        }
        for (ServerPlayer other : sender.level().players()) {
            if (other != sender && ModData.has(other, Condition.DEAF) && other.distanceToSqr(sender) < 16 * 16 && !ModData.lying(other)) {
                Ailments.motion(other, com.disabilities.net.MotionKind.EAR, 0);
                Ailments.bubble(other, Component.translatable("disabilities.bubble.eh").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 40);
            }
        }
    }

    public static boolean isMute(ServerPlayer player) {
        return ModData.has(player, Condition.MUTE);
    }

    /** True if this command is speech and the source is a mute player (it is then refused). */
    public static boolean blocks(CommandSourceStack source, String command) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !isMute(player)) {
            return false;
        }
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        int space = cmd.indexOf(' ');
        String root = (space < 0 ? cmd : cmd.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) {
            root = root.substring(colon + 1);
        }
        if (TALKING.contains(root)) {
            player.sendOverlayMessage(Component.translatable("disabilities.msg.mute").withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    private static void gesture(ServerPlayer player, int index) {
        Gesture gesture = Gesture.byOrdinal(index);
        if (gesture == null || ModData.asleep(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_GESTURE.get(player.getUUID());
        if (last != null && now - last < 15) {
            return;
        }
        LAST_GESTURE.put(player.getUUID(), now);
        Net.ShowGesture show = new Net.ShowGesture(player.getId(), gesture.ordinal());
        Component line = Component.translatable("disabilities.gesture.chat", player.getDisplayName(),
                        Chooser.badge(String.valueOf(gesture.glyph())), gesture.displayName().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        ServerLevel level = player.level();
        for (ServerPlayer other : level.players()) {
            if (other == player || other.distanceToSqr(player) < GESTURE_RANGE * GESTURE_RANGE) {
                if (ServerPlayNetworking.canSend(other, Net.ShowGesture.TYPE)) {
                    ServerPlayNetworking.send(other, show);
                }
                other.sendSystemMessage(line);
            }
        }
    }
}
