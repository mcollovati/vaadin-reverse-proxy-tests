package com.example.application.services;

import java.time.Duration;

import reactor.core.publisher.Flux;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;

@BrowserCallable
@AnonymousAllowed
public class GreetingService {

    public String sayHello(String name) {
        if (name == null || name.isEmpty()) {
            return "Welcome, friend!";
        }
        return "Hello " + name + "!";
    }

    public Flux<String> internationalSayHello(String name) {
        return Flux.just("Hello ##friend##!", "Ciao ##amico##!", "Hallo ##Freund##!", "Hei ##ystäväni##!")
                .map(text -> text.replaceFirst("##(.*)##", name != null && !name.isEmpty() ? name : "$1"))
                .delayElements(Duration.ofSeconds(1));
    }
}
