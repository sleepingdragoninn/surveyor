package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record S2CStructuresAddedPacket(RegistryKey<World> dimension, boolean shared, Table<RegistryKey<Structure>, ChunkPos, StructureStartSummary> starts, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> types, Multimap<RegistryKey<Structure>, TagKey<Structure>> tags) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_structures_added");

	public static S2CStructuresAddedPacket of(boolean shared, Multimap<RegistryKey<Structure>, ChunkPos> starts, WorldStructures structures) {
		return structures.createUpdatePacket(shared, starts);
	}

	public static S2CStructuresAddedPacket of(boolean shared, RegistryKey<Structure> key, ChunkPos pos, WorldStructures structures) {
		return of(shared, MapUtil.asMultiMap(Map.of(key, List.of(pos))), structures);
	}

	public static S2CStructuresAddedPacket read(PacketByteBuf buf) {
		return new S2CStructuresAddedPacket(
			buf.readRegistryKey(RegistryKeys.WORLD),
			buf.readBoolean(),
			MapUtil.asTable(buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readMap(
					PacketByteBuf::readChunkPos,
					b2 -> new StructureStartSummary(b2.readList(b3 -> WorldStructures.readStructurePieceNbt(Objects.requireNonNull(b3.readNbt()))))
				)
			)),
			buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE_TYPE)
			),
			MapUtil.asMultiMap(buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readList(b2 -> TagKey.of(RegistryKeys.STRUCTURE, b2.readIdentifier()))
			))
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeRegistryKey(dimension);
		buf.writeBoolean(shared);
		buf.writeMap(starts.rowMap(),
			PacketByteBuf::writeRegistryKey,
			(b, posMap) -> b.writeMap(posMap,
				PacketByteBuf::writeChunkPos,
				(b2, summary) -> b2.writeCollection(summary.getChildren(), (b3, piece) -> b3.writeNbt(piece.toNbt()))
			)
		);
		buf.writeMap(types,
			PacketByteBuf::writeRegistryKey,
			PacketByteBuf::writeRegistryKey
		);
		buf.writeMap(tags.asMap(),
			PacketByteBuf::writeRegistryKey,
			(b, c) -> b.writeCollection(c, (b2, t) -> b2.writeIdentifier(t.id()))
		);
	}

	@Override
	public Collection<PacketByteBuf> toBufs() {
		List<PacketByteBuf> bufs = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		writeBuf(buf);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			bufs.add(buf);
		} else {
			Multimap<RegistryKey<Structure>, ChunkPos> keySet = MapUtil.keyMultiMap(starts);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a structure update packet for {} - an individual structure would be too large to send!", keySet.keys().stream().findFirst().orElseThrow().getValue());
				return List.of();
			}
			Multimap<RegistryKey<Structure>, ChunkPos> firstHalf = HashMultimap.create();
			Multimap<RegistryKey<Structure>, ChunkPos> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			bufs.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, firstHalf), MapUtil.splitByKeySet(types, firstHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), firstHalf.keySet()))).toBufs());
			bufs.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, secondHalf), MapUtil.splitByKeySet(types, secondHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), secondHalf.keySet()))).toBufs());
		}
		return bufs;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
