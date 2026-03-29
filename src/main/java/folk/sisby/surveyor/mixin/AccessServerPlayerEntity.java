package folk.sisby.surveyor.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface AccessServerPlayerEntity {
	@Accessor
	MinecraftServer getServer();
}
