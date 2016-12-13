/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cowo;

import LanguageDetection.Cyzoku.LangDetectException;
import LanguageDetection.LanguageDetector;
import LanguageDetection.LanguageResourcesInitializer;
import Utils.Clock;
import Utils.Lemmatizer;
import Utils.MultisetMostFrequentFiltering;
import Utils.NGramDuplicatesCleaner;
import Utils.NGramFinder;
import Utils.StatusCleaner;
import Utils.StopWordsRemover;
import com.google.common.collect.*;
import com.google.common.collect.Multiset.Entry;
import java.io.*;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author C. Levallois
 *
 * FUNCTION OF THIS PROGRAM: Take a text as input, returns semantic networks as
 * an output.
 *
 * DETAIL OF OPERATIONS: 1. loads several stopwords files in memory 2. reads the
 * text file and does some housekeeping on it 3. lemmatization of the text 4.
 * extracts n-grams 5. housekeeping on n-grams, removal of least frequent terms
 * 6. removal of stopwords, removal of least frequent terms 7. removal of
 * redudant n-grams (as in: removal of "united states of" if "united stated of
 * America" exists frequently enough 8. determines all word co-occurrences for
 * each line of the text 9. prints vosViewer output 10. prints GML file 11.print
 * a short report of all these operations
 */
public class Controller {

    private Multiset<String> freqNGramsGlobal = HashMultiset.create();

    private LinkedHashMultimap<Integer, String> wordsPerLine = LinkedHashMultimap.create();
    private LinkedHashMultimap<Integer, String> wordsPerLineFiltered = LinkedHashMultimap.create();
    private HashSet<String> setOfWords = new HashSet();
    private HashMultiset<String> setNGrams = HashMultiset.create();
    private Multiset<String> multisetOfWords = ConcurrentHashMultiset.create();
    private Multiset<String> multisetOcc = ConcurrentHashMultiset.create();
    private Multiset<String> ngramsInLine = ConcurrentHashMultiset.create();
    private HashMap<String, Integer> ngramsCountinCorpus = new HashMap();
    private Multiset<String> setCombinations = ConcurrentHashMultiset.create();

    private int counterLinesLoaded;

    // logic of freqThreshold: the higher the number of stopwords filtered out, the lower the number of significant words which should be expected
    private int freqThreshold;
    private String wordSeparator;
    private String wk;
    private String wkOutput;
    private String textFile;
    private int counter = 0;
    private int numberOfDocs;
    private int occurrenceThreshold;
    private int maxgram;
    private int nbStopWords;
    private int minWordLength;
    private boolean binary = false;
    private boolean useScientificStopWords = true;
    private boolean useTDIDF = false;
    private boolean local = true;
    private String langProcessed = "en";

    private HashMap<Integer, String> mapOfLines = new HashMap();
    private HashMultiset<String> setFreqWords = HashMultiset.create();

    //OUTPUT FILES NAMES
    private String fileMapName;
    private String fileNetworkName;
    private String fileParametersName;
    private String fileGMLName;

    public static void main(String[] args) throws LangDetectException, IOException, InterruptedException {

        new Controller().run();

    }

    private void run() throws LangDetectException, IOException, InterruptedException {

        textFile = "abstracts.csv";
        wk = "H:\\files\\NetBeansProjects\\CowoMaven\\";

        binary = true;
        freqThreshold = 300;
        minWordLength = 3;
        maxgram = 4;
        occurrenceThreshold = 4;
        wordSeparator = " ";

        //PARAMETERS FOR STOPWORDS
        nbStopWords = 5000;

        System.out.println("use scientific stopwords? " + useScientificStopWords);
        System.out.println("word separator is: \"" + wordSeparator + "\"");
        useTDIDF = false;
        wkOutput = wk.concat("\\");

        System.out.println("---------------------------------");
        System.out.println();

        // #### 1. Loading languages detection
        LanguageResourcesInitializer.load();

        //-------------------------------------------------------------------------------------------------------------
        // ### 2. LOADING FILE IN MEMORY AND CLEANING  ...
        Clock loadingTime = new Clock("Loading text file: " + textFile);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wk + textFile), "UTF-8"))) {
            String currLine;
            while ((currLine = br.readLine()) != null) {
                currLine = TextCleaner.doBasicCleaning(currLine);
                String lang = new LanguageDetector().detect(currLine);
                if (lang != null && lang.equals(langProcessed)) {
                    counterLinesLoaded++;
                    mapOfLines.put(counterLinesLoaded, currLine);
                }

            } // end looping through all lines of the original text file
            System.out.println("nb of docs treated: " + counterLinesLoaded);

            counterLinesLoaded = 0;
        }
        loadingTime.closeAndPrintClock();
        //### END of file reading---------------------------------------------------------

        // ### LEMMATIZING
        Clock LemmatizerClock = new Clock("Lemmatizing");
        Lemmatizer lemmatizer = new Lemmatizer(langProcessed, local);

        mapOfLines.keySet().stream().forEach((i) -> {
            String line = lemmatizer.sentenceLemmatizer(mapOfLines.get(i));
            mapOfLines.put(i, line);
        });
        LemmatizerClock.closeAndPrintClock();

        // ### EXTRACTION set of NGrams
        NGramFinder ngf;

        for (Integer i : mapOfLines.keySet()) {
            ngf = new NGramFinder(mapOfLines.get(i));
            freqNGramsGlobal.addAll(ngf.runIt(3, binary));
        }

        System.out.println("size of freqSet (unique nGrams):" + freqNGramsGlobal.elementSet().size());

        //### Lemmatization FRENCH
        if (langProcessed.equals("fr")) {
            Clock clockFR = new Clock("Lemmatizing French declinations");
            Multiset<String> toRemove = HashMultiset.create();
            Multiset<String> toAdd = HashMultiset.create();
            Map<String, String> matching = new HashMap();
            for (Multiset.Entry<String> entry : freqNGramsGlobal.entrySet()) {
                String test = null;
                if (entry.getElement().endsWith("elle")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 2);
                } else if (entry.getElement().endsWith("ère")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 3);
                    test = test + "er";
                } else if (entry.getElement().endsWith("ique")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 3);
                    test = test + "e";
                } else if (entry.getElement().endsWith("ale")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 1);
                } else if (entry.getElement().endsWith("al")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 2);
                } else if (entry.getElement().contains("î")) {
                    test = entry.getElement().replace("î", "i");
                } else if (entry.getElement().contains("é")) {
                    test = entry.getElement().replace("é", "e");
                } else if (entry.getElement().contains("ê")) {
                    test = entry.getElement().replace("ê", "e");
                } else if (entry.getElement().contains("è")) {
                    test = entry.getElement().replace("è", "e");
                } else if (entry.getElement().endsWith("e")) {
                    test = entry.getElement().substring(0, entry.getElement().length() - 1);
                }
                if (test != null && freqNGramsGlobal.contains(test)) {
                    toRemove.add(entry.getElement(), entry.getCount());
                    toAdd.add(test, entry.getCount());
                    matching.put(entry.getElement(), test);
                }
            }
            freqNGramsGlobal.removeAll(toRemove);
            freqNGramsGlobal.addAll(toAdd);
            clockFR.printIntermediaryText("finished detecting duplicates, now about to replace them in text");
            clockFR.printElapsedTime();

            for (int i : mapOfLines.keySet()) {
                for (String term : toRemove) {
                    if (mapOfLines.get(i).contains(term)) {
                        String line = mapOfLines.get(i).replaceAll(term, matching.get(term));
                        mapOfLines.put(i, line);
                    }
                }
            }
            clockFR.closeAndPrintClock();
        }
        //removing nGrams contained in nGrams
        NGramDuplicatesCleaner duplicateCleaner = new NGramDuplicatesCleaner(langProcessed, local);
        freqNGramsGlobal = duplicateCleaner.removeDuplicates(freqNGramsGlobal, 5);

        System.out.println(
                "size of freqSet after removal of nGrams included innGrams (unique Words):" + freqNGramsGlobal.elementSet().size());

        //-------------------------------------------------------------------------------------------------------------        
        // #### 6. REMOVING STOPWORDS
        StopWordsRemover stopWordsRemover = new StopWordsRemover(useScientificStopWords, 3, langProcessed, local);
        String string;
        Iterator<String> it = freqNGramsGlobal.iterator();

        while (it.hasNext()) {
            string = it.next();
            if (stopWordsRemover.shouldItBeRemoved(string)) {
                it.remove();
            }
        }

        // ### REMOVING SMALL WORDS
        it = freqNGramsGlobal.iterator();

        while (it.hasNext()) {
            string = it.next();
            if (StatusCleaner.shouldItBeRemoved(string, 2)) {
                it.remove();
            }
        }

        // #### SORTS TERMS BY FREQUENCY, LEAVING OUT THE LESS FREQUENT ONE
        MultisetMostFrequentFiltering m = new MultisetMostFrequentFiltering();
        List<Multiset.Entry> entriesSortedByCount = m.keepMostfrequent(freqNGramsGlobal, freqThreshold);

        System.out.println(
                "entriesSortedByCount size:" + entriesSortedByCount.size());
        freqNGramsGlobal.clear();
        //removing the less frequent terms from freqNGramsGlobal:

        entriesSortedByCount.stream()
                .forEach((entry) -> {
                    freqNGramsGlobal.add((String) entry.getElement(), entry.getCount());
                }
                );

        System.out.println(
                "size of freqNGramsGlobal after only " + freqThreshold + " most frequent terms are kept: " + freqNGramsGlobal.elementSet().size());

        StringBuilder mostFrequentTerms;
        try ( //-------------------------------------------------------------------------------------------------------------
                // #### PRINTING MOST FREQUENT TERMS
                BufferedWriter brFreqTerms = new BufferedWriter(new FileWriter(wkOutput + "most freq terms.txt"))) {
            mostFrequentTerms = new StringBuilder();
            String currFrequentTerm;
            for (Entry<String> entry : entriesSortedByCount) {
                currFrequentTerm = entry.toString();
                System.out.println("most frequent words: " + currFrequentTerm);
                mostFrequentTerms.append("most frequent words: ").append(currFrequentTerm).append("\n");

            }
            brFreqTerms.write(mostFrequentTerms.toString());
        }

        //COUNTING IN HOW MANY DOCS EACH FREQUENT TERM OCCURS (FOR THE TD IDF MEASURE)
        Iterator<String> itIDF = freqNGramsGlobal.elementSet().iterator();
        HashMultiset countTermsInDocs = HashMultiset.create();

        while (itIDF.hasNext()) {
            String freqWordTerm = itIDF.next();
            Iterator<String> itLines = mapOfLines.values().iterator();
            while (itLines.hasNext()) {
                if (itLines.next().contains(freqWordTerm)) {
                    countTermsInDocs.add(freqWordTerm);
                }
            }
        }

        //    -------------------------------------------------------------------------------------------------------------  
        // #### COUNTING CO-OCCURRENCES PER LINE
        Clock calculatingCooccurrencesTime = new Clock("Determining all word co-occurrences for each line of the text");
        for (Integer lineNumber
                : mapOfLines.keySet()) {

            HashMap<String, Float> tdIDFScores = new HashMap();
            String line = mapOfLines.get(lineNumber);
            int countTermsInThisLine = 0;

            for (String term : freqNGramsGlobal) {
                if (line.contains(term)) {
                    countTermsInThisLine++;
                }
            }

            if (countTermsInThisLine < 2) {
                continue;
            }
//                System.out.println("this is the line \"" + currWords + "\"");

            line = TextCleaner.doBasicCleaning(line);

            Iterator<String> ireratorFreqNGramsGlobal = freqNGramsGlobal.elementSet().iterator();

            while (ireratorFreqNGramsGlobal.hasNext()) {
                String currFreqTerm = ireratorFreqNGramsGlobal.next();

                if (line.contains(currFreqTerm)) {
                    ngramsInLine.add(currFreqTerm);
                    //System.out.println("currFreqTerm matched is:" + currFreqTerm);

                    //snippet to find the count of a word in the current line
                    int lastIndex = 0;
                    int countTermInThisDoc = 0;
                    while (lastIndex != -1) {
                        lastIndex = line.indexOf(currFreqTerm, lastIndex);

                        if (lastIndex != -1) {
                            countTermInThisDoc++;
                            lastIndex++;
                        } else {
                            break;
                        }
                    }
//                        System.out.println("countTermInThisDoc: " + countTermInThisDoc);
                    //end snippet
                    if (useTDIDF) {
//                        System.out.println("countTermsInThisDoc: " + countTermsInThisDoc);
                        int countDocsInCorpus = numberOfDocs;
                        System.out.println("countDocsInCorpus: " + countDocsInCorpus);
                        int countDocsContainingThisTerm = countTermsInDocs.count(currFreqTerm);
//                        System.out.println("countDocsContainingThisTerm: " + countDocsContainingThisTerm);
                        float tdIDFscore = (float) (((float) countTermInThisDoc / (float) countTermsInThisLine) * (float) Math.log((double) countDocsInCorpus / (double) countDocsContainingThisTerm));
//                        System.out.println("tdIDFscore: " + tdIDFscore);

                        tdIDFScores.put(currFreqTerm, tdIDFscore);

                    }
//                        System.out.println(currFreqTerm);

                }

            }

            String arrayWords[] = new String[ngramsInLine.size()];
            if (arrayWords.length >= 2) {
                HashSet<String> setOcc = new HashSet();
                setOcc.addAll(new PerformCombinations(ngramsInLine.toArray(arrayWords)).call());

                Iterator<String> itOcc = setOcc.iterator();
                while (itOcc.hasNext()) {

                    String pairOcc = itOcc.next();
//                        System.out.println("current pair is:"+ pairOcc);
                    String[] pair = pairOcc.split(",");

                    if (pair.length == 2
                            & !pair[0].trim().equals(pair[1].trim()) & !pair[0].contains(pair[1]) & !pair[1].contains(pair[0])) {

                        if (useTDIDF) {
                            int weightOfThisEdge = Math.round(1000 * (float) ((float) tdIDFScores.get(pair[0]) + (float) tdIDFScores.get(pair[1])));
//                            System.out.println(weightOfThisEdge);
                            multisetOcc.add(pairOcc, weightOfThisEdge);
                        } else {
                            multisetOcc.add(pairOcc, (ngramsInLine.count(pair[0]) + ngramsInLine.count(pair[1])));
                        }

                    }

                }
                setCombinations.addAll(multisetOcc);
            }
            ngramsInLine.clear();
            multisetOcc.clear();

        }

        //normalization of edge weights to scale back to a range of 1 to 10:
        int maxWeight = 0;
        for (String element
                : setCombinations.elementSet()) {
            int currWeight = setCombinations.count(element);
            if (currWeight > maxWeight) {
                maxWeight = currWeight;
            }
        }
        for (String element
                : setCombinations.elementSet()) {
            int currWeight = setCombinations.count(element);
            int newWeight = Math.round(currWeight * 10 / (float) maxWeight) + 1;
            setCombinations.setCount(element, newWeight);
        }

//            Iterator<Multiset.Entry<String>> itSetCombinations = setCombinations.entrySet().iterator();
//            HashMap<String,Float> combiAndWeights = new HashMap();
//            while (itSetCombinations.hasNext()) {
//                Entry<String> currEntry = itSetCombinations.next();
//                combiAndWeights.put(currEntry.getElement(), (float)currEntry.getCount() / (float)10000);
//            }
        calculatingCooccurrencesTime.closeAndPrintClock();
        //-------------------------------------------------------------------------------------------------------------                 
        Clock printingOutputTime = new Clock("Printing Vosviewer files, GML file, report file");
        //-------------------------------------------------------------------------------------------------------------          
        // #### 9. PRINTING VOS VIEWER OUTPUT        
        ImmutableSet<Entry<String>> edgesSortedByCount = Multisets.copyHighestCountFirst(setCombinations).entrySet();

        HashMap<String, Integer> id = new HashMap();
        HashSet<String> idSet = new HashSet();
        int counterIds = 0;
        fileMapName = StringUtils.substring(textFile, 0, textFile.length() - 4).concat("_VosViewer_map.txt");
        BufferedWriter fileMapFile = new BufferedWriter(new FileWriter(wkOutput + fileMapName));
        StringBuilder mapSb = new StringBuilder();

        mapSb.append(
                "label,id\n");

        // #### Creates the map of ids
        for (Entry<String> entry : edgesSortedByCount) {
            String[] edge = entry.getElement().split(",");
            if (idSet.add(edge[0])) {
                id.put(edge[0], counterIds++);
                mapSb.append(edge[0]).append(", ").append(counterIds).append("\n");
            }
            if (idSet.add(edge[1])) {
                id.put(edge[1], counterIds++);
                mapSb.append(edge[1]).append(", ").append(counterIds).append("\n");
            }
        }

        fileMapFile.write(mapSb.toString());
        fileMapFile.flush();

        fileMapFile.close();

        // #### Creates the Vosviewer network (edges) of ids
        fileNetworkName = StringUtils.substring(textFile, 0, textFile.length() - 4).concat("_VosViewer_network.txt");
        BufferedWriter fileNetworkFile = new BufferedWriter(new FileWriter(wkOutput + fileNetworkName));

        System.out.println(wkOutput
                + fileNetworkName);
        StringBuilder networkSb = new StringBuilder();
        for (Entry<String> entry : edgesSortedByCount) {

            String[] edge = entry.getElement().split(",");

            float edgeWeight;
            if (useTDIDF) {
                edgeWeight = (float) entry.getCount() / (float) 1000;
            } else {
                edgeWeight = (float) entry.getCount();
            }
            networkSb.append(id.get(edge[0]) + 1).append(",").append(id.get(edge[1]) + 1).append(",").append((Float.toString(edgeWeight))).append("\n");
        }

        fileNetworkFile.write(networkSb.toString());
        fileNetworkFile.flush();

        fileNetworkFile.close();
        //-------------------------------------------------------------------------------------------------------------     
        // #### 10. PRINTING GML output        
        HashMap<String, Integer> idGML = new HashMap();
        HashSet<String> idSetGML = new HashSet();
        counterIds = 0;
        fileGMLName = StringUtils.substring(textFile, 0, textFile.length() - 4).concat(".gml");
        BufferedWriter fileGMLFile = new BufferedWriter(new FileWriter(wkOutput + fileGMLName));
        StringBuilder GMLSb = new StringBuilder();

        GMLSb.append(
                "graph [\n");

        // #### Creates the nodes
        for (Entry<String> entry : edgesSortedByCount) {
            String[] edge = entry.getElement().split(",");
            if (idSetGML.add(edge[0])) {
                idGML.put(edge[0], counterIds++);
                GMLSb.append("node\n[\nid ").append(counterIds).append("\nlabel \"").append(edge[0]).append("\"\n]\n");
            }
            if (idSetGML.add(edge[1])) {
                idGML.put(edge[1], counterIds++);
                GMLSb.append("node\n[\nid ").append(counterIds).append("\nlabel \"").append(edge[1]).append("\"\n]\n");
            }

        }
        for (Entry<String> entry : edgesSortedByCount) {
            String[] edge = entry.getElement().split(",");
            GMLSb.append("edge\n[\nsource ").append(id.get(edge[0]) + 1).append("\ntarget ").append(id.get(edge[1]) + 1).append("\nvalue ").append(entry.getCount()).append("\n]\n");
        }

        fileGMLFile.write(GMLSb.toString());
        fileGMLFile.flush();

        fileGMLFile.close();
        //-------------------------------------------------------------------------------------------------------------          
        // #### 11. PRINTING REPORT ON PARAMETERS EMPLOYED:
        fileParametersName = StringUtils.substring(textFile, 0, textFile.length() - 4).concat("_parameters.txt");
        BufferedWriter fileParametersFile = new BufferedWriter(new FileWriter(wkOutput + fileParametersName));
        StringBuilder parametersSb = new StringBuilder();

        parametersSb.append(
                "Report of the parameters used to extract co-occurrences in file \"").append(textFile).append("\".\n\n");

        parametersSb.append(
                "TD-IDF measure used to correct the frequency of terms per docs: ").append(useTDIDF).append(".\n");
        parametersSb.append(
                "Number of documents in the corpus: ").append(numberOfDocs).append(".\n");
        parametersSb.append(
                "Inclusion of n-grams up to (and including) ").append(maxgram).append("-grams.\n");
        parametersSb.append(
                "Binary or full counting of co-occurrences per document? Binary = ").append(binary).append("\n");
        parametersSb.append(
                "Size of the list of most frequent stopwords removed: ").append(nbStopWords).append(".\n");
        parametersSb.append(
                "max number of words allowed: ").append(freqThreshold).append(".\n");
        parametersSb.append(
                "min nb of occurrences in the entire corpus for a word to be processed: ").append(occurrenceThreshold).append(".\n");

        parametersSb.append(
                "min nb of characters for a word to be processed: ").append(minWordLength).append(".\n");
        parametersSb.append(
                "number of words found including n-grams: ").append(setFreqWords.size()).append(".\n");
        parametersSb.append(
                "number of nodes: ").append(counterIds).append(".\n");
        parametersSb.append(
                "number of edges: ").append(setCombinations.elementSet().size()).append(".\n\n\n");
        parametersSb.append(
                mostFrequentTerms.toString());

        fileParametersFile.write(parametersSb.toString());
        fileParametersFile.flush();

        fileParametersFile.close();

        //-------------------------------------------------------------------------------------------------------------     
        printingOutputTime.closeAndPrintClock();

        //-------Printing GEPHI Output--------------------------------------------------------------------------------     
//            Clock GephiVizTime = new Clock("Creating pdf of Gephi viz");
//            Screen1.logArea.setText(Screen1.logArea.getText().concat("This step takes longer - count 15 seconds on a powerful desktop\n"));
//            Screen1.logArea.setCaretPosition(Screen1.logArea.getText().length());
//            GephiTooKit.main(wkOutput, fileGMLName);
//            GephiVizTime.closeAndPrintClock();
//            Screen1.logArea.setText(Screen1.logArea.getText().concat("The resulting network is a file called \"GEPHI_output.png\" located in the same directory as your text file.\n"
//                    + "Results are ugly but it's going to improve! Check again regularly.\n"
//                    + "The source for this program is freely available at:\n"
//                    + "https://github.com/seinecle/MapText/blob/master/src/seinecle/utils/text/GUIMain.java\n"
//                    + "Thank you!\n"
//                    + "www.clementlevallois.net // twitter: @seinecle"));
//            Screen1.logArea.setCaretPosition(Screen1.logArea.getText().length());
        //System.exit(0);
    }
}
