package com.prodigalgal.xaigateway.admin.application;

public interface LiveSessionRuntimeAdapter {

    String protocol();

    LiveSessionRuntimeConnectResult connect(LiveSessionRuntimeRequest request);

    LiveSessionRuntimeExchangeResult send(LiveSessionRuntimeRequest request, LiveSessionRuntimeMessage message);

    LiveSessionRuntimeExchangeResult heartbeat(LiveSessionRuntimeRequest request);

    LiveSessionRuntimeExchangeResult close(LiveSessionRuntimeRequest request);
}
