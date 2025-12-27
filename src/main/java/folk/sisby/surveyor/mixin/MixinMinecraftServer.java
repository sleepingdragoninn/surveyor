package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.SurveyorServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements SurveyorServer {
	@Unique
	private ServerSummary surveyor$summary = null;

	@Override
	public ServerSummary surveyor$getSummary() {
		return surveyor$summary;
	}

	@Inject(method = "loadWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;prepareStartRegion(Lnet/minecraft/server/WorldGenerationProgressListener;)V"))
	public void loadSummary(CallbackInfo ci) {
		MinecraftServer self = (MinecraftServer) (Object) this;
		if (surveyor$summary != null) return;
		surveyor$summary = ServerSummary.load(self);
		surveyor$summary.loadWorlds();
	}

	@Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getOverworld()Lnet/minecraft/server/world/ServerWorld;"))
	private void saveSummary(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
		if (surveyor$summary != null) surveyor$summary.save(force, suppressLogs);
	}
}
