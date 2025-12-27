package folk.sisby.surveyor.mixin.client;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.ClientSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorNetworkHandler;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler implements SurveyorNetworkHandler {
	@Unique
	ClientSummary surveyor$summary = null;

	@Override
	public ClientSummary surveyor$getSummary() {
		return surveyor$summary;
	}

	@Accessor
	public abstract GameProfile getProfile();

	@Inject(method = "onGameJoin", at = @At("TAIL"))
	private void onJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
		if (surveyor$summary != null) return; // some mods might do this
		ClientPlayNetworkHandler self = (ClientPlayNetworkHandler) (Object) this;
		surveyor$summary = new ClientSummary(packet.sha256Seed(), self);
		surveyor$summary.connect();
	}

	@Inject(method = "clearWorld", at = @At("HEAD"))
	void saveOnDisconnect(CallbackInfo ci) {
		if (surveyor$summary != null) surveyor$summary.disconnect();
	}

	@Inject(method = "onPlayerRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getScoreboard()Lnet/minecraft/scoreboard/Scoreboard;"))
	void saveOnLeaveWorld(PlayerRespawnS2CPacket packet, CallbackInfo ci) { // just for unloading regions. ditch when we figure out how to do that better.
		ClientPlayNetworkHandler self = (ClientPlayNetworkHandler) (Object) this;
		if (surveyor$summary != null) surveyor$summary.leaveWorld(self.getWorld().getRegistryKey());
	}

	@Inject(method = "onDeathMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;showsDeathScreen()Z"))
	private void onDeathScreen(DeathMessageS2CPacket packet, CallbackInfo ci) {
		if (!Surveyor.CONFIG.builtins.playerDeathWaypoints) return;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || player.getWorld() == null) return;
		WorldSummary summary = WorldSummary.of(player.getWorld());
		if (summary.isClient()) {
			if (summary.landmarks() == null) return;
			summary.landmarks().put(
                    Landmark.createIncremental(summary.landmarks(), SurveyorClient.getClientUuid(), Surveyor.id("grave"), builder -> builder
					.add(LandmarkComponentTypes.POS, player.getBlockPos())
					.add(LandmarkComponentTypes.NAME, TextUtil.stripInteraction(packet.getMessage()))
					.add(LandmarkComponentTypes.TIME, player.getWorld().getTimeOfDay())
					.add(LandmarkComponentTypes.SEED, player.getRandom().nextInt())
				)
			);
		}
	}

}
