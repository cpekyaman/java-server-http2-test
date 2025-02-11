package dev.pkymn.issuedemo.http2.server.jetty.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.EventsHandler;

public final class ErrorLoggingEventHandler extends EventsHandler {
    private static final Logger LOG = LogManager.getLogger(ErrorLoggingEventHandler.class);

    @Override
    protected void onComplete(Request request, int status, HttpFields headers, Throwable failure) {
        if (failure != null) {
            LOG.warn(
                    "Unhandled error in request for url {} with status {}",
                    request.getHttpURI().asString(),
                    status,
                    failure);
        }
    }
}
