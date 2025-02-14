package folk.sisby.surveyor.mixin.client;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.NetworkHandlerSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorNetworkHandler;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler implements SurveyorNetworkHandler {
	@Unique
	NetworkHandlerSummary surveyor$summary = null;

	@Override
	public NetworkHandlerSummary surveyor$getSummary() {
		return surveyor$summary;
	}

	@Accessor
	public abstract GameProfile getProfile();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void onInit(MinecraftClient client, ClientConnection clientConnection, ClientConnectionState clientConnectionState, CallbackInfo ci) {
		ClientPlayNetworkHandler self = (ClientPlayNetworkHandler) (Object) this;
		surveyor$summary = new NetworkHandlerSummary(self);
	}

	@Inject(method = "onDeathMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;showsDeathScreen()Z"))
	private void onDeathScreen(DeathMessageS2CPacket packet, CallbackInfo ci) {
		if (!Surveyor.CONFIG.playerDeathLandmarks) return;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || player.getWorld() == null) return;
		WorldSummary summary = WorldSummary.of(player.getWorld());
		if (summary.isClient()) {
			if (summary.landmarks() == null) return;
			summary.landmarks().put(
				player.getWorld(),
				Landmark.createIncremental(summary.landmarks(), SurveyorClient.getClientUuid(), Surveyor.id("grave"), builder -> builder
					.add(LandmarkComponentTypes.POS, player.getBlockPos())
					.add(LandmarkComponentTypes.NAME, TextUtil.stripInteraction(packet.message()))
					.add(LandmarkComponentTypes.TIME, player.getWorld().getTimeOfDay())
					.add(LandmarkComponentTypes.SEED, player.getRandom().nextInt())
				)
			);
		}
	}

}
