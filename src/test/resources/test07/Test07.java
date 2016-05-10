package test07;

import java.io.IOException;
import java.io.Writer;

public class Test07 {

	private static final double LOG_10 = Math.log(10);

	public static void writePaddedInteger(Writer out, int value, int size) throws IOException {
//		if (value < 0) {
//			out.write('-');
//			if (value != Integer.MIN_VALUE) {
//				value = -value;
//			} else {
//				for (; size > 10; size--) {
//					out.write('0');
//				}
//				out.write("" + -(long) Integer.MIN_VALUE);
//				return;
//			}
//		}
//		if (value < 10) {
//			for (; size > 1; size--) {
//				out.write('0');
//			}
//			out.write(value + '0');
//		} else if (value < 100) {
			for (; size > 2; size--) {
				out.write('0');
			}
//			// Calculate value div/mod by 10 without using two expensive
//			// division operations. (2 ^ 27) / 10 = 13421772. Add one to
//			// value to correct rounding error.
//			int d = ((value + 1) * 13421772) >> 27;
//			out.write(d + '0');
//			// Append remainder by calculating (value - d * 10).
//			out.write(value - (d << 3) - (d << 1) + '0');
//		} else {
//			int digits;
//			if (value < 1000) {
//				digits = 3;
//			} else if (value < 10000) {
//				digits = 4;
//			} else {
//				digits = (int) (Math.log(value) / LOG_10) + 1;
//			}
//			for (; size > digits; size--) {
//				out.write('0');
//			}
//			out.write(Integer.toString(value));
//		}
	}
}
