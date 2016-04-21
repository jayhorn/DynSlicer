package test06;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;



public class TestClass06 {

	public void writeSignature2SourceLocationMapping(File outFile) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(outFile));
//		try (BufferedReader br = new BufferedReader(new FileReader(outFile))) {
			String line;
			while ((line = br.readLine()) != null) {
			}
//		} catch (Exception e) {
//			
//		}
	}
	


}

