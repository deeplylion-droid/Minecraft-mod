package com.filorosso.entity;

import com.filorosso.data.Couple;

/** The bosses of the bond (Recisione, the Burattinaio): only couples really hurt them. */
public interface BondBoss {
    /**
     * The damage it really takes: couple hits count fully, the others hardly at all.
     *
     * @param combo  a couple hit (both partners on it within a second, or a power of the bond)
     * @param couple the couple of whoever hit it (null if none)
     */
    float adjustDamage(float amount, boolean combo, Couple couple);

    /** Caught by a couple's lasso until then: slowed down, and that couple's hits count as couple hits. */
    void lassoed(long until, Couple couple);

    /** A kiss went off next to it (its wave counts as a couple hit). */
    default void kissed(Couple couple) {
    }
}
