package org.xmlBlaster.contrib.dbupdate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * Copy all xb_entries from one database to another.
 * 
 * <pre>
 * Compile: javac JdbcShuffler.java
 * </pre>
 * 
 * <pre>
 * Run:
 * export CLASSPATH=.:$XMLBLASTER_HOME/lib/sqljdbc.jar:$XMLBLASTER_HOME/lib/postgresql.jar
 * 
 * java org.xmlBlaster.contrib.dbupdate.JdbcShuffler &lt;hostname&gt; &lt;from&gt; &lt;to&gt;
 * 
 *    &lt;from&gt; and &lt;to&gt; is one of: SQLServer | Postgres | Oracle
 * 
 * Example:
 * java org.xmlBlaster.contrib.dbupdate.JdbcShuffler localhost SQLServer Postgres
 * </pre>
 * 
 * @author Marcel Ruff
 */
public class JdbcShuffler {
   final static int DATA_ID = 1;

   final static int QUEUE_NAME = 2;

   final static int PRIO = 3;

   final static int TYPE_NAME = 4;

   final static int PERSISTENT = 5;

   final static int SIZE_IN_BYTES = 6;

   final static int BLOB = 7;

   public final String SQLSERVER = "SQLServer";

   public final String POSTGRES = "Postgres";

   public final String ORACLE = "Oracle";

   // private String hostname = "192.168.1.28";
   private String hostname = "localhost";

   private String from = SQLSERVER;

   private String to = POSTGRES;

   private Connection conFrom;

   private Connection conTo;

   private ConDetail sqlServer;

   private ConDetail oracle;

   private ConDetail postgres;

   public JdbcShuffler(String[] args) {
      if (args.length > 0)
         this.hostname = args[0];
      if (args.length > 1)
         this.from = args[1];
      if (args.length > 2)
         this.to = args[2];
      System.setProperty("jdbc.drivers",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver:org.postgresql.Driver:oracle.jdbc.driver.OracleDriver");

      String password = "CHANGEPASSWORDHERE!";
      this.sqlServer = new ConDetail("sa", password, "jdbc:sqlserver://" + hostname
            + ":1433;responseBuffering=adaptive;databaseName=xmlBlaster");
      this.postgres = new ConDetail("postgres", password, "jdbc:postgresql://" + hostname + ":5432/xmlblaster");
      this.oracle = new ConDetail("system", password, "jdbc:oracle:thin:@" + hostname + ":1521:xmlb");
   }

   public void init() throws SQLException {
      System.out.println("java JdbcShuffler <hostname> <from> <to>\nInit connections ...");

      if (from.equalsIgnoreCase(SQLSERVER))
         conFrom = this.sqlServer.getConnection();
      else if (from.equalsIgnoreCase(POSTGRES))
         conFrom = this.postgres.getConnection();
      else if (from.equalsIgnoreCase(ORACLE))
         conFrom = this.oracle.getConnection();

      if (to.equalsIgnoreCase(SQLSERVER))
         conTo = this.sqlServer.getConnection();
      else if (to.equalsIgnoreCase(POSTGRES))
         conTo = this.postgres.getConnection();
      else if (to.equalsIgnoreCase(ORACLE))
         conTo = this.oracle.getConnection();
   }

   public void shutdown() throws SQLException {
      if (conFrom != null)
         conFrom.close();
      if (conTo != null)
         conTo.close();
   }

   private String getDate() {
      Date d = new Date();
      return d.toString();
   }

   public void shuffle() throws SQLException, IOException {
      PreparedStatement stFrom = null;
      int count = 0;
      int errors = 0;
      String tableName = "xb_entries";
      System.out.println(getDate() + " Hit a key to shuffle " + tableName + " from=" + this.from + " to=" + this.to
            + " ...");
      System.in.read();
      System.out.println(getDate() + " Shuffling now");
      try {
         stFrom = conFrom.prepareStatement("select * from xb_entries");
         ResultSet rs = stFrom.executeQuery();
         while (rs.next()) {
            PreparedStatement preStatement = conTo
                  .prepareStatement("INSERT INTO xb_entries VALUES ( ?, ?, ?, ?, ?, ?, ?)");

            long dataId = rs.getLong(DATA_ID);
            String queueName = rs.getString(QUEUE_NAME);
            int prio = rs.getInt(PRIO);
            String typeName = rs.getString(TYPE_NAME);
            if (typeName != null)
               typeName = typeName.trim();
            boolean persistent = isPersistent(rs.getString(PERSISTENT));
            long sizeInBytes = rs.getLong(SIZE_IN_BYTES);
            InputStream is = rs.getBinaryStream(BLOB);
            if (is == null) {
               errors++;
               String txt = "dataId='" + dataId + "' prio='" + prio + "' typeName='" + typeName + "' persistent='"
                     + persistent + "' sizeInBytes='" + sizeInBytes + "'";
               System.out.println("The stream for the blob of data: " + txt + " is null");
               continue;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
            while (true) {
               byte[] buf = new byte[1000];
               int num = is.read(buf);
               if (num == -1)
                  break;
               if (num > 0)
                  out.write(buf, 0, num);
            }
            byte[] blob = out.toByteArray();

            preStatement.setLong(DATA_ID, dataId);
            preStatement.setString(QUEUE_NAME, queueName);
            preStatement.setInt(PRIO, prio);
            preStatement.setString(TYPE_NAME, typeName);
            if (persistent == true)
               preStatement.setString(PERSISTENT, "T");
            else
               preStatement.setString(PERSISTENT, "F");
            preStatement.setLong(SIZE_IN_BYTES, sizeInBytes);
            ByteArrayInputStream blob_stream = new ByteArrayInputStream(blob);
            preStatement.setBinaryStream(BLOB, blob_stream, blob.length); // (int)sizeInBytes);
            int num = preStatement.executeUpdate();

            if (num == 0) {
               errors++;
               System.out.println("Update failed num=0");
            }

            preStatement.close();

            count += num;

            if (count % 1000 == 0)
               System.out.println(getDate() + " Shuffling #" + count + " errors=" + errors);
         }
         System.out
               .println(getDate() + " Done shuffle " + tableName + " from=" + this.from + " to=" + this.to + " ...");
         System.out.println(getDate() + " Done count=" + count + " errors=" + errors);
      } finally {
         if (stFrom != null)
            stFrom.close();
      }
   }

   private boolean isPersistent(String persistentAsChar) {
      if (persistentAsChar != null)
         persistentAsChar = persistentAsChar.trim();
      boolean persistent = false;
      if ("T".equalsIgnoreCase(persistentAsChar))
         persistent = true;
      return persistent;
   }

   public static void main(String[] args) {
      try {
         JdbcShuffler s = new JdbcShuffler(args);
         s.init();
         s.shuffle();
         s.shutdown();
      } catch (Throwable e) {
         e.printStackTrace();
         System.out.println("Shuffling failed: " + e.toString());
      }
   }

   class ConDetail {
      public ConDetail(String loginName, String passwd, String url) {
         super();
         this.loginName = loginName;
         this.passwd = passwd;
         this.url = url;
      }

      public String confName;

      public String loginName;

      public String passwd;

      public String url;

      private Connection con;

      public synchronized Connection getConnection() throws SQLException {
         if (this.con == null) {
            System.out.println("Get Connection: " + this.url);
            this.con = DriverManager.getConnection(this.url, this.loginName, this.passwd);
            System.out.println("Got Connection: " + this.url);
         }
         return this.con;
      }
   }
}
