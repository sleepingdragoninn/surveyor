package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorWorld;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;
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
		surveyor$summary = WorldSummary.load(self, Surveyor.getSavePath(self.getRegistryKey(), self.getServer()), false);
		SurveyorEvents.Invoke.worldLoad(self);
	}

	@Inject(method = "method_19499", at = @At("HEAD"))
	public void onPointOfInterestAdded(BlockPos blockPos, RegistryEntry<PointOfInterestType> poiType, CallbackInfo ci) {
		if (!Surveyor.CONFIG.netherPortalLandmarks) return;
		ServerWorld self = (ServerWorld) (Object) this;
		WorldSummary summary = WorldSummary.of(self);
		if (summary.landmarks() != null && poiType.getKey().orElse(null) == PointOfInterestTypes.NETHER_PORTAL && self.getBlockState(blockPos).contains(NetherPortalBlock.AXIS)) {
			summary.landmarks().put(self, new Landmark(WorldLandmarks.GLOBAL, Identifier.of(Surveyor.ID, "poi/nether_portal/%s/%s/%s".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ())), LandmarkComponentMap.builder()
				.add(LandmarkComponentTypes.POS, blockPos)
				.add(LandmarkComponentTypes.AXIS, self.getBlockState(blockPos).get(NetherPortalBlock.AXIS))
				.build()
			));
		}
	}

	@Inject(method = "method_39222", at = @At("HEAD"))
	public void onPointOfInterestRemoved(BlockPos blockPos, CallbackInfo ci) {
		ServerWorld self = (ServerWorld) (Object) this;
		WorldSummary summary = WorldSummary.of(self);
		if (summary.landmarks() == null) return;
		summary.landmarks().removeAll(self, l -> l.owner().equals(WorldLandmarks.GLOBAL)
			&& l.id().getNamespace().equals(Surveyor.ID)
			&& l.id().getPath().startsWith("poi")
			&& l.components().contains(LandmarkComponentTypes.POS)
			&& l.components().get(LandmarkComponentTypes.POS).equals(blockPos)
		);
	}
}
