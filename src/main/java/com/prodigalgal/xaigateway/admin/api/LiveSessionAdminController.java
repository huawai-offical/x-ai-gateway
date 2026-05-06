package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.LiveSessionService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/live-sessions")
public class LiveSessionAdminController {

    private final LiveSessionService liveSessionService;

    public LiveSessionAdminController(LiveSessionService liveSessionService) {
        this.liveSessionService = liveSessionService;
    }

    @GetMapping
    public List<LiveSessionResponse> list() {
        return liveSessionService.list();
    }

    @PostMapping
    public LiveSessionResponse create(@RequestBody LiveSessionCreateRequest request) {
        return liveSessionService.create(request);
    }

    @PostMapping("/{sessionKey}/connect")
    public LiveSessionResponse connect(@PathVariable String sessionKey) {
        return liveSessionService.connect(sessionKey);
    }

    @PostMapping("/{sessionKey}/heartbeat")
    public LiveSessionResponse heartbeat(@PathVariable String sessionKey) {
        return liveSessionService.heartbeat(sessionKey);
    }

    @PostMapping("/{sessionKey}/runtime-events")
    public LiveSessionResponse sendRuntimeEvent(
            @PathVariable String sessionKey,
            @RequestBody LiveSessionRuntimeEventRequest request) {
        return liveSessionService.sendRuntimeEvent(sessionKey, request);
    }

    @PostMapping("/{sessionKey}/close")
    public LiveSessionResponse close(@PathVariable String sessionKey) {
        return liveSessionService.close(sessionKey);
    }

    @PostMapping("/resume/{resumeToken}")
    public LiveSessionResponse resume(@PathVariable String resumeToken) {
        return liveSessionService.resume(resumeToken);
    }

    @GetMapping("/{sessionKey}")
    public LiveSessionResponse get(@PathVariable String sessionKey) {
        return liveSessionService.get(sessionKey);
    }

    @GetMapping("/{sessionKey}/events")
    public List<LiveSessionEventResponse> events(
            @PathVariable String sessionKey,
            @RequestParam(required = false) Long afterEventId) {
        return liveSessionService.listEvents(sessionKey, afterEventId);
    }

    @PostMapping("/{sessionKey}/events")
    public LiveSessionEventResponse appendEvent(
            @PathVariable String sessionKey,
            @RequestBody LiveSessionEventRequest request) {
        return liveSessionService.appendEvent(sessionKey, request);
    }

    @GetMapping(value = "/{sessionKey}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String stream(
            @PathVariable String sessionKey,
            @RequestParam(required = false) Long afterEventId) {
        return liveSessionService.replaySse(sessionKey, afterEventId);
    }

    @GetMapping("/{sessionKey}/conformance")
    public LiveSessionConformanceResponse conformance(@PathVariable String sessionKey) {
        return liveSessionService.conformance(sessionKey);
    }
}
