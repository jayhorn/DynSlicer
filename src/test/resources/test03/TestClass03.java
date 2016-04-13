package test03;

public class TestClass03 {

	public static void main(String[] args) {
		if (args.length > 0) {
			String s = args[0];
			(new TestClass03()).foo(s);
		}
	}
	
	public void foo(String s) {
		int idx = s.indexOf("X");
		System.out.println(s.substring(idx));		
	}
}
