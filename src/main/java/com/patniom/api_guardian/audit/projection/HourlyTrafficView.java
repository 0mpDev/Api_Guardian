package com.patniom.api_guardian.audit.projection;

public interface HourlyTrafficView {
    Integer getHour();
    Long getCount();
}
