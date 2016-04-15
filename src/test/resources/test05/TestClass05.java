package test05;

import java.io.File;
import java.io.IOException;

public class TestClass05 {
	
	static public class A {
		public A() {}
		public String s;
		public int i;
	}

	public void foo(A a) throws IOException {
//		A b = a;
		
		File t = File.createTempFile("t", ".txt");
		t.deleteOnExit();
		a.toString();
	}
	
//	private void bar(A a) {
//		a.toString();
//	}
//	

}

