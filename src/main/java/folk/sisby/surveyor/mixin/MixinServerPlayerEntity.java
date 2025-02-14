package folk.sisby.surveyor.mixin;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorPlayer;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class MixinServerPlayerEntity implements SurveyorPlayer {
	@Unique
	PlayerSummary.ServerPlayerEntitySummary surveyor$summary = null;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void init(MinecraftServer server, ServerWorld world, GameProfile profile, CallbackInfo ci) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		surveyor$summary = new PlayerSummary.ServerPlayerEntitySummary(self);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	public void writeSurveyorData(NbtCompound nbt, CallbackInfo ci) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		surveyor$summary.writeNbt(nbt);
		ServerSummary.of(self.getServer()).updatePlayer(Surveyor.getUuid(self), nbt, false, self.getServer());
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	public void readSurveyorData(NbtCompound nbt, CallbackInfo ci) {
		surveyor$summary.read(nbt);
	}

	@Inject(method = "setClientSettings", at = @At("HEAD"))
	public void setSurveyorViewDistance(ClientSettingsC2SPacket packet, CallbackInfo ci) {
		surveyor$summary.setViewDistance(packet.viewDistance());
	}

	@Inject(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/damage/DamageTracker;update()V"))
	public void onDeath(DamageSource damageSource, CallbackInfo ci) {
		if (!Surveyor.CONFIG.playerDeathLandmarks) return;
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		WorldLandmarks summary = WorldSummary.of(self.getServerWorld()).landmarks();
		if (summary == null) return;
		summary.put(
			self.getServerWorld(),
			Landmark.createIncremental(summary, SurveyorClient.getClientUuid(), Surveyor.id("grave"), builder -> builder
				.add(LandmarkComponentTypes.POS, self.getBlockPos())
				.add(LandmarkComponentTypes.NAME, TextUtil.stripInteraction(self.getDamageTracker().getDeathMessage()))
				.add(LandmarkComponentTypes.TIME, self.getWorld().getTimeOfDay())
				.add(LandmarkComponentTypes.SEED, self.getRandom().nextInt())
			)
		);
	}

	@Inject(method = "copyFrom", at = @At("TAIL"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		surveyor$summary.copyFrom(PlayerSummary.of(oldPlayer));
	}

	@Override
	public PlayerSummary surveyor$getSummary() {
		return surveyor$summary;
	}
}
