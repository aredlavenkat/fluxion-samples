package ai.fluxion.sample.workflow;

import ai.fluxion.core.model.Document;
import ai.fluxion.core.model.Stage;
import ai.fluxion.rules.domain.RuleAction;
import ai.fluxion.rules.domain.RuleCondition;
import ai.fluxion.rules.domain.RuleDefinition;
import ai.fluxion.rules.domain.RuleSet;
import ai.fluxion.workflow.temporal.activities.FluxionRuleActivities;
import ai.fluxion.workflow.temporal.activities.FluxionRuleActivitiesImpl;
import ai.fluxion.workflow.temporal.activities.RuleActivityRequest;
import ai.fluxion.workflow.temporal.activities.RuleActivityResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates a richer Temporal workflow that orchestrates Fluxion rule evaluation.
 * The sample covers both the automatic approval path and the manual-review branch
 * (simulated human involvement) so teams can map the patterns onto their workers.
 */
public final class WorkflowQuickstartApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowQuickstartApp.class);
    private static final String TASK_QUEUE = "workflow-quickstart";

    private WorkflowQuickstartApp() {
    }

    public static void main(String[] args) {
        RuleSet orderRules = buildRuleSet();
        Document lowValueOrder = orderDocument(280.50, "STANDARD");
        Document highValueOrder = orderDocument(2150.75, "EXPRESS");

        try (TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance()) {
            Worker worker = environment.newWorker(TASK_QUEUE);
            worker.registerActivitiesImplementations(new FluxionRuleActivitiesImpl(), new ThresholdManualApprovalActivities());
            worker.registerWorkflowImplementationTypes(OrderApprovalWorkflowImpl.class);
            environment.start();

            runScenario(environment, lowValueOrder, orderRules, "LOW VALUE");
            runScenario(environment, highValueOrder, orderRules, "HIGH VALUE");
        }
    }

    private static void runScenario(TestWorkflowEnvironment environment,
                                     Document document,
                                     RuleSet ruleSet,
                                     String label) {
        OrderApprovalWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                OrderApprovalWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build()
        );

        OrderApprovalRequest request = new OrderApprovalRequest(document, ruleSet, Duration.ofMinutes(2).toSeconds());
        OrderApprovalResult result = workflow.approve(request);

        LOGGER.info("=== {} ORDER DECISION ===", label);
        LOGGER.info("Approved: {}", result.approved());
        LOGGER.info("Reason  : {}", result.reason());
        result.sharedAttributes().forEach((key, value) ->
                LOGGER.info("  attr[{}]={}", key, value));
        LOGGER.info("===========================\n");
    }

    private static RuleSet buildRuleSet() {
        Stage purchaseStage = new Stage(Map.of("$match", Map.of("type", "purchase")));

        RuleAction enrichAttributes = ctx -> {
            Document doc = ctx.document();
            Object orderId = doc.get("orderId");
            Object amountValue = doc.get("amount");
            Object shipping = doc.get("shipping");

            if (orderId != null) {
                ctx.putSharedAttribute("orderId", orderId);
            }
            if (shipping != null) {
                ctx.putSharedAttribute("shipping", shipping);
            }

            double amount = amountValue instanceof Number number ? number.doubleValue() : 0.0;
            ctx.putSharedAttribute("amount", amount);
            ctx.putSharedAttribute("flag", amount >= 1000 ? "review" : "auto");
        };

        RuleAction annotateDecision = ctx -> {
            String decision = ctx.sharedAttributes().getOrDefault("flag", "auto").toString();
            ctx.putSharedAttribute("decisionId", UUID.randomUUID().toString());
            ctx.putSharedAttribute("decisionSource", "fluxion-rule:" + decision);
        };

        RuleDefinition rule = RuleDefinition.builder("order-screening")
                .description("Routes orders to auto/manual approval based on amount")
                .condition(RuleCondition.pipeline(List.of(purchaseStage)))
                .addAction(enrichAttributes)
                .addAction(annotateDecision)
                .build();

        return RuleSet.builder()
                .id("orders")
                .name("Order Approval Rules")
                .version("1.0.0")
                .addRule(rule)
                .build();
    }

    private static Document orderDocument(double amount, String shippingTier) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("type", "purchase");
        payload.put("amount", amount);
        payload.put("shipping", shippingTier);
        payload.put("customer", Map.of("tier", shippingTier.startsWith("EXP") ? "GOLD" : "BRONZE"));
        return new Document(payload);
    }

    // ----------------------------------------------------------------------
    // Workflow + activity definitions borrowed from the workflow test suite.
    // ----------------------------------------------------------------------

    @WorkflowInterface
    interface OrderApprovalWorkflow {
        @WorkflowMethod
        OrderApprovalResult approve(OrderApprovalRequest request);
    }

    record OrderApprovalRequest(Document document, RuleSet ruleSet, long manualApprovalTimeoutSeconds) {
    }

    record OrderApprovalResult(boolean approved, String reason, Map<String, Object> sharedAttributes) {
    }

    public static final class OrderApprovalWorkflowImpl implements OrderApprovalWorkflow {

        private final FluxionRuleActivities ruleActivities;
        private final ManualApprovalActivities manualActivities;

        public OrderApprovalWorkflowImpl() {
            ActivityOptions options = ActivityOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(1))
                    .build();
            this.ruleActivities = Workflow.newActivityStub(FluxionRuleActivities.class, options);
            this.manualActivities = Workflow.newActivityStub(ManualApprovalActivities.class, options);
        }

        @Override
        public OrderApprovalResult approve(OrderApprovalRequest request) {
            RuleActivityRequest ruleRequest = new RuleActivityRequest(
                    request.document(),
                    request.ruleSet(),
                    true,
                    false
            );
            RuleActivityResult ruleResult = ruleActivities.evaluateRuleSet(ruleRequest);

            Map<String, Object> shared = new LinkedHashMap<>(ruleResult.sharedAttributes());
            String flag = shared.getOrDefault("flag", "auto").toString();
            if (!"review".equals(flag)) {
                return new OrderApprovalResult(true, "approved", shared);
            }

            Object orderId = shared.getOrDefault("orderId", "unknown");
            Object amountValue = shared.getOrDefault("amount", 0);
            double amount = amountValue instanceof Number number ? number.doubleValue() : 0.0;

            boolean approved = manualActivities.requestApproval(
                    orderId.toString(),
                    amount,
                    request.manualApprovalTimeoutSeconds()
            );
            shared.put("manualApproval", approved);
            shared.put("reason", approved ? "manual-approved" : "manual-rejected");
            return new OrderApprovalResult(approved, (String) shared.get("reason"), shared);
        }
    }

    @ActivityInterface
    interface ManualApprovalActivities {
        boolean requestApproval(String orderId, double amount, long timeoutSeconds);
    }

    static final class ThresholdManualApprovalActivities implements ManualApprovalActivities {

        @Override
        public boolean requestApproval(String orderId, double amount, long timeoutSeconds) {
            LOGGER.info("Manual approval requested for order {} amount={} timeout={}s", orderId, amount, timeoutSeconds);
            boolean approved = amount < 2000;
            LOGGER.info("Manual approval {} for order {}", approved ? "GRANTED" : "DECLINED", orderId);
            return approved;
        }
    }
}
