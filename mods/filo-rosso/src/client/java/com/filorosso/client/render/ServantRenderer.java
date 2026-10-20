package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Lo Spezzacuori's servants: zombies in his colours, with glowing eyes and a broken heart on the chest. */
public class ServantRenderer extends ZombieRenderer {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/servant.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/servant_glow.png"));

    public ServantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new EyesLayer<ZombieRenderState, ZombieModel<ZombieRenderState>>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return TEXTURE;
    }
}
