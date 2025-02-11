package dev.pkymn.issuedemo.http2.client.apache;

import dev.pkymn.issuedemo.http2.client.tls.TrustAllTrustManager;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

final class ClientBuilder {
    private final Logger LOG = LogManager.getLogger(this.getClass());

    private final RequestConfig defaultRequestConfig;

    ClientBuilder(RequestConfig defaultRequestConfig) {
        this.defaultRequestConfig = defaultRequestConfig;
    }

    CloseableHttpAsyncClient build() {
        IOReactorConfig ioReactorConfig = getIoReactorConfig();

        return getDefaultHttpClientBuilder(ioReactorConfig)
                .setIOReactorConfig(ioReactorConfig)
                .setConnectionManager(createConnectionManager())
                .setUserAgent("Apache Http Client")
                .setRedirectStrategy(DefaultRedirectStrategy.INSTANCE)
                .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
                .build();
    }

    private HttpAsyncClientBuilder getDefaultHttpClientBuilder(IOReactorConfig ioReactorConfig) {
        return HttpAsyncClientBuilder.create()
                .disableAutomaticRetries()
                .setConnectionManagerShared(false)
                .setUserAgent("Apache Http Client")
                .setDefaultRequestConfig(defaultRequestConfig)
                .setIOReactorConfig(ioReactorConfig);
    }

    private PoolingAsyncClientConnectionManager createConnectionManager() {
        PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                        .setConnPoolPolicy(PoolReusePolicy.FIFO);

        connectionManagerBuilder.setConnectionConfigResolver(
                (route) -> {
                    ConnectionConfig.Builder connectionConfigBuilder =
                            ConnectionConfig.custom()
                                    .setTimeToLive(TimeValue.of(30, TimeUnit.SECONDS))
                                    .setConnectTimeout(Timeout.of(10, TimeUnit.SECONDS));
                    connectionConfigBuilder.setValidateAfterInactivity(TimeValue.of(2, TimeUnit.SECONDS));
                    return connectionConfigBuilder.build();
                });

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, new TrustManager[] {new TrustAllTrustManager()}, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.warn("Could not initialize client ssl context", e);
            System.exit(1);
        }

        connectionManagerBuilder.setTlsStrategy(
                ClientTlsStrategyBuilder.create()
                        .setTlsVersions(TLS.V_1_2, TLS.V_1_3)
                        .setSslContext(sslContext)
                        .build());
        connectionManagerBuilder.setMaxConnTotal(100);
        connectionManagerBuilder.setMaxConnPerRoute(20);
        connectionManagerBuilder.setDefaultTlsConfig(
                TlsConfig.custom()
                        .setSupportedProtocols(TLS.V_1_2, TLS.V_1_3)
                        .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                        .build());

        return connectionManagerBuilder.build();
    }

    private IOReactorConfig getIoReactorConfig() {
        return IOReactorConfig.custom()
                .setSoTimeout(Timeout.of(5, TimeUnit.SECONDS))
                .setSelectInterval(TimeValue.of(500, TimeUnit.MILLISECONDS))
                .setTcpNoDelay(true)
                .build();
    }
}
