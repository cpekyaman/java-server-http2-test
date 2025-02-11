package dev.pkymn.issuedemo.http2.server.jetty;

import dev.pkymn.issuedemo.http2.config.CommonProperties;
import dev.pkymn.issuedemo.http2.server.config.ServerProperties;
import dev.pkymn.issuedemo.http2.server.jetty.http2.LogContextHTTP2ServerConnectionFactory;
import dev.pkymn.issuedemo.http2.server.jetty.listener.ErrorLoggingEventHandler;
import dev.pkymn.issuedemo.http2.server.servlet.PingServlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.util.TimeZone;

public final class JettyServer {
    private static final Logger LOG = LogManager.getLogger(JettyServer.class);

    private final String baseDirectory;

    public JettyServer() {
        this.baseDirectory = CommonProperties.BASE_DIRECTORY.getValue() + File.separator + "/jetty";
    }

    public void start() {
        Server server = createServer();

        ServletContextHandler servletContext =
                new ServletContextHandler("/http2-server", ServletContextHandler.NO_SESSIONS);
        servletContext.addServlet(new ServletHolder(new PingServlet()), "/api/ping");

        ErrorLoggingEventHandler errorLoggingEventHandler = new ErrorLoggingEventHandler();
        errorLoggingEventHandler.setHandler(servletContext);
        server.setHandler(errorLoggingEventHandler);

        // base http configuration
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new SecureRequestCustomizer());
        httpConfig.setRequestHeaderSize(ServerProperties.SERVER_HTTPS_MAX_HEADER_SIZE.getIntValue());
        httpConfig.setResponseHeaderSize(ServerProperties.SERVER_HTTPS_MAX_HEADER_SIZE.getIntValue());

        // http11 protocol
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

        // http2 protocol
        HTTP2ServerConnectionFactory http2 = new LogContextHTTP2ServerConnectionFactory(httpConfig);
        http2.setMaxConcurrentStreams(ServerProperties.SERVER_HTTP2_MAX_STREAMS.getIntValue());
        http2.setConnectProtocolEnabled(true);

        // alpn support
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(http11.getProtocol());

        // tls support
        SslConnectionFactory tls = createSslConnectionFactory(alpn);

        // https connector that supports http 1.1 / 2 via alpn
        ServerConnector connector = new ServerConnector(server, tls, alpn, http2, http11);
        connector.setName("Https/2");
        connector.setPort(ServerProperties.SERVER_HTTPS_PORT.getIntValue());
        server.addConnector(connector);

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            LOG.warn("Could not start server", e);
            System.exit(1);
        }
    }

    private Server createServer() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setDaemon(true);
        threadPool.setMaxThreads(ServerProperties.SERVER_HTTPS_MAX_THREADS.getIntValue());

        Server server = new Server(threadPool);
        server.addBean(new ConnectionLimit(ServerProperties.SERVER_HTTPS_MAX_CONNECTIONS.getIntValue(), server));
        configureAccessLogs(server);

        return server;
    }

    private SslConnectionFactory createSslConnectionFactory(ALPNServerConnectionFactory alpn) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(ServerProperties.SERVER_HTTPS_KEYSTORE_FILE.getValue());
        sslContextFactory.setKeyStorePassword(ServerProperties.SERVER_HTTPS_KEYSTORE_PASS.getValue());
        sslContextFactory.setNeedClientAuth(false);
        sslContextFactory.setProtocol(ServerProperties.SERVER_HTTPS_SSL_PROTOCOL.getValue());
        sslContextFactory.setIncludeProtocols(ServerProperties.SERVER_HTTPS_ENABLED_PROTOCOLS.getValue().split("\\+"));

        return new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
    }

    private void configureAccessLogs(Server server) {
        RequestLogWriter accessLogWriter = new RequestLogWriter(baseDirectory + "/logs/access_log.yyyy_MM_dd.log");
        accessLogWriter.setFilenameDateFormat("yyyy-MM-dd");
        accessLogWriter.setRetainDays(1);
        accessLogWriter.setTimeZone(TimeZone.getDefault().getID());

        server.setRequestLog(
                new CustomRequestLog(accessLogWriter, ServerProperties.SERVER_ACCESS_LOG_FORMAT.getValue()));
    }

    public static void main(String[] args) {
        new JettyServer().start();
    }
}
