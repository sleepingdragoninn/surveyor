package folk.sisby.surveyor.util;

import net.minecraft.storage.ReadView;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

public class ArrayUtil {
	public static int[] ofSingle(int value, int size) {
		int[] array = new int[size];
		Arrays.fill(array, value);
		return array;
	}

	public static boolean isSingle(int[] ints) {
		int head = ints[0];
		for (int i : ints) {
			if (i != head) return false;
		}
		return true;
	}

	public static Vec3d toVec3d(double[] doubles) {
		return new Vec3d(doubles[0], doubles[1], doubles[2]);
	}

	public static int[] ofBlockBox(BlockBox box) {
		return new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()};
	}
}
