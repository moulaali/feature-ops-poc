package com.example.search.config;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.util.UnleashConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnleashConfigProperties.class)
public class UnleashClientConfig {

    private static final Logger log = LoggerFactory.getLogger(UnleashClientConfig.class);

    @Bean(destroyMethod = "shutdown")
    public Unleash unleash(UnleashConfigProperties properties) {
        log.info("Configuring Unleash SDK client: url={}, appName={}, refreshInterval={}s",
                properties.apiUrl(), properties.appName(), properties.fetchTogglesIntervalSeconds());

        UnleashConfig config = UnleashConfig.builder()
                .appName(properties.appName())
                .instanceId(properties.instanceId())
                .unleashAPI(properties.apiUrl())
                .apiKey(properties.apiKey())
                .fetchTogglesInterval(properties.fetchTogglesIntervalSeconds())
                .synchronousFetchOnInitialisation(false)
                .subscriber(new UnleashSubscriber() {
                    @Override
                    public void onReady(UnleashReady ready) {
                        log.info("Unleash client is ready and connected to server.");
                    }

                    @Override
                    public void togglesFetched(ClientFeaturesResponse toggleResponse) {
                        log.info("Unleash toggles polled/fetched. Status: {}", toggleResponse.getStatus());
                    }

                    @Override
                    public void onError(UnleashException unleashException) {
                        log.warn("Unleash client event error: {}", unleashException.getMessage());
                    }
                })
                .build();

        return new DefaultUnleash(config);
    }
}
