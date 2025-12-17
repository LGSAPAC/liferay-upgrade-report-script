/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * The use of any scripts provided in this ticket and the instructions
 * accompanying them for a purpose other than the one for which they were
 * delivered, or in the context of an incident not expressly analyzed in this
 * ticket, is not covered by the Liferay Support Service, unless expressly
 * stated otherwise.
 */

import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class PrintDatabaseInfo {

	public void doPrint(out) throws Exception {
		Connection con = null;
		ResultSet rs = null;
		PreparedStatement ps = null;

		DB db = DBManagerUtil.getDB();

		DBType dbType = db.getDBType();

		String filePath =
			PropsUtil.get(PropsKeys.LIFERAY_HOME) + "/databaseTables.info";

		PrintWriter logFile = new PrintWriter(filePath);

		try {
			con = DataAccess.getConnection();

			String timeStamp =
				new SimpleDateFormat("MM/dd/yyyy hh:mm:ss").format(
					Calendar.getInstance().getTime());

			DatabaseMetaData metadata = con.getMetaData();

			String catalog = con.getCatalog();
			String schema = null;

			if ((catalog == null) && (dbType.equals(DBType.ORACLE))) {
				catalog = metadata.getUserName();
				schema = catalog;
			}

			logFile.println(
				timeStamp + " Tables in database " + catalog + " sorted by " +
					"number of rows");

			rs = metadata.getTables(catalog, schema, "%", null);

			List<TableInfo> tables = new ArrayList<TableInfo>();

			while (rs.next()) {
				String tableName = rs.getString("TABLE_NAME");
				String tableType = rs.getString("TABLE_TYPE");

				if (dbType.equals(DBType.MARIADB)) {
					if (!tableType.equals("BASE TABLE")) {
						continue
					}
				}	
				else if (!"TABLE".equals(tableType)) {
					continue;
				}
				
				ResultSet rs2 = null;

				try {
					ps = con.prepareStatement(
						"select count(*) from " + tableName);

					rs2 = ps.executeQuery();

					if (rs2.next()) {
						tables.add(new TableInfo(tableName, rs2.getInt(1)));
					}
				}
				catch (Exception e) {
					System.out.println(
						"Unable to recover data from " + tableName);
				}
			}

			printResults(tables, logFile);
		}
		finally {

			logFile.close();

			out.println("Check the results in " + filePath);
		}
	}

    private void printResults(
    	List<PrintDatabaseInfo.TableInfo> tables, PrintWriter logFile) {

		logFile.println(String.format("%-30s %10s", "Table name", "Rows"));
        logFile.println(String.format(
        	"%-30s %10s", "--------------", "--------------"));

        Collections.sort(tables);

		for(PrintDatabaseInfo.TableInfo table : tables) {
            logFile.println(table);
        }
    }

	static class TableInfo implements Comparable<TableInfo> {
        private int rows;
        private String name;

        TableInfo(String name, int rows) {
            this.name = name;
            this.rows = rows;
        }

        public String toString() {
            return String.format("%-30s %10d", this.name, rows);
        }

        public int compareTo(TableInfo other) {
            if (this.rows == other.rows) {
                return this.name.compareTo(other.name);
            }
            else {
                return other.rows - this.rows;
            }
        }
    }
}

new PrintDatabaseInfo().doPrint(out);