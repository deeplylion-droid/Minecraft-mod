package ctest.mixin;

import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test only: Xvfb + Mesa has no sRGB-capable GLX visual, so do not ask for one. */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlBackend")
public class GlBackendMixin {
    @Redirect(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z"))
    private boolean ctest$attribute(int attribute, int value) {
        return SDLVideo.SDL_GL_SetAttribute(attribute, attribute == 22 ? 0 : value);
    }
}
