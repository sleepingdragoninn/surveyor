package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.SurveyorNetworking;
import net.minecraft.registry.DynamicRegistryManager;

public interface C2SPacket extends SurveyorPacket {
	default void send(DynamicRegistryManager registryManager) {
		SurveyorNetworking.C2S_SENDER.accept(registryManager, this);
	}
}
