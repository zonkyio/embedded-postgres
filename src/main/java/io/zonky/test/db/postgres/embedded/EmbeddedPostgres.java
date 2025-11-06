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
package io.zonky.test.db.postgres.embedded;

import io.zonky.test.db.postgres.util.LinuxUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.unmodifiableMap;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // "postgres"
public class EmbeddedPostgres implements Closeable
{
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);
    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_S = "5";
    private static final String PG_SUPERUSER = "postgres";
    private static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);
    private static final String LOCK_FILE_NAME = "epg-lock";

    private final File pgDir;

    private final Duration pgStartupWait;
    private final File dataDirectory, lockFile;
    private final UUID instanceId = UUID.randomUUID();
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<String, String> postgresConfig;
    private final Map<String, String> localeConfig;
    private final Map<String, String> connectConfig;

    private volatile FileOutputStream lockStream;
    private volatile FileLock lock;
    private final boolean cleanDataDirectory;

    private final ProcessBuilder.Redirect errorRedirector;
    private final ProcessBuilder.Redirect outputRedirector;
    private final Process process;

    EmbeddedPostgres(File parentDirectory, File dataDirectory, boolean cleanDataDirectory,
        Map<String, String> postgresConfig, Map<String, String> localeConfig, int port, Map<String, String> connectConfig,
        PgBinaryResolver pgBinaryResolver, ProcessBuilder.Redirect errorRedirector, ProcessBuilder.Redirect outputRedirector) throws IOException
    {
        this(parentDirectory, dataDirectory, cleanDataDirectory, postgresConfig, localeConfig, port, connectConfig,
                pgBinaryResolver, errorRedirector, outputRedirector, DEFAULT_PG_STARTUP_WAIT, null, null);
    }

    EmbeddedPostgres(File parentDirectory, File dataDirectory, boolean cleanDataDirectory,
                     Map<String, String> postgresConfig, Map<String, String> localeConfig, int port, Map<String, String> connectConfig,
                     PgBinaryResolver pgBinaryResolver, ProcessBuilder.Redirect errorRedirector,
                     ProcessBuilder.Redirect outputRedirector, Duration pgStartupWait,
                     File overrideWorkingDirectory, Consumer<File> dataDirectoryCustomizer) throws IOException
    {
        this.cleanDataDirectory = cleanDataDirectory;
        this.postgresConfig = new HashMap<>(postgresConfig);
        this.localeConfig = new HashMap<>(localeConfig);
        this.connectConfig = new HashMap<>(connectConfig);
        this.port = port;
        this.pgDir = prepareBinaries(pgBinaryResolver, overrideWorkingDirectory);
        this.errorRedirector = errorRedirector;
        this.outputRedirector = outputRedirector;
        this.pgStartupWait = pgStartupWait;
        Objects.requireNonNull(this.pgStartupWait, "Wait time cannot be null");

        if (parentDirectory != null) {
            mkdirs(parentDirectory);
            cleanOldDataDirectories(parentDirectory);
            if (dataDirectory != null) {
                this.dataDirectory = dataDirectory;
            } else {
                this.dataDirectory = new File(parentDirectory, instanceId.toString());
            }
        } else {
            this.dataDirectory = dataDirectory;
        }
        if (this.dataDirectory == null) {
            throw new IllegalArgumentException("no data directory");
        }
        LOG.trace("{} postgres data directory is {}", instanceId, this.dataDirectory);
        mkdirs(this.dataDirectory);

        lockFile = new File(this.dataDirectory, LOCK_FILE_NAME);

        if (cleanDataDirectory || !new File(dataDirectory, "postgresql.conf").exists()) {
            initdb();
        }

        lock();

        if (dataDirectoryCustomizer != null) {
            dataDirectoryCustomizer.accept(dataDirectory);
        }

        this.process = startPostmaster();
    }

    public Process getProcess() {
    	return this.process;
    }

    public DataSource getTemplateDatabase()
    {
        return getDatabase("postgres", "template1");
    }

    public DataSource getTemplateDatabase(Map<String, String> properties)
    {
        return getDatabase("postgres", "template1", properties);
    }

    public DataSource getPostgresDatabase() {
        return getDatabase("postgres", "postgres");
    }

    public DataSource getPostgresDatabase(Map<String, String> properties)
    {
        return getDatabase("postgres", "postgres", properties);
    }

    public DataSource getDatabase(String userName, String dbName) {
        return getDatabase(userName, dbName, connectConfig);
    }

    public DataSource getDatabase(String userName, String dbName, Map<String, String> properties)
    {
        final PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("localhost");
        ds.setPortNumber(port);
        ds.setDatabaseName(dbName);
        ds.setUser(userName);

        properties.forEach((propertyKey, propertyValue) -> {
            try {
                ds.setProperty(propertyKey, propertyValue);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return ds;
    }

    public String getJdbcUrl(String userName, String dbName)
    {
        return String.format(JDBC_FORMAT, port, dbName, userName);
    }

    public int getPort()
    {
        return port;
    }

    Map<String, String> getConnectConfig()
    {
        return unmodifiableMap(connectConfig);
    }

    private static int detectPort() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            while (!socket.isBound()) {
                Thread.sleep(50);
            }
            return socket.getLocalPort();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread interrupted", e);
        }
    }

    private void lock() throws IOException
    {
        lockStream = new FileOutputStream(lockFile);
        if ((lock = lockStream.getChannel().tryLock()) == null) {
            throw new IllegalStateException("could not lock " + lockFile);
        }
    }

    private void initdb()
    {
        final StopWatch watch = new StopWatch();
        watch.start();
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(
                "-A", "trust", "-U", PG_SUPERUSER,
                "-D", dataDirectory.getPath(), "-E", "UTF-8"));
        args.addAll(createLocaleOptions());
        system(INIT_DB, args);
        LOG.info("{} initdb completed in {}", instanceId, watch);
    }

    private Process startPostmaster() throws IOException
    {
        final StopWatch watch = new StopWatch();
        watch.start();
        if (started.getAndSet(true)) {
            throw new IllegalStateException("Postmaster already started");
        }

        final List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(
                "-D", dataDirectory.getPath(),
                "-o", String.join(" ", createInitOptions()),
                "-w", "start"
        ));

        final ProcessBuilder builder = new ProcessBuilder();
        PG_CTL.applyTo(builder, args);

        builder.redirectErrorStream(true);
        builder.redirectError(errorRedirector);
        builder.redirectOutput(outputRedirector);
        final Process postmaster = builder.start();

        if (outputRedirector.type() == ProcessBuilder.Redirect.Type.PIPE) {
            ProcessOutputLogger.logOutput(LOG, postmaster, POSTGRES.processName());
        }

        LOG.info("{} postmaster started as {} on port {}.  Waiting up to {} for server startup to finish.", instanceId, postmaster.toString(), port, pgStartupWait);

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        waitForServerStartup(watch);
        return postmaster;
    }

    private List<String> createInitOptions()
    {
        final List<String> initOptions = new ArrayList<>();
        initOptions.addAll(Arrays.asList(
                "-p", Integer.toString(port),
                "-F"));

        for (final Entry<String, String> config : postgresConfig.entrySet())
        {
            initOptions.add("-c");
            initOptions.add(config.getKey() + "=" + config.getValue());
        }

        return initOptions;
    }

    private List<String> createLocaleOptions()
    {
        final List<String> localeOptions = new ArrayList<>();
        for (final Entry<String, String> config : localeConfig.entrySet()) {
            localeOptions.add(String.format("--%s=%s", config.getKey(), config.getValue()));
        }
        return localeOptions;
    }

    private void waitForServerStartup(StopWatch watch) throws IOException
    {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(pgStartupWait.toMillis(), TimeUnit.MILLISECONDS);
        while (System.nanoTime() - start < maxWaitNs) {
            try {
                verifyReady();
                LOG.info("{} postmaster startup finished in {}", instanceId, watch);
                return;
            } catch (final SQLException e) {
                lastCause = e;
                LOG.trace("While waiting for server startup", e);
            }

            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IOException("Gave up waiting for server to start after " + pgStartupWait.toMillis() + "ms", lastCause);
    }

    private void verifyReady() throws SQLException
    {
        final InetAddress localhost = InetAddress.getLoopbackAddress();
        try (Socket sock = new Socket()) {
            sock.setSoTimeout((int) Duration.ofMillis(500).toMillis());
            sock.connect(new InetSocketAddress(localhost, port), (int) Duration.ofMillis(500).toMillis());
        } catch (final IOException e) {
            throw new SQLException("connect failed", e);
        }
        try (Connection c = getPostgresDatabase().getConnection() ;
                Statement s = c.createStatement() ;
                ResultSet rs = s.executeQuery("SELECT 1"))
        {
            if (!rs.next()) {
                throw new IllegalStateException("expecting single row");
            }
            if (1 != rs.getInt(1)) {
                throw new IllegalStateException("expecting 1");
            }
            if (rs.next()) {
                throw new IllegalStateException("expecting single row");
            }
        }
    }

    private Thread newCloserThread()
    {
        final Thread closeThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    EmbeddedPostgres.this.close();
                }
                catch (IOException ex) {
                    LOG.error("Unexpected IOException from Closeables.close", ex);
                }
            }
        });
        closeThread.setName("postgres-" + instanceId + "-closer");
        return closeThread;
    }

    @Override
    public void close() throws IOException
    {
        if (closed.getAndSet(true)) {
            return;
        }
        final StopWatch watch = new StopWatch();
        watch.start();
        try {
            pgCtl(dataDirectory, "stop");
            LOG.info("{} shut down postmaster in {}", instanceId, watch);
        } catch (final Exception e) {
            LOG.error("Could not stop postmaster " + instanceId, e);
        }
        if (lock != null) {
            lock.release();
        }
        try {
            lockStream.close();
        } catch (IOException e) {
            LOG.error("while closing lockStream", e);
        }

        if (cleanDataDirectory && System.getProperty("ot.epg.no-cleanup") == null) {
            try {
                FileUtils.deleteDirectory(dataDirectory);
            } catch (IOException e) {
                LOG.error("Could not clean up directory {}", dataDirectory.getAbsolutePath());
            }
        } else {
            LOG.info("Did not clean up directory {}", dataDirectory.getAbsolutePath());
        }
    }

    private void pgCtl(File dir, String action)
    {
        final List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(
                "-D", dir.getPath(), action,
                "-m", PG_STOP_MODE, "-t",
                PG_STOP_WAIT_S, "-w"
        ));
        system(PG_CTL, args);
    }

    private void cleanOldDataDirectories(File parentDirectory)
    {
        final File[] children = parentDirectory.listFiles();
        if (children == null) {
            return;
        }
        for (final File dir : children)
        {
            if (!dir.isDirectory()) {
                continue;
            }

            final File lockFile = new File(dir, LOCK_FILE_NAME);
            final boolean isTooNew = System.currentTimeMillis() - lockFile.lastModified() < 10 * 60 * 1000;
            if (!lockFile.exists() || isTooNew) {
                continue;
            }
            try (FileOutputStream fos = new FileOutputStream(lockFile);
                 FileLock lock = fos.getChannel().tryLock()) {
                if (lock != null) {
                    LOG.info("Found stale data directory {}", dir);
                    if (new File(dir, "postmaster.pid").exists()) {
                        try {
                            pgCtl(dir, "stop");
                            LOG.info("Shut down orphaned postmaster!");
                        } catch (Exception e) {
                            if (LOG.isDebugEnabled()) {
                                LOG.warn("Failed to stop postmaster " + dir, e);
                            } else {
                                LOG.warn("Failed to stop postmaster " + dir + ": " + e.getMessage());
                            }
                        }
                    }
                    FileUtils.deleteDirectory(dir);
                }
            } catch (final OverlappingFileLockException e) {
                // The directory belongs to another instance in this VM.
                LOG.trace("While cleaning old data directories", e);
            } catch (final Exception e) {
                LOG.warn("While cleaning old data directories", e);
            }
        }
    }

    private static File getWorkingDirectory()
    {
        final File tempWorkingDirectory = new File(System.getProperty("java.io.tmpdir"), "embedded-pg");
        return new File(System.getProperty("ot.epg.working-dir", tempWorkingDirectory.getPath()));
    }

    public static EmbeddedPostgres start() throws IOException
    {
        return builder().start();
    }

    public static EmbeddedPostgres.Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private final File parentDirectory = getWorkingDirectory();
        private File overrideWorkingDirectory;
        private File builderDataDirectory;
        private final Map<String, String> config = new HashMap<>();
        private final Map<String, String> localeConfig = new HashMap<>();
        private boolean builderCleanDataDirectory = true;
        private int builderPort = 0;
        private final Map<String, String> connectConfig = new HashMap<>();
        private PgBinaryResolver pgBinaryResolver = DefaultPostgresBinaryResolver.INSTANCE;
        private Duration pgStartupWait = DEFAULT_PG_STARTUP_WAIT;
        private Consumer<File> dataDirectoryCustomizer;

        private ProcessBuilder.Redirect errRedirector = ProcessBuilder.Redirect.PIPE;
        private ProcessBuilder.Redirect outRedirector = ProcessBuilder.Redirect.PIPE;

        Builder() {
            config.put("timezone", "UTC");
            config.put("synchronous_commit", "off");
            config.put("max_connections", "300");
        }

        public Builder setPGStartupWait(Duration pgStartupWait) {
            Objects.requireNonNull(pgStartupWait);
            if (pgStartupWait.isNegative()) {
               throw new IllegalArgumentException("Negative durations are not permitted.");
            }

            this.pgStartupWait = pgStartupWait;
            return this;
        }
        public Builder setCleanDataDirectory(boolean cleanDataDirectory) {
            builderCleanDataDirectory = cleanDataDirectory;
            return this;
        }

        public Builder setDataDirectory(Path path) {
            return setDataDirectory(path.toFile());
        }

        public Builder setDataDirectory(File directory) {
            builderDataDirectory = directory;
            return this;
        }

        public Builder setDataDirectory(String path) {
            return setDataDirectory(new File(path));
        }

        public Builder setServerConfig(String key, String value) {
            config.put(key, value);
            return this;
        }

        public Builder setLocaleConfig(String key, String value) {
            localeConfig.put(key, value);
            return this;
        }

        public Builder setConnectConfig(String key, String value) {
            connectConfig.put(key, value);
            return this;
        }

        public Builder setOverrideWorkingDirectory(File workingDirectory) {
            overrideWorkingDirectory = workingDirectory;
            return this;
        }

        public Builder setPort(int port) {
            builderPort = port;
            return this;
        }

        public Builder setErrorRedirector(ProcessBuilder.Redirect errRedirector) {
            this.errRedirector = errRedirector;
            return this;
        }

        public Builder setOutputRedirector(ProcessBuilder.Redirect outRedirector)
        {
            this.outRedirector = outRedirector;
            return this;
        }

        public Builder setPgBinaryResolver(PgBinaryResolver pgBinaryResolver) {
            this.pgBinaryResolver = pgBinaryResolver;
            return this;
        }

        public Builder setDataDirectoryCustomizer(final Consumer<File> dataDirectoryCustomizer) {
            this.dataDirectoryCustomizer = dataDirectoryCustomizer;
            return this;
        }

        public EmbeddedPostgres start() throws IOException {
            if (builderPort == 0)
            {
                builderPort = detectPort();
            }
            if (builderDataDirectory == null) {
                builderDataDirectory = Files.createTempDirectory("epg").toFile();
            }
            return new EmbeddedPostgres(parentDirectory, builderDataDirectory, builderCleanDataDirectory, config,
                    localeConfig, builderPort, connectConfig, pgBinaryResolver, errRedirector, outRedirector,
                    pgStartupWait, overrideWorkingDirectory, dataDirectoryCustomizer);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Builder builder = (Builder) o;
            return builderCleanDataDirectory == builder.builderCleanDataDirectory &&
                    builderPort == builder.builderPort &&
                    Objects.equals(parentDirectory, builder.parentDirectory) &&
                    Objects.equals(builderDataDirectory, builder.builderDataDirectory) &&
                    Objects.equals(config, builder.config) &&
                    Objects.equals(localeConfig, builder.localeConfig) &&
                    Objects.equals(connectConfig, builder.connectConfig) &&
                    Objects.equals(pgBinaryResolver, builder.pgBinaryResolver) &&
                    Objects.equals(pgStartupWait, builder.pgStartupWait) &&
                    Objects.equals(errRedirector, builder.errRedirector) &&
                    Objects.equals(outRedirector, builder.outRedirector) &&
                    Objects.equals(dataDirectoryCustomizer != null ? dataDirectoryCustomizer.getClass() : null,
                                   builder.dataDirectoryCustomizer != null ? builder.dataDirectoryCustomizer.getClass() : null);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentDirectory, builderDataDirectory, config, localeConfig, builderCleanDataDirectory, builderPort, connectConfig, pgBinaryResolver, pgStartupWait, errRedirector, outRedirector);
        }
    }

    private void system(Command command, List<String> args)
    {
        try {
            final ProcessBuilder builder = new ProcessBuilder();

            command.applyTo(builder, args);
            builder.redirectErrorStream(true);
            builder.redirectError(errorRedirector);
            builder.redirectOutput(outputRedirector);

            final Process process = builder.start();

            if (outputRedirector.type() == ProcessBuilder.Redirect.Type.PIPE) {
                ProcessOutputLogger.logOutput(LOG, process, command.processName());
            }
            if (0 != process.waitFor()) {
                throw new IllegalStateException(String.format("Process %s failed", builder.command()));
            }
        } catch (final RuntimeException e) { // NOPMD
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void mkdirs(File dir)
    {
        if (!dir.mkdirs() && !(dir.isDirectory() && dir.exists())) {
            throw new IllegalStateException("could not create " + dir);
        }
    }

    private static final Lock PREPARE_BINARIES_LOCK = new ReentrantLock();
    private static final Map<PgBinaryResolver, File> PREPARE_BINARIES = new HashMap<>();

    /**
     * Get current operating system string. The string is used in the appropriate postgres binary name.
     *
     * @return Current operating system string.
     */
    private static String getOS()
    {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "Windows";
        }
        if (SystemUtils.IS_OS_MAC_OSX) {
            return "Darwin";
        }
        if (SystemUtils.IS_OS_LINUX) {
            return "Linux";
        }
        throw new UnsupportedOperationException("Unknown OS " + SystemUtils.OS_NAME);
    }

    /**
     * Get the machine architecture string. The string is used in the appropriate postgres binary name.
     *
     * @return Current machine architecture string.
     */
    private static String getArchitecture()
    {
        return "amd64".equals(SystemUtils.OS_ARCH) ? "x86_64" : SystemUtils.OS_ARCH;
    }

    /**
     * Unpack archive compressed by tar with xz compression. By default system tar is used (faster). If not found, then the
     * java implementation takes place.
     *
     * @param stream    A stream with the postgres binaries.
     * @param targetDir The directory to extract the content to.
     */
    private static void extractTxz(InputStream stream, File targetDir) throws IOException {
        try (
                XZInputStream xzIn = new XZInputStream(stream);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)
        ) {
            final Set<File> dirsToUpdate = new HashSet<>();
            final Phaser phaser = new Phaser(1);
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir, individualFile);

                if (fsObject.exists()) {
                    fsObject.setLastModified(System.currentTimeMillis());

                    File parentDir = fsObject.getParentFile();
                    while (parentDir != null) {
                        dirsToUpdate.add(parentDir);
                        if (targetDir.equals(parentDir)) {
                            break;
                        }
                        parentDir = parentDir.getParentFile();
                    }
                } else if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsObject.toPath(), target);
                } else if (entry.isFile()) {
                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    if (read == -1) {
                        throw new IllegalStateException("could not read " + individualFile);
                    }
                    mkdirs(fsObject.getParentFile());

                    final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(fsObject.toPath(), CREATE, WRITE);
                    final ByteBuffer buffer = ByteBuffer.wrap(content);

                    phaser.register();
                    fileChannel.write(buffer, 0, fileChannel, new CompletionHandler<Integer, Channel>() {
                        @Override
                        public void completed(Integer written, Channel channel) {
                            closeChannel(channel);
                        }

                        @Override
                        public void failed(Throwable error, Channel channel) {
                            LOG.error("Could not write file {}", fsObject.getAbsolutePath(), error);
                            closeChannel(channel);
                        }

                        private void closeChannel(Channel channel) {
                            try {
                                channel.close();
                            } catch (IOException e) {
                                LOG.error("Unexpected error while closing the channel", e);
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        }
                    });
                } else if (entry.isDirectory()) {
                    mkdirs(fsObject);
                } else {
                    throw new UnsupportedOperationException(
                            String.format("Unsupported entry found: %s", individualFile)
                    );
                }

                if (individualFile.startsWith("bin/") || individualFile.startsWith("./bin/")) {
                    fsObject.setExecutable(true, false);
                }
            }

            for (File updatedDir : dirsToUpdate) {
                updatedDir.setLastModified(System.currentTimeMillis());
            }

            phaser.arriveAndAwaitAdvance();
        }
    }

    private static File prepareBinaries(PgBinaryResolver pgBinaryResolver, File overrideWorkingDirectory)
    {
        PREPARE_BINARIES_LOCK.lock();
        try {
            if (PREPARE_BINARIES.containsKey(pgBinaryResolver) && PREPARE_BINARIES.get(pgBinaryResolver).exists()) {
                return PREPARE_BINARIES.get(pgBinaryResolver);
            }

            final String system = getOS();
            final String machineHardware = getArchitecture();

            LOG.info("Detected a {} {} system", system, machineHardware);
            File pgDir;
            final InputStream pgBinary;
            try {
                pgBinary = pgBinaryResolver.getPgBinary(system, machineHardware);
            } catch (final IOException e) {
                throw new ExceptionInInitializerError(e);
            }

            if (pgBinary == null) {
                throw new IllegalStateException("No Postgres binary found for " + system + " / " + machineHardware);
            }

            try (DigestInputStream pgArchiveData = new DigestInputStream(pgBinary, MessageDigest.getInstance("MD5"));
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                IOUtils.copy(pgArchiveData, baos);
                pgArchiveData.close();

                String pgDigest = Hex.encodeHexString(pgArchiveData.getMessageDigest().digest());
                File workingDirectory = Optional.ofNullable(overrideWorkingDirectory).orElse(getWorkingDirectory());
                pgDir = new File(workingDirectory, String.format("PG-%s", pgDigest));

                mkdirs(pgDir);
                workingDirectory.setWritable(true, false);

                final File pgDirExists = new File(pgDir, ".exists");

                if (!isPgBinReady(pgDirExists)) {
                    File unpackLockFile = new File(pgDir, LOCK_FILE_NAME);
                    try (FileOutputStream lockStream = new FileOutputStream(unpackLockFile);
                         FileLock unpackLock = lockStream.getChannel().tryLock()) {
                        if (unpackLock != null) {
                            LOG.info("Extracting Postgres...");
                            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                                extractTxz(bais, pgDir);
                            }
                            if (!pgDirExists.createNewFile()) {
                                pgDirExists.setLastModified(System.currentTimeMillis());
                            }
                        } else {
                            // the other guy is unpacking for us.
                            int maxAttempts = 60;
                            while (!isPgBinReady(pgDirExists) && --maxAttempts > 0) {
                                Thread.sleep(1000L);
                            }
                            if (!isPgBinReady(pgDirExists)) {
                                throw new IllegalStateException("Waited 60 seconds for postgres to be unpacked but it never finished!");
                            }
                        }
                    } finally {
                        if (unpackLockFile.exists() && !unpackLockFile.delete()) {
                            LOG.error("could not remove lock file {}", unpackLockFile.getAbsolutePath());
                        }
                    }
                }
            } catch (final IOException | NoSuchAlgorithmException e) {
                LOG.error("Got error while unpacking Postgres", e);
                throw new ExceptionInInitializerError(e);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(ie);
            }
            PREPARE_BINARIES.put(pgBinaryResolver, pgDir);
            LOG.info("Postgres binaries at {}", pgDir);
            return pgDir;
        } finally {
            PREPARE_BINARIES_LOCK.unlock();
        }
    }

    private static boolean isPgBinReady(File pgDirExists) {
        if (!pgDirExists.exists()) {
            return false;
        }

        File parentDir = pgDirExists.getParentFile();
        File[] otherFiles = Optional.ofNullable(parentDir.listFiles(file -> !file.equals(pgDirExists))).orElseGet(() -> new File[0]);

        long contentLastModified = Stream.of(otherFiles).mapToLong(File::lastModified).max().orElse(Long.MAX_VALUE);
        return parentDir.lastModified() - 100 <= pgDirExists.lastModified() && contentLastModified <= pgDirExists.lastModified();
    }

    @Override
    public String toString()
    {
        return "EmbeddedPG-" + instanceId;
    }

    private final Command INIT_DB = new Command("initdb");
    private final Command POSTGRES = new Command("postgres");
    private final Command PG_CTL = new Command("pg_ctl");

    private class Command {

        private final String commandName;

        private Command(String commandName) {
            this.commandName = commandName;
        }

        public String processName() {
            return commandName;
        }

        public void applyTo(ProcessBuilder builder, List<String> arguments) {
            List<String> command = new ArrayList<>();

            if (LinuxUtils.isUnshareAvailable()) {
                command.addAll(Arrays.asList("unshare", "-U"));
            }

            String extension = SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
            command.add(new File(pgDir, "bin/" + commandName + extension).getPath());
            command.addAll(arguments);

            builder.command(command);
        }
    }
}
