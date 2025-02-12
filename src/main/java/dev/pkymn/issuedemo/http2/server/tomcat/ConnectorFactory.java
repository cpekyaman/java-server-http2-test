package dev.pkymn.issuedemo.http2.server.tomcat;

import dev.pkymn.issuedemo.http2.server.config.ServerProperties;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

final class ConnectorFactory {
    private ConnectorFactory() {
        // util class
    }

    static Connector https() {
        Http11NioProtocol httpsProtocol = createHttpsProtocol();

        Connector httpsConnector = new Connector(httpsProtocol);
        httpsConnector.setPort(ServerProperties.SERVER_HTTPS_PORT.getIntValue());
        httpsConnector.setScheme("https");
        httpsConnector.setSecure(true);

        Http2Protocol http2Protocol = new Http2Protocol();
        http2Protocol.setMaxConcurrentStreams(ServerProperties.SERVER_HTTP2_MAX_STREAMS.getIntValue());

        httpsConnector.addUpgradeProtocol(http2Protocol);
        return httpsConnector;
    }

    private static Http11NioProtocol createHttpsProtocol() {
        Http11NioProtocol httpsProtocol = new Http11NioProtocol();

        httpsProtocol.setSslImplementationName("org.apache.tomcat.util.net.jsse.JSSEImplementation");
        httpsProtocol.setMaxHttpHeaderSize(ServerProperties.SERVER_HTTPS_MAX_HEADER_SIZE.getIntValue());
        httpsProtocol.setMaxThreads(ServerProperties.SERVER_HTTPS_MAX_THREADS.getIntValue());
        httpsProtocol.setMaxConnections(ServerProperties.SERVER_HTTPS_MAX_CONNECTIONS.getIntValue());
        httpsProtocol.setUseKeepAliveResponseHeader(true);
        httpsProtocol.setConnectionTimeout(20000);

        SSLHostConfig sslHostConfig = createSslHostConfig(httpsProtocol.getDefaultSSLHostConfigName());
        httpsProtocol.setSSLEnabled(true);
        httpsProtocol.addSslHostConfig(sslHostConfig);
        return httpsProtocol;
    }

    private static SSLHostConfig createSslHostConfig(String hostName) {
        String keyStoreFile = ServerProperties.SERVER_HTTPS_KEYSTORE_FILE.getValue();
        String keyStorePassword = ServerProperties.SERVER_HTTPS_KEYSTORE_PASS.getValue();

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName(hostName);

        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(keyStoreFile);
        certificate.setCertificateKeystorePassword(keyStorePassword);

        sslHostConfig.addCertificate(certificate);
        sslHostConfig.setCertificateVerification("NONE");
        sslHostConfig.setSslProtocol(ServerProperties.SERVER_HTTPS_SSL_PROTOCOL.getValue());
        sslHostConfig.setProtocols(ServerProperties.SERVER_HTTPS_ENABLED_PROTOCOLS.getValue());

        return sslHostConfig;
    }

}
