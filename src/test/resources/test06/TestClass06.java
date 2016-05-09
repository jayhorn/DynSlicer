package test06;

public class TestClass06 {

	private class A {
		int i;
		public A(Object s) {
			i =s.hashCode();
		}
		public int get() {
			return i;
		}
	}
	
	public void failingConstructor(Object s) {
		A t = new A(s);
		t.get();
		
	}

}
