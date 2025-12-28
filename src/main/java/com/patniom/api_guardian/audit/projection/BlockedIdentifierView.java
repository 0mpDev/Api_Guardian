package com.patniom.api_guardian.audit.projection;

public interface BlockedIdentifierView {
    String getIdentifier();
    Long getCount();
}
