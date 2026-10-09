package com.google.mlkit.nl.translate;
public final class TranslateRemoteModel {
    private final String language;
    private TranslateRemoteModel(String language) { this.language = language; }
    public String getLanguage() { return language; }
    public static final class Builder {
        private final String language;
        public Builder(String language) { this.language = language; }
        public TranslateRemoteModel build() { return new TranslateRemoteModel(language); }
    }
}
