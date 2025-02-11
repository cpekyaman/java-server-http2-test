package dev.pkymn.issuedemo.http2.client.jdk;

import dev.pkymn.issuedemo.http2.client.Http2Client;
import dev.pkymn.issuedemo.http2.client.tls.TrustAllTrustManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;

public final class JdkHttp2Client extends Http2Client {
    private final HttpClient client;

    public JdkHttp2Client() {
        this.client = createHttpClient();
    }

    public static void main(String[] args) {
        new JdkHttp2Client().run(args);
    }

    private HttpClient createHttpClient() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, new TrustManager[] {new TrustAllTrustManager()}, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.warn("Could not initialize client ssl context", e);
            System.exit(1);
        }

        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.2", "TLSv1.3"});
        sslParameters.setNeedClientAuth(false);
        sslParameters.setApplicationProtocols(new String[] {"h2", "http/1.1"});

        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslParameters(sslParameters)
                .executor(newExecutor("HttpClient", 8))
                .build();
    }

    @Override
    protected int sendRequest(String senderId, String traceId) throws IOException, InterruptedException {
        HttpRequest request = newRequest(traceId, senderId);
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpRequest newRequest(String traceId, String senderId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(requestUri()))
                .version(HttpClient.Version.HTTP_2)
                .timeout(Duration.ofSeconds(2))
                .header("trace-id", traceId)
                .header("sender-id", senderId)
                .GET()
                .build();
    }
}
