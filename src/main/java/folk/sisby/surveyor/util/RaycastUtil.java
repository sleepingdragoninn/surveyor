package folk.sisby.surveyor.util;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;

public class RaycastUtil {
	public static HitResult playerViewRaycast(ServerPlayerEntity player, int renderDistance) {
		// Calculate View Distance to Rendering Cylinder
		Vec3d cameraPos = player.getCameraPosVec(1.0F);
		double pitch = player.getPitch(1.0F);
		double phi = -pitch * (float) (Math.PI / 180.0);
		int blockRadius = renderDistance << 4;
		double y = blockRadius * Math.tan(phi);
		double distance;
		double bottom = player.getEntityWorld().getBottomY() - cameraPos.y;
		double top = player.getEntityWorld().getTopYInclusive() - cameraPos.y;
		if (y < bottom || y > top) { // Distance To Circular Planes
			distance = Math.abs(MathHelper.clamp(y, bottom, top) / Math.sin(phi));
		} else { // Distance To Curved Surface
			distance = Math.sqrt(y * y + blockRadius * blockRadius);
		}

		// Scale Cartesian Rotation Vector By Distance
		Vec3d cameraRotation = player.getRotationVec(1.0F);
		Vec3d endPos = cameraPos.add(cameraRotation.x * distance, cameraRotation.y * distance, cameraRotation.z * distance);
		return BlockView.raycast(
			cameraPos,
			endPos,
			new RaycastContext(
				cameraPos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player
			),
			(innerContext, pos) -> {
				WorldChunk chunk = player.getEntityWorld().getChunkManager().getWorldChunk(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
				if (chunk == null) {
					Vec3d vec3d = innerContext.getStart().subtract(innerContext.getEnd());
					return BlockHitResult.createMissed(pos.toCenterPos(), Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), pos);
				}
				BlockState blockState = chunk.getBlockState(pos);
				FluidState fluidState = blockState.getFluidState();
				Vec3d vec3d = innerContext.getStart();
				Vec3d vec3d2 = innerContext.getEnd();
				VoxelShape voxelShape = innerContext.getBlockShape(blockState, player.getEntityWorld(), pos);
				BlockHitResult blockHitResult = player.getEntityWorld().raycastBlock(vec3d, vec3d2, pos, voxelShape, blockState);
				VoxelShape voxelShape2 = innerContext.getFluidShape(fluidState, player.getEntityWorld(), pos);
				BlockHitResult blockHitResult2 = voxelShape2.raycast(vec3d, vec3d2, pos);
				double d = blockHitResult == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult.getPos());
				double e = blockHitResult2 == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult2.getPos());
				return d <= e ? blockHitResult : blockHitResult2;
			}, innerContext -> {
				Vec3d vec3d = innerContext.getStart().subtract(innerContext.getEnd());
				return BlockHitResult.createMissed(innerContext.getEnd(), Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), BlockPos.ofFloored(innerContext.getEnd()));
			}
		);
	}
}
