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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class LinuxUtils {

    private static final Logger logger = LoggerFactory.getLogger(LinuxUtils.class);

    private static final String DISTRIBUTION_NAME = resolveDistributionName();

    private static final boolean UNSHARE_USEABLE = unshareUseable();

    private LinuxUtils() {}

    public static String getDistributionName() {
        return DISTRIBUTION_NAME;
    }

    public static boolean isUnshareUseable() { return UNSHARE_USEABLE; }

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

    private static boolean unshareUseable() {
        if (SystemUtils.IS_OS_LINUX) {
            int uid;
            try {
                Class<?> c = Class.forName("com.sun.security.auth.module.UnixSystem");
                Object o = c.getDeclaredConstructor().newInstance();
                Method method = c.getDeclaredMethod("getUid");
                uid = ((Number) method.invoke(o)).intValue();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                    NoSuchMethodException | InvocationTargetException e) {
                return false;
            }
            if (uid == 0) {
                final List<String> command = new ArrayList<>();
                command.addAll(Arrays.asList(
                        "unshare", "-U",
                        "id", "-u"
                ));
                final ProcessBuilder builder = new ProcessBuilder(command);
                final Process process;
                try {
                    process = builder.start();
                } catch (IOException e) {
                    return false;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    return false;
                }
                try {
                    if (process.exitValue() == 0 && br.readLine() != "0") {
                        return true;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
