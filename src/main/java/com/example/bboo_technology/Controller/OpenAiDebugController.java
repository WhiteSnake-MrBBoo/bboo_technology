package com.example.bboo_technology.Controller;


import com.example.bboo_technology.Config.OpenAiConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 로컬 개발용 OpenAI 설정 디버그 엔드포인트.
 * ※ 실서비스 배포 전에 반드시 제거하거나 보호 필요.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/debug/openai")
public class OpenAiDebugController {

    private final OpenAiConfig openAiConfig;

    @GetMapping("/config")
    public Map<String, Object> debugConfig() {
        Map<String, Object> res = new HashMap<>();

        String apiKey = openAiConfig.getApiKey();
        boolean hasKey = apiKey != null && !apiKey.isBlank();

        res.put("hasKey", hasKey);
        res.put("keyLength", hasKey ? apiKey.length() : 0);
        res.put("baseUrl", openAiConfig.getBaseUrl());
        res.put("defaultModel", openAiConfig.getDefaultModel());
        res.put("summaryModel", openAiConfig.getSummaryModel());
        res.put("hostScriptModel", openAiConfig.getHostScriptModel());
        res.put("marketingPointsModel", openAiConfig.getMarketingPointsModel());

        // 절대 full key 반환 X
        return res;
    }
}
