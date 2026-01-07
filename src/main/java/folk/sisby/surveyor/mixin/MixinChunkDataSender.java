package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.SurveyorExploration;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkDataSender.class)
public class MixinChunkDataSender {
	@Inject(method = "sendChunkData", at = @At("HEAD"))
	private static void sendChunkData(ServerPlayNetworkHandler handler, ServerWorld world, WorldChunk chunk, CallbackInfo ci) {
		SurveyorExploration.of(handler.getPlayer()).addChunk(chunk.getWorld().getRegistryKey(), chunk.getPos(), false);
	}
}
