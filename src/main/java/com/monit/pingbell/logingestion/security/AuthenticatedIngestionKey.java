package com.monit.pingbell.logingestion.security;

/** Principal set by {@link LogIngestionApiKeyAuthenticationFilter} for a request authenticated
 * via a log ingestion API key, as opposed to a human JWT ({@code AuthenticatedMember}). */
public record AuthenticatedIngestionKey(Long monitorId, Long tenantId) {
}
