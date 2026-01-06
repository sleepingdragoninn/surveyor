package folk.sisby.surveyor.structure;

import net.minecraft.util.math.BlockBox;

import java.util.Collection;

public class StructureStartSummary {
	protected final Collection<StructurePieceSummary> children;
	protected BlockBox boundingBox;

	public StructureStartSummary(Collection<StructurePieceSummary> children) {
		this.children = children;
	}

	public BlockBox getBoundingBox() {
		if (boundingBox == null) {
			BlockBox.encompass(children.stream().map(StructurePieceSummary::getBoundingBox)::iterator).ifPresent(blockBox -> boundingBox = blockBox);
			if (boundingBox == null) return new BlockBox(0, 0, 0, 0, 0, 0);
		}
		return boundingBox;
	}

	public Collection<StructurePieceSummary> getChildren() {
		return children;
	}
}
