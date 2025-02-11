package dev.pkymn.issuedemo.http2.client;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

final class RequestSenderWorker implements Runnable {
    private final Logger log;
    private final SenderFunction senderFunction;
    private final AtomicBoolean shutdownSignal;
    private final CountDownLatch senderCountdown;

    RequestSenderWorker(
            Logger log,
            SenderFunction senderFunction,
            AtomicBoolean shutdownSignal,
            CountDownLatch senderCountdown) {

        this.log = log;
        this.senderFunction = senderFunction;
        this.shutdownSignal = shutdownSignal;
        this.senderCountdown = senderCountdown;
    }

    @Override
    public void run() {
        try {
            while (!shutdownSignal.get()) {
                try {
                    String traceId = UUID.randomUUID().toString();
                    String senderId = Thread.currentThread().getName();

                    ThreadContext.put("traceId", traceId);
                    ThreadContext.put("senderId", senderId);

                    log.debug("Sending request");
                    int responseStatus = senderFunction.sendRequest(senderId, traceId);
                    if (responseStatus == 500) {
                        log.info("client got expected response for request, stopping test");
                        shutdownSignal.set(true);
                        break;
                    } else {
                        log.debug("Received response with status {}", responseStatus);
                    }

                    Thread.sleep(100);
                } catch (IOException | InterruptedException e) {
                    log.warn("Request sender failed unexpectedly", e);
                    break;
                } finally {
                    ThreadContext.clearAll();
                }
            }
        } finally {
            senderCountdown.countDown();
        }
    }

    @FunctionalInterface
    interface SenderFunction {
        int sendRequest(String senderId, String traceId) throws IOException, InterruptedException;
    }
}
