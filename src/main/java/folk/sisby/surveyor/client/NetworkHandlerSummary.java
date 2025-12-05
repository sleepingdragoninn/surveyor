package folk.sisby.surveyor.client;

import com.google.common.collect.Sets;
import folk.sisby.surveyor.PlayerSummary;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NetworkHandlerSummary {
	private final ClientPlayNetworkHandler handler;
	private final Map<UUID, PlayerSummary> offlineSummaries;

	public NetworkHandlerSummary(ClientPlayNetworkHandler handler) {
		this.handler = handler;
		this.offlineSummaries = new HashMap<>();
	}

	public static NetworkHandlerSummary of(ClientPlayNetworkHandler handler) {
		return ((SurveyorNetworkHandler) handler).surveyor$getSummary();
	}

	public void mergeSummaries(Map<UUID, PlayerSummary> summaries) {
		offlineSummaries.putAll(summaries);
	}

	public void matchSummaries(Map<UUID, PlayerSummary> summaries) {
		offlineSummaries.clear();
		offlineSummaries.putAll(summaries);
	}

	public Map<UUID, PlayerSummary> players(Set<UUID> group) {
		Map<UUID, PlayerSummary> outMap = new HashMap<>();
		for (UUID uuid : Sets.union(group, offlineSummaries.keySet())) {
			PlayerEntity player = handler.getWorld().getPlayerByUuid(uuid);
			if (player != null) {
				outMap.put(uuid, new PlayerSummary.PlayerEntitySummary(player));
			} else {
				outMap.put(uuid, offlineSummaries.get(uuid));
			}
		}
		return outMap;
	}

}
