/**
 * 
 */
package dict.build;

/**
 * @author zhangcheng
 *
 */
public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

/*		if (args.length == 0) {
			System.out.println("rawpath");
			return;
		}
		
		String rawpath = null;
		if (args.length > 0) {
			rawpath = args[0];
		}*/
	    
	    
	    String rawpath = "C:\\Users\\Volong\\Desktop\\new_word_discover\\test.txt";
		
		String leftNgramFreqSortFilePath = null;
		String rightNgramFreqSortFilePath = null;
		String entropyFilePath = null;

		FastBuilder builder = new FastBuilder();

		if (null == rightNgramFreqSortFilePath)
			rightNgramFreqSortFilePath = builder.genRightNgramFreqSortFile(rawpath, 6, 10 * 1024);
		if (null == leftNgramFreqSortFilePath)
			leftNgramFreqSortFilePath = builder.genLeftNgramFreqSortFile(rawpath, 6, 10 * 1024);
		if (null == entropyFilePath)
			entropyFilePath = builder.mergeEntropy(rightNgramFreqSortFilePath, leftNgramFreqSortFilePath);

		builder.extractWords(rightNgramFreqSortFilePath, entropyFilePath);
	}
}
