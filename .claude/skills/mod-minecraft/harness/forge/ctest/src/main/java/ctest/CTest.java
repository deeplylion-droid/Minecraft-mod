package ctest;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Test driver for Minecraft 1.20.1 + Forge clients (test only, never shipped): see {@link Driver}. */
@Mod("ctest")
public class CTest {
    public CTest() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new Driver());
        }
    }
}
