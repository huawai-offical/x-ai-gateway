package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import reactor.core.publisher.Flux;

public interface GatewayChatRuntime {

    boolean supports(CatalogCandidateView candidate);

    GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context);

    Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context);
}
