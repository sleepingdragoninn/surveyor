package folk.sisby.surveyor.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class ListUtil {
	public static <T> List<T> splitSet(List<T> list, BitSet set, BitSet oldSet) {
		List<T> outList = new ArrayList<>();
		int c = 0;
		for (int i = 0; i < Math.min(oldSet.length(), set.length()); i++) {
			if (oldSet.get(i)) {
				if (set.get(i)) {
					outList.add(list.get(c));
				}
				c++;
			}
		}
		return outList;
	}
}
