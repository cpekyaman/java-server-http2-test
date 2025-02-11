package dev.pkymn.issuedemo.http2.server.tomcat.valve;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;

public final class LoggingAccessLogValve extends AccessLogValve {
    private static final Logger LOG = LogManager.getLogger(LoggingAccessLogValve.class);

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        ThreadContext.put("traceId", request.getHeader("trace-id"));
        ThreadContext.put("senderId", request.getHeader("sender-id"));

        super.invoke(request, response);
    }

    @Override
    public void log(Request request, Response response, long time) {
        Throwable throwable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (throwable != null) {
            LOG.warn(
                    "Unhandled error in request for url {} with status {}",
                    request.getRequestURL(),
                    response.getStatus(),
                    throwable);
        }

        super.log(request, response, time);

        ThreadContext.clearAll();
    }
}
