/*------------------------------------------------------------------------------
 * Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Global;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.engine.qos.GetReturnQosServer;
import org.xmlBlaster.client.key.PublishKey;

import org.w3c.dom.Text;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * For every database access, an instance of this class does the work in a dedicated thread.
 */
public class XmlDBAdapter
{
   private static final String   ME = "XmlDBAdapter";
   private final Global          glob;
   private static Logger log = Logger.getLogger(XmlDBAdapter.class.getName());
   private byte[]                content;
   private NamedConnectionPool   namedPool = null;


   /**
    * Create the worker instance to handle a single RDBMS request.
    * @param content    The SQL statement
    * @param namedPool  A pool of JDBC connections for the RDBMS users
    */
   public XmlDBAdapter(Global glob, byte[] content, NamedConnectionPool namedPool)
   {
      this.glob = glob;

      this.content = content;
      this.namedPool = namedPool;
      if (this.namedPool == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("XmlDBAdapter: namedPool is null, check your -ProtocolPlugin[JDBC][1.0] configuration");
      }
   }

   /**
    * Query the database.
    * <p />
    * INSERT, UPDATE, CREATE results look like this (content variable):
    * <pre>
    *&lt;?xml version="1.0" encoding="UTF-8"?>
    *&lt;dbadapterresults>
    *     &lt;row>
    *        1 row(s) were affected during the update.
    *     &lt;/row>
    *&lt;/dbadapterresults>
    * </pre>
    *
    * SELECT results look like this:
    * <pre>
    *&lt;?xml version="1.0" encoding="UTF-8"?>
    *&lt;dbadapterresults>
    *  &lt;desc>
    *     &lt;numcolumns>2&lt;/numcolumns>
    *     &lt;columnnames>
    *        &lt;column>NAME&lt;/column>
    *        &lt;column>AGE&lt;/column>
    *     &lt;/columnnames>
    *     &lt;rownum>2&lt;/rownum>
    *  &lt;/desc>
    *  &lt;results>
    *     &lt;row>
    *        &lt;NAME>
    *        Ben
    *        &lt;/NAME>
    *        &lt;AGE>
    *        6
    *        &lt;/AGE>
    *     &lt;/row>
    *     &lt;row>
    *        &lt;NAME>
    *        Tim
    *        &lt;/NAME>
    *        &lt;AGE>
    *        8
    *        &lt;/AGE>
    *     &lt;/row>
    *  &lt;/results>
    *&lt;/dbadapterresults>
    * </pre>
    *
    * Exceptions like this:
    * <pre>
    *   &lt;exception id='" + id + "'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;reason>&lt;![cdata[
    *        bla bla
    *      ]]>&lt;/reason>
    *   &lt;/exception>
    * </pre>
    *
    * @return One MsgUnitRaw with the content as described above.
    */
   public MsgUnit[] query()
   {
      Document document = null;

      try {
         document = createDocument();
      }
      catch (Exception e) {
         log.warning("Problems with request: " + new String(content));
         XmlBlasterException ex = new XmlBlasterException(ME+".SqlInitError", e.getMessage());
         return getResponseMessage(ex.toXml().getBytes(), "XmlBlasterException");
      }

      ConnectionDescriptor descriptor = null;


      try {
         if (log.isLoggable(Level.FINE)) log.fine("Get connection ...");
         descriptor = new ConnectionDescriptor(document);
         if (log.isLoggable(Level.FINE)) log.fine("Access DB ...");
         document = queryDB(descriptor);
      }
      catch (XmlBlasterException e) {
         //log.error(e.id, "query failed: " + e.getMessage());
         return getResponseMessage(e.toXml().getBytes(), "XmlBlasterException");
      }
      catch (Throwable e) {
         //log.error(e.id, "query failed: " + e.getMessage());
         return getResponseMessage(e.toString().getBytes(), "Exception");
      }

      if (descriptor.getConfirmation()) {
         return getResponseMessage(document);
      }

      return new MsgUnit[0];
   }


   /**
    * Parse the XML encoded SQL statement.
    */
   private Document createDocument() throws Exception
   {
      DocumentBuilderFactory dbf = glob.getDocumentBuilderFactory();
      dbf.setNamespaceAware(true);
      //dbf.setCoalescing(true);
      //dbf.setValidating(false);
      //dbf.setIgnoringComments(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      if (log.isLoggable(Level.FINE)) log.fine("Tracing " + new String(content));
      ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
      Document doc = db.parse(inputStream);
      return doc;
   }


   /**
    * Query the database.
    */
   private Document queryDB(ConnectionDescriptor descriptor) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering queryDB() ...");
      Connection  conn = null;
      Statement   stmt = null;
      ResultSet   rs = null;
      Document doc = null;

      try {
         conn =  namedPool.reserve(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword()); // using default connection pool properties
         stmt = conn.createStatement();

         String command = descriptor.getCommand();

         if (descriptor.getInteraction().equalsIgnoreCase("update")) {
            if (log.isLoggable(Level.FINE)) log.fine("Trying DB update '" + command + "' ...");

            int rowsAffected = stmt.executeUpdate(command);

            doc = createUpdateDocument(rowsAffected, descriptor);
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine("Trying SQL query '" + command + "' ...");
            rs = stmt.executeQuery(command);
            doc =
               DBAdapterUtils.createDocument(descriptor.getDocumentrootnode(),
                                             descriptor.getRowrootnode(),
                                             descriptor.getRowlimit(), rs);
         }
         if (log.isLoggable(Level.FINE)) log.fine("Query successful done, connection released");
      }
      catch (SQLException e) {
         String str = "SQLException in query '" + descriptor.getCommand() + "' : " + e;
         log.warning(str + ": sqlSTATE=" + e.getSQLState() + " we destroy the connection in case it's stale");
         // If io exception (we lost database server) release connection
         // But how can we find out if it is a connection problem or an SQL
         // error of a wrong SQL statement?
         // Probably sqlState can tell us, but this is not implemented:
         String sqlState = e.getSQLState(); // DatabaseMetaData method getSQLStateType can be used to discover whether the driver returns the XOPEN type or the SQL 99 type
         // To be on the save side we always destroy the connection:
         namedPool.eraseConnection(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword(), conn);
         conn = null;
         throw new XmlBlasterException(ME, str);
      }
      catch (Throwable e) {
         e.printStackTrace();
         String str = "Unexpected exception in query '" + descriptor.getCommand() + "' : " + e;
         log.severe(str + ": We destroy the connection in case it's stale");
         namedPool.eraseConnection(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword(), conn);
         conn = null;
         throw new XmlBlasterException(ME, str);
      }
      finally {
         try {
            if (rs!=null) rs.close();
            if (stmt!=null) stmt.close();
         }
         catch (SQLException e) {
            log.warning("Closing of stmt failed: " + e.toString());
         }
         if (conn!=null) namedPool.release(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword(), conn);
      }

      return doc;
   }


   /**
    * @param rowsAffected
    * @param descriptor
    */
   private Document createEmptyDocument() throws XmlBlasterException
   {
      DocumentBuilderFactory factory = this.glob.getDocumentBuilderFactory();

      factory.setValidating(false);
      factory.setIgnoringComments(false);
      factory.setNamespaceAware(false);
      try {
         return factory.newDocumentBuilder().newDocument();
      } catch (ParserConfigurationException e) {
         log.severe("Can't create xml document: " + e.toString());
         throw new XmlBlasterException(ME, "Can't create xml document: " + e.toString());
      }
   }


   /**
    * @param rowsAffected
    * @param descriptor
    */
   private Document createUpdateDocument(int rowsAffected, ConnectionDescriptor descriptor) throws XmlBlasterException
   {
      Document document = createEmptyDocument();

      Element root = (Element)document.createElement(descriptor.getDocumentrootnode());
      document.appendChild(root);
      Element row =  (Element)document.createElement(descriptor.getRowrootnode());
      root.appendChild(row);
      Text rows = (Text)document.createTextNode(rowsAffected + " row(s) were affected during the update.");
      row.appendChild(rows);
      return document;
   }


   /**
    * SELECT results in XML.
    */
   private MsgUnit[] getResponseMessage(Document doc)
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try { out = XmlNotPortable.write(doc); } catch(IOException e) { log.severe("getResponseMessage failed: " + e.getMessage()); }
      return getResponseMessage(out.toByteArray(), "QueryResults");
   }


   /**
    * Create the result/exception/return message.
    * <p />
    * Note that the Publish...Wrapper are for get() and update() identical
    * @param content
    * @param contentMimeExtended Informative only, "XmlBlasterException" or "QueryResults"
    */
   private MsgUnit[] getResponseMessage(byte[] content, String contentMimeExtended)
   {
      PublishKey key = new PublishKey(glob, "__sys_jdbc."+ME, "text/xml", contentMimeExtended);
      GetReturnQosServer retQos = new GetReturnQosServer(glob, null, Constants.STATE_OK);

      MsgUnit mu = new MsgUnit(key.getData(), content, retQos.getData());

      if (log.isLoggable(Level.FINEST)) log.finest("SQL Results...\n" + new String(content));
      MsgUnit[] msgArr = new MsgUnit[1];
      msgArr[0] = mu;
      return msgArr;
   }
}
