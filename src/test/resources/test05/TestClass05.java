package test05;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;



public class TestClass05 {
	
//	public void foo(String a) throws IOException {		
//		File t = File.createTempFile(a, ".txt");
//		t.deleteOnExit();
//		a.toString();
//	}

	public boolean compareFiles(File out, File gold) {
		try (FileReader fR1 = new FileReader(out);
				FileReader fR2 = new FileReader(gold);
				BufferedReader reader1 = new BufferedReader(fR1);
				BufferedReader reader2 = new BufferedReader(fR2);) {
//			String line1, line2;
//			while (true) // Continue while there are equal lines
//			{
//				line1 = reader1.readLine();
//				line2 = reader2.readLine();
//
//				// End of file 1
//				if (line1 == null) {
//					// Equal only if file 2 also ended
//					return (line2 == null ? true : false);
//				}
//
//				// Different lines, or end of file 2
//				if (!line1.equalsIgnoreCase(line2)) {
//					return false;
//				}
//			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
}

