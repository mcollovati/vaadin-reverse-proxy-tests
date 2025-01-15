import React from "react";
import {
    Button,
    HorizontalLayout,
    Notification,
    TextField
} from "@vaadin/react-components";
import {GreetingService} from "Frontend/generated/endpoints";
import {useSignal} from "@vaadin/hilla-react-signals";

export default function HelloHilla() {
    const nameSignal = useSignal("");

    function showNotification(text: string) {
        Notification.show(text);
    }

    return <HorizontalLayout style={{alignItems: 'end'}} theme="margin spacing">
        <TextField label="Your name"
                   onValueChanged={ev => nameSignal.value = ev.detail.value}></TextField>
        <Button
            onClick={ev => GreetingService.sayHello(nameSignal.value).then(showNotification)}>
            Say hello
        </Button>
        <Button
            onClick={ev => GreetingService.internationalSayHello(nameSignal.value).onNext(showNotification)}>
            Say hello in many languages
        </Button>
    </HorizontalLayout>;
}