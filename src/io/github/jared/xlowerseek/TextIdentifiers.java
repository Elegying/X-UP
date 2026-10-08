package io.github.jared.xlowerseek;

/** X renders t.co entities as bare display URLs. Use the same URL grammar for matching and protection. */
final class TextIdentifiers {
    static final String URL="https?://\\S+|www\\.\\S+|\\b(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?:/\\S*)?";
    static String withoutUrls(String text){return text.replaceAll(URL,"");}
}
