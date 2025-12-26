package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorWorld;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class MixinServerWorld implements SurveyorWorld {
	@Unique
	private WorldSummary surveyor$summary = null;

	@Override
	public WorldSummary surveyor$getSummary() {
		return surveyor$summary;
	}

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionOptions;chunkGenerator()Lnet/minecraft/world/gen/chunk/ChunkGenerator;"))
	public void loadSummary(CallbackInfo ci) {
		ServerWorld self = (ServerWorld) (Object) this;
		MinecraftServer server = self.getServer();
		RegistryKey<World> dimension = self.getRegistryKey();
		DynamicRegistryManager manager = self.getRegistryManager();
		surveyor$summary = new WorldSummary(server, dimension, manager, Surveyor.getSavePath(self.getRegistryKey(), self.getServer()));
		SurveyorEvents.Invoke.worldLoad(self);
	}

	@Inject(method = "method_19499", at = @At("HEAD"))
	public void onPointOfInterestAdded(BlockPos blockPos, RegistryEntry<PointOfInterestType> poiType, CallbackInfo ci) {
		ServerWorld self = (ServerWorld) (Object) this;
		WorldSummary summary = WorldSummary.of(self);
		if (summary.landmarks() == null) return;
		if (poiType.getKey().isEmpty() || !Surveyor.CONFIG.builtins.poiLandmarks.contains(poiType.getKey().get().getValue().toString())) return;
		Identifier poi = poiType.getKey().get().getValue();
		summary.landmarks().put(Landmark.global(
			Identifier.of(poi.getNamespace(), "poi/%s/%s/%s/%s".formatted(poi.getPath(), blockPos.getX(), blockPos.getY(), blockPos.getZ())),
			builder -> LandmarkComponentTypes.forBlock(builder, self, blockPos)
		));
	}

	@Inject(method = "method_39222", at = @At("HEAD"))
	public void onPointOfInterestRemoved(BlockPos blockPos, CallbackInfo ci) {
		ServerWorld self = (ServerWorld) (Object) this;
		WorldSummary summary = WorldSummary.of(self);
		if (summary.landmarks() == null) return;
		summary.landmarks().removeAll(l -> l.owner().equals(WorldLandmarks.GLOBAL)
			&& l.id().getPath().startsWith("poi")
			&& l.components().contains(LandmarkComponentTypes.POS)
			&& l.components().get(LandmarkComponentTypes.POS).equals(blockPos)
		);
	}
}
