package folk.sisby.surveyor.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class ListUtil {

	public static <T> List<T> splitSet(List<T> list, BitSet set, BitSet oldSet) {
		List<T> outList = new ArrayList<>();
		
		int length = Math.min(Math.min(oldSet.length(), set.length()), list.size());
		for (int i = 0; i < length; i++) {
			if (set.get(i)) outList.add(list.get(i));
		}
		return outList;
	}
}
