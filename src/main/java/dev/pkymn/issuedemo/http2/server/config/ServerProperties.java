package dev.pkymn.issuedemo.http2.server.config;

import java.util.Locale;

public enum ServerProperties {
    // logging
    SERVER_ACCESS_LOG_FORMAT("%t \"%r\" %s %D - %{sender-id}i - %{trace-id}i"),

    // https
    SERVER_HTTPS_PORT("8443"),
    SERVER_HTTPS_MAX_HEADER_SIZE("65536"),
    SERVER_HTTPS_MAX_THREADS("50"),
    SERVER_HTTPS_MAX_CONNECTIONS("200"),
    SERVER_HTTPS_KEYSTORE_FILE("./cert/tomcat_local.p12"),
    SERVER_HTTPS_KEYSTORE_PASS("pkymn"),
    SERVER_HTTPS_SSL_PROTOCOL("TLSv1.3"),
    SERVER_HTTPS_ENABLED_PROTOCOLS("TLSv1.2+TLSv1.3"),

    // http2
    SERVER_HTTP2_MAX_STREAMS("128");

    final String defaultValue;

    ServerProperties(String defaultValue) {
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
}
