package org.sps.backend.Learning;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class HelloController {

    // Le template qui est utilisé pour formater les messages de salutation
    private static final String template = "Hello, %s!";

    // Les Atomic sont VITAUX dans un environnement concurrentiel
    private final AtomicLong counter = new AtomicLong();


    @GetMapping("/api/hello")
    public Greeting greeting(@RequestParam(defaultValue = "World") String name) {
        return new Greeting(counter.incrementAndGet(), template.formatted(name));
    }

}
