/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.swce.cloud.autoconfigure.zuul.ratelimit.filters;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;

import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimitKeyGenerator;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants;
import com.github.swce.cloud.autoconfigure.zuul.ratelimit.support.RateLimitExceededException;
import com.google.common.collect.Maps;
import com.netflix.zuul.context.RequestContext;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UrlPathHelper;

/**
 * @author Marcos Barbero
 * @author Michal Šváb
 * @author Liel Chayoun
 */
public class RateLimitPreFilter extends AbstractRateLimitFilter {

    private final RateLimitProperties properties;
    private final RateLimiter rateLimiter;
    private final RateLimitKeyGenerator rateLimitKeyGenerator;

    public RateLimitPreFilter(final RateLimitProperties properties, final RouteLocator routeLocator,
                              final UrlPathHelper urlPathHelper, final RateLimiter rateLimiter,
                              final RateLimitKeyGenerator rateLimitKeyGenerator, final RateLimitUtils rateLimitUtils) {
        super(properties, routeLocator, urlPathHelper, rateLimitUtils);
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.rateLimitKeyGenerator = rateLimitKeyGenerator;
    }

    @Override
    public String filterType() {
        return PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return properties.getPreFilterOrder();
    }

    @Override
    public Object run() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        final HttpServletResponse response = ctx.getResponse();
        final HttpServletRequest request = ctx.getRequest();
        final Route route = route(request);

        policy(route, request).forEach(policy -> {
            Map<String, String> responseHeaders = Maps.newHashMap();

            final String key = rateLimitKeyGenerator.key(request, route, policy);
            final Rate rate = rateLimiter.consume(policy, key, null);
            final String httpHeaderKey = key.replaceAll("[^A-Za-z0-9-.]", "_").replaceAll("__", "_");

            final Long limit = policy.getLimit();
            final Long remaining = rate.getRemaining();
            if (limit != null) {
                responseHeaders.put(RateLimitConstants.HEADER_LIMIT + httpHeaderKey, String.valueOf(limit));
                responseHeaders.put(RateLimitConstants.HEADER_REMAINING + httpHeaderKey,
                    String.valueOf(Math.max(remaining, 0)));
            }

            final Long quota = policy.getQuota();
            final Long remainingQuota = rate.getRemainingQuota();
            if (quota != null) {
                request.setAttribute(RateLimitConstants.REQUEST_START_TIME, System.currentTimeMillis());
                responseHeaders.put(RateLimitConstants.HEADER_QUOTA + httpHeaderKey, String.valueOf(quota));
                responseHeaders.put(RateLimitConstants.HEADER_REMAINING_QUOTA + httpHeaderKey,
                    String.valueOf(MILLISECONDS.toSeconds(Math.max(remainingQuota, 0))));
            }

            responseHeaders.put(RateLimitConstants.HEADER_RESET + httpHeaderKey, String.valueOf(rate.getReset()));

            if (properties.isAddResponseHeaders()) {
                for (Map.Entry<String, String> headersEntry : responseHeaders.entrySet()) {
                    response.setHeader(headersEntry.getKey(), headersEntry.getValue());
                }
            }

            if ((limit != null && remaining < 0) || (quota != null && remainingQuota < 0)) {
                ctx.setResponseStatusCode(HttpStatus.TOO_MANY_REQUESTS.value());
                ctx.put("rateLimitExceeded", "true");
                ctx.setSendZuulResponse(false);
                throw new RateLimitExceededException();
            }
        });

        return null;
    }
}
