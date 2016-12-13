/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package LanguageDetection;

import Admin.Parameters;
import LanguageDetection.Cyzoku.DetectorFactory;
import LanguageDetection.Cyzoku.LangDetectException;
import Utils.Stopwords;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;


/**
 *
 * @author LEVALLOIS
 */

public class LanguageResourcesInitializer {

    private static Map<String, Set<String>> multiLanguageStopwords;


    public static void load() {
        try {
            DetectorFactory.loadProfileChooseSource("LanguageDetection/Cyzoku/lang/", true);
        } catch (LangDetectException ex) {
            Logger.getLogger(LanguageResourcesInitializer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LanguageResourcesInitializer.class.getName()).log(Level.SEVERE, null, ex);
        }

        multiLanguageStopwords = new HashMap();

        for (String lang : Parameters.supportedLanguages) {
            Set<String> stopwords = Stopwords.getStopWords(lang, Parameters.local);
            if (stopwords != null) {
                multiLanguageStopwords.put(lang, stopwords);
            }
        }

    }

    public static Map<String, Set<String>> getMultiLanguageStopwords() {
        return multiLanguageStopwords;
    }
    
    

}
