package dev.pkymn.issuedemo.http2.server.tomcat.servlet;

import dev.pkymn.issuedemo.http2.server.servlet.PingServlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;

import java.util.Set;

public final class Http2ServerServletContainerInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) {
        ServletRegistration.Dynamic pingServletRegistration = ctx.addServlet("PingServlet", new PingServlet());
        pingServletRegistration.addMapping("/api/ping");
        pingServletRegistration.setLoadOnStartup(1);
    }
}
