package folk.sisby.surveyor.structure;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionStructureSummary {
	public static final String KEY_STRUCTURES = "structures";
	public static final String KEY_STARTS = "starts";
	public static final String KEY_PIECES = "pieces";

	protected final Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> starts = new ConcurrentHashMap<>();
	protected boolean dirty = false;

	RegionStructureSummary() {
	}

	RegionStructureSummary(Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> starts) {
		this.starts.putAll(starts);
	}

	protected static StructureStartSummary summarisePieces(StructureContext context, StructureStart start) {
		List<StructurePieceSummary> pieces = new ArrayList<>();
		for (StructurePiece piece : start.getChildren()) {
			if (piece.getType().equals(StructurePieceType.JIGSAW)) {
				pieces.addAll(JigsawPieceSummary.tryFromPiece(piece));
			} else {
				pieces.add(StructurePieceSummary.fromPiece(context, piece, start.getChildren().size() <= 10));
			}
		}
		return new StructureStartSummary(pieces);
	}

	public static StructurePieceSummary readStructurePieceNbt(NbtCompound nbt) {
		if (nbt.getString("id").equals(Registries.STRUCTURE_PIECE.getId(StructurePieceType.JIGSAW).toString())) {
			return new JigsawPieceSummary(nbt);
		} else {
			return new StructurePieceSummary(nbt);
		}
	}

	protected static RegionStructureSummary readNbt(NbtCompound nbt) {
		Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> structures = new ConcurrentHashMap<>();
		NbtCompound structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getKeys()) {
			RegistryKey<Structure> key = RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(structureId));
			NbtCompound structureCompound = structuresCompound.getCompound(structureId);
			NbtCompound startsCompound = structureCompound.getCompound(KEY_STARTS);
			for (String posKey : startsCompound.getKeys()) {
				int x = Integer.parseInt(posKey.split(",")[0]);
				int z = Integer.parseInt(posKey.split(",")[1]);
				NbtCompound startCompound = startsCompound.getCompound(posKey);
				Collection<StructurePieceSummary> pieces = new ArrayList<>();
				for (NbtElement pieceElement : startCompound.getList(KEY_PIECES, NbtElement.COMPOUND_TYPE)) {
					pieces.add(readStructurePieceNbt((NbtCompound) pieceElement));
				}
				structures.computeIfAbsent(key, p -> new ConcurrentHashMap<>()).put(new ChunkPos(x, z), new StructureStartSummary(pieces));
			}
		}
		return new RegionStructureSummary(structures);
	}

	public boolean contains(World world, StructureStart start) {
		RegistryKey<Structure> key = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getKey(start.getStructure()).orElse(null);
		if (key == null) {
			Surveyor.LOGGER.error("Encountered an unregistered structure! {} | {}", start, start.getStructure());
			return true;
		}
		return starts.containsKey(key) && starts.get(key).containsKey(start.getPos());
	}

	public boolean contains(RegistryKey<Structure> key, ChunkPos pos) {
		return starts.containsKey(key) && starts.get(key).containsKey(pos);
	}

	public StructureStartSummary get(RegistryKey<Structure> key, ChunkPos pos) {
		return starts.get(key).get(pos);
	}

	public Multimap<RegistryKey<Structure>, ChunkPos> keySet() {
		return MapUtil.keyMultiMap(starts);
	}

	public void put(ServerWorld world, StructureStart start) {
		RegistryKey<Structure> key = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getKey(start.getStructure()).orElseThrow();
		starts.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
		ChunkPos pos = start.getPos();
		StructureStartSummary summary = summarisePieces(StructureContext.from(world), start);
		starts.get(key).put(pos, summary);
		dirty();
	}

	public void put(RegistryKey<Structure> key, ChunkPos pos, StructureStartSummary summary) {
		starts.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(pos, summary);
		dirty();
	}

	protected NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound structuresCompound = new NbtCompound();
		starts.forEach((key, starts) -> {
			NbtCompound structureCompound = new NbtCompound();
			NbtCompound startsCompound = new NbtCompound();
			starts.forEach((pos, summary) -> {
				NbtList pieceList = new NbtList(summary.getChildren().stream().map(p -> (NbtElement) p.toNbt()).toList(), NbtElement.COMPOUND_TYPE);
				NbtCompound startCompound = new NbtCompound();
				startCompound.put(KEY_PIECES, pieceList);
				startsCompound.put("%s,%s".formatted(pos.x, pos.z), startCompound);
			});
			structureCompound.put(KEY_STARTS, startsCompound);
			structuresCompound.put(key.getValue().toString(), structureCompound);
		});
		nbt.put(KEY_STRUCTURES, structuresCompound);
		return nbt;
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
