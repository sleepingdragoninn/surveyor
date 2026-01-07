package folk.sisby.surveyor.packet;

public interface ShareFlagged<T extends S2CPacket> {
	T withShared(boolean shared);
}
