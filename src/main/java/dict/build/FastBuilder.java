package dict.build;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.sort.SortConfig;
import com.fasterxml.sort.std.TextFileSorter;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;

import dict.build.dictionary.DictionaryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Jennifer
 * 
 */
public class FastBuilder {

	private static final Logger LOG = LoggerFactory.getLogger(FastBuilder.class);

	public static final String stopwords = "的很了么呢是嘛个都也比还这于不与才上用就好在和对挺去后没说";
	
	
	/**
	 * 输入的字符是否是汉字
	 * @param a char
	 * @return boolean
	 */
	public static boolean isChinese(char a) { 
	     int v = (int)a; 
	     return (v >=19968 && v <= 171941); 	
	}
	
	public static boolean allChs(String s){
		if (null == s || "".equals(s.trim())) {
		    return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (!isChinese(s.charAt(i))) {
			    return false;
			}
		}
		return true;
	}
	
	public TreeMap<String, double[]> loadPosprop() {
		
		TreeMap<String, double[]> prop = Maps.newTreeMap();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/pos_prop.txt")))){
            String l = null;
            while (null != (l = br.readLine())) {
                // 覃   0.9047619047619048  0.028985507246376812    0.06625258799171843
                // 字  这个字出现在词首的几率       这个字出现在词中的几率                这个字出现在词尾的几率
				String[] seg = l.split("\t");
				prop.put(seg[0], new double[]{Double.parseDouble(seg[1]), Double.parseDouble(seg[2]), Double.parseDouble(seg[3])});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return prop;
	}

	public String parse(String filepath) {

		File in = new File(filepath);
		File out = new File(in.getParentFile(), "out.data");

		try (BufferedReader ir = Files.newReader(in, Charsets.UTF_8);
				BufferedWriter ow = Files.newWriter(out, Charsets.UTF_8);) {
			String line = null;
			while (null != (line = ir.readLine())) {
				String[] seg = line.split(",");
				StringBuilder bui = new StringBuilder();
				for (int i = 6; i < seg.length; ++i) {
					bui.append(seg[i]);
				}
				bui.append("\n");
				ow.write(bui.toString());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out.getAbsolutePath();
	}

	private String reverse(String raw) {
		StringBuilder bui = new StringBuilder();
		for (int i = raw.length() - 1; i >= 0; --i)
			bui.append(raw.charAt(i));
		return bui.toString();
	}

	public void sortFile(File in, File out) {
		try (final TextFileSorter sorter = new TextFileSorter(new SortConfig())) {
		    sorter.sort(new FileInputStream(in), new PrintStream(out));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String genLeftNgramFreqSortFile(String rawTextFile, int maxLen, int memSize) {

		File rawFile = new File(rawTextFile);

		File dir = rawFile.getParentFile();

		File ngramFile = new File(dir, "left_ngram.data");
		File ngramSortFile = new File(dir, "left_ngram_sort.data");
		File ngramFreqFile = new File(dir, "left_ngram_freq.data");
		File ngramFreqSortFile = new File(dir, "left_ngram_freq_sort.data");

		try (BufferedReader rawFileReader = Files.newReader(rawFile, Charsets.UTF_8);
				BufferedWriter ngramFileWriter = Files.newWriter(ngramFile, Charsets.UTF_8);
				BufferedWriter ngramFreqFileWriter = Files.newWriter(ngramFreqFile, Charsets.UTF_8)) {
		    
			String line = null;
			while (null != (line = rawFileReader.readLine())) {
			    
			    // 去除停顿词
				line = repaceStopWords(line);
				
				for (String sen : Splitter.on(" ").omitEmptyStrings().splitToList(line)) {
				    
				    // 反转  123 -> 321
					sen = reverse(sen.trim());
					
					if (!allChs(sen)) {
					    continue;
					}
					
					sen = "$" + sen + "$";
					for (int i = 1; i < sen.length() - 1; ++i) {
						ngramFileWriter.write(sen.substring(i, Math.min(maxLen + i,  sen.length())) + "\n");
					}
				}
			}
			
//			ngramFileWriter.close();
			ngramFileWriter.flush();
			
			sortFile(ngramFile, ngramSortFile);

			try(BufferedReader ngramSortFileReader = Files.newReader(ngramSortFile, Charsets.UTF_8)) {
				String first = null;
				String ngramSortFileLine = null;
				Map<String, CounterMap> stats = Maps.newHashMap();
				while (null != (ngramSortFileLine = ngramSortFileReader.readLine())) {
					if (null == first) {
						for (int i = 1; i < ngramSortFileLine.length(); ++i) {
							String word = ngramSortFileLine.substring(0, i);
							String suffix = ngramSortFileLine.substring(i).substring(0, 1);
							if (stats.containsKey(word)) {
								stats.get(word).incr(suffix);
							} else {
								CounterMap cm = new CounterMap();
								cm.incr(suffix);
								stats.put(word, cm);
							}
						}
						first = ngramSortFileLine.substring(0, 1);
					} else {
						if (!ngramSortFileLine.startsWith(first)) {

							StringBuilder builder = new StringBuilder();
							for (String word : stats.keySet()) {
								CounterMap cm = stats.get(word);
								
								int freq = 0;
								for (String k : cm.countAll().keySet()) {
									freq += cm.get(k);
								}
								
								double re = 0;
								for (String k : cm.countAll().keySet()) {
									double p = cm.get(k) * 1.0 / freq;
									re += -1 * Math.log(p) / Math.log(2) * p;
								}
								// 为什么少了  append("\t").append(freq)
								builder.append(reverse(word)).append("\t").append(re).append("\n");
							}
							ngramFreqFileWriter.write(builder.toString());
							stats.clear();
							first = ngramSortFileLine.substring(0, 1);
						}
						for (int i = 1; i < ngramSortFileLine.length(); ++i) {
							String w = ngramSortFileLine.substring(0, i);
							String suffix = ngramSortFileLine.substring(i).substring(0, 1);
							if (stats.containsKey(w)) {
								stats.get(w).incr(suffix);
							} else {
								CounterMap cm = new CounterMap();
								cm.incr(suffix);
								stats.put(w, cm);
							}
						}
					}
				}
//				ngramFreqFileWriter.close();
				ngramFreqFileWriter.flush();
			}
			
			sortFile(ngramFreqFile, ngramFreqSortFile);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return ngramFreqSortFile.getAbsolutePath();
	}

    private static final String repaceStopWords(String line) {
        return line.replaceAll("[" + stopwords + "]", " ")
        		.replaceAll("\\p{Punct}", " ")
        		.replaceAll("\\pP", " ")
        		.replaceAll("　", " ")
        		.replaceAll("\\p{Blank}", " ")
        		.replaceAll("\\p{Space}", " ")
        		.replaceAll("\\p{Cntrl}", " ");
    }

	public String genRightNgramFreqSortFile(String rawTextFile, int maxLen, int memSize) {

		File rawFile = new File(rawTextFile);

		File dir = rawFile.getParentFile();

		File ngramFile = new File(dir, "right_ngram.data");
		File ngramSortFile = new File(dir, "right_ngram_sort.data");
		File ngramFreqFile = new File(dir, "right_ngram_freq.data");
		File ngramFreqSortFile = new File(dir, "right_ngram_freq_sort.data");

		try (BufferedReader rawFileReader = Files.newReader(rawFile, Charsets.UTF_8);
				BufferedWriter ngramFileWriter = Files.newWriter(ngramFile, Charsets.UTF_8);
				BufferedWriter ngramFreqFileWriter = Files.newWriter(ngramFreqFile, Charsets.UTF_8)) {
		    
			String line = null;
			while (null != (line = rawFileReader.readLine())) {
				line = repaceStopWords(line);
				for (String sen : Splitter.on(" ").omitEmptyStrings().splitToList(line)) {
					sen = sen.trim();
					// 判断是不是中文
					// 为什么不在替换的时候将不是中文的字符使用正则替换？
					if (!allChs(sen)) {
					    continue;
					}
					// 不明白为什么要加 $
					sen = "$" + sen + "$";
					for (int i = 1; i < sen.length() - 1; ++i) {
						ngramFileWriter.write(sen.substring(i, Math.min(maxLen + i,  sen.length())) + "\n");
					}
				}
			}
			
			ngramFileWriter.flush();
			
			System.out.println("right is over, gen sorting...");
			
			sortFile(ngramFile, ngramSortFile);
			
			try(BufferedReader ngramSortFileReader = Files.newReader(ngramSortFile, Charsets.UTF_8)) {
				// 第一个字
			    String first = null;
				String ngramSortFileLine = null;
				Map<String, CounterMap> stats = Maps.newHashMap();
				while (null != (ngramSortFileLine = ngramSortFileReader.readLine())) {
					if (null == first) {
						for (int i = 1; i < ngramSortFileLine.length(); ++i) {
							String word = ngramSortFileLine.substring(0, i);
							// 相邻的后缀字
							// TODO ngramSortFileLine.substring(i, i + 1)
							String suffix = ngramSortFileLine.substring(i).substring(0, 1);
							// 统计某个词所有后缀字出现的次数
							if (stats.containsKey(word)) {
								stats.get(word).incr(suffix);
							} else {
								CounterMap cm = new CounterMap();
								cm.incr(suffix);
								stats.put(word, cm);
							}
						}
						first = ngramSortFileLine.substring(0, 1);
					} else {
					    
					    // 将所有以 first 开头的词写入到 ngramFreqFile 文件中
						if (!ngramSortFileLine.startsWith(first)) {

							StringBuilder builder = new StringBuilder();
							for (String word : stats.keySet()) {
								CounterMap cm = stats.get(word);
								
								// 某个词所有相邻后缀字出现的次数
								int freq = 0;
								for (String k : cm.countAll().keySet()) {
									freq += cm.get(k);
								}
								// 熵
								double re = 0;
								for (String k : cm.countAll().keySet()) {
									double p = cm.get(k) * 1.0 / freq;
									re += -1 * Math.log(p) / Math.log(2) * p;
								}
								builder.append(word).append("\t").append(freq).append("\t").append(re).append("\n");
							}
							ngramFreqFileWriter.write(builder.toString());
							stats.clear();
							// 将当前行的第一个字赋给 first，准备统计以该 first 开头的相邻后缀字
							first = ngramSortFileLine.substring(0, 1);
						}
						for (int i = 1; i < ngramSortFileLine.length(); ++i) {
							String w = ngramSortFileLine.substring(0, i);
							String suffix = ngramSortFileLine.substring(i).substring(0, 1);
							if (stats.containsKey(w)) {
								stats.get(w).incr(suffix);
							} else {
								CounterMap cm = new CounterMap();
								cm.incr(suffix);
								stats.put(w, cm);
							}
						}
					}
				}
//				freqWriter.close();
				ngramFreqFileWriter.flush();
			}
			
			// 将排好序的数据写入到 ngramFreqSortFile 文件
			sortFile(ngramFreqFile, ngramFreqSortFile);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return ngramFreqSortFile.getAbsolutePath();
	}

	public String mergeEntropy(String freqRight, String left) {


		File rightNgramFreqSortFile = new File(freqRight);
		File leftNgramFreqSortFile = new File(left);
		File mergeTmp = new File(rightNgramFreqSortFile.getParentFile(), "merge.tmp");
		File mergeTmp2 = new File(rightNgramFreqSortFile.getParentFile(), "merge.tmp2");
		File entropyFile = new File(rightNgramFreqSortFile.getParentFile(), "merge_entropy.data");

		try (BufferedReader rightNgramFreqSortFileReader = Files.newReader(rightNgramFreqSortFile, Charsets.UTF_8);
				BufferedReader leftNgramFreqSortFileReader = Files.newReader(leftNgramFreqSortFile, Charsets.UTF_8);
				BufferedWriter mergeTmpWriter = Files.newWriter(mergeTmp, Charsets.UTF_8);
				BufferedWriter entropyFileWriter = Files.newWriter(entropyFile, Charsets.UTF_8)) {
		    
			String line = null;
			while (null != (line = rightNgramFreqSortFileReader.readLine())) {
				mergeTmpWriter.write(line + "\n");
			}
			line = null;
			while (null != (line = leftNgramFreqSortFileReader.readLine())) {
				mergeTmpWriter.write(line + "\n");
			}
			
//			mergeTmpWriter.close();
			mergeTmpWriter.flush();
			
			// 将排序后的文件写到 mergeTmp2 中
			sortFile(mergeTmp, mergeTmp2);
			
            BufferedReader mergeTmp2Reader = Files.newReader(mergeTmp2, Charsets.UTF_8);

			String line1 = null;
			String line2 = null;
			
			/*
			 *  词    熵
			 *  一    1.3709505944546687
			 */
			line1 = mergeTmp2Reader.readLine();
			
			/*
			 *  词     频次     熵
			 *  一     5    2.321928094887362
			 */
			line2 = mergeTmp2Reader.readLine();
			
			while (true) {

				if (null == line1 || null == line2) {
				    break;
				}
				
				String[] seg1 = line1.split("\t");
				String[] seg2 = line2.split("\t");
				
				// 如果两个关键词相等
				if (!seg1[0].equals(seg2[0])) {
//					line1 = new String(line2.getBytes());
					line1 = line2;
					line2 = mergeTmp2Reader.readLine();
					continue;
				}
				
				// 这个判断好像是多余的
				if (seg1.length < 2) {
				    System.out.println("长度小于 2 的行:" + line1);
//					line1 = new String(line2.getBytes());
					line1 = line2;
					line2 = mergeTmp2Reader.readLine();
					continue;
				}
				
				line1 = mergeTmp2Reader.readLine();
				line2 = mergeTmp2Reader.readLine();
				
				// 这个判断好像是多余的
				if (seg1.length < 3 && seg2.length < 3) {
				    System.out.println("第一行小于 3:" + line1);
				    System.out.println("第二行小于 3:" + line2);
				    continue;
				}
				
				
				// 左熵 TODO double leftEntropy = Double.parseDouble(seg1[1])
				double leftEntropy = seg1.length == 2 ? Double.parseDouble(seg1[1]) : Double.parseDouble(seg2[1]);
				
				// 右熵 TODO double rightEntropy = Double.parseDouble(seg2[2])
				double rightEntropy = seg1.length == 3 ? Double.parseDouble(seg1[2]) : Double.parseDouble(seg2[2]);
				
				// 频次 TODO int freq = Integer.parseInt(seg2[1])
				int freq = seg1.length == 3 ? Integer.parseInt(seg1[1]) : Integer.parseInt(seg2[1]);
				
				double e = Math.min(leftEntropy, rightEntropy);
				
				// 词    词频    熵
				entropyFileWriter.write(seg1[0] + "\t" + freq + "\t" + e + "\n");

			}
//			entropyFileWriter.close();
			entropyFileWriter.flush();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return entropyFile.toString();
	}
	
	public static boolean allLetterOrNumber(String w) {

		for (char c : w.toLowerCase().toCharArray()) {
			boolean letter = c >= 'a' && c <= 'z';
			boolean digit = c >= '0' && c <= '9';
			if (!letter && !digit) {
			    return false;
			}
		}
		return true;
	}

	public void extractWords(String rightNgramFreqSortFilePath, String entropyFilePath) {

		LOG.info("start to extract words");
		
		TreeMap<String, double[]> posProp = this.loadPosprop();

		// 词 - 词频
		RadixTree<Integer> tree = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());

		File rightNgramFreqSortFile = new File(rightNgramFreqSortFilePath);
		File entropyFile = new File(entropyFilePath);
		File wordsFile = new File(entropyFile.getParentFile(), "words.data");
		File wordsSortFile = new File(entropyFile.getParentFile(), "words_sort.data");

		try (BufferedReader rightNgramFreqSortFileReader = Files.newReader(rightNgramFreqSortFile, Charsets.UTF_8);
				BufferedReader entropyFileReader = Files.newReader(entropyFile, Charsets.UTF_8);
				BufferedWriter wordsFileWriter = Files.newWriter(wordsFile, Charsets.UTF_8)) {

			String line = null;
			// 总词数
			long total = 0;
			while (null != (line = rightNgramFreqSortFileReader.readLine())) {
				String[] seg = line.split("\t");
				
				// 这个条件好像是多余的
				if (seg.length < 3) {
				    continue;
				}
				
				tree.put(seg[0], Integer.parseInt(seg[1]));
				
				total += 1;
/*				if (total % 1000 == 0) {
				}*/
			}
			
			LOG.info("load freq to radix tree done: " + total);
			LOG.info("build freq TST done!");
			
			line = null;
			int cnt = 0;
			while (null != (line = entropyFileReader.readLine())) {
			    
				cnt += 1;
				
				String[] seg = line.split("\t");
				// 这个条件好像是多余的
				if (3 != seg.length) {
				    continue;
				}
				String word = seg[0];
				// 英文单词或者数字
				// 这个条件好像是多余的
				if (allLetterOrNumber(word)) {
				    System.out.println("allLetterOrNumber:" + word);
					continue;
				}
				
				int freq = Integer.parseInt(seg[1]);
				double entropy = Double.parseDouble(seg[2]);
				long max = -1;
				// i = 1 过滤了长度为 1 的词
				for (int i = 1; i < word.length(); ++i) {
					String lw = word.substring(0, i);
					String rw = word.substring(i);
					Integer lfObj = tree.getValueForExactKey(lw);
					Integer rfObj = tree.getValueForExactKey(rw);
					
					long lf = -1;
					long rf = -1;
					if (null != lfObj) {
						lf = lfObj.intValue();
					}
					if (null != rfObj) {
						rf = rfObj.intValue();
					}
					if (-1 == lf || -1 == rf) {
					    continue;
					}
					
					long ff = lf * rf;
					if (ff > max) {
					    max = ff;
					}
				}
				double pf = freq * total / max;
				double pmi = Math.log(pf) / Math.log(2);
				if (Double.isNaN(pmi)) {
				    continue;
				}
				double pp = -1;
				if (null != posProp.get(word.subSequence(0, 1)) 
				        && null != posProp.get(word.subSequence(word.length() - 1, word.length()))) {
				    
				    pp = Math.min(posProp.get(word.subSequence(0, 1))[0], 
				            posProp.get(word.subSequence(word.length() - 1, word.length()))[2]);
				}
				
				if (pmi < 1 || entropy < 3 || pp < 0.1 || DictionaryFactory.getDictionary().contains(word)) {
				    continue;
				}
				// 词    频次    点间互信息    熵    ngram
				wordsFileWriter.write(word + "\t" + freq + "\t" + pmi + "\t" + entropy + "\t"  + pp + "\n");
			}
			
			LOG.info("extract words done: " + cnt);
			
//			wordsFileWriter.close();
			wordsFileWriter.flush();
			
			LOG.info("start to sort extracted words");
			
			try (final SplitFileSorter sorter = new SplitFileSorter(new SortConfig())) {
			    
				sorter.sort(new FileInputStream(wordsFile), new PrintStream(wordsSortFile));
				
			} catch (IOException e) {
				e.printStackTrace();
			}

			LOG.info("all done");
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
