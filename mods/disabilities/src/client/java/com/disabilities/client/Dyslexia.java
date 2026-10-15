package com.disabilities.client;

import com.disabilities.condition.Condition;
import com.disabilities.util.Scrambler;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.Arrays;

/**
 * Dyslexia: letters move around inside words as the text is drawn. Only the order changes (each
 * letter keeps its style and width), so text width, centring and wrapping stay exact.
 */
public final class Dyslexia {
    private Dyslexia() {
    }

    public static FormattedCharSequence apply(FormattedCharSequence text) {
        if (!Me.has(Condition.DYSLEXIA)) {
            return text;
        }
        Collector c = new Collector();
        text.accept(c);
        if (c.size < 3) {
            return text;
        }
        int n = c.size;
        int[] cps = Arrays.copyOf(c.codepoints, n);
        int[] order = Scrambler.order(cps, Util.getMillis() / 450L);
        long time = Util.getMillis() / 300L;
        int[] positions = c.positions;
        Style[] styles = c.styles;
        return sink -> {
            for (int i = 0; i < n; i++) {
                int from = order[i];
                if (!sink.accept(positions[i], styles[from], Scrambler.mirror(cps[from], i, time))) {
                    return false;
                }
            }
            return true;
        };
    }

    private static final class Collector implements net.minecraft.util.FormattedCharSink {
        int size;
        int[] positions = new int[32];
        int[] codepoints = new int[32];
        Style[] styles = new Style[32];

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            if (this.size == this.positions.length) {
                int grow = this.size * 2;
                this.positions = Arrays.copyOf(this.positions, grow);
                this.codepoints = Arrays.copyOf(this.codepoints, grow);
                this.styles = Arrays.copyOf(this.styles, grow);
            }
            this.positions[this.size] = position;
            this.codepoints[this.size] = codepoint;
            this.styles[this.size] = style;
            this.size++;
            return true;
        }
    }
}
