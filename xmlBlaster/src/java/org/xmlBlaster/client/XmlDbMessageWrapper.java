/*------------------------------------------------------------------------------
Name:      XmlDbMessageWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping a SQL request with XML.
Version:   $Id: XmlDbMessageWrapper.java,v 1.2 2000/07/03 13:44:02 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;


/**
 * Wrapping a SQL request with XML, to be used in the 'content' of a message.
 * <p />
 * This helps you to send a SQL request to the xmlBlaster JDBC service from James.
 * @see javaclients.jdbc.XmlDbMessageWrapper
 */
public class XmlDbMessageWrapper
 {

   private static String ME = "XmlDbMessageWrapper";
   private String content;
   private String user = null;
   private String passwd = null;
   private String url = null;


   /**
    * Constructor creates XML request.
    * <p />
    * Initialize current query with init() and access it with the toXml() or the toMessage() method.
    * @param user   The login name to database, "postgres"
    * @param passwd The DB password
    * @param url    Any valid JDBC url, e.g. "jdbc:postgresql://24.3.47.214/postgres");
    */
   public XmlDbMessageWrapper(String user, String passwd, String url)
   {
      this.user = user;
      this.passwd = passwd;
      this.url = url;
   }


   /**
    * Set the query properties.
    * <p />
    * @param limit    Maximum results to deliver, 50, used to limit the number of result rows
    * @param confirm  true/false, when set to true, you get an answer
    * @param queryStr Any valid SQL syntax, "select * from intrauser"
    */
   public void initQuery(int limit, boolean confirm, String queryStr)
   {
      init("query", limit, confirm, queryStr);
   }


   /**
    * Set the update/insert/delete properties.
    * <p />
    * @param confirm  true/false, when set to true, you get an answer
    * @param updateStr Any valid SQL syntax, e.g. "INSERT INTO person VALUES(name='Peter')"
    */
   public void initUpdate(boolean confirm, String updateStr)
   {
      init("update", 1, confirm, updateStr);
   }


   /**
    * Set the query properties.
    * <p />
    * Access the message to send with the toMessage() method.
    * You can reuse this object for different init() calls.
    *
    * @param type     "query" or "update", "insert", "delete"
    * @param limit    Maximum results to deliver, 50, used to limit the number of result rows
    * @param confirm  true/false, when set to true, you get an answer
    * @param queryStr Any valid SQL syntax, "select * from intrauser"
    */
   public void init(String type, int limit, boolean confirm, String queryStr)
   {
      if (type.equalsIgnoreCase("insert") || type.equalsIgnoreCase("delete"))
         type = "update";

      content = "" + "<database:adapter>" + " <database:url>" + url
                     + "</database:url>" + " <database:username>" + user
                     + "</database:username>" + " <database:password>"
                     + passwd + "</database:password>"
                     + " <database:interaction type='" + type + "'/>"
                     + " <database:command>" + queryStr
                     + "</database:command>"
                     + " <database:connectionlifespan ttl='1'/>"
                     + " <database:rowlimit max='" + limit + "'/>"
                     + " <database:confirmation confirm='" + confirm
                     + "'/>" + "</database:adapter>";
   }


   /**
    * Returns the 'message content' which is the SQL request coded in XML.
    */
   public String toXml() throws XmlBlasterException
   {
      if (content == null) throw new XmlBlasterException(ME, "Please use init() method before calling toXml().");
      return content;
   }


   /**
    * Creates the complete message for you, which you can publish to xmlBlaster.
    * <p />
    * You will receive the result set wrapped in XML with a asynchronous update().
    */
   public MessageUnit toMessage() throws XmlBlasterException
   {
      PublishQosWrapper qos = new PublishQosWrapper(new Destination("__sys__jdbc"));
      PublishKeyWrapper key = new PublishKeyWrapper("", "text/xml", "SQL_QUERY");
      return new MessageUnit(key.toXml(), toXml().getBytes(), qos.toXml());
   }
}
