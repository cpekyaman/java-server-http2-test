package dev.pkymn.issuedemo.http2.client.tls;

import java.net.Socket;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

public final class TrustAllTrustManager extends X509ExtendedTrustManager {
    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        // do nothing
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket){
        // do nothing
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        // do nothing
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        // do nothing
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        // do nothing
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // do nothing
    }
}
