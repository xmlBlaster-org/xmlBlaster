/*
 * -----------------------------------------------------------------------------
 * Name:      XmlDBAdapterWorker.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id: XmlDBAdapterWorker.java,v 1.12 2000/07/09 13:55:16 ruff Exp $
 * ------------------------------------------------------------------------------
 */
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;

import org.w3c.dom.Text;
import com.sun.xml.tree.XmlDocument;
import com.sun.xml.tree.ElementNode;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * For every database access, an instance of this class does the work in a dedicated thread. 
 */
public class XmlDBAdapterWorker extends Thread {

   private static final String   ME = "WorkerThread";
   private String                cust;
   private byte[]                content;
   private I_Publish             callback = null;
   private NamedConnectionPool   namedPool = null;

   /**
    * Create the worker instance to handle a single RDBMS request. 
    * @param cust       The sender of the SQL message
    * @param content    The SQL statement
    * @param callback   Interface to publish the XML based result set
    * @param namedPool  A pool of JDBC connections for the RDBMS users
    */
   public XmlDBAdapterWorker(String cust, byte[] content,
                             I_Publish callback, NamedConnectionPool namedPool) {
      this.cust = cust;
      this.content = content;
      this.callback = callback;
      this.namedPool = namedPool;
   }

   /**
    * Parse the XML encoded SQL statement. 
    */
   private XmlDocument createDocument() throws Exception {
      XmlDocument          document = null;
      ByteArrayInputStream bais = new ByteArrayInputStream(content);

      try {
         document = XmlDocument.createXmlDocument(bais, false);
      }
      catch (Exception e) {
         Log.warning(ME, "Exception in create: " + e);

         throw (e);
      }

      return document;
   }

   /**
    * Query the database. 
    */
   private XmlDocument queryDB(ConnectionDescriptor descriptor) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering queryDB() ...");
      Connection  conn = null;
      Statement   stmt = null;
      ResultSet   rs = null;
      XmlDocument doc = null;

      try {
         conn =  namedPool.reserve(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword()); // using default connection pool properties
         stmt = conn.createStatement();

         String   command = descriptor.getCommand();

         try {
            if (descriptor.getInteraction().equalsIgnoreCase("update")) {
               if (Log.TRACE) Log.trace(ME, "Trying DB update '" + command + "' ...");

               int   rowsAffected = stmt.executeUpdate(command);

               doc = createUpdateDocument(rowsAffected, descriptor);
            }
            else {
               if (Log.TRACE) Log.trace(ME, "Trying SQL query '" + command + "' ...");
               rs = stmt.executeQuery(command);
               doc =
                  DBAdapterUtils.createDocument(descriptor.getDocumentrootnode(),
                                                descriptor.getRowrootnode(),
                                                descriptor.getRowlimit(), rs);
            }
         } finally {
            if (rs!=null) rs.close();
            if (stmt!=null) stmt.close();
            if (conn!=null) namedPool.release(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword(), conn);
            if (Log.TRACE) Log.trace(ME, "Query successful done, connection released");
         }
      }
      catch (SQLException e) {
         Log.warning(ME, "Exception in query '" + descriptor.getCommand() + "' : " + e);
         throw new XmlBlasterException(ME, e.getMessage());
      }

      return doc;
   }

   /**
    * @param rowsAffected
    * @param descriptor
    */
   private XmlDocument createUpdateDocument(int rowsAffected,
                                            ConnectionDescriptor descriptor) {
      XmlDocument document = new XmlDocument();
      ElementNode root =
         (ElementNode) document.createElement(descriptor.getDocumentrootnode());

      document.appendChild(root);

      ElementNode row =
         (ElementNode) document.createElement(descriptor.getRowrootnode());

      root.appendChild(row);

      Text  rows =
         (Text) document.createTextNode(rowsAffected
                                        + "row(s) were affected during the update.");

      row.appendChild(rows);

      return document;
   }


   /**
    */
   private void notifyCust(XmlDocument doc)
   {
      ByteArrayOutputStream bais = new ByteArrayOutputStream();
      try { doc.write(bais); } catch(IOException e) { Log.error(ME, e.getMessage()); }
      notifyCust(bais.toByteArray(), "QueryResults");
   }

   /**
    * @param content
    * @param contentMimeExtended Informative only, "XmlBlasterException" or "QueryResults"
    */
   private void notifyCust(byte[] content, String contentMimeExtended)
   {
      PublishKeyWrapper key = new PublishKeyWrapper("__sys_jdbc."+ME, "text/xml", contentMimeExtended);
      PublishQosWrapper qos = new PublishQosWrapper(new Destination(cust));

      try {
         MessageUnit mu = new MessageUnit(key.toXml(), content, qos.toXml());
         String      oid = callback.publish(mu);
         if (Log.DUMP) Log.plain("Delivered Results...\n" + new String(content));
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Exception in notify: " + e.reason);
      }
      catch (Exception e) {
         Log.error(ME, "Exception in notify: " + e);
      }
   }

   /**
    * Query the database. 
    */
   public void run() {
      XmlDocument document = null;

      try {
         document = createDocument();
      }
      catch (Exception e) {
         Log.error(ME+".SqlInitError", e.getMessage());
         XmlBlasterException ex = new XmlBlasterException(ME+".SqlInitError", e.getMessage());
         notifyCust(ex.toXml().getBytes(), "XmlBlasterException");
         return;
      }

      ConnectionDescriptor descriptor = null;

      if (Log.TRACE) Log.trace(ME, "Get connection ...");
      descriptor = new ConnectionDescriptor(document);

      try {
         if (Log.TRACE) Log.trace(ME, "Access DB ...");
         document = queryDB(descriptor);
      }
      catch (XmlBlasterException e) {
         Log.error(e.id, e.reason);
         notifyCust(e.toXml().getBytes(), "XmlBlasterException");
         return;
      }

      if (descriptor.getConfirmation()) {
         notifyCust(document);
      }
   }
}
