package dev.pkymn.issuedemo.http2.server.tomcat;

import dev.pkymn.issuedemo.http2.config.CommonProperties;
import dev.pkymn.issuedemo.http2.server.config.ServerProperties;
import dev.pkymn.issuedemo.http2.server.tomcat.config.TomcatProperties;
import dev.pkymn.issuedemo.http2.server.tomcat.servlet.Http2ServerServletContainerInitializer;
import dev.pkymn.issuedemo.http2.server.tomcat.valve.LoggingAccessLogValve;
import dev.pkymn.issuedemo.http2.server.tomcat.valve.LoggingErrorReportValve;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;

public final class TomcatServer {
    private static final Logger LOG = LogManager.getLogger(TomcatServer.class);

    private final Thread shutdownHook;
    private final Tomcat tomcat;
    private final String baseDirectory;

    public TomcatServer() {
        this.tomcat = new Tomcat();
        this.shutdownHook = new Thread(this::performShutdown);
        this.baseDirectory = CommonProperties.BASE_DIRECTORY.getValue() + File.separator + "/tomcat";
    }

    public void start() {
        this.configure();
        addShutdownHook();
        try {
            tomcat.start();
        } catch (LifecycleException e) {
            LOG.warn("An error occurred during startup.", e);
        } finally {
            if (!isSuccessfulStartup()) {
                LOG.warn("Shutting down JVM due to startup failure");
                System.exit(1);
            } else {
                LOG.info(
                        "Running Tomcat {} in base directory {}",
                        tomcat.getClass().getPackage().getImplementationVersion(),
                        Paths.get(baseDirectory).toAbsolutePath());
            }
        }

        tomcat.getServer().await();
    }

    private boolean isSuccessfulStartup() {
        Container[] children = this.tomcat.getHost().findChildren();
        for (Container container : children) {
            return container.getState().isAvailable();
        }
        return false;
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void performShutdown() {
        LOG.info("Shutting down Tomcat");

        try {
            long startShutdownTime = System.currentTimeMillis();

            StandardServer server = (StandardServer) tomcat.getServer();
            server.stopAwait();

            LifecycleState state = server.getState();
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0 && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                LOG.info("Nothing to do, already stopped");
            } else {
                tomcat.stop();
                tomcat.destroy();
            }
            LOG.info("Shutdown completed in: {} milliseconds.", (System.currentTimeMillis() - startShutdownTime));
        } catch (LifecycleException e) {
            LOG.warn("An error occurred during shutdown.", e);
        }
    }

    void configure() {
        tomcat.setBaseDir(baseDirectory);
        tomcat.enableNaming();

        configureServer();
        configureService();
        configureEngine();
        configureHost();
        configureContext();

        this.tomcat.getConnector().setThrowOnFailure(true);
    }

    private void configureServer() {
        Server server = tomcat.getServer();
        server.setShutdown("SHUTDOWN");
        server.setPort(TomcatProperties.SERVER_SHUTDOWN_PORT.getIntValue());
        server.setPortOffset(TomcatProperties.SERVER_PORT_OFFSET.getIntValue());

        AprLifecycleListener aprLifecycleListener = new AprLifecycleListener();
        aprLifecycleListener.setSSLEngine(TomcatProperties.SERVER_APR_SSL_ENABLE.getValue());
        server.addLifecycleListener(aprLifecycleListener);

        server.addLifecycleListener(new JreMemoryLeakPreventionListener());
        server.addLifecycleListener(new GlobalResourcesLifecycleListener());
        server.addLifecycleListener(new ThreadLocalLeakPreventionListener());
    }

    private void configureService() {
        Service service = tomcat.getService();
        if (service instanceof StandardService standardService) {
            standardService.setGracefulStopAwaitMillis(Duration.ofSeconds(30).toMillis());
        } else {
            throw new IllegalStateException("Tomcat Service is expected to be an instance of StandardService");
        }

        service.addConnector(ConnectorFactory.https());
    }

    private void configureEngine() {
        Engine engine = tomcat.getEngine();
        engine.setDefaultHost("localhost");

        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("X-Forwarded-For");
        remoteIpValve.setProtocolHeader("X-Forwarded-Proto");

        engine.getPipeline().addValve(remoteIpValve);
    }

    private void configureHost() {
        Host host = tomcat.getHost();

        host.setName("localhost");
        host.setAppBase("webapps");
        host.setStartStopThreads(0);
        host.setAutoDeploy(false);
        host.setCreateDirs(true);
        host.setDeployOnStartup(false);

        host.getPipeline().addValve(createAccessLogValve());
        host.getPipeline().addValve(createErrorReportValve());
    }

    private void configureContext() {
        String applicationName = "http2-server";

        Context context =
                tomcat.addContext(
                        tomcat.getHost(), "/" + applicationName, applicationName, new File(".").getAbsolutePath());

        WebResourceRoot standardRoot = new StandardRoot(context);
        context.setResources(standardRoot);

        context.setParentClassLoader(getClass().getClassLoader());
        if (context instanceof StandardContext standardContext) {
            standardContext.setDelegate(true);

            standardContext.setFailCtxIfServletStartFails(true);
            standardContext.setClearReferencesRmiTargets(false);
            standardContext.setClearReferencesThreadLocals(false);
        }

        context.setUseHttpOnly(true);
        context.addLifecycleListener(new FixContextListener());

        context.addServletContainerInitializer(new Http2ServerServletContainerInitializer(), Set.of());
    }

    private AccessLogValve createAccessLogValve() {
        AccessLogValve accessLogValve = new LoggingAccessLogValve();
        accessLogValve.setSuffix(".log");
        accessLogValve.setRequestAttributesEnabled(true);
        accessLogValve.setPattern(ServerProperties.SERVER_ACCESS_LOG_FORMAT.getValue());

        return accessLogValve;
    }

    private ErrorReportValve createErrorReportValve() {
        ErrorReportValve errorReportValve = new LoggingErrorReportValve();
        errorReportValve.setShowReport(true);
        errorReportValve.setShowServerInfo(true);
        return errorReportValve;
    }

    private static class FixContextListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            Context eventContext = (Context) event.getLifecycle();
            if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
                eventContext.setConfigured(true);
            }
        }
    }

    public static void main(String[] args) {
        new TomcatServer().start();
    }
}
