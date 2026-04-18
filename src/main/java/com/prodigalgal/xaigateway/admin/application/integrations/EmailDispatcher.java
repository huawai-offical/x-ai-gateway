package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.infra.persistence.entity.NotificationChannelEntity;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatcher {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    public EmailDispatcher(ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        this.javaMailSenderProvider = javaMailSenderProvider;
    }

    public OutboundDispatchResult dispatch(NotificationChannelEntity channel, OutboundEventEnvelope envelope) {
        JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
        if (sender == null) {
            return new OutboundDispatchResult(false, null, null, "当前环境未配置 JavaMailSender。");
        }
        String emailTo = Optional.ofNullable(channel.getEmailTo()).map(String::trim).orElse(null);
        if (emailTo == null || emailTo.isBlank()) {
            return new OutboundDispatchResult(false, null, null, "当前 EMAIL channel 未配置收件人。");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailTo.split("\\s*,\\s*"));
            message.setSubject("[" + envelope.severity() + "] " + envelope.eventType() + " - " + envelope.summary());
            message.setText(buildBody(envelope));
            sender.send(message);
            return new OutboundDispatchResult(true, 202, "sent to " + emailTo, null);
        } catch (Exception exception) {
            return new OutboundDispatchResult(false, null, null, exception.getMessage());
        }
    }

    private String buildBody(OutboundEventEnvelope envelope) {
        StringBuilder body = new StringBuilder();
        body.append("summary: ").append(envelope.summary()).append('\n');
        body.append("eventType: ").append(envelope.eventType()).append('\n');
        body.append("severity: ").append(envelope.severity()).append('\n');
        body.append("entity: ").append(envelope.entityType()).append('/').append(envelope.entityRef()).append('\n');
        body.append("occurredAt: ").append(envelope.occurredAt()).append('\n');
        if (envelope.traceUrl() != null) {
            body.append("traceUrl: ").append(envelope.traceUrl()).append('\n');
        }
        if (envelope.runbookUrl() != null) {
            body.append("runbookUrl: ").append(envelope.runbookUrl()).append('\n');
        }
        if (envelope.details() != null && !envelope.details().isEmpty()) {
            body.append("details: ").append(envelope.details()).append('\n');
        }
        return body.toString();
    }
}
