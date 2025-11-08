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

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

public class ConnectionInfo {

    private final String dbName;
    private final int port;
    private final String user;
    private final Map<String, String> properties;

    @SuppressWarnings("unused")
    public ConnectionInfo(final String dbName, final int port, final String user) {
        this(dbName, port, user, emptyMap());
    }

    public ConnectionInfo(final String dbName, final int port, final String user, final Map<String, String> properties) {
        this.dbName = dbName;
        this.port = port;
        this.user = user;
        this.properties = new HashMap<>(properties);
    }

    public String getUser() {
        return user;
    }

    public String getDbName() {
        return dbName;
    }

    public int getPort() {
        return port;
    }

    public Map<String, String> getProperties() {
        return unmodifiableMap(properties);
    }
}
