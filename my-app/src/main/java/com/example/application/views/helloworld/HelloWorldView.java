package com.example.application.views.helloworld;

import com.example.application.services.GreetingService;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Hello World Flow")
@Route(value = "hello-flow")
@Menu(title = "Hello World Flow", icon = "globe", order = 10)
public class HelloWorldView extends HorizontalLayout {

    private final transient GreetingService service;
    private final TextField name;

    public HelloWorldView(GreetingService service) {
        this.service = service;
        name = new TextField("Your name");
        Button sayHello = new Button("Say hello");
        sayHello.addClickListener(e -> {
            Notification.show(this.service.sayHello(name.getValue()));
        });
        sayHello.addClickShortcut(Key.ENTER);

        Button sayHelloInternational = new Button(
                "Say hello in many languages");
        sayHelloInternational.addClickListener(e -> {
            UI ui = UI.getCurrent();
            Scheduler uiAccessScheduler = Schedulers
                    .fromExecutor(task -> ui.access(task::run));
            this.service.internationalSayHello(name.getValue())
                    .publishOn(uiAccessScheduler).subscribe(Notification::show);
        });

        setMargin(true);
        setDefaultVerticalComponentAlignment(Alignment.END);

        add(name, sayHello, sayHelloInternational);
    }

}
