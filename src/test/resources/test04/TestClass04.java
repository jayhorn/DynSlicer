package test04;

public class TestClass04 {
	
	static public class A {
		public A() {}
		public String s;
		public int i;
	}

	public void foo(A a) {
		A b = a;
		a.i = 7;
		bar(b);
	}
	
	private void bar(A a) {
		a.toString();
	}
}

