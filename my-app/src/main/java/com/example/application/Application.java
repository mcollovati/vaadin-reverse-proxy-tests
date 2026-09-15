package com.example.application;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.theme.aura.Aura;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 */
@SpringBootApplication
@PWA(name = "My App", shortName = "My App", offlineResources = {})
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("context://styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    FilterRegistrationBean<?> redirectTest() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(
                new OncePerRequestFilter() {

                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain) throws IOException {
                        response.sendRedirect(
                                request.getContextPath() + "/hello-flow");
                    }
                });
        registrationBean.addUrlPatterns("/test-redirect");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * A bare Server-Sent Events stream, independent of Vaadin, that emits one
     * event every 500 ms for 30 seconds.
     * <p>
     * {@code curl -N <proxy-url>sse-probe} tells you in one command whether a
     * proxy passes an event stream through unbuffered: the ticks must trickle
     * in one by one. Arriving in a burst at the end means the proxy is
     * buffering the response, which is what breaks SSE push.
     * <p>
     * Registered as a filter rather than an MVC controller so it stays
     * reachable whatever the Vaadin servlet is mapped to.
     */
    @Bean
    FilterRegistrationBean<?> sseProbe() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(
                new OncePerRequestFilter() {

                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain) throws IOException {
                        response.setContentType("text/event-stream");
                        response.setCharacterEncoding("UTF-8");
                        response.setHeader("Cache-Control", "no-cache");
                        // Asks nginx not to buffer even when proxy_buffering is
                        // left on, so the probe can tell a missing
                        // `proxy_buffering off` from a broken one.
                        response.setHeader("X-Accel-Buffering", "no");

                        PrintWriter writer = response.getWriter();
                        for (int tick = 0; tick < 60; tick++) {
                            writer.write("data: tick " + tick + "\n\n");
                            writer.flush();
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                });
        registrationBean.addUrlPatterns("/sse-probe");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    @ConditionalOnProperty(name = "vaadin.url-mapping")
    FilterRegistrationBean<?> publicImagesAliasFilter(
            @Value("${vaadin.url-mapping}") String urlMapping) {
        String baseMapping = urlMapping.replaceFirst("/\\*$", "");
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(
                new OncePerRequestFilter() {

                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain)
                            throws ServletException, IOException {
                        request.getRequestDispatcher(request.getRequestURI()
                                .substring(baseMapping.length()))
                                .forward(request, response);
                    }
                });
        registrationBean.addUrlPatterns(baseMapping + "/icons/icon.png",
                baseMapping + "/images/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

}
