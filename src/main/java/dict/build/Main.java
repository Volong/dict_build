package dict.build;

public class Main {

	public static void main(String[] args) {

	    String rawpath = "C:\\Users\\Volong\\Desktop\\new_word_discover\\tianlongbabu_jinyong.txt";
		
		FastBuilder builder = new FastBuilder();

		String rightNgramFreqSortFilePath = builder.genRightNgramFreqSortFile(rawpath, 6);
		String leftNgramFreqSortFilePath = builder.genLeftNgramFreqSortFile(rawpath, 6);
		String entropyFilePath = builder.mergeEntropy(rightNgramFreqSortFilePath, leftNgramFreqSortFilePath);

		builder.extractWords(rightNgramFreqSortFilePath, entropyFilePath);
	}
}
