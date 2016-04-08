/**
 * 
 */
package test02;

/**
 * @author schaef
 *
 */
public class TestClass02 {

	public static void main(String[] args) {
		new TestClass02().test01(1);
	}
	
	String s;
	
	public int test01(int x) {
		s=null;
		try {
			s= foo(x);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return s.length();
	}

	private String foo(int x) {
		if (x>0) {
			throw new RuntimeException("bye");
		}
		return "Hello";
	}
}
