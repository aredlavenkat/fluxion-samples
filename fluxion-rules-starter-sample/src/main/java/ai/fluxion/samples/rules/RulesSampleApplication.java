package ai.fluxion.samples.rules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point that wires the Fluxion rules starter for REST-based
 * evaluation demos used throughout the sample repository.
 */
@SpringBootApplication
public class RulesSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RulesSampleApplication.class, args);
    }
}
