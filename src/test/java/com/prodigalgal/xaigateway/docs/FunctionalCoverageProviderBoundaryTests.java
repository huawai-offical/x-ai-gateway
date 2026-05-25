package com.prodigalgal.xaigateway.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalCoverageProviderBoundaryTests {

    private static final Pattern GENERIC_PROVIDER_FACT =
            Pattern.compile("\"provider\"\\s*:\\s*\"openai_compatible\"");

    @Test
    void shouldRejectGenericOpenAiCompatibleCorePromiseInSdkAndCoverageDocs() throws IOException {
        String sdkDocs = Files.readString(Path.of("docs/public-sdk-examples.md"));
        String coverageDocs = Files.readString(Path.of("docs/functional-service-api-coverage-matrix.md"));
        String coverageSource = Files.readString(Path.of("src/main/resources/functional-service-api-coverage-matrix.json"));

        assertFalse(sdkDocs.contains("OpenAI-compatible Generic"), "SDK 示例不能恢复 generic 模式标题。");
        assertFalse(coverageDocs.contains("OpenAI-compatible Generic"), "覆盖矩阵不能恢复 generic 核心承诺。");
        assertFalse(coverageSource.contains("OpenAI-compatible Generic"), "覆盖矩阵事实源不能恢复 generic 核心承诺。");
        assertFalse(GENERIC_PROVIDER_FACT.matcher(coverageSource).find(),
                "覆盖矩阵事实源不能把 provider=openai_compatible 作为默认核心事实。");

        assertTrue(sdkDocs.contains("provider-specific OpenAI-compatible native profile"),
                "SDK 示例必须明确 provider-specific native profile。");
        assertTrue(coverageDocs.contains("provider-specific OpenAI-compatible native profile"),
                "覆盖矩阵文档必须明确 provider-specific native profile。");
        assertTrue(coverageSource.contains("provider_specific_openai_compatible_profiles"),
                "覆盖矩阵事实源必须使用 provider-specific profile 表达。");
    }
}
