package ai.fluxion.samples.rules.bootstrap;

import ai.fluxion.rules.actions.RuleActionRegistry;
import ai.fluxion.rules.spring.boot.RuleSetsLoadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RuleBootstrapListener {

    @EventListener
    public void onRulesLoaded(RuleSetsLoadedEvent event) {
        RuleActionRegistry.register("flag-order", context ->
                context.putAttribute("flagged", true));
        RuleActionRegistry.register("flag-return", context ->
                context.putAttribute("returnFlagged", true));
    }
}
