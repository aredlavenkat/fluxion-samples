package ai.fluxion.samples.rules.bootstrap;

import ai.fluxion.rules.domain.RuleAction;
import ai.fluxion.rules.spi.RuleActionContributor;

import java.util.Map;

/**
 * Registers custom rule actions required by the sample DSL payloads.
 */
public final class SampleRuleActionContributor implements RuleActionContributor {

    @Override
    public Map<String, RuleAction> ruleActions() {
        return Map.of(
                "flag-order", context -> context.putAttribute("flagged", true),
                "flag-return", context -> context.putAttribute("returnFlagged", true)
        );
    }
}
