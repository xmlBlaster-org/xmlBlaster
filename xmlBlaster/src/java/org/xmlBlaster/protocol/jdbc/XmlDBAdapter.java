/*------------------------------------------------------------------------------
 * Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id: XmlDBAdapter.java,v 1.17 2001/02/24 23:16:38 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.client.PublishKeyWrapper;

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
   private byte[]                content;
   private NamedConnectionPool   namedPool = null;


   /**
    * Create the worker instance to handle a single RDBMS request.
    * @param content    The SQL statement
    * @param namedPool  A pool of JDBC connections for the RDBMS users
    */
   public XmlDBAdapter(byte[] content, NamedConnectionPool namedPool)
   {
      this.content = content;
      this.namedPool = namedPool;
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
    * @return One MessageUnit with the content as described above.
    */
   public MessageUnit[] query()
   {
      Document document = null;

      try {
         document = createDocument();
      }
      catch (Exception e) {
         Log.exception(ME+".SqlInitError", e);
         Log.warn(ME+".SqlInitError", new String(content));
         XmlBlasterException ex = new XmlBlasterException(ME+".SqlInitError", e.getMessage());
         return getResponseMessage(ex.toXml().getBytes(), "XmlBlasterException");
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
         return getResponseMessage(e.toXml().getBytes(), "XmlBlasterException");
      }

      if (descriptor.getConfirmation()) {
         return getResponseMessage(document);
      }

      return new MessageUnit[0];
   }


   /**
    * Parse the XML encoded SQL statement.
    */
   private Document createDocument() throws Exception
   {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      //dbf.setCoalescing(true);
      //dbf.setValidating(false);
      //dbf.setIgnoringComments(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      //Log.info(ME, "Tracing " + new String(content));
      ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
      Document doc = db.parse(inputStream);
      return doc;
   }


   /**
    * Query the database.
    */
   private Document queryDB(ConnectionDescriptor descriptor) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering queryDB() ...");
      Connection  conn = null;
      Statement   stmt = null;
      ResultSet   rs = null;
      Document doc = null;

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
         Log.warn(ME, "Exception in query '" + descriptor.getCommand() + "' : " + e);
         throw new XmlBlasterException(ME, e.getMessage());
      }

      return doc;
   }


   /**
    * @param rowsAffected
    * @param descriptor
    */
   private Document createEmptyDocument() throws XmlBlasterException
   {
      // !!! performance: should we have a singleton?
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(false);
      factory.setIgnoringComments(false);
      factory.setNamespaceAware(false);
      try {
         return factory.newDocumentBuilder().newDocument();
      } catch (ParserConfigurationException e) {
         Log.error(ME, "Can't create xml document: " + e.toString());
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
   private MessageUnit[] getResponseMessage(Document doc)
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try { out = XmlNotPortable.write(doc); } catch(IOException e) { Log.error(ME, e.getMessage()); }
      return getResponseMessage(out.toByteArray(), "QueryResults");
   }


   /**
    * Create the result/exception/return message.
    * <p />
    * Note that the Publish...Wrapper are for get() and update() identical
    * @param content
    * @param contentMimeExtended Informative only, "XmlBlasterException" or "QueryResults"
    */
   private MessageUnit[] getResponseMessage(byte[] content, String contentMimeExtended)
   {
      PublishKeyWrapper key = new PublishKeyWrapper("__sys_jdbc."+ME, "text/xml", contentMimeExtended);
      // GetReturnQoS qos = new GetReturnQos(); !!! still missing, Hack

      MessageUnit mu = new MessageUnit(key.toXml(), content, "<qos></qos>");

      if (Log.DUMP) Log.plain("SQL Results...\n" + new String(content));
      MessageUnit[] msgArr = new MessageUnit[1];
      msgArr[0] = mu;
      return msgArr;
   }
}
