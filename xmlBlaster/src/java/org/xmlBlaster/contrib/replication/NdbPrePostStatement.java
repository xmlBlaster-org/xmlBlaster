/*------------------------------------------------------------------------------
Name:      NdbPrePostStatement.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.I_PrePostStatement;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * NdbPrePostStatement
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class NdbPrePostStatement implements I_PrePostStatement {
   
   private static Logger log = Logger.getLogger(NdbPrePostStatement.class.getName());
   private String comChannel;
   
   public NdbPrePostStatement() {
      
   }

   /**
    * Transforms the column of C_OUTS.COM_CHANNEL to be C_INS.COM_CHANNEL='20'. Since in the DEE this value is null and the NDB needs to know from whom it comes.
    */
   public boolean preStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception {
      if (tableDescription.getCompleteTableName().indexOf("C_INS") != -1 /* || tableDescription.getCompleteTableName().indexOf("C_OUTS") != -1*/) {
         try {
            ClientProperty tmpCh = new ClientProperty("COM_CHANNEL", Constants.TYPE_INT, null, this.comChannel);
            currentRow.setColumn(tmpCh);
         }
         catch (Exception e1) {
            log.warning("error when trying to add a new column to the row");
            e1.printStackTrace();
         }
      }
      return true; // try it anyway
   }

   /**
    * Reads the COM_MESSAGEID, COM_CHANNEL and COM_TXTL columns of C_INS and writes with these values an entry in the C_IN_TEXTS 
    */
   public void postStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception {
      if (tableDescription.getCompleteTableName().indexOf("C_INS") != -1) {
         ClientProperty prop = currentRow.getColumn("COM_MESSAGEID");
         long comMsgId = 0L;
         int ch;
         String txtl = null;
         if (prop != null) {
            comMsgId = prop.getLongValue();
         }
         prop = currentRow.getColumn("COM_CHANNEL");
         if (prop != null) {
            ch = prop.getIntValue();
         }
         else
            ch = 20;
         prop = currentRow.getColumn("COM_TXTL");
         if (prop != null) {
            txtl = prop.getStringValue();
            if (txtl == null)
               txtl = "";
         }
         else
            txtl = "";
         Statement st2 = conn.createStatement();            
         String sql1 = "insert into AIS.C_IN_TEXTS (COM_MESSAGEID, COM_CHANNEL, COM_TXTL) VALUES (" + comMsgId + ", " + ch + ", '" + txtl + "')";
         String sql2 = "delete from AIS.C_INS";
         log.info("fix insert '" + sql1 + "'");
         log.info("(fix delete '" + sql2 + "'");

         try {
            st2.executeUpdate(sql1);
            try { st2.close(); } catch (Exception e) { e.printStackTrace(); }
            st2 = conn.createStatement();            
            st2.executeUpdate(sql2);
         }
         finally {
            try { if (st2 != null) st2.close(); } catch (Exception e) { e.printStackTrace(); }
         }
      }
   }

   public Set getUsedPropertyKeys() {
      // TODO Auto-generated method stub
      return null;
   }

   public void init(I_Info info) throws Exception {
      this.comChannel = info.get("dbWriter.ndbPrePostStatement.comChannel", "20");
   }

   public void shutdown() throws Exception {
   }
   
}
