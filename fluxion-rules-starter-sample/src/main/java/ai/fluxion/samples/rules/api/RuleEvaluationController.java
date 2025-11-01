package ai.fluxion.samples.rules.api;

import java.util.List;
import java.util.Map;

import ai.fluxion.core.model.Document;
import ai.fluxion.rules.engine.RuleEvaluationResult;
import ai.fluxion.rules.engine.RulePass;
import ai.fluxion.rules.spring.boot.RuleEvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple REST controller exposing the auto-configured {@link RuleEvaluationService}
 * so the sample can execute rule sets over HTTP payloads.
 */
@RestController
@RequestMapping("/rules")
public class RuleEvaluationController {

    private final RuleEvaluationService ruleEvaluationService;

    public RuleEvaluationController(RuleEvaluationService ruleEvaluationService) {
        this.ruleEvaluationService = ruleEvaluationService;
    }

    @PostMapping("{ruleSetId}/evaluate")
    @ResponseStatus(HttpStatus.OK)
    public EvaluationResponse evaluate(@PathVariable String ruleSetId,
                                       @RequestBody Map<String, Object> payload) {
        RuleEvaluationResult result = ruleEvaluationService.evaluate(new Document(payload), ruleSetId);
        List<RulePass> passes = result.passes();
        return new EvaluationResponse(ruleSetId, passes.stream().map(pass ->
                new RulePassSummary(pass.rule().id(), pass.rule().name(), pass.rule().salience(),
                        pass.context().attributes())).toList());
    }

    public record EvaluationResponse(String ruleSetId, List<RulePassSummary> passes) {
    }

    public record RulePassSummary(String id, String name, int salience, Map<String, Object> attributes) {
    }
}
