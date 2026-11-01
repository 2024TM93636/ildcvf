package com.ildcvf.llm.demo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/llm/demo/proposals")
public class LiveProposalDemoController {
    private final LiveProposalDemoService service;
    public LiveProposalDemoController(LiveProposalDemoService service) { this.service = service; }
    public record Request(String trafficEventId, String findingId) {}
    @PostMapping
    public LiveProposalDemoService.Response generate(@RequestBody Request request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body required");
        return service.generate(request.trafficEventId(), request.findingId());
    }
}

@Configuration
class GroqDemoConfiguration {
    @Bean
    GroqDemoClient groqDemoClient(@Value("${ildcvf.llm.demo.assets-root:../experiments/e6-llm-reliability}") String assetsRoot) {
        return new GroqDemoClient(Path.of(assetsRoot));
    }
}
