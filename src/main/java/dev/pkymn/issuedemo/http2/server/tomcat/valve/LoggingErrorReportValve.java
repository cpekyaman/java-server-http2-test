package dev.pkymn.issuedemo.http2.server.tomcat.valve;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LoggingErrorReportValve extends ErrorReportValve {
    private static final Logger LOG = LogManager.getLogger(LoggingErrorReportValve.class);

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int statusCode = response.getStatus();

        if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }

        LOG.warn(
                "Unhandled error in request for url {} with status {}",
                request.getRequestURL(),
                response.getStatus(),
                throwable);

        super.report(request, response, throwable);
    }
}
