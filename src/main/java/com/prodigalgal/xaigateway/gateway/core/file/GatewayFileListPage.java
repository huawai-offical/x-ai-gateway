package com.prodigalgal.xaigateway.gateway.core.file;

import java.util.List;

public record GatewayFileListPage(
        List<GatewayFileResponse> data,
        boolean hasMore,
        String firstId,
        String lastId
) {

    public GatewayFileListPage {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
