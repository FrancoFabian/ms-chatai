package mx.mrw.chattodolist.service;

import java.util.List;

public record ChatTaskResult(
        String reply,
        String provider,
        String model,
        UsageMetrics usage,
        List<String> questions) {
}
