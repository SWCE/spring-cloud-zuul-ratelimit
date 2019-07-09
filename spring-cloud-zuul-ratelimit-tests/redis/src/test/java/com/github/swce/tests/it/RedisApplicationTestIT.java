package com.github.swce.tests.it;

import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_LIMIT;
import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_QUOTA;
import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_REMAINING;
import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_REMAINING_QUOTA;
import static com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_RESET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.repository.RedisRateLimiter;
import com.github.swce.tests.RedisApplication;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Marcos Barbero
 * @since 2017-06-27
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RedisApplicationTestIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext context;

    @Test
    public void testRedisRateLimiter() {
        RateLimiter rateLimiter = context.getBean(RateLimiter.class);
        assertTrue("RedisRateLimiter", rateLimiter instanceof RedisRateLimiter);
    }

    @Test
    public void testNotExceedingCapacityRequest() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceA", String.class);
        HttpHeaders headers = response.getHeaders();
        assertHeaders(headers, "rate-limit-application_serviceA_127.0.0.1", false, false);
        assertEquals(OK, response.getStatusCode());
    }

    @Test
    public void testExceedingCapacity() throws InterruptedException {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceB", String.class);
        HttpHeaders headers = response.getHeaders();
        String key = "rate-limit-application_serviceB_127.0.0.1";
        assertHeaders(headers, key, false, false);
        assertEquals(OK, response.getStatusCode());

        for (int i = 0; i < 2; i++) {
            response = this.restTemplate.getForEntity("/serviceB", String.class);
        }

        assertEquals(TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotEquals(RedisApplication.ServiceController.RESPONSE_BODY, response.getBody());

        TimeUnit.SECONDS.sleep(2);

        response = this.restTemplate.getForEntity("/serviceB", String.class);
        headers = response.getHeaders();
        assertHeaders(headers, key, false, false);
        assertEquals(OK, response.getStatusCode());
    }

    @Test
    public void testNoRateLimit() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceC", String.class);
        HttpHeaders headers = response.getHeaders();
        assertHeaders(headers, "rate-limit-application_serviceC", true, false);
        assertEquals(OK, response.getStatusCode());
    }

    @Test
    public void testMultipleUrls() {
        String randomPath = UUID.randomUUID().toString();

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                randomPath = UUID.randomUUID().toString();
            }

            ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceD/" + randomPath, String.class);
            HttpHeaders headers = response.getHeaders();
            assertHeaders(headers, "rate-limit-application_serviceD_serviceD_" + randomPath, false, false);
            assertEquals(OK, response.getStatusCode());
        }
    }

    @Test
    public void testExceedingQuotaCapacityRequest() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceE", String.class);
        HttpHeaders headers = response.getHeaders();
        String key = "rate-limit-application_serviceE_127.0.0.1";
        assertHeaders(headers, key, false, true);
        assertEquals(OK, response.getStatusCode());

        response = this.restTemplate.getForEntity("/serviceE", String.class);
        headers = response.getHeaders();
        assertHeaders(headers, key, false, true);
        assertEquals(TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    public void testUsingBreakOnMatchGeneralCaseWithCidr() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceF", String.class);
        HttpHeaders headers = response.getHeaders();
        String key = "rate-limit-application_serviceF_127.0.0.1_127.0.0.0_22";
        assertHeaders(headers, key, false, false);
        assertEquals(OK, response.getStatusCode());

        response = this.restTemplate.getForEntity("/serviceF", String.class);
        headers = response.getHeaders();
        assertHeaders(headers, key, false, false);
        assertEquals(OK, response.getStatusCode());
    }

    @Test
    public void testUsingBreakOnMatchNoMatchGeneralCaseWithCidr() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/serviceG", String.class);
        HttpHeaders headers = response.getHeaders();
        String key = "rate-limit-application_serviceG_127.0.0.1";
        assertHeaders(headers, key, false, false);
        assertEquals(OK, response.getStatusCode());

        response = this.restTemplate.getForEntity("/serviceG", String.class);
        headers = response.getHeaders();
        assertHeaders(headers, key, false, false);
        assertEquals(TOO_MANY_REQUESTS, response.getStatusCode());
    }

    private void assertHeaders(HttpHeaders headers, String key, boolean nullable, boolean quotaHeaders) {
        String quota = headers.getFirst(HEADER_QUOTA + key);
        String remainingQuota = headers.getFirst(HEADER_REMAINING_QUOTA + key);
        String limit = headers.getFirst(HEADER_LIMIT + key);
        String remaining = headers.getFirst(HEADER_REMAINING + key);
        String reset = headers.getFirst(HEADER_RESET + key);

        if (nullable) {
            if (quotaHeaders) {
                assertNull(quota);
                assertNull(remainingQuota);
            } else {
                assertNull(limit);
                assertNull(remaining);
            }
            assertNull(reset);
        } else {
            if (quotaHeaders) {
                assertNotNull(quota);
                assertNotNull(remainingQuota);
            } else {
                assertNotNull(limit);
                assertNotNull(remaining);
            }
            assertNotNull(reset);
        }
    }
}
