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
		A t=null;
		try {
			t = new A(s);
		} catch (Exception e) {}
		t.get();
	}

}
