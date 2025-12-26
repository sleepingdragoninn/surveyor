package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public interface SyncPacket extends C2SPacket, S2CPacket {
	default void send(ServerPlayerEntity sender, WorldSummary summary, NetworkMode mode) {
		if (mode.atMost(NetworkMode.NONE)) return;
		if (summary.isClient()) {
			send();
		} else {
			send(sender, (ServerWorld) summary.world(), mode);
		}
	}
}
