package folk.sisby.surveyor.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;

public record SectionSummary(Palette<BlockState> blockPalette, int[] blockIndices, Palette<RegistryEntry<Biome>> biomePalette, int[] biomeIndices) {
	public static SectionSummary ofSection(ChunkSection section) {
		if (section.isEmpty()) {
			return null;
		} else {
			int[] blockIndices = new int[PalettedContainer.PaletteProvider.BLOCK_STATE.getContainerSize()];
			section.getBlockStateContainer().data.storage.writePaletteIndices(blockIndices);
			int[] biomeIndices = new int[PalettedContainer.PaletteProvider.BIOME.getContainerSize()];
			((PalettedContainer<RegistryEntry<Biome>>) section.getBiomeContainer()).data.storage.writePaletteIndices(biomeIndices);
			return new SectionSummary(
				section.getBlockStateContainer().data.palette,
				blockIndices,
				((PalettedContainer<RegistryEntry<Biome>>) section.getBiomeContainer()).data.palette,
				biomeIndices
			);
		}
	}

	public BlockState getBlockState(int relativeX, int y, int relativeZ) {
		return blockPalette().get(blockIndices()[PalettedContainer.PaletteProvider.BLOCK_STATE.computeIndex(relativeX, y & 15, relativeZ)]);
	}

	public RegistryEntry<Biome> getBiomeEntry(int relativeX, int y, int relativeZ, int bottomY, int topY) {
		return biomePalette().get(biomeIndices()[PalettedContainer.PaletteProvider.BIOME.computeIndex(BiomeCoords.fromBlock(relativeX) & 3, MathHelper.clamp(BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(bottomY), BiomeCoords.fromBlock(topY) - 1) & 3, BiomeCoords.fromBlock(relativeZ) & 3)]);
	}
}
