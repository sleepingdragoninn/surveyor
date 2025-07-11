package folk.sisby.surveyor.structure;

import folk.sisby.surveyor.util.ArrayUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class StructurePieceSummary extends StructurePiece {
	protected final NbtCompound pieceNbt;
	protected final RegistryKey<StructurePieceType> typeKey;

	public StructurePieceSummary(StructurePieceType type, int chainLength, BlockBox boundingBox, NbtCompound pieceNbt) {
		super(type, chainLength, boundingBox);
		this.typeKey = Registries.STRUCTURE_PIECE.getKey(type).orElseThrow();
		this.pieceNbt = pieceNbt;
	}

	public StructurePieceSummary(NbtCompound nbt) {
		super(Registries.STRUCTURE_PIECE.get(Identifier.of(nbt.getString("id").orElseThrow())), nbt); // Might set the type as null
		this.typeKey = RegistryKey.of(RegistryKeys.STRUCTURE_PIECE, Identifier.of(nbt.getString("id").orElseThrow()));
		this.pieceNbt = nbt.getCompound("nbt").orElse(new NbtCompound());
	}

	public static StructurePieceSummary fromPiece(StructureContext context, StructurePiece piece, boolean withNbt) {
		StructurePieceSummary summary = new StructurePieceSummary(piece.getType(), piece.getChainLength(), piece.getBoundingBox(), new NbtCompound());
		if (withNbt) {
			NbtCompound summaryNbt = summary.toNbt();
			NbtCompound pieceNbt = piece.toNbt(context);
			for (String key : summaryNbt.getKeys()) {
				pieceNbt.remove(key);
			}
			for (String key : pieceNbt.getKeys()) {
				summary.pieceNbt.put(key, pieceNbt.get(key));
			}
		}
		return summary;
	}

	public final NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("id", typeKey.getValue().toString());
		nbt.putIntArray("BB", ArrayUtil.ofBlockBox(this.boundingBox));
		Direction direction = this.getFacing();
		nbt.putInt("O", direction == null ? -1 : direction.getHorizontalQuarterTurns());
		nbt.putInt("GD", this.chainLength);
		this.writeNbt(null, nbt);  // context only used for writeNbt
		return nbt;
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		if (!pieceNbt.isEmpty()) nbt.put("nbt", pieceNbt);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
	}

	public NbtCompound getPieceNbt() {
		return pieceNbt;
	}
}
