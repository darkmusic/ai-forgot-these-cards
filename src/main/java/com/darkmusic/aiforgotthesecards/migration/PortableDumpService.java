package com.darkmusic.aiforgotthesecards.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.Objects.requireNonNull;

public class PortableDumpService {

    private static final Logger log = LoggerFactory.getLogger(PortableDumpService.class);
    private static final int FORMAT_VERSION = 1;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String TABLES_PREFIX = "tables/";

    // Explicit scope so we don't accidentally export internal Hibernate tables.
    private static final List<TableSpec> TABLES_IN_INSERT_ORDER = List.of(
            TableSpec.of("theme", List.of("id")),
            TableSpec.of("user", List.of("id")),
            TableSpec.of("deck", List.of("id")),
            TableSpec.of("tag", List.of("id")),
            TableSpec.of("deck_tag", List.of("deck_id", "tag_id")),
            TableSpec.of("card", List.of("id")),
            TableSpec.of("card_tag", List.of("card_id", "tag_id")),
            TableSpec.of("user_card_srs", List.of("id")),
            TableSpec.of("ai_chat", List.of("id"))
    );

    private static final List<TableSpec> TABLES_IN_DELETE_ORDER;

    static {
        List<TableSpec> reversed = new ArrayList<>(TABLES_IN_INSERT_ORDER);
        java.util.Collections.reverse(reversed);
        TABLES_IN_DELETE_ORDER = List.copyOf(reversed);
    }

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PortableDumpService(DataSource dataSource) {
        this.dataSource = requireNonNull(dataSource, "dataSource");
        this.objectMapper = new ObjectMapper();
    }

    public enum Command {
        EXPORT,
        IMPORT,
        VALIDATE;

        public static Command from(String raw) {
            String v = requireNonNull(raw, "command").trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "export" -> EXPORT;
                case "import" -> IMPORT;
                case "validate" -> VALIDATE;
                default -> throw new IllegalArgumentException("Unknown command: " + raw);
            };
        }
    }

    public enum ImportMode {
        FAIL_IF_NOT_EMPTY,
        TRUNCATE;

        public static ImportMode from(String raw) {
            String v = requireNonNull(raw, "mode").trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "fail-if-not-empty", "fail_if_not_empty" -> FAIL_IF_NOT_EMPTY;
                case "truncate" -> TRUNCATE;
                default -> throw new IllegalArgumentException("Unknown import mode: " + raw);
            };
        }
    }

    public record Manifest(
            int formatVersion,
            long exportedAtEpochMs,
            String sourceDatabaseProduct,
            List<TableManifest> tables
    ) {
    }

    public record TableManifest(
            String table,
            List<ColumnManifest> columns,
            long rowCount,
            List<String> orderBy
    ) {
    }

    public record ColumnManifest(
            String name,
            int jdbcType,
            String typeName,
            boolean nullable
    ) {
    }

    private record TableSpec(String table, List<String> orderBy) {
        static TableSpec of(String table, List<String> orderBy) {
            return new TableSpec(table, orderBy);
        }
    }

    public void exportTo(Path zipPath) throws Exception {
        Files.createDirectories(zipPath.getParent());

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String quote = safeQuote(meta);
            String product = meta.getDatabaseProductName();

            List<TableManifest> tableManifests = new ArrayList<>();

            try (OutputStream fileOut = Files.newOutputStream(zipPath);
                 ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {

                for (TableSpec tableSpec : TABLES_IN_INSERT_ORDER) {
                    TableExport export = exportTable(connection, zipOut, quote, tableSpec);
                    tableManifests.add(new TableManifest(
                            export.table,
                            export.columns,
                            export.rowCount,
                            tableSpec.orderBy
                    ));
                }

                Manifest manifest = new Manifest(
                        FORMAT_VERSION,
                        System.currentTimeMillis(),
                        product,
                        tableManifests
                );

                zipOut.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
                // Avoid Jackson closing the underlying ZipOutputStream.
                zipOut.write(objectMapper.writeValueAsBytes(manifest));
                zipOut.closeEntry();
            }
        }

        log.info("Portable dump written: {}", zipPath);
    }

    public void importFrom(Path zipPath, ImportMode mode) throws Exception {
        validate(zipPath);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String quote = safeQuote(meta);
            String product = meta.getDatabaseProductName();
            boolean isPostgres = product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
            boolean isSqlite = product != null && product.toLowerCase(Locale.ROOT).contains("sqlite");

            // SQLite pragma changes like synchronous/journal_mode must happen outside transactions.
            if (isSqlite) {
                connection.setAutoCommit(true);
                applySqliteImportPragmas(connection);
            }

            connection.setAutoCommit(false);

            if (mode == ImportMode.FAIL_IF_NOT_EMPTY) {
                ensureEmpty(connection, quote);
            } else {
                truncateAll(connection, quote, isPostgres, isSqlite);
            }

            Manifest manifest = readManifest(zipPath);
            importAllTables(connection, zipPath, quote, manifest);

            if (isPostgres) {
                resetPostgresSequences(connection, quote);
            }
            connection.commit();

            if (isSqlite) {
                // foreign_key_check must be outside transactions.
                connection.setAutoCommit(true);
                finalizeSqliteImport(connection);
            }
        }

        log.info("Portable dump imported from: {}", zipPath);
    }

    public void validate(Path zipPath) throws Exception {
        if (!Files.exists(zipPath)) {
            throw new IllegalArgumentException("Portable dump not found: " + zipPath);
        }

        Manifest manifest = readManifest(zipPath);
        if (manifest.formatVersion() != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported portable dump formatVersion=" + manifest.formatVersion());
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            for (TableManifest table : manifest.tables()) {
                String entryName = tableEntryName(table.table());
                if (zipFile.getEntry(entryName) == null) {
                    throw new IllegalStateException("Missing ZIP entry: " + entryName);
                }
            }
        }
    }

    private Manifest readManifest(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(MANIFEST_ENTRY);
            if (entry == null) {
                throw new IllegalStateException("Missing manifest entry: " + MANIFEST_ENTRY);
            }
            try (InputStream in = zipFile.getInputStream(entry)) {
                return objectMapper.readValue(in, Manifest.class);
            }
        }
    }

    private void ensureEmpty(Connection connection, String quote) throws SQLException {
        for (TableSpec spec : TABLES_IN_INSERT_ORDER) {
            if (!isTableEmpty(connection, quote, spec.table)) {
                throw new IllegalStateException("Target DB is not empty (table has rows): " + spec.table);
            }
        }
    }

    private boolean isTableEmpty(Connection connection, String quote, String table) throws SQLException {
        String sql = "select 1 from " + q(quote, table) + " limit 1";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return !rs.next();
        }
    }

    private void truncateAll(Connection connection, String quote, boolean isPostgres, boolean isSqlite) throws SQLException {
        if (isSqlite) {
            // Disable FK enforcement while truncating.
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF");
            }
        }

        if (isPostgres) {
            // Fast path.
            StringBuilder sb = new StringBuilder("TRUNCATE TABLE ");
            for (int i = 0; i < TABLES_IN_DELETE_ORDER.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(q(quote, TABLES_IN_DELETE_ORDER.get(i).table));
            }
            sb.append(" RESTART IDENTITY CASCADE");
            try (Statement st = connection.createStatement()) {
                st.execute(sb.toString());
            }
            return;
        }

        // Portable fallback.
        try (Statement st = connection.createStatement()) {
            for (TableSpec spec : TABLES_IN_DELETE_ORDER) {
                st.execute("DELETE FROM " + q(quote, spec.table));
            }
        }
    }

    private void applySqliteImportPragmas(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA temp_store = MEMORY");
        }
    }

    private void finalizeSqliteImport(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                if (rs.next()) {
                    String table = rs.getString(1);
                    String rowid = rs.getString(2);
                    String parent = rs.getString(3);
                    throw new IllegalStateException("SQLite foreign_key_check failed; first violation: table=" + table + " rowid=" + rowid + " parent=" + parent);
                }
            }
        }
    }

    private void importAllTables(Connection connection, Path zipPath, String quote, Manifest manifest) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            for (TableSpec spec : TABLES_IN_INSERT_ORDER) {
                TableManifest tm = manifest.tables().stream()
                        .filter(t -> t.table().equals(spec.table))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Manifest missing table: " + spec.table));

                ZipEntry entry = zipFile.getEntry(tableEntryName(spec.table));
                if (entry == null) {
                    throw new IllegalStateException("Missing ZIP entry for table: " + spec.table);
                }

                importTable(connection, quote, spec.table, tm.columns(), zipFile.getInputStream(entry));
            }
        }
    }

    private void importTable(Connection connection,
                             String quote,
                             String table,
                             List<ColumnManifest> sourceColumns,
                             InputStream jsonlStream
    ) throws Exception {

        TableTargetMeta targetMeta = readTargetMeta(connection, quote, table);
        List<String> insertColumns = new ArrayList<>();
        for (ColumnManifest cm : sourceColumns) {
            if (targetMeta.columnsByName.containsKey(cm.name)) {
                insertColumns.add(cm.name);
            }
        }

        if (insertColumns.isEmpty()) {
            log.warn("Skipping table {}; no matching columns", table);
            return;
        }

        String insertSql = buildInsertSql(quote, table, insertColumns);
        long inserted = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jsonlStream, StandardCharsets.UTF_8));
             PreparedStatement ps = connection.prepareStatement(insertSql)) {

            String line;
            int batchSize = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Map<String, Object> row = objectMapper.readValue(line, new TypeReference<>() {
                });

                for (int i = 0; i < insertColumns.size(); i++) {
                    String column = insertColumns.get(i);
                    ColumnTargetMeta colMeta = targetMeta.columnsByName.get(column);
                    Object raw = row.get(column);
                    setParam(ps, i + 1, colMeta.jdbcType, raw);
                }

                ps.addBatch();
                batchSize++;
                inserted++;

                if (batchSize >= 500) {
                    ps.executeBatch();
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                ps.executeBatch();
            }
        }

        log.info("Imported {} rows into {}", inserted, table);
    }

    private void resetPostgresSequences(Connection connection, String quote) throws SQLException {
        for (TableSpec spec : TABLES_IN_INSERT_ORDER) {
            // Only tables with an id column.
            TableTargetMeta meta = readTargetMeta(connection, quote, spec.table);
            if (!meta.columnsByName.containsKey("id")) {
                continue;
            }

            // Use pg_get_serial_sequence to find the right identity/serial sequence.
            String tableRegclass = quoteRegclass(quote, spec.table);
            String sequence;
            try (PreparedStatement ps = connection.prepareStatement("select pg_get_serial_sequence(?, ?)");) {
                ps.setString(1, tableRegclass);
                ps.setString(2, "id");
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        continue;
                    }
                    sequence = rs.getString(1);
                }
            }

            if (sequence == null || sequence.isBlank()) {
                continue;
            }

            String maxSql = "select coalesce(max(id), 0) from " + q(quote, spec.table);
            long maxId;
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(maxSql)) {
                rs.next();
                maxId = rs.getLong(1);
            }

            try (PreparedStatement ps = connection.prepareStatement("select setval(?::regclass, ?, true)")) {
                ps.setString(1, sequence);
                ps.setLong(2, maxId);
                ps.execute();
            }
        }
    }

    private static String quoteRegclass(String quote, String table) {
        // pg_get_serial_sequence expects a regclass-parseable string.
        // Using explicit identifier quotes preserves reserved words like user.
        return q(quote, table);
    }

    private String buildInsertSql(String quote, String table, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(q(quote, table)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q(quote, columns.get(i)));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(')');
        return sb.toString();
    }

    private void setParam(PreparedStatement ps, int index, int jdbcType, Object raw) throws SQLException {
        if (raw == null) {
            ps.setNull(index, jdbcType == Types.NULL ? Types.VARCHAR : jdbcType);
            return;
        }

        if (jdbcType == Types.BOOLEAN || jdbcType == Types.BIT) {
            ps.setBoolean(index, toBoolean(raw));
            return;
        }

        if (jdbcType == Types.BIGINT || jdbcType == Types.INTEGER || jdbcType == Types.SMALLINT || jdbcType == Types.TINYINT) {
            ps.setLong(index, toLong(raw));
            return;
        }

        if (jdbcType == Types.FLOAT || jdbcType == Types.REAL || jdbcType == Types.DOUBLE || jdbcType == Types.DECIMAL || jdbcType == Types.NUMERIC) {
            ps.setBigDecimal(index, toBigDecimal(raw));
            return;
        }

        if (jdbcType == Types.TIMESTAMP || jdbcType == Types.TIMESTAMP_WITH_TIMEZONE) {
            Timestamp ts = toTimestamp(raw);
            if (ts != null) {
                ps.setTimestamp(index, ts);
            } else {
                ps.setObject(index, raw);
            }
            return;
        }

        // Default: let the driver coerce.
        ps.setObject(index, raw);
    }

    private static boolean toBoolean(Object raw) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.intValue() != 0;
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("t") || s.equals("1") || s.equals("yes") || s.equals("y");
    }

    private static long toLong(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        return Long.parseLong(raw.toString().trim());
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(raw.toString().trim());
    }

    private static Timestamp toTimestamp(Object raw) {
        if (raw instanceof Timestamp ts) return ts;
        if (raw instanceof Instant i) return Timestamp.from(i);
        if (raw instanceof OffsetDateTime odt) return Timestamp.from(odt.toInstant());
        if (raw instanceof LocalDateTime ldt) return Timestamp.valueOf(ldt);
        if (raw instanceof Number n) return new Timestamp(n.longValue());

        String s = raw.toString().trim();
        try {
            // Accept ISO-8601 instants.
            if (s.endsWith("Z") || s.contains("+")) {
                return Timestamp.from(Instant.parse(s));
            }
        } catch (Exception ignored) {
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(s));
        } catch (Exception ignored) {
            return null;
        }
    }

    private TableTargetMeta readTargetMeta(Connection connection, String quote, String table) throws SQLException {
        String sql = "select * from " + q(quote, table) + " where 1=0";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            Map<String, ColumnTargetMeta> columns = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columns.put(md.getColumnLabel(i), new ColumnTargetMeta(md.getColumnType(i)));
            }
            return new TableTargetMeta(columns);
        }
    }

    private record ColumnTargetMeta(int jdbcType) {
    }

    private record TableTargetMeta(Map<String, ColumnTargetMeta> columnsByName) {
    }

    private record TableExport(String table, List<ColumnManifest> columns, long rowCount) {
    }

    private TableExport exportTable(Connection connection,
                                   ZipOutputStream zipOut,
                                   String quote,
                                   TableSpec spec
    ) throws Exception {

        List<ColumnManifest> columns = readColumns(connection, quote, spec.table);
        String select = buildSelectSql(quote, spec.table, spec.orderBy);
        String entryName = tableEntryName(spec.table);
        zipOut.putNextEntry(new ZipEntry(entryName));

        long count = 0;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipOut, StandardCharsets.UTF_8));
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(select)) {

            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String name = md.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(name, toJsonSafe(value));
                }
                writer.write(objectMapper.writeValueAsString(row));
                writer.write("\n");
                count++;
            }
            writer.flush();
        } finally {
            zipOut.closeEntry();
        }

        log.info("Exported {} rows from {}", count, spec.table);
        return new TableExport(spec.table, columns, count);
    }

    private List<ColumnManifest> readColumns(Connection connection, String quote, String table) throws SQLException {
        String sql = "select * from " + q(quote, table) + " where 1=0";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            List<ColumnManifest> out = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.add(new ColumnManifest(
                        md.getColumnLabel(i),
                        md.getColumnType(i),
                        md.getColumnTypeName(i),
                        md.isNullable(i) != ResultSetMetaData.columnNoNulls
                ));
            }
            return out;
        }
    }

    private String buildSelectSql(String quote, String table, List<String> orderBy) {
        StringBuilder sb = new StringBuilder();
        sb.append("select * from ").append(q(quote, table));
        if (orderBy != null && !orderBy.isEmpty()) {
            sb.append(" order by ");
            for (int i = 0; i < orderBy.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(q(quote, orderBy.get(i)));
            }
        }
        return sb.toString();
    }

    private static Object toJsonSafe(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Timestamp ts) {
            LocalDateTime ldt = ts.toLocalDateTime();
            return ldt.toString();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        if (value instanceof LocalDate ld) {
            return ld.toString();
        }
        if (value instanceof LocalTime lt) {
            return lt.toString();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        // Jackson will serialize BigDecimal precisely; keep it as-is.
        return value;
    }

    private static String tableEntryName(String table) {
        return TABLES_PREFIX + table + ".jsonl";
    }

    private static String safeQuote(DatabaseMetaData meta) throws SQLException {
        String quote = meta.getIdentifierQuoteString();
        if (quote == null) return "\"";
        quote = quote.trim();
        if (quote.isEmpty()) return "\"";
        return quote;
    }

    private static String q(String quote, String identifier) {
        // Very small utility; identifiers are internal constants, not user-provided.
        return quote + identifier + quote;
    }
}
