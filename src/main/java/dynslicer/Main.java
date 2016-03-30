package dynslicer;

public class Main {

	public static void main(String[] args) {
		final String inFile = "/Users/schaef/git/integration-test/corpus/sorting/00_sort/Sort01/classes/Sort01.class";
		final String outFile = "./Sort01.class";

		InstrumentConditionals icond = new InstrumentConditionals();
		icond.instrumentClass(inFile, outFile);
		
		System.out.println("done");
	}

}