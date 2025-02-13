package folk.sisby.surveyor.landmark;

import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public interface Landmark {
	UUID owner();

	Identifier id();

	BlockPos pos();

	default @Nullable DyeColor color() {
		return null;
	}

	default @Nullable Text name() {
		return null;
	}

	default @Nullable Identifier texture() {
		return null;
	}

	default Map<UUID, Map<Identifier, Landmark>> put(Map<UUID, Map<Identifier, Landmark>> changed, World world, WorldLandmarks landmarks) {
		return landmarks.putForBatch(changed, this);
	}

	default Map<UUID, Map<Identifier, Landmark>> remove(Map<UUID, Map<Identifier, Landmark>> changed, World world, WorldLandmarks landmarks) {
		return landmarks.removeForBatch(changed, this.owner(), this.id());
	}
}
