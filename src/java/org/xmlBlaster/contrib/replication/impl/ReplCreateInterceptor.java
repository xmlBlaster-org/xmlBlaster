package org.xmlBlaster.contrib.replication.impl;

import java.sql.CallableStatement;
import java.sql.Connection;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbCreateInterceptor;

public class ReplCreateInterceptor implements I_DbCreateInterceptor {

   private String statement;
   private String identifier;
   
   public ReplCreateInterceptor() {
      
   }
   
   private final String getSql(Connection conn) throws Exception {
      if (statement != null)
         return statement;
      if (conn == null)
         return null;
      String name = conn.getMetaData().getDatabaseProductName();
      if (name == null)
         throw new Exception("Could not retrieve the name of the database, you must configure 'repl.createInterceptor.statement'");
      
      if (name.toLowerCase().indexOf("oracle") > -1)
         return "{call dbms_application_info.set_client_info(?)}";
      else {
         throw new Exception("There is no default for database '" + name + "': you must configure 'repl.createInterceptor.statement'");
      }
   }
   
   /**
    * @see org.xmlBlaster.contrib.db.I_DbCreateInterceptor#onCreateConnection(java.sql.Connection)
    */
   public void onCreateConnection(Connection conn) throws Exception {
         try {
            String sql = getSql(conn);
            CallableStatement st = conn.prepareCall(sql);
            st.setString(1, identifier);
            st.executeQuery();
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
   }


   /**
    * @see org.xmlBlaster.contrib.db.I_DbCreateInterceptor#init(org.xmlBlaster.contrib.I_Info)
    */
   public void init(I_Info info) throws Exception {
      statement = info.get("repl.createInterceptor.statement", null);
      identifier = info.get("repl.createInterceptor.identifier", "REPLICATION");
   }


   /**
    * @see org.xmlBlaster.contrib.db.I_DbCreateInterceptor#shutdown()
    */
   public void shutdown() {
   }
   
}
