package dev.pkymn.issuedemo.http2.config;


import java.io.File;
import java.util.Locale;

public enum CommonProperties {
    BASE_DIRECTORY(System.getProperty("user.dir") + File.separator + "work");

    final String defaultValue;

    CommonProperties(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getValue() {
        String value = System.getProperty(name().toLowerCase(Locale.ROOT).replace('_', '.'));
        if (value != null && !value.isEmpty()) {
            return value;
        }

        value = System.getenv(name());
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return defaultValue;
    }
}
