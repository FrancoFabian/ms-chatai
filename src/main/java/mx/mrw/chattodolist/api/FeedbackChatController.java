package mx.mrw.chattodolist.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.mrw.chattodolist.api.dto.FeedbackChatRequest;
import mx.mrw.chattodolist.api.dto.FeedbackChatResponse;
import mx.mrw.chattodolist.api.dto.ImproveMessageRequest;
import mx.mrw.chattodolist.api.dto.ImproveMessageResponse;
import mx.mrw.chattodolist.api.dto.UsageByModelResponse;
import mx.mrw.chattodolist.api.dto.UsageMetricsResponse;
import mx.mrw.chattodolist.api.dto.UsageSummaryResponse;
import mx.mrw.chattodolist.api.dto.UsageTotalsResponse;
import mx.mrw.chattodolist.security.AuthContext;
import mx.mrw.chattodolist.service.ChatTaskResult;
import mx.mrw.chattodolist.service.ChatTaskService;
import mx.mrw.chattodolist.service.ImproveMessageResult;
import mx.mrw.chattodolist.service.UsageSummaryData;
import mx.mrw.chattodolist.support.RequestContext;

@Validated
@RestController
@RequestMapping("/api")
public class FeedbackChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackChatController.class);

    private final ChatTaskService chatTaskService;

    public FeedbackChatController(ChatTaskService chatTaskService) {
        this.chatTaskService = chatTaskService;
    }

    @PostMapping("/feedback-chat")
    public FeedbackChatResponse createFeedbackTask(
            @Valid @RequestBody FeedbackChatRequest request,
            HttpServletRequest httpServletRequest) {

        String subject = subject(httpServletRequest);
        String requestId = requestId(httpServletRequest);
        LOGGER.info("Processing feedback-chat taskId={} subject={}", request.taskId(), subject);

        ChatTaskResult result = chatTaskService.process(request, subject, requestId);

        return new FeedbackChatResponse(
                result.reply(),
                result.provider(),
                result.model(),
                requestId,
                toUsageResponse(result.usage()),
                result.questions());
    }

    @PostMapping("/feedback-chat/improve-message")
    public ImproveMessageResponse improveMessage(
            @Valid @RequestBody ImproveMessageRequest request,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        String requestId = requestId(httpServletRequest);
        LOGGER.info("Improving feedback message subject={} route={}", subject, request.route());

        ImproveMessageResult result = chatTaskService.improveMessage(request, subject, requestId);
        return new ImproveMessageResponse(
                result.improvedMessage(),
                result.provider(),
                result.model(),
                requestId,
                toUsageResponse(result.usage()));
    }

    @GetMapping("/feedback-chat/usage-summary")
    public UsageSummaryResponse usageSummary(HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        String requestId = requestId(httpServletRequest);
        UsageSummaryData summaryData = chatTaskService.usageSummary(subject);

        UsageTotalsResponse totals = new UsageTotalsResponse(
                summaryData.totals().eventCount(),
                summaryData.totals().promptTokens(),
                summaryData.totals().completionTokens(),
                summaryData.totals().cachedPromptTokens(),
                summaryData.totals().totalTokens(),
                summaryData.totals().estimatedCostUsd());

        java.util.List<UsageByModelResponse> byModel = summaryData.byModel()
            .stream()
            .map(item -> new UsageByModelResponse(
                    item.model(),
                    item.eventCount(),
                    item.promptTokens(),
                    item.completionTokens(),
                    item.cachedPromptTokens(),
                    item.totalTokens(),
                    item.estimatedCostUsd()))
            .toList();

        return new UsageSummaryResponse(requestId, "USD", totals, byModel);
    }

    private UsageMetricsResponse toUsageResponse(mx.mrw.chattodolist.service.UsageMetrics usage) {
        return new UsageMetricsResponse(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.cachedPromptTokens(),
                usage.totalTokens(),
                usage.estimatedCostUsd());
    }

    private String requestId(HttpServletRequest httpServletRequest) {
        Object value = httpServletRequest.getAttribute(RequestContext.REQUEST_ID);
        return value == null ? null : value.toString();
    }

    private String subject(HttpServletRequest httpServletRequest) {
        AuthContext authContext = (AuthContext) httpServletRequest.getAttribute(RequestContext.AUTH_CONTEXT);
        return authContext == null ? "unknown" : authContext.subject();
    }
}
