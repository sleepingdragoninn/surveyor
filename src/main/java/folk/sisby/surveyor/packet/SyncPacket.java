package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.config.NetworkMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public interface SyncPacket extends C2SPacket, S2CPacket {
	default void send(ServerPlayerEntity sender, World world, NetworkMode mode) {
		if (mode.atMost(NetworkMode.NONE)) return;
		if (world instanceof ServerWorld sw) {
			send(sender, sw, mode);
		} else {
			send(world.getRegistryManager());
		}
	}
}
