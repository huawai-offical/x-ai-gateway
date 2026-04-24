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
import java.time.Instant;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "gateway_user_balance_ledger",
        indexes = {
                @Index(name = "idx_user_balance_ledger_user_created", columnList = "user_id,created_at")
        }
)
@Comment("用户余额流水，追加写入。")
public class GatewayUserBalanceLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private GatewayUserEntity user;

    @Column(name = "delta_token_credits", nullable = false)
    private long deltaTokenCredits;

    @Column(name = "balance_after_token_credits", nullable = false)
    private long balanceAfterTokenCredits;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public GatewayUserEntity getUser() {
        return user;
    }

    public void setUser(GatewayUserEntity user) {
        this.user = user;
    }

    public long getDeltaTokenCredits() {
        return deltaTokenCredits;
    }

    public void setDeltaTokenCredits(long deltaTokenCredits) {
        this.deltaTokenCredits = deltaTokenCredits;
    }

    public long getBalanceAfterTokenCredits() {
        return balanceAfterTokenCredits;
    }

    public void setBalanceAfterTokenCredits(long balanceAfterTokenCredits) {
        this.balanceAfterTokenCredits = balanceAfterTokenCredits;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
