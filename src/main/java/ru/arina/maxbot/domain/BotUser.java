package ru.arina.maxbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bot_users")
public class BotUser {

    @Id
    private Long id;

    @Column(nullable = false, length = 255)
    private String displayName = "";

    @Column(length = 255)
    private String username;

    @Column(length = 255)
    private String fullName;

    @Column(length = 255)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ConversationState conversationState = ConversationState.IDLE;

    @Column(length = 255)
    private String draftFullName;

    @Column(length = 255)
    private String draftCompanyName;

    @Column(length = 255)
    private String draftProblemType;

    @Column(length = 1000)
    private String draftDescription;

    private Long activeTicketId;

    private Integer listPage = 0;

    @Column(length = 32)
    private String listMode = "NONE";

    private Instant createdAt;

    private Instant updatedAt;

    private Instant lastSeenAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public ConversationState getConversationState() {
        return conversationState;
    }

    public void setConversationState(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    public String getDraftFullName() {
        return draftFullName;
    }

    public void setDraftFullName(String draftFullName) {
        this.draftFullName = draftFullName;
    }

    public String getDraftCompanyName() {
        return draftCompanyName;
    }

    public void setDraftCompanyName(String draftCompanyName) {
        this.draftCompanyName = draftCompanyName;
    }

    public String getDraftProblemType() {
        return draftProblemType;
    }

    public void setDraftProblemType(String draftProblemType) {
        this.draftProblemType = draftProblemType;
    }

    public String getDraftDescription() {
        return draftDescription;
    }

    public void setDraftDescription(String draftDescription) {
        this.draftDescription = draftDescription;
    }

    public Long getActiveTicketId() {
        return activeTicketId;
    }

    public void setActiveTicketId(Long activeTicketId) {
        this.activeTicketId = activeTicketId;
    }

    public Integer getListPage() {
        return listPage;
    }

    public void setListPage(Integer listPage) {
        this.listPage = listPage;
    }

    public String getListMode() {
        return listMode;
    }

    public void setListMode(String listMode) {
        this.listMode = listMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isRegistered() {
        return fullName != null && !fullName.isBlank() && companyName != null && !companyName.isBlank();
    }

    public void clearDraft() {
        this.draftFullName = null;
        this.draftCompanyName = null;
        this.draftProblemType = null;
        this.draftDescription = null;
        this.activeTicketId = null;
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSeenAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
