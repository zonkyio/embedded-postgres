/*
 * Copyright 2025 Tomas Vanek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.postgres.embedded;

import io.zonky.test.db.postgres.util.ArchUtils;
import io.zonky.test.db.postgres.util.LinuxUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.lowerCase;

public class DefaultPostgresBinaryResolver implements PgBinaryResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPostgresBinaryResolver.class);

    public static final DefaultPostgresBinaryResolver INSTANCE = new DefaultPostgresBinaryResolver();

    private DefaultPostgresBinaryResolver() {}

    @Override
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        String architecture = ArchUtils.normalize(machineHardware);
        String distribution = LinuxUtils.getDistributionName();

        if (logger.isInfoEnabled())
            logger.info("Detected distribution: '{}'", Optional.ofNullable(distribution).orElse("Unknown"));

        if (distribution != null) {
            Resource resource = findPgBinary(normalize(format("postgres-%s-%s-%s.txz", system, architecture, distribution)));
            if (resource != null) {
                logger.info("Distribution specific postgres binaries found: '{}'", resource.getFilename());
                return resource.getInputStream();
            } else {
                logger.debug("Distribution specific postgres binaries not found");
            }
        }

        Resource resource = findPgBinary(normalize(format("postgres-%s-%s.txz", system, architecture)));
        if (resource != null) {
            logger.info("System specific postgres binaries found: '{}'", resource.getFilename());
            return resource.getInputStream();
        }

        if ((Strings.CS.equals(system, "Darwin") && Strings.CS.equals(machineHardware, "aarch64"))
                || (Strings.CS.equals(system, "Windows") && Strings.CS.equals(architecture, "arm_64"))) {
            resource = findPgBinary(normalize(format("postgres-%s-%s.txz", system, "x86_64")));
            if (resource != null) {
                logger.warn("No native binaries supporting ARM architecture found. " +
                        "Trying to use binaries for x64 architecture instead: '{}'. " +
                        "Make sure you have enabled emulation for this purpose. " +
                        "Note that performance may be degraded.", resource.getFilename());
                return resource.getInputStream();
            }
        }

        logger.error("No postgres binaries found, you need to add an appropriate maven dependency " +
                "that meets the following parameters - system: '{}', architecture: '{}' " +
                "[https://github.com/zonkyio/embedded-postgres#additional-architectures]", system, architecture);
        throw new IllegalStateException("Missing embedded postgres binaries");
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private static Resource findPgBinary(String resourceLocation) throws IOException {
        logger.trace("Searching for postgres binaries - location: '{}'", resourceLocation);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<URL> urls = Collections.list(classLoader.getResources(resourceLocation));

        if (urls.size() > 1) {
            logger.error("Detected multiple binaries of the same architecture: '{}'", urls);
            throw new IllegalStateException("Duplicate embedded postgres binaries");
        }
        if (urls.size() == 1) {
            return new Resource(urls.get(0));
        }

        return null;
    }

    private static String normalize(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return lowerCase(input.replace(' ', '_'));
    }

    private static class Resource {

        private final URL url;

        public Resource(URL url) {
            this.url = url;
        }

        public String getFilename() {
            return FilenameUtils.getName(url.getPath());
        }

        public InputStream getInputStream() throws IOException {
            URLConnection con = this.url.openConnection();
            try {
                return con.getInputStream();
            } catch (IOException ex) {
                // Close the HTTP connection (if applicable).
                if (con instanceof HttpURLConnection) {
                    ((HttpURLConnection) con).disconnect();
                }
                throw ex;
            }
        }
    }
}
