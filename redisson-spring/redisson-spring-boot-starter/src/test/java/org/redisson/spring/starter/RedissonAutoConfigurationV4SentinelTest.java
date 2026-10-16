package org.redisson.spring.starter;

import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RedissonAutoConfigurationV4SentinelTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedissonAutoConfigurationV4.class))
            // Inspect the real client's configuration without opening Redis connections.
            .withBean(RedissonAutoConfigurationCustomizer.class,
                    () -> config -> config.setLazyInitialization(true))
            .withPropertyValues(
                    "spring.data.redis.sentinel.master=mymaster",
                    "spring.data.redis.sentinel.nodes=localhost:26379,localhost:26380",
                    "spring.data.redis.username=redis-user",
                    "spring.data.redis.password=redis-password",
                    "spring.data.redis.database=4");

    @Test
    void usesSentinelAuthenticationPropertiesWithoutConnectionDetails() {
        contextRunner.withPropertyValues(
                "spring.data.redis.sentinel.username=sentinel-user",
                "spring.data.redis.sentinel.password=sentinel-password")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(DataRedisConnectionDetails.class);
                    DataRedisProperties.Sentinel properties = context.getBean(DataRedisProperties.class).getSentinel();
                    assertThat(properties.getUsername()).isEqualTo("sentinel-user");
                    assertThat(properties.getPassword()).isEqualTo("sentinel-password");

                    Config config = context.getBean(RedissonClient.class).getConfig();
                    SentinelServersConfig sentinel = config.useSentinelServers();
                    assertThat(sentinel.getSentinelUsername()).isEqualTo("sentinel-user");
                    assertThat(sentinel.getSentinelPassword()).isEqualTo("sentinel-password");
                    assertThat(sentinel.getMasterName()).isEqualTo("mymaster");
                    assertThat(sentinel.getSentinelAddresses()).containsExactly(
                            "redis://localhost:26379", "redis://localhost:26380");
                    assertThat(sentinel.getDatabase()).isEqualTo(4);
                    assertThat(config.getUsername()).isEqualTo("redis-user");
                    assertThat(config.getPassword()).isEqualTo("redis-password");
                });
    }

    @Test
    void supportsPasswordOnlySentinelAuthentication() {
        contextRunner.withPropertyValues("spring.data.redis.sentinel.password=sentinel-password")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SentinelServersConfig sentinel = context.getBean(RedissonClient.class)
                            .getConfig().useSentinelServers();
                    assertThat(sentinel.getSentinelUsername()).isNull();
                    assertThat(sentinel.getSentinelPassword()).isEqualTo("sentinel-password");
                });
    }

    @Test
    void leavesSentinelAuthenticationUnsetWhenPropertiesAreAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SentinelServersConfig sentinel = context.getBean(RedissonClient.class)
                    .getConfig().useSentinelServers();
            assertThat(sentinel.getSentinelUsername()).isNull();
            assertThat(sentinel.getSentinelPassword()).isNull();
        });
    }

    @Test
    void connectionDetailsTakePrecedenceOverSentinelProperties() {
        DataRedisConnectionDetails details = mock(DataRedisConnectionDetails.class,
                withSettings().mockMaker(MockMakers.PROXY));
        DataRedisConnectionDetails.Sentinel sentinelDetails = mock(DataRedisConnectionDetails.Sentinel.class,
                withSettings().mockMaker(MockMakers.PROXY));
        when(details.getUsername()).thenReturn("details-redis-user");
        when(details.getPassword()).thenReturn("details-redis-password");
        when(details.getSentinel()).thenReturn(sentinelDetails);
        when(sentinelDetails.getMaster()).thenReturn("details-master");
        when(sentinelDetails.getNodes()).thenReturn(Arrays.asList(
                new DataRedisConnectionDetails.Node("sentinel.example", 26381)));
        when(sentinelDetails.getDatabase()).thenReturn(5);
        when(sentinelDetails.getUsername()).thenReturn("details-sentinel-user");
        when(sentinelDetails.getPassword()).thenReturn("details-sentinel-password");

        contextRunner.withBean(DataRedisConnectionDetails.class, () -> details)
                .withPropertyValues(
                        "spring.data.redis.sentinel.username=property-sentinel-user",
                        "spring.data.redis.sentinel.password=property-sentinel-password")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Config config = context.getBean(RedissonClient.class).getConfig();
                    SentinelServersConfig sentinel = config.useSentinelServers();
                    assertThat(sentinel.getSentinelUsername()).isEqualTo("details-sentinel-user");
                    assertThat(sentinel.getSentinelPassword()).isEqualTo("details-sentinel-password");
                    assertThat(sentinel.getMasterName()).isEqualTo("details-master");
                    assertThat(sentinel.getSentinelAddresses()).containsExactly("redis://sentinel.example:26381");
                    assertThat(sentinel.getDatabase()).isEqualTo(5);
                    assertThat(config.getUsername()).isEqualTo("details-redis-user");
                    assertThat(config.getPassword()).isEqualTo("details-redis-password");
                });
    }
}
