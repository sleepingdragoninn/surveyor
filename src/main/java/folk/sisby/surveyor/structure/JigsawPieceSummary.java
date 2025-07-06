package folk.sisby.surveyor.structure;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.pool.FeaturePoolElement;
import net.minecraft.structure.pool.ListPoolElement;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.pool.StructurePoolElementType;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JigsawPieceSummary extends StructurePieceSummary {
	public static final String KEY_POS = "pos";
	public static final String KEY_DELTA_Y = "deltaY";
	public static final String KEY_ROTATION = "rotation";
	public static final String KEY_JUNCTIONS = "junctions";
	public static final BiMap<String, StructurePoolElementType<?>> TYPE_KEYS = HashBiMap.create(Map.of(
		"single", StructurePoolElementType.SINGLE_POOL_ELEMENT,
		"feature", StructurePoolElementType.FEATURE_POOL_ELEMENT
	));

	// Piece Data
	protected final BlockPos pos;
	protected final int deltaY;
	protected final BlockRotation rotation;
	protected final List<JigsawJunction> junctions;

	// Element Data
	protected final StructurePoolElementType<?> elementType;
	protected final Identifier id;

	public JigsawPieceSummary(BlockPos pos, int deltaY, BlockRotation rotation, StructurePoolElementType<?> elementType, Identifier id, int chainLength, BlockBox boundingBox, List<JigsawJunction> junctions) {
		super(StructurePieceType.JIGSAW, chainLength, boundingBox, new NbtCompound());
		this.pos = pos;
		this.deltaY = deltaY;
		this.rotation = rotation;
		this.elementType = elementType;
		this.id = id;
		this.junctions = junctions;
	}

	public JigsawPieceSummary(NbtCompound nbt) {
		super(nbt);
		this.pos = BlockPos.fromLong(nbt.getLong(KEY_POS).get());
		this.deltaY = nbt.getInt(KEY_DELTA_Y).get();
		this.rotation = BlockRotation.values()[nbt.getInt(KEY_ROTATION).get()];
		this.junctions = new ArrayList<>();
		if (nbt.contains(KEY_JUNCTIONS)) {
			int[] junctionArray = nbt.getIntArray(KEY_JUNCTIONS).get();
			for (int i = 4; i <= junctionArray.length; i += 5) {
				junctions.add(new JigsawJunction(junctionArray[i - 4], junctionArray[i - 3], junctionArray[i - 2], junctionArray[i - 1], StructurePool.Projection.values()[junctionArray[i]]));
			}
		}
		String idKey = TYPE_KEYS.keySet().stream().filter(nbt::contains).findFirst().orElseThrow();
		this.elementType = TYPE_KEYS.get(idKey);
		this.id = Identifier.of(nbt.getString(idKey).get());
	}

	public static List<StructurePieceSummary> tryFromElement(StructurePoolElement poolElement, PoolStructurePiece piece) {
		if (poolElement instanceof ListPoolElement listElement) {
			List<StructurePieceSummary> allSummaries = new ArrayList<>();
			listElement.elements.forEach(e -> allSummaries.addAll(tryFromElement(e, piece)));
			return allSummaries;
		} else if (poolElement instanceof SinglePoolElement singleElement && singleElement.location.left().isPresent()) {
			return List.of(new JigsawPieceSummary(piece.getPos(), piece.getGroundLevelDelta(), piece.getRotation(), StructurePoolElementType.SINGLE_POOL_ELEMENT, singleElement.location.left().orElseThrow(), piece.getChainLength(), poolElement.getBoundingBox(piece.structureTemplateManager, piece.getPos(), piece.getRotation()), piece.getJunctions()));
		} else if (poolElement instanceof FeaturePoolElement featureElement && featureElement.feature.getKey().isPresent()) {
			return List.of(new JigsawPieceSummary(piece.getPos(), piece.getGroundLevelDelta(), piece.getRotation(), StructurePoolElementType.FEATURE_POOL_ELEMENT, featureElement.feature.getKey().orElseThrow().getValue(), piece.getChainLength(), poolElement.getBoundingBox(piece.structureTemplateManager, piece.getPos(), piece.getRotation()), piece.getJunctions()));
		}
		return List.of();
	}

	public static List<StructurePieceSummary> tryFromPiece(StructurePiece piece) {
		if (piece instanceof PoolStructurePiece poolPiece) {
			return tryFromElement(poolPiece.getPoolElement(), poolPiece);
		}
		return List.of();
	}

	@Override
	public void writeNbt(StructureContext context, NbtCompound nbt) {
		super.writeNbt(context, nbt);
		nbt.putLong(KEY_POS, this.pos.asLong());
		nbt.putInt(KEY_DELTA_Y, this.deltaY);
		nbt.putInt(KEY_ROTATION, this.rotation.ordinal());
		String idKey = TYPE_KEYS.inverse().get(elementType);
		nbt.putString(idKey, id.toString());
		if (!junctions.isEmpty()) {
			int[] junctionArray = new int[junctions.size() * 5];
			for (int i = 0; i < junctions.size(); i++) {
				JigsawJunction j = junctions.get(i);
				junctionArray[i * 5] = j.getSourceX();
				junctionArray[i * 5 + 1] = j.getSourceGroundY();
				junctionArray[i * 5 + 2] = j.getSourceZ();
				junctionArray[i * 5 + 3] = j.getDeltaY();
				junctionArray[i * 5 + 4] = j.getDestProjection().ordinal();
			}
			nbt.putIntArray(KEY_JUNCTIONS, junctionArray);
		}
	}

	public BlockPos getPos() {
		return pos;
	}

	public int getDeltaY() {
		return deltaY;
	}

	public BlockRotation getRotation() {
		return rotation;
	}

	public StructurePoolElementType<?> getElementType() {
		return elementType;
	}

	public Identifier getId() {
		return id;
	}

	public List<JigsawJunction> getJunctions() {
		return junctions;
	}
}
