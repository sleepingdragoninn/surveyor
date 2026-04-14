package folk.sisby.surveyor.util;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

public class BitSetUtil {
	public static Collection<BitSet> half(BitSet original) {
		BitSet firstHalf = BitSet.valueOf(original.toLongArray());
		BitSet secondHalf = BitSet.valueOf(original.toLongArray());
		int fromIndex = 0;
		for (int i = 0; i < original.cardinality() / 2; i++) {
			fromIndex = firstHalf.nextSetBit(fromIndex);
			firstHalf.clear(fromIndex);
		}
		secondHalf.xor(firstHalf);
		return List.of(firstHalf, secondHalf);
	}
}
