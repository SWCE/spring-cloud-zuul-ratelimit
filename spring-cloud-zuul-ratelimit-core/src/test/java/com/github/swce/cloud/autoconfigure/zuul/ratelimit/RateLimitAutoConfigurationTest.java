package com.github.swce.cloud.autoconfigure.zuul.ratelimit;

import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimitKeyGenerator;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.RedisRateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jHazelcastRateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jIgniteRateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jInfinispanRateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jJCacheRateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.DefaultRateLimitKeyGenerator;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.StringToMatchTypeConverter;
import com.hazelcast.core.IMap;
import com.netflix.zuul.ZuulFilter;
import io.github.bucket4j.grid.GridBucketState;
import java.util.List;
import java.util.Map;
import org.apache.ignite.IgniteCache;
import org.infinispan.functional.FunctionalMap.ReadWriteMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * @author Marcos Barbero
 */
public class RateLimitAutoConfigurationTest {

    private AnnotationConfigWebApplicationContext context;

    @Before
    public void setUp() {
        System.setProperty(PREFIX + ".enabled", "true");
        this.context = new AnnotationConfigWebApplicationContext();
        this.context.setServletContext(new MockServletContext());
        this.context.register(Conf.class);
        this.context.register(RateLimitAutoConfiguration.class);
    }

    @After
    public void tearDown() {
        System.clearProperty(PREFIX + ".enabled");
        System.clearProperty(PREFIX + ".repository");
        System.clearProperty(PREFIX + ".defaultPolicyList");
        System.clearProperty(PREFIX + ".policyList");

        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    public void testStringToMatchTypeConverter() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_JCACHE");
        this.context.refresh();

        Assert.assertNotNull(this.context.getBean(StringToMatchTypeConverter.class));
    }

    @Test
    public void testZuulFilters() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_JCACHE");
        this.context.refresh();

        Map<String, ZuulFilter> zuulFilterMap = context.getBeansOfType(ZuulFilter.class);
        assertThat(zuulFilterMap.size()).isEqualTo(2);
        assertThat(zuulFilterMap.keySet()).containsExactly("rateLimiterPreFilter", "rateLimiterPostFilter");
    }

    @Test
    public void testRedisRateLimiterByProperty() {
        System.setProperty(PREFIX + ".repository", "REDIS");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimiter.class) instanceof RedisRateLimiter);
    }

    @Test
    public void testBucket4jJCacheRateLimiterByProperty() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_JCACHE");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimiter.class) instanceof Bucket4jJCacheRateLimiter);
    }

    @Test
    public void testBucket4jHazelcastRateLimiterByProperty() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_HAZELCAST");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimiter.class) instanceof Bucket4jHazelcastRateLimiter);
    }

    @Test
    public void testBucket4jIgniteRateLimiterByProperty() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_IGNITE");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimiter.class) instanceof Bucket4jIgniteRateLimiter);
    }

    @Test
    public void testBucket4jInfinispanRateLimiterByProperty() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_INFINISPAN");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimiter.class) instanceof Bucket4jInfinispanRateLimiter);
    }

    @Test
    public void testDefaultRateLimitKeyGenerator() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_JCACHE");
        this.context.refresh();

        Assert.assertTrue(this.context.getBean(RateLimitKeyGenerator.class) instanceof DefaultRateLimitKeyGenerator);
    }

    @Test
    public void testPolicyAdjuster() {
        System.setProperty(PREFIX + ".repository", "BUCKET4J_JCACHE");
        System.setProperty(PREFIX + ".defaultPolicyList[0].limit", "3");
        System.setProperty(PREFIX + ".defaultPolicyList[1].limit", "4");
        System.setProperty(PREFIX + ".policyList.a[0].limit", "5");
        System.setProperty(PREFIX + ".policyList.a[1].limit", "6");
        this.context.refresh();

        RateLimitProperties rateLimitProperties = this.context.getBean(RateLimitProperties.class);

        List<Policy> defaultPolicyList = rateLimitProperties.getDefaultPolicyList();
        assertThat(defaultPolicyList).hasSize(2);
        assertThat(defaultPolicyList.get(0).getLimit()).isEqualTo(3);
        assertThat(defaultPolicyList.get(1).getLimit()).isEqualTo(4);
        Map<String, List<Policy>> policyList = rateLimitProperties.getPolicyList();
        assertThat(policyList).hasSize(1);
        List<Policy> policyA = policyList.get("a");
        assertThat(policyA).hasSize(2);
        assertThat(policyA.get(0).getLimit()).isEqualTo(5);
        assertThat(policyA.get(1).getLimit()).isEqualTo(6);
    }

    @Configuration
    public static class Conf {

        @Bean
        public RouteLocator routeLocator() {
            return Mockito.mock(RouteLocator.class);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return Mockito.mock(ObjectMapper.class);
        }

        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }

        @Bean
        @Qualifier("RateLimit")
        @SuppressWarnings("unchecked")
        public IMap<String, GridBucketState> hazelcastMap() {
            return Mockito.mock(IMap.class);
        }

        @Bean
        @Qualifier("RateLimit")
        @SuppressWarnings("unchecked")
        public IgniteCache<String, GridBucketState> igniteCache() {
            return Mockito.mock(IgniteCache.class);
        }

        @Bean
        @Qualifier("RateLimit")
        @SuppressWarnings("unchecked")
        public ReadWriteMap<String, GridBucketState> infinispanMap() {
            return Mockito.mock(ReadWriteMap.class);
        }
    }
}