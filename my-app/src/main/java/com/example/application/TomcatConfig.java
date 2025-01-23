package com.example.application;

import java.net.InetAddress;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty("tomcat.ajp.port")
@Configuration
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final String PROTOCOL = "AJP/1.3";

    @Value("${tomcat.ajp.port:8009}") //Defined on application.properties
    private int ajpPort;

    @Value("${tomcat.ajp.address:::}") //Defined on application.properties
    private InetAddress ajpAddress;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Connector ajpConnector = new Connector(PROTOCOL);
        ajpConnector.setPort(ajpPort);
        AbstractAjpProtocol<?> ajpProtocol = (AbstractAjpProtocol<?>) ajpConnector.getProtocolHandler();
        ajpProtocol.setSecretRequired(false);
        ajpProtocol.setAddress(ajpAddress);
        factory.addAdditionalTomcatConnectors(ajpConnector);
    }
}