package liquibase.database.core;

import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.executor.ExecutorService;
import liquibase.logging.LogFactory;
import liquibase.statement.core.RawSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.util.StringUtils;

import java.math.BigInteger;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates MySQL database support.
 */
public class MySQLDatabase extends AbstractJdbcDatabase {
    public static final String PRODUCT_NAME = "MySQL";
    private Boolean hasJdbcConstraintDeferrableBug;

    private static Set<String> reservedWords = new HashSet();

    public MySQLDatabase() {
        super.setCurrentDateTimeFunction("NOW()");
        // objects in mysql are always case sensitive
        super.quotingStartCharacter ="`";
        super.quotingEndCharacter="`";
        setHasJdbcConstraintDeferrableBug(null);
    }

    @Override
    public String getShortName() {
        return "mysql";
    }


//todo: handle    @Override
//    public String getConnectionUsername() throws DatabaseException {
//        return super.getConnection().getConnectionUserName().replaceAll("\\@.*", "");
//    }

    @Override
    public String correctObjectName(String name, Class<? extends DatabaseObject> objectType) {
        if (objectType.equals(PrimaryKey.class) && name.equals("PRIMARY")) {
            return null;
        } else {
            name = super.correctObjectName(name, objectType);
            if (name == null) {
                return null;
            }
            if (!this.isCaseSensitive()) {
                return name.toLowerCase();
            }
            return name;
        }
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "MySQL";
    }

    @Override
    public Integer getDefaultPort() {
        return 3306;
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        // If it looks like a MySQL, swims like a MySQL and quacks like a MySQL,
        // it may still not be a MySQL, but a MariaDB.
        return (
                (PRODUCT_NAME.equalsIgnoreCase(conn.getDatabaseProductName()))
                && (! conn.getDatabaseProductVersion().toLowerCase().contains("mariadb"))
        );
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url.startsWith("jdbc:mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        return null;
    }


    @Override
    public boolean supportsSequences() {
        return false;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return false;
    }

    @Override
    protected boolean mustQuoteObjectName(String objectName, Class<? extends DatabaseObject> objectType) {
        return super.mustQuoteObjectName(objectName, objectType) || (!objectName.contains("(") && !objectName.matches("\\w+"));
    }

    @Override
    public String getLineComment() {
        return "-- ";
    }

    @Override
    protected String getAutoIncrementClause() {
        return "AUTO_INCREMENT";
    }

    @Override
    protected boolean generateAutoIncrementStartWith(final BigInteger startWith) {
    	// startWith not supported here. StartWith has to be set as table option.
        return false;
    }

    public String getTableOptionAutoIncrementStartWithClause(BigInteger startWith){
    	String startWithClause = String.format(getAutoIncrementStartWithClause(), (startWith == null) ? defaultAutoIncrementStartWith : startWith);
    	return getAutoIncrementClause() + startWithClause;
    }

    @Override
    protected boolean generateAutoIncrementBy(BigInteger incrementBy) {
        // incrementBy not supported
        return false;
    }

    @Override
    protected String getAutoIncrementOpening() {
        return "";
    }

    @Override
    protected String getAutoIncrementClosing() {
        return "";
    }

    @Override
    protected String getAutoIncrementStartWithClause() {
        return "=%d";
    }

    @Override
    public String getConcatSql(String... values) {
        StringBuffer returnString = new StringBuffer();
        returnString.append("CONCAT_WS(");
        for (String value : values) {
            returnString.append(value).append(", ");
        }

        return returnString.toString().replaceFirst(", $", ")");
    }

    @Override
    public boolean supportsTablespaces() {
        return false;
    }

    @Override
    public boolean supportsSchemas() {
        return false;
    }

    @Override
    public boolean supportsCatalogs() {
        return true;
    }

    @Override
    public String escapeIndexName(String catalogName, String schemaName, String indexName) {
        return escapeObjectName(indexName, Index.class);
    }

    @Override
    public boolean supportsForeignKeyDisable() {
        return true;
    }

    @Override
    public boolean disableForeignKeyChecks() throws DatabaseException {
        boolean enabled = ExecutorService.getInstance().getExecutor(this).queryForInt(new RawSqlStatement("SELECT @@FOREIGN_KEY_CHECKS")) == 1;
        ExecutorService.getInstance().getExecutor(this).execute(new RawSqlStatement("SET FOREIGN_KEY_CHECKS=0"));
        return enabled;
    }

    @Override
    public void enableForeignKeyChecks() throws DatabaseException {
        ExecutorService.getInstance().getExecutor(this).execute(new RawSqlStatement("SET FOREIGN_KEY_CHECKS=1"));
    }

    @Override
    public CatalogAndSchema getSchemaFromJdbcInfo(String rawCatalogName, String rawSchemaName) {
        return new CatalogAndSchema(rawCatalogName, null).customize(this);
    }

    @Override
    public String escapeStringForDatabase(String string) {
        string = super.escapeStringForDatabase(string);
        if (string == null) {
            return null;
        }
        return string.replace("\\", "\\\\");
    }

    @Override
    public boolean createsIndexesForForeignKeys() {
        return true;
    }

    @Override
    public boolean isReservedWord(String string) {
        if (reservedWords.contains(string.toUpperCase())) {
            return true;
        }
        return super.isReservedWord(string);
    }

    public int getDatabasePatchVersion() throws DatabaseException {
        String databaseProductVersion = this.getDatabaseProductVersion();
        if (databaseProductVersion == null) {
            return 0;
        }

        String versionStrings[] = databaseProductVersion.split("\\.");
        try {
            return Integer.parseInt(versionStrings[2].replaceFirst("\\D.*", ""));
        } catch (IndexOutOfBoundsException e) {
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }

    }

    {
        //list from http://dev.mysql.com/doc/refman/5.6/en/reserved-words.html
        reservedWords.addAll(Arrays.asList("ACCESSIBLE",
                "ADD",
                "ALL",
                "ALTER",
                "ANALYZE",
                "AND",
                "AS",
                "ASC",
                "ASENSITIVE",
                "BEFORE",
                "BETWEEN",
                "BIGINT",
                "BINARY",
                "BLOB",
                "BOTH",
                "BY",
                "CALL",
                "CASCADE",
                "CASE",
                "CHANGE",
                "CHAR",
                "CHARACTER",
                "CHECK",
                "COLLATE",
                "COLUMN",
                "CONDITION",
                "CONSTRAINT",
                "CONTINUE",
                "CONVERT",
                "CREATE",
                "CROSS",
                "CURRENT_DATE",
                "CURRENT_TIME",
                "CURRENT_TIMESTAMP",
                "CURRENT_USER",
                "CURSOR",
                "DATABASE",
                "DATABASES",
                "DAY_HOUR",
                "DAY_MICROSECOND",
                "DAY_MINUTE",
                "DAY_SECOND",
                "DEC",
                "DECIMAL",
                "DECLARE",
                "DEFAULT",
                "DELAYED",
                "DELETE",
                "DESC",
                "DESCRIBE",
                "DETERMINISTIC",
                "DISTINCT",
                "DISTINCTROW",
                "DIV",
                "DOUBLE",
                "DROP",
                "DUAL",
                "EACH",
                "ELSE",
                "ELSEIF",
                "ENCLOSED",
                "ESCAPED",
                "EXISTS",
                "EXIT",
                "EXPLAIN",
                "FALSE",
                "FETCH",
                "FLOAT",
                "FLOAT4",
                "FLOAT8",
                "FOR",
                "FORCE",
                "FOREIGN",
                "FROM",
                "FULLTEXT",
                "GENERATED",
                "GET",
                "GRANT",
                "GROUP",
                "HAVING",
                "HIGH_PRIORITY",
                "HOUR_MICROSECOND",
                "HOUR_MINUTE",
                "HOUR_SECOND",
                "IF",
                "IGNORE",
                "IN",
                "INDEX",
                "INFILE",
                "INNER",
                "INOUT",
                "INSENSITIVE",
                "INSERT",
                "INT",
                "INT1",
                "INT2",
                "INT3",
                "INT4",
                "INT8",
                "INTEGER",
                "INTERVAL",
                "INTO",
                "IS",
                "ITERATE",
                "JOIN",
                "KEY",
                "KEYS",
                "KILL",
                "LEADING",
                "LEAVE",
                "LEFT",
                "LIKE",
                "LIMIT",
                "LINEAR",
                "LINES",
                "LOAD",
                "LOCALTIME",
                "LOCALTIMESTAMP",
                "LOCK",
                "LONG",
                "LONGBLOB",
                "LONGTEXT",
                "LOOP",
                "LOW_PRIORITY",
                "MASTER_SSL_VERIFY_SERVER_CERT",
                "MATCH",
                "MAXVALUE",
                "MEDIUMBLOB",
                "MEDIUMINT",
                "MEDIUMTEXT",
                "MIDDLEINT",
                "MINUTE_MICROSECOND",
                "MINUTE_SECOND",
                "MOD",
                "MODIFIES",
                "NATURAL",
                "NOT",
                "NO_WRITE_TO_BINLOG",
                "NULL",
                "NUMERIC",
                "ON",
                "OPTIMIZE",
                "OPTIMIZER_COSTS",
                "OPTION",
                "OPTIONALLY",
                "OR",
                "ORDER",
                "OUT",
                "OUTER",
                "OUTFILE",
                "PARTITION",
                "PRECISION",
                "PRIMARY",
                "PROCEDURE",
                "PURGE",
                "RANGE",
                "READ",
                "READS",
                "READ_WRITE",
                "REAL",
                "REFERENCES",
                "REGEXP",
                "RELEASE",
                "RENAME",
                "REPEAT",
                "REPLACE",
                "REQUIRE",
                "RESIGNAL",
                "RESTRICT",
                "RETURN",
                "REVOKE",
                "RIGHT",
                "RLIKE",
                "SCHEMA",
                "SCHEMAS",
                "SECOND_MICROSECOND",
                "SELECT",
                "SENSITIVE",
                "SEPARATOR",
                "SET",
                "SHOW",
                "SIGNAL",
                "SMALLINT",
                "SPATIAL",
                "SPECIFIC",
                "SQL",
                "SQLEXCEPTION",
                "SQLSTATE",
                "SQLWARNING",
                "SQL_BIG_RESULT",
                "SQL_CALC_FOUND_ROWS",
                "SQL_SMALL_RESULT",
                "SSL",
                "STARTING",
                "STORED",
                "STRAIGHT_JOIN",
                "TABLE",
                "TERMINATED",
                "THEN",
                "TINYBLOB",
                "TINYINT",
                "TINYTEXT",
                "TO",
                "TRAILING",
                "TRIGGER",
                "TRUE",
                "UNDO",
                "UNION",
                "UNIQUE",
                "UNLOCK",
                "UNSIGNED",
                "UPDATE",
                "USAGE",
                "USE",
                "USING",
                "UTC_DATE",
                "UTC_TIME",
                "UTC_TIMESTAMP",
                "VALUES",
                "VARBINARY",
                "VARCHAR",
                "VARCHARACTER",
                "VARYING",
                "VIRTUAL",
                "WHEN",
                "WHERE",
                "WHILE",
                "WITH",
                "WRITE",
                "XOR",
                "YEAR_MONTH",
                "ZEROFILL"));
    }

    /**
     * Tests if this MySQL / MariaDB database has a bug where the JDBC driver returns constraints as
     * DEFERRABLE INITIAL IMMEDIATE even though neither MySQL nor MariaDB support DEFERRABLE CONSTRAINTs at all.
     * We need to know about this because this could lead to errors in database snapshots.
     * @return true if this database is affected, false if not, null if we cannot tell (e.g. OfflineConnection)
     */
    public Boolean hasBugJdbcConstraintsDeferrable() {
        if (getConnection() instanceof OfflineConnection)
            return null;
        if (getHasJdbcConstraintDeferrableBug() != null)  // cached value
            return getHasJdbcConstraintDeferrableBug();

        String randomIdentifier = "TMP_" + StringUtils.randomIdentifer(16);
        try
        {
            // Get the real connection and metadata reference
            java.sql.Connection conn = ((JdbcConnection) getConnection()).getUnderlyingConnection();
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            String sql = "CREATE TABLE " + randomIdentifier + " (\n" +
                    "  id INT PRIMARY KEY,\n" +
                    "  self_ref INT NOT NULL,\n" +
                    "  CONSTRAINT c_self_ref FOREIGN KEY(self_ref) REFERENCES " + randomIdentifier + "(id)\n" +
                    ")";
            ExecutorService.getInstance().getExecutor(this).execute(new RawSqlStatement(sql));

            try (
                ResultSet rs = metaData.getImportedKeys(getDefaultCatalogName(), getDefaultSchemaName(), randomIdentifier)
            ) {
                if (!rs.next()) {
                    throw new UnexpectedLiquibaseException("Error during testing for MySQL/MariaDB JDBC driver bug: " +
                            "could not retrieve JDBC metadata information for temporary table '" +
                            randomIdentifier + "'");
                }
                if (rs.getShort("DEFERRABILITY") != DatabaseMetaData.importedKeyNotDeferrable) {
                    setHasJdbcConstraintDeferrableBug(true);
                    LogFactory.getInstance().getLog().warning("Your MySQL/MariaDB database JDBC driver might have " +
                            "a bug where constraints are reported as DEFERRABLE, even though MySQL/MariaDB do not " +
                            "support this feature. A workaround for this problem will be used. Please check with " +
                            "MySQL/MariaDB for availability of fixed JDBC drivers to avoid this warning.");
                } else {
                    setHasJdbcConstraintDeferrableBug(false);
                }
            }

        } catch (DatabaseException|SQLException e) {
            throw new UnexpectedLiquibaseException("Error during testing for MySQL/MariaDB JDBC driver bug.", e);
        } finally {
            try {
                ExecutorService.getInstance().reset();
                ExecutorService.getInstance().getExecutor(this).execute(
                        new RawSqlStatement("DROP TABLE " + randomIdentifier));
            } catch (DatabaseException e) {
                throw new UnexpectedLiquibaseException("Error during testing for MySQL/MariaDB JDBC driver bug. " +
                        "Could not drop the temporary test table " + randomIdentifier + ". You will need to remove it " +
                        "manually. Sorry.", e);
            }
        }

        return getHasJdbcConstraintDeferrableBug();
    }

    /**
     * returns true if the JDBC drivers suffers from a bug where constraints are reported as DEFERRABLE, even though
     * MySQL/MariaDB do not support this feature.
     * @return true if the JDBC is probably affected, false if not.
     */
    protected Boolean getHasJdbcConstraintDeferrableBug() {
        return hasJdbcConstraintDeferrableBug;
    }

    protected void setHasJdbcConstraintDeferrableBug(Boolean hasJdbcConstraintDeferrableBug) {
        this.hasJdbcConstraintDeferrableBug = hasJdbcConstraintDeferrableBug;
    }
}
