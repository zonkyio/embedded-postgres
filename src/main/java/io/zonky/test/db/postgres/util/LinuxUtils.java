/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zonky.test.db.postgres.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class LinuxUtils {

    private static final Logger logger = LoggerFactory.getLogger(LinuxUtils.class);

    private static final String DISTRIBUTION_NAME = resolveDistributionName();
    private static final boolean UNSHARE_AVAILABLE = unshareAvailable();

    private LinuxUtils() {}

    public static String getDistributionName() {
        return DISTRIBUTION_NAME;
    }

    public static boolean isUnshareAvailable() {
        return UNSHARE_AVAILABLE;
    }

    private static String resolveDistributionName() {
        if (!SystemUtils.IS_OS_LINUX) {
            return null;
        }

        try {
            Path target;
            try (InputStream source = LinuxUtils.class.getResourceAsStream("/sh/detect_linux_distribution.sh")) {
                target = Files.createTempFile("detect_linux_distribution_", ".sh");
                Files.copy(source, target, REPLACE_EXISTING);
            }

            ProcessBuilder builder = new ProcessBuilder();
            builder.command("sh", target.toFile().getAbsolutePath());

            Process process = builder.start();
            process.waitFor();

            if (process.exitValue() != 0) {
                throw new IOException("Execution of the script to detect the Linux distribution failed with error code: '" + process.exitValue() + "'");
            }

            String distributionName;
            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                distributionName = outputReader.readLine();
            }

            if (StringUtils.isBlank(distributionName)) {
                logger.warn("It's not possible to detect the name of the Linux distribution, the detection script returned empty output");
                return null;
            }

            if (distributionName.startsWith("Debian")) {
                distributionName = "Debian";
            }
            if (distributionName.equals("openSUSE project")) {
                distributionName = "openSUSE";
            }

            return distributionName;
        } catch (Exception e) {
            logger.error("It's not possible to detect the name of the Linux distribution", e);
            return null;
        }
    }

    private static boolean unshareAvailable() {
        if (!SystemUtils.IS_OS_LINUX) {
            return false;
        }

        try {
            Class<?> clazz = Class.forName("com.sun.security.auth.module.UnixSystem");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getDeclaredMethod("getUid");
            int uid = ((Number) method.invoke(instance)).intValue();

            if (uid != 0) {
                return false;
            }

            ProcessBuilder builder = new ProcessBuilder();
            builder.command("unshare", "-U", "id", "-u");

            Process process = builder.start();
            process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                if (process.exitValue() == 0 && !"0".equals(outputReader.readLine())) {
                    builder.command("unshare", "-U", "id", "-un");
                    Process nameprocess = builder.start();
                    nameprocess.waitFor();
                    if (nameprocess.exitValue() == 0) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
