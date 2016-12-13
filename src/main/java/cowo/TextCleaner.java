/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cowo;

/**
 *
 * @author C. Levallois
 */
public class TextCleaner {

    public static String doBasicCleaning(String currLine) {

        currLine = currLine.replaceAll("’", "'");
        currLine = currLine.replaceAll("“", "'");
        currLine = currLine.replaceAll("”", "'");
        currLine = currLine.replaceAll("»", " ");
        currLine = currLine.replaceAll("«", " ");
        currLine = currLine.replaceAll("\\(", " ");
        currLine = currLine.replaceAll("\\)", " ");
        currLine = currLine.replaceAll("'", " ");
        currLine = currLine.replaceAll("\\.", " ");
        currLine = currLine.replaceAll(",", " ");
        currLine = currLine.replaceAll("\\?", "");
        currLine = currLine.toLowerCase();
        currLine = currLine.replaceAll(" +", " ");

        currLine = currLine.replaceAll("behaviour", "behavior");

        currLine = currLine.replaceAll("in vitro", "invitro");

        return currLine;
    }
}
