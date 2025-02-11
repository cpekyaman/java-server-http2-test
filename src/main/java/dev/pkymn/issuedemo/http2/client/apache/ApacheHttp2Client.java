package dev.pkymn.issuedemo.http2.client.apache;

import dev.pkymn.issuedemo.http2.client.Http2Client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ApacheHttp2Client extends Http2Client {
    private final CloseableHttpAsyncClient client;

    public ApacheHttp2Client() {
        this.client = new ClientBuilder(requestConfig()).build();
        this.client.start();
    }

    public static void main(String[] args) {
        new ApacheHttp2Client().run(args);
    }

    @Override
    protected int sendRequest(String senderId, String traceId) throws IOException, InterruptedException {
        try {
            return new AsyncSender(requestUri(), requestConfig())
                    .sendRequest(client, senderId, traceId)
                    .get(2, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    private static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setResponseTimeout(Timeout.of(5, TimeUnit.SECONDS))
                .setConnectionRequestTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .setAuthenticationEnabled(false)
                .setRedirectsEnabled(false)
                .build();
    }
}
