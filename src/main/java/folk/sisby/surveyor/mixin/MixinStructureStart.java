package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.structure.WorldStructures;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart {
	@Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/Structure;postPlace(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/structure/StructurePiecesList;)V"))
	private void structureGenerated(StructureWorldAccess serverWorldAccess, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, CallbackInfo ci) {
		StructureStart self = (StructureStart) (Object) this;
		ServerWorld world = serverWorldAccess instanceof ServerWorld sw ? sw : ((ChunkRegion) serverWorldAccess).world;
		WorldStructures.onStructurePlace(world, self);
	}
}
