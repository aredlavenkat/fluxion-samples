package ai.fluxion.samples.rules.bootstrap;

import ai.fluxion.rules.spring.boot.RuleSetsLoadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Demonstrates reacting to {@link RuleSetsLoadedEvent} by registering runtime
 * rule actions. Applications can extend this pattern to add custom actions or
 * hooks once the starter finishes loading definitions.
 */
@Component
public class RuleBootstrapListener {

    @EventListener
    public void onRulesLoaded(RuleSetsLoadedEvent event) {
        // Additional hooks could run here once rules are loaded.
    }
}
