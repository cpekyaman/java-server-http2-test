package dev.pkymn.issuedemo.http2.client.apache;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class AsyncSender implements FutureCallback<Message<HttpResponse, byte[]>> {
    private final String requestUri;
    private final RequestConfig requestConfig;
    // this is not absolutely necessary for testing, we're just mimicking our client.
    private final CompletableFuture<Integer> resultFuture;

    AsyncSender(String requestUri, RequestConfig requestConfig) {
        this.requestUri = requestUri;
        this.requestConfig = requestConfig;
        this.resultFuture = new CompletableFuture<>();
    }

    CompletableFuture<Integer> sendRequest(CloseableHttpAsyncClient client, String senderId, String traceId) {

        AsyncRequestProducer requestProducer =
                AsyncRequestBuilder.get(requestUri)
                        .addHeader("trace-id", traceId)
                        .addHeader("sender-id", senderId)
                        .build();

        BasicResponseConsumer<byte[]> responseConsumer = new BasicResponseConsumer<>(new BasicAsyncEntityConsumer());

        Future<Message<HttpResponse, byte[]>> clientFuture =
                client.execute(requestProducer, responseConsumer, null, createHttpClientContext(), this);

        resultFuture.whenComplete(
                (res, ex) -> {
                    if (clientFuture.isDone()) {
                        return;
                    }
                    clientFuture.cancel(true);
                });

        return resultFuture;
    }

    private HttpClientContext createHttpClientContext() {
        HttpClientContext httpClientContext = new HttpClientContext();
        httpClientContext.setRequestConfig(requestConfig);
        return httpClientContext;
    }

    @Override
    public void completed(Message<HttpResponse, byte[]> result) {
        resultFuture.complete(result.getHead().getCode());
    }

    @Override
    public void failed(Exception ex) {
        resultFuture.completeExceptionally(ex);
    }

    @Override
    public void cancelled() {
        resultFuture.completeExceptionally(new CancellationException("request cancelled"));
    }
}
