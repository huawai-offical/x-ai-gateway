package com.prodigalgal.xaigateway.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "payment_order",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order_order_no", columnNames = "order_no")
        },
        indexes = {
                @Index(name = "idx_payment_order_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_payment_order_status_created", columnList = "status,created_at"),
                @Index(name = "idx_payment_order_provider_status_created", columnList = "provider,status,created_at")
        }
)
public class PaymentOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 96)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency;

    @Column(name = "token_credits", nullable = false)
    private long tokenCredits;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "provider_trade_no", length = 128)
    private String providerTradeNo;

    @Column(name = "provider_instance_code", length = 96)
    private String providerInstanceCode;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "checkout_method", length = 64)
    private String checkoutMethod;

    @Column(name = "provider_payload_json", columnDefinition = "text")
    private String providerPayloadJson;

    @Column(name = "checkout_expires_at", columnDefinition = "timestamp with time zone")
    private Instant checkoutExpiresAt;

    @Column(name = "refund_amount_minor", nullable = false)
    private long refundAmountMinor;

    @Column(name = "refunded_at", columnDefinition = "timestamp with time zone")
    private Instant refundedAt;

    @Column(name = "disputed_at", columnDefinition = "timestamp with time zone")
    private Instant disputedAt;

    @Column(name = "reconciled_at", columnDefinition = "timestamp with time zone")
    private Instant reconciledAt;

    @Column(name = "reconcile_status", length = 64)
    private String reconcileStatus;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "paid_at", columnDefinition = "timestamp with time zone")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getTokenCredits() {
        return tokenCredits;
    }

    public void setTokenCredits(long tokenCredits) {
        this.tokenCredits = tokenCredits;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProviderTradeNo() {
        return providerTradeNo;
    }

    public void setProviderTradeNo(String providerTradeNo) {
        this.providerTradeNo = providerTradeNo;
    }

    public String getProviderInstanceCode() {
        return providerInstanceCode;
    }

    public void setProviderInstanceCode(String providerInstanceCode) {
        this.providerInstanceCode = providerInstanceCode;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getCheckoutMethod() {
        return checkoutMethod;
    }

    public void setCheckoutMethod(String checkoutMethod) {
        this.checkoutMethod = checkoutMethod;
    }

    public String getProviderPayloadJson() {
        return providerPayloadJson;
    }

    public void setProviderPayloadJson(String providerPayloadJson) {
        this.providerPayloadJson = providerPayloadJson;
    }

    public Instant getCheckoutExpiresAt() {
        return checkoutExpiresAt;
    }

    public void setCheckoutExpiresAt(Instant checkoutExpiresAt) {
        this.checkoutExpiresAt = checkoutExpiresAt;
    }

    public long getRefundAmountMinor() {
        return refundAmountMinor;
    }

    public void setRefundAmountMinor(long refundAmountMinor) {
        this.refundAmountMinor = refundAmountMinor;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(Instant refundedAt) {
        this.refundedAt = refundedAt;
    }

    public Instant getDisputedAt() {
        return disputedAt;
    }

    public void setDisputedAt(Instant disputedAt) {
        this.disputedAt = disputedAt;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(Instant reconciledAt) {
        this.reconciledAt = reconciledAt;
    }

    public String getReconcileStatus() {
        return reconcileStatus;
    }

    public void setReconcileStatus(String reconcileStatus) {
        this.reconcileStatus = reconcileStatus;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
