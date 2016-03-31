/**
 * 
 */
package test01;

/**
 * @author schaef
 *
 */
public class TestClass01 {

	public int test01(int x, int y, int z) {
		String s = "Hello";
		if (x==0) {
			s = null;
		}
		
		if (y==0) {
			if (z==0) {
				s = "42";
			} else {
				s = null;
			}
		}
		return s.length();
	}

}
