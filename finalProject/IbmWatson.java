import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.util.StringUtils;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;


public class IbmWatson {
	class MyDocument
	{
		public Document d;
		public float score;
		public MyDocument()
		{

		}
		public MyDocument(Document d,float score)
		{
			this.d = d;
			this.score = score;
		}
	}

	static String _TITLE_ = "TITLE: ";
	static String _CATEGORY_ = "CATEGORY: ";
	static String _CONTENT_ = "CONTENT: ";
	static String inputFile = new String();
	static boolean _printCategory=false,_printContent=false,_WRITE_TO_FILE_=false;
	static StringBuffer title= null;//= new StringBuffer();
	static StringBuffer category = null;//= new StringBuffer();
	static StringBuffer contents = null;//= new StringBuffer();
	public static void setUpFlags()
	{
		_printCategory = true;
		_printContent = true;
	}
	public static void clearAllBuffers()
	{
		if(title != null)
		{
			title.setLength(0);
		}
		if(category != null)
		{
			category.setLength(0);
		}
		if(contents != null)
		{
			contents.setLength(0);
		}
	}
	public static void parseLine(String line,BufferedWriter bw,IndexWriter w,String fname)
	{
		int phase = 0;
		try
		{

			if(line.startsWith("[[") && line.endsWith("]]"))// Means its the Doc Title
			{
				//bw.newLine();
				phase = 1;
				int start = line.indexOf("[[");
				int end = line.indexOf("]]");
				String titleStr = line.substring((start+2),end);
				//System.out.println(title);s
				if(title != null && contents != null)
				{
					if(category == null )// This can Be null
					{
						storeInDoc(w,title.toString(),"",contents.toString());
					}
					else
					{
						storeInDoc(w,title.toString(),category.toString(),contents.toString());
					}
					if(_WRITE_TO_FILE_)
					{
						bw.newLine();
						if(title != null)
						{
							bw.write(_TITLE_+title.toString());
						}
						if(category != null )
						{
							bw.newLine();
							bw.write(_CATEGORY_+category.toString());
						}
						if(contents != null )
						{
							bw.newLine();
							bw.write(_CONTENT_+contents.toString());
						}
					}
					clearAllBuffers();

				}
				// RESET ALL STRING BUFFERS

				title = new StringBuffer(titleStr);
				//bw.write(_TITLE_+title.toString());
				setUpFlags();// Make the FLAG TRUE once we know it is Title
				//bw.newLine();
			}
			else if(line.isEmpty())
			{
				return;
			}
			else if(line.contains("#redirect"))
			{
				phase = 2;
				return;// just skip this line
			}
			else if(line.contains("categories:"))
			{
				int position = line.indexOf(":");
				if(_printCategory)
				{
					//bw.newLine();
					String subStr = line.substring(position+1);
					String categ = subStr.replaceAll("\\p{Punct}+", "");
					category = new StringBuffer(categ);
					//bw.write(_CATEGORY_+category.toString());
					_printCategory = false;
				}
				else
				{
					String categ =line.replaceAll("\\p{Punct}+", "");
					category.append(categ);
					//bw.write(categ);
				}
				//bw.newLine();
			}
			else if(line.matches("^==.*?==$")) // FOR NOW I WILL SKIP THESE LINES WHICH HAVE ==WORD SOME WORD== CAN USE THIS LATER TO IMPROVE SOMETHIG
			{
				phase = 3;
				return;// just skip this line
			}
			else if(line.contains("[tpl]") && line .contains("[/tpl]"))
			{
				phase = 4;
				StringBuffer truncatedString = new StringBuffer(line);
				int startPos,prevStartPos=0,endPos,prevEndPos=0;
				while(truncatedString.toString().contains("[tpl]") 
						&& truncatedString.toString() .contains("[/tpl]"))
				{
					startPos = truncatedString.toString().indexOf("[tpl]");
					endPos  = truncatedString.toString().indexOf("[/tpl]");
					if((endPos+6) <= startPos)
					{
						startPos = prevStartPos;
					}
					prevStartPos = startPos;
					prevEndPos = endPos;
					truncatedString.replace(startPos, (endPos+6), "");
				}
				//System.out.println(truncatedString.toString());
				if(_printContent)
				{
					//bw.newLine();
					String content = truncatedString.toString().replaceAll("\\p{Punct}+", "");
					contents = new StringBuffer(content);
					//bw.write(_CONTENT_+content);
					_printContent = false;
				}
				else
				{
					String content = truncatedString.toString().replaceAll("\\p{Punct}+", "");
					contents.append(content);
					//bw.write(truncatedString.toString().replaceAll("\\p{Punct}+", ""));
				}
				//bw.newLine();
			}
			else// Normal Line no Special Cases
			{
				phase = 5;
				//System.out.println(line);
				//bw.write(line.replaceAll("\\p{Punct}+", " "));
				if(_printContent)
				{
					//bw.newLine();
					//bw.write(_CONTENT_+line.replaceAll("\\p{Punct}+", ""));
					String content = line.replaceAll("\\p{Punct}+", "");
					contents = new StringBuffer(content);
					_printContent = false;
				}
				else
				{
					String content = line.replaceAll("\\p{Punct}+", "");
					contents.append(content);
					//bw.write(line.replaceAll("\\p{Punct}+", ""));
				}
				//bw.newLine();
			}
		}
		catch(Exception e)
		{
			System.out.println(" Exception occured During Parsing in Phase : "+phase);
			System.out.println(" Exception occured For File : "+fname);
			System.out.println("Title : "+title.toString());
			e.printStackTrace();

		}
	}
	private static void storeInDoc(IndexWriter w, String titleValue,String categoryValue,String contents) throws IOException 
	{
		Document doc = new Document();
		//use a string field for isbn because we don't want it tokenized
		doc.add(new StringField("TITLE",titleValue, Field.Store.YES));

		doc.add(new TextField("CATEGORY", categoryValue, Field.Store.YES));
		doc.add(new TextField("CONTENTS", contents, Field.Store.YES));
		w.addDocument(doc);
	}
	public static void parseDocument(List<String> fileNames,IndexWriter w)
	{
		for(String fname: fileNames)
		{
			String line = null;
			try
			{
				FileReader inFile = new FileReader(fname);
				BufferedReader bufffer = new BufferedReader(inFile);
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fname), "UTF-8"));
				File fout = new File("output.txt");
				FileOutputStream fos = new FileOutputStream(fout);
				BufferedWriter bw = null;
				boolean skip = false;

				while((line = br.readLine()) != null) 
				{
					if(!skip && "==References==".equalsIgnoreCase(line) || "==Distinctions==".equalsIgnoreCase(line) || "==Gallery==".equalsIgnoreCase(line) 
							|| "==Images==".equalsIgnoreCase(line) || "==Photo gallery==".equalsIgnoreCase(line) || "==External links==".equalsIgnoreCase(line))
					{
						skip = true; // Skip till end of Reference or srat of next section
						continue;
					}
					if(skip == true && (line.matches("==\\w+\\s*\\w+==") || (line.contains("[[") && line.contains("]]"))))//http://regexr.com/
					{
						skip = false;
					}
					if(skip == true)
					{
						continue;
					}
					parseLine(line.toLowerCase(),bw,w,fname);

				} 
				bw.close();
				System.out.println(" Parsing Completed");
				bufffer.close();
			}
			catch(Exception e)
			{
				System.out.println("Failed in Read File Contents for Fname "+fname);
				System.out.println("Exception :: "+e);
				e.printStackTrace();
			}
		}
	}
	static boolean skipLine(String line,boolean skip) 
	{
		if(line.contains("==references=="))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	static SortedMap<Float,Document> consolidateTheResultsFromCategoryAndContent(LinkedHashMap<String,MyDocument> mapOfCategory,LinkedHashMap<String,MyDocument> mapOfContent)
	{
		//List<Document> consolidatedList = new LinkedList<Document>();
		SortedMap<Float,Document> sortedContent = new TreeMap<Float,Document>();
		Set<String> categorySet = mapOfCategory.keySet();
		for(String cat:categorySet)
		{
			if(mapOfContent.containsKey(cat))
			{
				MyDocument docFromContent = mapOfContent.get(cat);
				MyDocument docFromCategory = mapOfCategory.get(cat);
				sortedContent.put((docFromContent.score+docFromCategory.score),docFromContent.d);
			}
		}

		return sortedContent;
	}
	public static void main(String[] args) throws IOException, ParseException 
	{
		boolean isExit = false;
		boolean indexExists = false;
		Scanner input = new Scanner(System.in);
		boolean usingBM25Similarity =false;
		int choice;
		int queryFormat=0;
		queryFormat = Integer.valueOf(args[0]);
		String typeOfAnalyzer= args[1]; // W- White Space Analyzer , D - Default(Standard Analyzer) , S -SnowBall Analyzer
		
		
		String querystr = new String();
		String categoryStr = new String();
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
		WhitespaceAnalyzer whiteAnalyzer = new WhitespaceAnalyzer(Version.LUCENE_40);
		SnowballAnalyzer snowBall = new SnowballAnalyzer(Version.LUCENE_40, "English");
		if(queryFormat == 1)
		{
			usingBM25Similarity = true;
		}
		File directory = new File("index-directory");
		if(directory.exists())
		{
			indexExists = true;
		}
		System.out.println("System is Using Analyzer Of Type : "+typeOfAnalyzer);
		System.out.println("System is Using BM25Similiraty : "+usingBM25Similarity);
		System.out.println("Index Construction Required : "+!indexExists);
		
		Directory index = FSDirectory.open(new File("index-directory"));
		IndexWriterConfig config = null;
		//https://lucene.apache.org/core/4_0_0/analyzers-common/overview-summary.html
		if("W".equalsIgnoreCase(typeOfAnalyzer))
		{
			config = new IndexWriterConfig(Version.LUCENE_40, whiteAnalyzer);
		}
		else if("S".equalsIgnoreCase(typeOfAnalyzer))
		{
			config = new IndexWriterConfig(Version.LUCENE_40, snowBall);
		}
		else
		{
			config = new IndexWriterConfig(Version.LUCENE_40,analyzer );
		}
		if(queryFormat == 1)
		{
			config.setSimilarity(new BM25Similarity());
		}
		IndexWriter w = new IndexWriter(index, config);
		int _COUNTWITHIN_ ;
		int hitsWithinRange =0,lineNumber = 1,ques=1;
		StringBuffer question= null;
		StringBuffer answer= null;
		StringBuffer categoryToUse= null;
		String fileName = "questions.txt",line=null;
		FileReader inFile = null;
		BufferedReader br = null;
		
		

		try
		{
			if(!indexExists)
			{
				List<String> fileNames = new ArrayList<String>();
				File[] files = new File("C:/Users/Rahul/Desktop/GitHub/csc583-Information-Retrieval/IBMWatson/wiki/").listFiles();
				for (File file : files) 
				{
					if (file.isFile() && file.getName().endsWith(".txt") && !file.getName().equals("output.txt")) 
					{
						fileNames.add(file.getAbsolutePath());
					}
				}
				parseDocument(fileNames,w);
				System.out.println("ALL DOCUMENTS WERE SUCCESSFULLY PARSED");
				w.close();
			}
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			System.out.println("EXCEPTION WHILE Opening Index Directory");
		}
		// PARSING THE DOCUMENT SHOULD BE DONE BY NOW !!!
		// 3. search
		int hitsPerPage = 10;
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		if(queryFormat == 1)
        {
			searcher.setSimilarity(new BM25Similarity());
        }
		TopScoreDocCollector collector ;//= TopScoreDocCollector.create(hitsPerPage, true);
		// All This is Independant Of the Query Do it Once
		Query q;
		while(!isExit)
		{
			System.out.println();
			System.out.println("1: Enter the Query and Search on Imprefect System");
			System.out.println("2: Evaluate Questions on My Imperfect System (This does not Use Category)");
			System.out.println("3: Enter the Query and Category to Search On Improved System");
			System.out.println("4: Evaluate Questions on My Improvised System (This Uses Category)");
			System.out.println("5: Evaluation based on consolidation of results based on Category and Query [DID NOT IMPROVE RESULTS]");
			System.out.println("6: Exit");
			System.out.println("Enter Your Choice :");
			choice = input.nextInt();
			input.nextLine();
			System.out.println();
			switch(choice)
			{
			case 1:
				System.out.println("Enter your Query");
				querystr = input.nextLine();
				querystr = querystr.toLowerCase().replaceAll("\\p{Punct}+", "");
				collector = TopScoreDocCollector.create(hitsPerPage, true);
				if("W".equalsIgnoreCase(typeOfAnalyzer))
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", whiteAnalyzer).parse(querystr);
				}
				else if("S".equalsIgnoreCase(typeOfAnalyzer))
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", snowBall).parse(querystr);
				}
				else
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", analyzer).parse(querystr);
				}
							
				searcher.search(q, collector);
				ScoreDoc[] hits = collector.topDocs().scoreDocs;
				for(int i=0;i<hits.length;++i) 
				{
					int docId = hits[i].doc;
					float score = hits[i].score;
					Document d = searcher.doc(docId);
					String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
					System.out.println(result);
				}
				break;
			case 2:
				// Question Evaluation
				File foutNormal = new File("ResultsAtPos1NormalSystem.txt");
				FileOutputStream fosNormal = new FileOutputStream(foutNormal);
				BufferedWriter bwNormal = new BufferedWriter(new OutputStreamWriter(fosNormal));
				hitsWithinRange = 0;
				_COUNTWITHIN_ = 0;
				lineNumber = 1;
				System.out.println("Enter the Position at which the result should be found");
				_COUNTWITHIN_ = input.nextInt();input.nextLine();
				System.out.println("Enter the Name of the Questions File");
				fileName = input.nextLine();
				inFile = new FileReader(fileName);
				br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
				HashMap<String,String> questionAnswers = new LinkedHashMap<String,String>();

				line = null;
				while((line = br.readLine()) != null) 
				{
					if(line.isEmpty())
					{
						if(question != null && answer != null && categoryToUse != null)
						{
							List<String> catageoryAndAnswer = new LinkedList<String>();
							catageoryAndAnswer.add(categoryToUse.toString());catageoryAndAnswer.add(answer.toString());
							questionAnswers.put(question.toString(), answer.toString());
						}
						question = null;answer=null;categoryToUse=null;
						lineNumber = 1;
						continue;
					}
					else if(lineNumber == 1) // Category 
					{
						categoryToUse = new StringBuffer(line);
						lineNumber++;
						continue;
					}
					else if(lineNumber == 2)// Question
					{
						question = new StringBuffer(line);
						lineNumber++;
					}
					else if(lineNumber == 3)
					{
						answer = new StringBuffer(line);
					}
				}
				br.close();
				Set<String> keys = questionAnswers.keySet();
				System.out.println("Total Questions : "+keys.size());
				ques = 1;
				for(String q1: keys)
				{
					querystr = q1;
					querystr = querystr.toLowerCase().replaceAll("\\p{Punct}+", "");
					collector = TopScoreDocCollector.create(hitsPerPage, true);
					if("W".equalsIgnoreCase(typeOfAnalyzer))
					{
						q = new QueryParser(Version.LUCENE_40, "CONTENTS", whiteAnalyzer).parse(querystr);
					}
					else if("S".equalsIgnoreCase(typeOfAnalyzer))
					{
						q = new QueryParser(Version.LUCENE_40, "CONTENTS", snowBall).parse(querystr);
					}
					else
					{
						q = new QueryParser(Version.LUCENE_40, "CONTENTS", analyzer).parse(querystr);
					}
					searcher.search(q, collector);
					ScoreDoc[] hitsTest = collector.topDocs().scoreDocs;
					// 4. display results

					boolean found = false;
					for(int i=0;i<hitsTest.length;++i) 
					{
						int docId = hitsTest[i].doc;
						float score = hitsTest[i].score;
						Document d = searcher.doc(docId);
						String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
						if(d.get("TITLE").equalsIgnoreCase(questionAnswers.get(q1)))
						{
							if((i+1) <=_COUNTWITHIN_)
							{
								bwNormal.write("Question: "+q1);
								bwNormal.newLine();
								bwNormal.write(result);
								bwNormal.newLine();
								hitsWithinRange++;
							}
							found = true;
							break;
						}
					}
					if(!found)
					{
						
					}
				}
				bwNormal.close();
				System.out.println("Number of Hits in Top "+_COUNTWITHIN_+" :"+hitsWithinRange);
				System.out.println("Done");
				//END
				break;
			case 3:
				System.out.println("Enter your Query");
				querystr = input.nextLine();
				querystr = querystr.toLowerCase().replaceAll("\\p{Punct}+", "");
				System.out.println("Enter Category");
				categoryStr = input.nextLine();
				categoryStr = categoryStr.toLowerCase().replaceAll("\\p{Punct}+", "");
				querystr = querystr+" "+categoryStr;
				Query improvedQ = null;
				if("W".equalsIgnoreCase(typeOfAnalyzer))
				{
					improvedQ = new QueryParser(Version.LUCENE_40, "CONTENTS", whiteAnalyzer).parse(querystr);
				}
				else if("S".equalsIgnoreCase(typeOfAnalyzer))
				{
					improvedQ = new QueryParser(Version.LUCENE_40, "CONTENTS", snowBall).parse(querystr);
				}
				else
				{
					improvedQ = new QueryParser(Version.LUCENE_40, "CONTENTS", analyzer).parse(querystr);
				}
				collector = TopScoreDocCollector.create(hitsPerPage, true);
				searcher.search(improvedQ, collector);
				ScoreDoc[] improvedHits = collector.topDocs().scoreDocs;
				// 4. display results
				for(int i=0;i<improvedHits.length;++i) 
				{
					int docId = improvedHits[i].doc;
					float score = improvedHits[i].score;
					Document d = searcher.doc(docId);
					String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
					System.out.println(result);
				}
				break;
			case 4: 
				// Question Evaluation
				File foutImproved = new File("ResultsAtPos1ImprovedSystem.txt");
				FileOutputStream fosImproved = new FileOutputStream(foutImproved);
				BufferedWriter bwImproved = new BufferedWriter(new OutputStreamWriter(fosImproved));
				
				hitsWithinRange = 0;
				_COUNTWITHIN_ = 0;
				lineNumber = 1;
				System.out.println("Enter the Position at which the result should be found");
				_COUNTWITHIN_ = input.nextInt();input.nextLine();
				System.out.println("Enter the Name of the Questions File");
				fileName = input.nextLine();
				inFile = new FileReader(fileName);
				br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
				HashMap<String,List<String>> questionAnswersWithCategory = new LinkedHashMap<String,List<String>>(); // Question, < Category,Answer>
				lineNumber = 1;
				line = null;
				while((line = br.readLine()) != null) 
				{
					if(line.isEmpty())
					{
						if(question != null && answer != null && categoryToUse != null)
						{
							List<String> catageoryAndAnswer = new LinkedList<String>();
							catageoryAndAnswer.add(categoryToUse.toString());catageoryAndAnswer.add(answer.toString());
							questionAnswersWithCategory.put(question.toString(), catageoryAndAnswer);
						}
						question = null;answer=null;categoryToUse=null;
						lineNumber = 1;
						continue;
					}
					else if(lineNumber == 1) // Category 
					{
						categoryToUse = new StringBuffer(line);
						lineNumber++;
						continue;
					}
					else if(lineNumber == 2)// Question
					{
						question = new StringBuffer(line);
						lineNumber++;
					}
					else if(lineNumber == 3)
					{
						answer = new StringBuffer(line);
					}
				}
				br.close();
				Set<String> keys1 = questionAnswersWithCategory.keySet();
				System.out.println("Total Questions : "+keys1.size());
				ques = 1;
				for(String q1: keys1)
				{
					querystr = q1;
					querystr = questionAnswersWithCategory.get(q1).get(0)+ " " +querystr ; // THis is basically Category + Question and search it on CONTENTS
					querystr = querystr.toLowerCase().replaceAll("\\p{Punct}+", "");
					collector = TopScoreDocCollector.create(hitsPerPage, true);
					String [] fieldsMq = {"CONTENTS"};
					Query mq = null;
					if("W".equalsIgnoreCase(typeOfAnalyzer))
					{
						mq = new MultiFieldQueryParser(Version.LUCENE_40, fieldsMq, whiteAnalyzer).parse(querystr);
					}
					else if("S".equalsIgnoreCase(typeOfAnalyzer))
					{
						mq = new MultiFieldQueryParser(Version.LUCENE_40, fieldsMq, snowBall).parse(querystr);
					}
					else
					{
						mq = new MultiFieldQueryParser(Version.LUCENE_40, fieldsMq, analyzer).parse(querystr);
					}
					searcher.search(mq, collector);
					ScoreDoc[] hitsTest = collector.topDocs().scoreDocs;
					// 4. display results
					boolean found = false;
					for(int i=0;i<hitsTest.length;++i) 
					{
						int docId = hitsTest[i].doc;
						float score = hitsTest[i].score;
						Document d = searcher.doc(docId);
						String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
						if(d.get("TITLE").equalsIgnoreCase(questionAnswersWithCategory.get(q1).get(1)))
						{
							if((i+1) <=_COUNTWITHIN_)
							{

								bwImproved.write("Question: "+q1);
								bwImproved.newLine();
								bwImproved.write(result);
								bwImproved.newLine();
								hitsWithinRange++;
							}
							found = true;
							break;
						}
					}
					if(!found)
					{
					}
					
				}
				bwImproved.close();
				System.out.println("Number of Hits in Top "+_COUNTWITHIN_+" :"+hitsWithinRange);
				System.out.println("Done");
				break;
			case 5:
				System.out.println("Enter your Query");
				querystr = input.nextLine();
				querystr = querystr.toLowerCase().replaceAll("\\p{Punct}+", "");
				System.out.println("Enter Category");
				categoryStr = input.nextLine();
				categoryStr = categoryStr.toLowerCase().replaceAll("\\p{Punct}+", "");
				collector = TopScoreDocCollector.create(50, true);

				if("W".equalsIgnoreCase(typeOfAnalyzer))
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", whiteAnalyzer).parse(querystr);
				}
				else if("S".equalsIgnoreCase(typeOfAnalyzer))
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", snowBall).parse(querystr);
				}
				else
				{
					q = new QueryParser(Version.LUCENE_40, "CONTENTS", analyzer).parse(querystr);
				}
				Query categoryQuery = null;
				if("W".equalsIgnoreCase(typeOfAnalyzer))
				{
					categoryQuery = new QueryParser(Version.LUCENE_40, "CONTENTS", whiteAnalyzer).parse(querystr);
				}
				else if("S".equalsIgnoreCase(typeOfAnalyzer))
				{
					categoryQuery = new QueryParser(Version.LUCENE_40, "CONTENTS", snowBall).parse(querystr);
				}
				else
				{
					categoryQuery = new QueryParser(Version.LUCENE_40, "CONTENTS", analyzer).parse(querystr);
				}
				searcher.search(categoryQuery, collector);
				ScoreDoc[] hitsCategory = collector.topDocs().scoreDocs;
				LinkedHashMap<String,MyDocument> mapOfCategoryDocs = new LinkedHashMap<String,MyDocument>();
				IbmWatson watson = new IbmWatson();
				for(int i=0;i<hitsCategory.length;++i) 
				{
					int docId = hitsCategory[i].doc;
					float score = hitsCategory[i].score;
					Document d = searcher.doc(docId);

					MyDocument myDoc = watson.new MyDocument(d,score);
					String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
					mapOfCategoryDocs.put(d.get("TITLE"), myDoc);
					System.out.println(result);
				}
				System.out.println("----");
				collector = TopScoreDocCollector.create(50, true);
				searcher.search(q, collector);
				ScoreDoc[] hitsQuery = collector.topDocs().scoreDocs;
				LinkedHashMap<String,MyDocument> mapOfContentDocs = new LinkedHashMap<String,MyDocument>();
				for(int i=0;i<hitsQuery.length;++i) 
				{
					int docId = hitsQuery[i].doc;
					float score = hitsQuery[i].score;
					Document d = searcher.doc(docId);
					MyDocument myDoc = watson.new MyDocument(d,score);
					String result = (i + 1) + ". " + d.get("TITLE") + "\t" + score;
					mapOfContentDocs.put(d.get("TITLE"), myDoc);
					System.out.println(result);
				}
				SortedMap<Float,Document> sortedContent = consolidateTheResultsFromCategoryAndContent(mapOfCategoryDocs,mapOfContentDocs);
				System.out.println("----------------");
				Set<Float> key = sortedContent.keySet();
				int k=0;
				for(Float scor:key)
				{
					Document d = sortedContent.get(scor);
					String result = (k + 1) + ". " + d.get("TITLE") + "\t" + scor;
					System.out.println(result);
				}
				break;
			case 6:
				isExit = true;
				// reader can only be closed when there
				// is no need to access the documents any more.
				reader.close();
				break;

			}
		}
	}

}
