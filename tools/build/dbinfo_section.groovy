// BEGIN included '3_database_info.groovy'

// Imports for '3_database_info.groovy'
import com.liferay.portal.kernel.dao.db.DB
import com.liferay.portal.kernel.dao.db.DBManagerUtil
import com.liferay.portal.kernel.dao.db.DBType
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

// Wrapper function for '3_database_info.groovy'
def invoke__3_database_info_groovy(Map<String, Object> uasContext) {

    try {
        // Rows of every table, read-only. Each count has its own statement (always closed) and a timeout, so one
        // very large or locked table cannot block the report; tables that cannot be counted are listed separately.
        int queryTimeoutSeconds = (uasContext['tableCountTimeoutSeconds'] ?: 120) as int

        Connection connection = null
        ResultSet rs = null

        try {
            DB db = DBManagerUtil.getDB()
            DBType dbType = db.getDBType()

            connection = DataAccess.getConnection()

            DatabaseMetaData metadata = connection.getMetaData()

            String catalog = connection.getCatalog()
            String schema = null

            if ((catalog == null) && (dbType.equals(DBType.ORACLE))) {
                catalog = metadata.getUserName()
                schema = catalog
            }

            rs = metadata.getTables(catalog, schema, "%", null)

            def tableNames = []
            while (rs.next()) {
                String tableType = rs.getString("TABLE_TYPE")
                if ("TABLE".equals(tableType) || "BASE TABLE".equals(tableType)) {
                    tableNames << rs.getString("TABLE_NAME")
                }
            }

            def tables = []
            def unreadable = []

            tableNames.each { String tableName ->
                def statement = null
                ResultSet countResult = null
                try {
                    statement = connection.createStatement()
                    try {
                        statement.setQueryTimeout(queryTimeoutSeconds)
                    } catch (Throwable ignored) {
                    }
                    countResult = statement.executeQuery("select count(*) from " + tableName)
                    if (countResult.next()) {
                        tables << [name: tableName, rows: countResult.getLong(1)]
                    }
                } catch (Throwable t) {
                    unreadable << "${tableName}: ${t.class.simpleName}${t.message ? ' - ' + t.message.readLines().find() : ''}"
                } finally {
                    DataAccess.cleanUp(statement, countResult)
                }
            }

            // Same order as before: most rows first, then by name
            tables.sort { a, b -> a.rows == b.rows ? a.name <=> b.name : b.rows <=> a.rows }

            def tableDetails = new StringBuilder("tableName, rowsCount\n")
            tables.each { tableDetails.append("${it.name}, ${it.rows}\n") }

            println UASUtils.toCSVLine('data_tables_count', "${tables.size()}", "")
            println UASUtils.toCSVLine('data_tables_info', "", "${tableDetails.toString()}")
            println UASUtils.toCSVLine('data_tables_unreadable_count', unreadable.size(),
                    "Tables that could not be counted (query timeout ${queryTimeoutSeconds}s or error):\n${unreadable.collect { " - ${it}" }.join('\n')}")
        }
        finally {
            DataAccess.cleanUp(connection, null, rs)
        }

        println ""
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '3_database_info.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__3_database_info_groovy()

// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the '@@FILE_NAME@@'
// invoke__3_database_info_groovy([ debug: false ])
// END included '3_database_info.groovy'
