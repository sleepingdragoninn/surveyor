package folk.sisby.surveyor.client;

import com.mojang.authlib.GameProfile;

public interface SurveyorNetworkHandler {
	ClientSummary surveyor$getSummary();

	GameProfile getProfile();
}
