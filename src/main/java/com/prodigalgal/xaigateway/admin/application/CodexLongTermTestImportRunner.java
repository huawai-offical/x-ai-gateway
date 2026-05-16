package com.prodigalgal.xaigateway.admin.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "gateway.codex-test", name = "import-auth-json-path")
public class CodexLongTermTestImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CodexLongTermTestImportRunner.class);

    private final CodexLongTermTestImportService importService;
    private final OfficialAccountAdminService officialAccountAdminService;
    private final ConfigurableApplicationContext applicationContext;
    private final String authJsonPath;
    private final String poolName;
    private final boolean importOnly;
    private final boolean liveSmoke;

    public CodexLongTermTestImportRunner(
            CodexLongTermTestImportService importService,
            OfficialAccountAdminService officialAccountAdminService,
            ConfigurableApplicationContext applicationContext,
            @Value("${gateway.codex-test.import-auth-json-path}") String authJsonPath,
            @Value("${gateway.codex-test.pool-name:codex-long-term-test}") String poolName,
            @Value("${gateway.codex-test.import-only:false}") boolean importOnly,
            @Value("${gateway.codex-test.live-smoke:false}") boolean liveSmoke) {
        this.importService = importService;
        this.officialAccountAdminService = officialAccountAdminService;
        this.applicationContext = applicationContext;
        this.authJsonPath = authJsonPath;
        this.poolName = poolName;
        this.importOnly = importOnly;
        this.liveSmoke = liveSmoke;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String rawJson = Files.readString(Path.of(authJsonPath));
        CodexLongTermTestImportResult result = importService.importAuthJson(rawJson, poolName);
        log.info(
                "Codex 长期测试账号导入完成：status={}, accountId={}, poolId={}, externalAccountId={}, routeEligible={}, routeBlockReason={}, credentialFingerprint={}",
                result.status(),
                result.accountId(),
                result.poolId(),
                result.externalAccountId(),
                result.routeEligible(),
                result.routeBlockReason(),
                result.credentialFingerprint()
        );
        if (liveSmoke) {
            var smoke = officialAccountAdminService.codexResponsesSmoke(
                    result.accountId(),
                    new com.prodigalgal.xaigateway.admin.api.OfficialCodexResponsesSmokeRequest(
                            "gpt-5.4@low",
                            "x-ai-gateway codex long-term live smoke",
                            false,
                            null,
                            20
                    )
            );
            var keepalive = smoke.keepalive();
            log.info(
                    "Codex 长期测试真实 smoke 完成：status={}, classification={}, skippedReason={}, accountId={}, baseUrl={}, path={}, codexAppApi={}, httpStatus={}, upstreamRequestId={}, upstreamResponseId={}, durationMs={}, failureType={}, failureMessage={}, keepaliveHttpStatus={}, keepaliveFailureType={}, credentialFingerprint={}",
                    smoke.status(),
                    smoke.classification(),
                    smoke.skippedReason(),
                    smoke.accountId(),
                    smoke.baseUrl(),
                    smoke.path(),
                    smoke.codexAppApi(),
                    smoke.httpStatus(),
                    smoke.upstreamRequestId(),
                    smoke.upstreamResponseId(),
                    smoke.durationMs(),
                    smoke.failureType(),
                    smoke.failureMessage(),
                    keepalive == null ? null : keepalive.get("httpStatus"),
                    keepalive == null ? null : keepalive.get("failureType"),
                    smoke.credentialFingerprint()
            );
        }
        if (importOnly) {
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
