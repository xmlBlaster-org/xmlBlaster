/*------------------------------------------------------------------------------
 * Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id: XmlDBAdapter.java,v 1.30 2003/03/24 16:13:20 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.enum.Constants;
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
import java.io.OutputStream;
import java.io.IOException;

/**
 * For every database access, an instance of this class does the work in a dedicated thread.
 */
public class XmlDBAdapter
{
   private static final String   ME = "XmlDBAdapter";
   private final Global          glob;
   private final LogChannel      log;
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
      this.log = glob.getLog("jdbc");
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
         log.exception(ME+".SqlInitError", e);
         log.warn(ME+".SqlInitError", "Problems with request: " + new String(content));
         XmlBlasterException ex = new XmlBlasterException(ME+".SqlInitError", e.getMessage());
         return getResponseMessage(ex.toXml().getBytes(), "XmlBlasterException");
      }

      ConnectionDescriptor descriptor = null;

      if (log.TRACE) log.trace(ME, "Get connection ...");
      descriptor = new ConnectionDescriptor(document);

      try {
         if (log.TRACE) log.trace(ME, "Access DB ...");
         document = queryDB(descriptor);
      }
      catch (XmlBlasterException e) {
         //log.error(e.id, "query failed: " + e.getMessage());
         return getResponseMessage(e.toXml().getBytes(), "XmlBlasterException");
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
      if (log.TRACE) log.trace(ME, "Tracing " + new String(content));
      ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
      Document doc = db.parse(inputStream);
      return doc;
   }


   /**
    * Query the database.
    */
   private Document queryDB(ConnectionDescriptor descriptor) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering queryDB() ...");
      Connection  conn = null;
      Statement   stmt = null;
      ResultSet   rs = null;
      Document doc = null;

      try {
         conn =  namedPool.reserve(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword()); // using default connection pool properties
         stmt = conn.createStatement();

         String command = descriptor.getCommand();

         if (descriptor.getInteraction().equalsIgnoreCase("update")) {
            if (log.TRACE) log.trace(ME, "Trying DB update '" + command + "' ...");

            int rowsAffected = stmt.executeUpdate(command);

            doc = createUpdateDocument(rowsAffected, descriptor);
         }
         else {
            if (log.TRACE) log.trace(ME, "Trying SQL query '" + command + "' ...");
            rs = stmt.executeQuery(command);
            doc =
               DBAdapterUtils.createDocument(descriptor.getDocumentrootnode(),
                                             descriptor.getRowrootnode(),
                                             descriptor.getRowlimit(), rs);
         }
         if (log.TRACE) log.trace(ME, "Query successful done, connection released");
      }
      catch (SQLException e) {
         String str = "SQLException in query '" + descriptor.getCommand() + "' : " + e;
         log.warn(ME, str + ": sqlSTATE=" + e.getSQLState() + " we destroy the connection in case it's stale");
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
         log.error(ME, str + ": We destroy the connection in case it's stale");
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
            log.warn(ME, "Closing of stmt failed: " + e.toString());
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
      //kkrafft2 (09/04/2002): the DocumentBuilderFactory should be switched by xmlBlaster.properties
      String factoryBackup = System.getProperty("javax.xml.parsers.DocumentBuilderFactory");
      String newFactory = glob.getProperty().get("javax.xml.parsers.DocumentBuilderFactory", "org.apache.crimson.jaxp.DocumentBuilderFactoryImpl");
      System.setProperty("javax.xml.parsers.DocumentBuilderFactory",newFactory);
      // !!! performance: should we have a singleton?
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // restory factory
      if( factoryBackup != null )
         System.setProperty("javax.xml.parsers.DocumentBuilderFactory",factoryBackup);


      factory.setValidating(false);
      factory.setIgnoringComments(false);
      factory.setNamespaceAware(false);
      try {
         return factory.newDocumentBuilder().newDocument();
      } catch (ParserConfigurationException e) {
         log.error(ME, "Can't create xml document: " + e.toString());
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
      try { out = XmlNotPortable.write(doc); } catch(IOException e) { log.error(ME, "getResponseMessage failed: " + e.getMessage()); }
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

      if (log.DUMP) log.plain(ME, "SQL Results...\n" + new String(content));
      MsgUnit[] msgArr = new MsgUnit[1];
      msgArr[0] = mu;
      return msgArr;
   }
}
