/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

/*
 * -----------------------------------------------------------------------------
 * Name:      XmlDBAdapterWorker.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id: XmlDBAdapterWorker.java,v 1.3 2000/06/03 12:32:36 ruff Exp $
 * ------------------------------------------------------------------------------
 */

package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.*;
import org.xmlBlaster.util.pool.jdbc.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.client.CorbaConnection;
import org.omg.CosNaming.*;
import org.xmlBlaster.client.UpdateQoS;
import java.io.*;

import org.w3c.dom.*;
import com.sun.xml.tree.*;
import java.sql.*;
import java.io.*;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class XmlDBAdapterWorker extends Thread {

   private static final String   ME = "WorkerThread";
   private String[]              args;
   private String                cust;
   private byte[]                content;
   private String                qos;
   private Server                xmlBlaster;

   private XmlDocument           erorDocument = null;

   /**
    * Constructor declaration
    *
    *
    * @param args
    * @param cust
    * @param content
    * @param qos
    * @param xmlBlaster
    *
    * @see
    */
   public XmlDBAdapterWorker(String[] args, String cust, byte[] content,
                             String qos, Server xmlBlaster) {
      this.args = args;
      this.cust = cust;
      this.content = content;
      this.qos = qos;
      this.xmlBlaster = xmlBlaster;
   }

   /**
    * Method declaration
    *
    *
    * @return
    *
    * @throws Exception
    *
    * @see
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
    * Method declaration
    *
    *
    * @param descriptor
    *
    * @return
    *
    * @throws SQLException
    *
    * @see
    */
   private XmlDocument queryDB(ConnectionDescriptor descriptor)
           throws SQLException {
      if (Log.CALLS) Log.calls(ME, "Entering queryDB() ...");
      Connection  conn = null;
      Statement   s = null;
      ResultSet   rs = null;
      XmlDocument doc = null;

      try {
         conn =
            ConnectionManager.getInstance().getConnectionWrapper(descriptor).getConnection();
         s = conn.createStatement();

         String   command = descriptor.getCommand();

         if (descriptor.getInteraction().equalsIgnoreCase("update")) {
            Log.info(ME, "Trying DB update ...");

            int   rowsAffected = s.executeUpdate(command);

            doc = createUpdateDocument(rowsAffected, descriptor);
         }
         else {
            Log.info(ME, "Trying DB select ...");
            rs = s.executeQuery(command);
            doc =
               DBAdapterUtils.createDocument(descriptor.getDocumentrootnode(),
                                             descriptor.getRowrootnode(),
                                             descriptor.getRowlimit(), rs);
         }
      }
      catch (SQLException e) {
         Log.warning(ME, "Exception in query: " + e);

         throw e;
      }

      return doc;
   }

   /**
    * Method declaration
    *
    *
    * @param rowsAffected
    * @param descriptor
    *
    * @return
    *
    * @see
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
    * Method declaration
    *
    *
    * @param exception
    *
    * @return
    *
    * @see
    */
   private XmlDocument createErrorDocument(Exception exception) {
      XmlDocument document = new XmlDocument();
      ElementNode root = (ElementNode) document.createElement("exception");

      document.appendChild(root);

      ElementNode extype = (ElementNode) document.createElement("type");

      root.appendChild(extype);

      Text  extypevalue =
         (Text) document.createTextNode(exception.toString());

      extype.appendChild(extypevalue);

      ElementNode exmessage = (ElementNode) document.createElement("message");

      root.appendChild(exmessage);

      Text  exmessagevalue =
         (Text) document.createTextNode(exception.getMessage());

      exmessage.appendChild(exmessagevalue);

      return document;
   }

   /**
    * Method declaration
    *
    *
    * @param doc
    *
    * @see
    */
   private void notifyCust(XmlDocument doc) {
      String                  qos = "" + "<qos>"
                                    + " <destination queryType='EXACT'>"
                                    + cust + " </destination>" + "</qos>";

      String                  xmlKey =
         "" + "<?xml version='1.0' encoding='ISO-8859-1' ?>" + "<key oid='"
         + ME + "' contentMime = 'text/xml'>" + "</key>";

      ByteArrayOutputStream   bais = new ByteArrayOutputStream();

      try {
         doc.write(bais);

         MessageUnit mu = new MessageUnit(xmlKey, bais.toByteArray());
         String      oid = xmlBlaster.publish(mu, qos);

         System.out.println("Delivered Results...");
      }
      catch (XmlBlasterException e) {
         System.out.println("Exception in notify: " + e.reason);
      }
      catch (Exception e) {
         System.out.println("Exception in notify: " + e);
      }
   }

   /**
    * Method declaration
    *
    *
    * @see
    */
   public void run() {
      XmlDocument document = null;

      try {
         document = createDocument();
      }
      catch (Exception e) {
         document = createErrorDocument(e);
      }

      ConnectionDescriptor descriptor = null;

      Log.trace(ME, "Get connection ...");
      descriptor = new ConnectionDescriptor(document);

      try {
         Log.trace(ME, "Access DB ...");
         document = queryDB(descriptor);
      }
      catch (Exception e) {
         document = createErrorDocument(e);
      }

      if (descriptor.getConfirmation()) {
         notifyCust(document);
      }
   }

}



/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

