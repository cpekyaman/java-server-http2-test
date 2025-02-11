package dev.pkymn.issuedemo.http2.server.jetty.http2;

import org.apache.logging.log4j.ThreadContext;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;

import java.util.concurrent.TimeoutException;

public final class LogContextHTTP2ServerConnectionFactory extends HTTP2ServerConnectionFactory {
    public LogContextHTTP2ServerConnectionFactory(HttpConfiguration httpConfiguration) {
        super(httpConfiguration);
    }

    @Override
    protected ServerSessionListener newSessionListener(Connector connector, EndPoint endPoint) {
        return new LogContextHttpServerSessionListener(endPoint);
    }

    private final class LogContextHttpServerSessionListener extends HTTPServerSessionListener {
        private LogContextHttpServerSessionListener(EndPoint endPoint) {
            super(endPoint);
        }

        // we fill log context with some tracing information here.
        @Override
        public Stream.Listener onNewStream(Stream stream, HeadersFrame frame) {
            MetaData.Request request = (MetaData.Request) frame.getMetaData();

            HttpFields headers = request.getHttpFields();

            ThreadContext.put("traceId", headers.get("trace-id"));
            ThreadContext.put("senderId", headers.get("sender-id"));

            return super.onNewStream(stream, frame);
        }

        // we clear the log context when a session or stream is closed.

        @Override
        public boolean onIdleTimeout(Session session) {
            boolean result;
            try {
                result = super.onIdleTimeout(session);
            } finally {
                ThreadContext.clearAll();
            }
            return result;
        }

        @Override
        public void onClose(Session session, GoAwayFrame frame, Callback callback) {
            try {
                super.onClose(session, frame, callback);
            } finally {
                ThreadContext.clearAll();
            }
        }

        @Override
        public void onFailure(Session session, Throwable failure, Callback callback) {
            try {
                super.onFailure(session, failure, callback);
            } finally {
                ThreadContext.clearAll();
            }
        }

        @Override
        public void onReset(Stream stream, ResetFrame frame, Callback callback) {
            try {
                super.onReset(stream, frame, callback);
            } finally {
                ThreadContext.clearAll();
            }
        }

        @Override
        public void onFailure(Stream stream, int error, String reason, Throwable failure, Callback callback) {
            try {
                super.onFailure(stream, error, reason, failure, callback);
            } finally {
                ThreadContext.clearAll();
            }
        }

        @Override
        public void onIdleTimeout(Stream stream, TimeoutException x, Promise<Boolean> promise) {
            try {
                super.onIdleTimeout(stream, x, promise);
            } finally {
                ThreadContext.clearAll();
            }
        }

        @Override
        public void onClosed(Stream stream) {
            try {
                super.onClosed(stream);
            } finally {
                ThreadContext.clearAll();
            }
        }
    }
}
