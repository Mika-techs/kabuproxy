package de.mik.kabuproxy.config;

import lombok.AccessLevel;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Central access to all runtime settings. Every property can be set via env var, see microprofile-config.properties.
 */
@Getter
@Named("kabuConfig")
@ApplicationScoped
public class KabuConfig
{
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Getter(AccessLevel.NONE) @Inject @ConfigProperty(name = "kabu.cred.key") private Optional<String> credKey;
    @Inject @ConfigProperty(name = "kabu.dev.auth", defaultValue = "false") private boolean devAuth;

    @Getter(AccessLevel.NONE) @Inject @ConfigProperty(name = "kabu.oidc.provider.uri") private Optional<String> oidcProviderUri;
    @Getter(AccessLevel.NONE) @Inject @ConfigProperty(name = "kabu.oidc.client.id") private Optional<String> oidcClientId;
    @Getter(AccessLevel.NONE) @Inject @ConfigProperty(name = "kabu.oidc.client.secret") private Optional<String> oidcClientSecret;
    @Inject @ConfigProperty(name = "kabu.oidc.admin.group", defaultValue = "kabuproxy-admin") private String adminGroup;
    @Getter(AccessLevel.NONE) @Inject @ConfigProperty(name = "kabu.public.url") private Optional<String> publicUrl;

    @Inject @ConfigProperty(name = "kabu.crawl.enabled", defaultValue = "true") private boolean crawlEnabled;
    @Inject @ConfigProperty(name = "kabu.crawl.interval.minutes", defaultValue = "30") private int crawlIntervalMinutes;
    @Inject @ConfigProperty(name = "kabu.crawl.active.from", defaultValue = "06:00") private String crawlActiveFrom;
    @Inject @ConfigProperty(name = "kabu.crawl.active.to", defaultValue = "22:00") private String crawlActiveTo;
    @Inject @ConfigProperty(name = "kabu.crawl.request.delay.millis", defaultValue = "400") private long requestDelayMillis;
    @Inject @ConfigProperty(name = "kabu.refresh.cooldown.minutes", defaultValue = "5") private int refreshCooldownMinutes;
    @Inject @ConfigProperty(name = "kabu.digikabu.base.url", defaultValue = "https://digikabu.de") private String digikabuBaseUrl;

    public String getCredKey()
    {
        return credKey.orElse("");
    }

    public String getOidcProviderUri()
    {
        return oidcProviderUri.orElse("");
    }

    public String getOidcClientId()
    {
        return oidcClientId.orElse("");
    }

    public String getOidcClientSecret()
    {
        return oidcClientSecret.orElse("");
    }

    public String getPublicUrl()
    {
        return publicUrl.orElse("");
    }

    public Duration getCrawlInterval()
    {
        return Duration.ofMinutes(crawlIntervalMinutes);
    }

    public Duration getRefreshCooldown()
    {
        return Duration.ofMinutes(refreshCooldownMinutes);
    }

    public LocalTime getCrawlActiveFromTime()
    {
        return LocalTime.parse(crawlActiveFrom);
    }

    public LocalTime getCrawlActiveToTime()
    {
        return LocalTime.parse(crawlActiveTo);
    }

    /**
     * OIDC redirect URI. Behind a TLS-terminating reverse proxy the container only sees http, so KABU_PUBLIC_URL wins.
     */
    public String getOidcRedirectUri()
    {
        if (getPublicUrl().isBlank())
        {
            return "${baseURL}/callback";
        }
        return stripTrailingSlash(getPublicUrl()) + "/callback";
    }

    /**
     * Authentik end-session endpoint, derived from the issuer URL (…/application/o/&lt;slug&gt;/end-session/).
     */
    public String getOidcEndSessionUri()
    {
        if (getOidcProviderUri().isBlank())
        {
            return null;
        }
        return stripTrailingSlash(getOidcProviderUri()) + "/end-session/";
    }

    private static String stripTrailingSlash(String url)
    {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
