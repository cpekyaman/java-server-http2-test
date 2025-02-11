package dev.pkymn.issuedemo.http2.server.tomcat.config;


import java.util.Locale;

public enum TomcatProperties {
    // Tomcat server
    SERVER_SHUTDOWN_PORT("8005"),
    SERVER_PORT_OFFSET("0"),
    SERVER_APR_SSL_ENABLE("off"),

    SERVER_USE_NIO2("false");

    final String defaultValue;

    TomcatProperties(String defaultValue) {
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

    public int getIntValue() {
        return Integer.parseInt(getValue());
    }

    public boolean getBooleanValue() {
        return Boolean.parseBoolean(getValue());
    }
}
