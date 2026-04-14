package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import reactor.core.publisher.Flux;

public interface GatewayChatRuntime {

    default ExecutionBackend backend() {
        return ExecutionBackend.SPRING_AI;
    }

    boolean supports(CatalogCandidateView candidate);

    CanonicalResponse execute(GatewayChatRuntimeContext context);

    Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context);
}
