package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class MixinServerWorld {
	@Inject(method = "method_66017(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/registry/entry/RegistryEntry;)V", at = @At("HEAD"))
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

	@Inject(method = "method_66019(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/registry/entry/RegistryEntry;)V", at = @At("HEAD"))
	public void onPointOfInterestRemoved(BlockPos blockPos, RegistryEntry<PointOfInterestType> oldPoiType, CallbackInfo ci) {
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
