package dev.pkymn.issuedemo.http2.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Http2Client {
    protected final Logger LOG = LogManager.getLogger(this.getClass());

    private int senderCount;
    private int testDurationMinutes;
    private String requestUri;

    public final void run(String[] args) {
        LOG.info("starting test");

        parseArguments(args);
        checkServerRunning();

        LOG.info("Running {} senders for {} minutes for target url {}", senderCount, testDurationMinutes, requestUri);

        ExecutorService requestSenderExecutor = newExecutor("RequestSender", senderCount);

        CountDownLatch senderCountdown = new CountDownLatch(senderCount);
        AtomicBoolean shutdownSignal = new AtomicBoolean(false);

        LOG.info("Creating request senders");
        for (int i = 0; i < senderCount; i++) {
            requestSenderExecutor.submit(
                    new RequestSenderWorker(LOG, this::sendRequest, shutdownSignal, senderCountdown));
        }

        waitForFinish(senderCountdown, shutdownSignal, requestSenderExecutor, testDurationMinutes);

        LOG.info("test completed");
    }

    private void parseArguments(String[] args) {
        if (args.length > 3) {
            LOG.warn("unsupported number of arguments are supplied");
            System.exit(1);
        }

        senderCount = 1;
        testDurationMinutes = 5;
        requestUri = "https://localhost:8443/http2-server/api/ping";

        for (String arg : args) {
            String[] argSplit = arg.split("=");
            if (argSplit.length != 2) {
                LOG.warn("invalid argument {}", arg);
                System.exit(1);
            }

            switch (argSplit[0]) {
                case "-s":
                case "--senderCount":
                    senderCount = Integer.parseInt(argSplit[1]);
                    break;
                case "-d":
                case "--duration":
                    testDurationMinutes = Integer.parseInt(argSplit[1]);
                    break;
                case "-r":
                case "--requestUrl":
                    requestUri = argSplit[1];
                    break;
                default:
                    LOG.warn("invalid argument {}", arg);
                    System.exit(1);
            }
        }
    }

    private void checkServerRunning() {
        LOG.info("checking if server is running");

        boolean serverRunning = false;
        int maxTries = 10;

        try {
            for (int tries = 0; tries < maxTries && !serverRunning; tries++) {
                try {
                    this.sendRequest("main", UUID.randomUUID().toString());
                    serverRunning = true;
                } catch (IOException e) {
                    LOG.warn("could not connect to server, will try again", e);
                    Thread.sleep(2000);
                }
            }
        } catch (InterruptedException e) {
            LOG.warn("server check request interrupted, exiting", e);
            System.exit(2);
        }

        if (!serverRunning) {
            LOG.warn("server is not running or not reachable, exiting");
            System.exit(3);
        }
    }

    protected abstract int sendRequest(String senderId, String traceId) throws IOException, InterruptedException;

    protected final ExecutorService newExecutor(String name, int threadCount) {
        AtomicInteger threadId = new AtomicInteger(1);

        return Executors.newFixedThreadPool(
                threadCount,
                r -> {
                    Thread senderThread = new Thread(r);
                    senderThread.setDaemon(true);
                    senderThread.setName(name + "-" + threadId.getAndIncrement());
                    return senderThread;
                });
    }

    private void waitForFinish(
            CountDownLatch senderCountdown,
            AtomicBoolean shutdownSignal,
            ExecutorService requestSenderExecutor,
            int testDurationMinutes) {

        try {
            LOG.info("Waiting for senders to finish");
            if (senderCountdown.await(testDurationMinutes, TimeUnit.MINUTES)) {
                LOG.info("Request senders are done");
            } else {
                LOG.warn("Request senders have been running for too long, shutting down");
                shutdownSignal.set(true);
            }
        } catch (InterruptedException e) {
            LOG.warn("Could not wait for senders to finish", e);
        } finally {
            requestSenderExecutor.shutdown();
            try {
                if (!requestSenderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("Sender executor did not shutdown in time");
                }
            } catch (InterruptedException e) {
                LOG.warn("Could not wait for sender executor to shutdown", e);
            }
        }
    }

    protected final String requestUri() {
        return requestUri;
    }
}
