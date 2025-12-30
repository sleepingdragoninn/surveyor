package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.NetherPortal;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortal.class)
public class MixinNetherPortal {
	@Shadow @Final private @Nullable BlockPos lowerCorner;
	@Shadow @Final private int height;
	@Shadow @Final private Direction negativeDir;
	@Shadow @Final private int width;

	@Inject(method = "createPortal", at = @At("TAIL"))
	private void onCreatePortal(WorldAccess world, CallbackInfo ci) {
		if (!Surveyor.CONFIG.builtins.netherPortalLandmarks) return;
		if (!(world instanceof ServerWorld serverWorld)) return;
		WorldSummary summary = WorldSummary.of(serverWorld);
		if (summary.landmarks() == null) return;
		Identifier id = Identifier.of(PointOfInterestTypes.NETHER_PORTAL.getValue().getNamespace(), "poi/%s/%s/%s/%s".formatted(PointOfInterestTypes.NETHER_PORTAL.getValue().getPath(), lowerCorner.getX(), lowerCorner.getY(), lowerCorner.getZ()));
		summary.landmarks().put(Landmark.global(id, builder -> LandmarkComponentTypes.forBlock(builder, serverWorld, lowerCorner)
			.add(LandmarkComponentTypes.COLOR, DyeColor.PURPLE.getFireworkColor())
			.add(LandmarkComponentTypes.BOX, BlockBox.create(lowerCorner, this.lowerCorner.offset(Direction.UP, this.height - 1).offset(negativeDir, width - 1)))
		));
	}
}
