/*------------------------------------------------------------------------------
Name:      XmlDbMessageWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;


/**
 * Wrapping a SQL request with XML, to be used in the 'content' of a message.
 * <p />
 * This helps you to send a SQL request to the xmlBlaster JDBC service from James.
 * @see org.xmlBlaster.protocol.jdbc.ConnectionDescriptor
 */
public class XmlDbMessageWrapper
 {
   private final Global glob;
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
   public XmlDbMessageWrapper(Global glob, String user, String passwd, String url)
   {
      this.glob = glob;
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

      StringBuffer tmp = new StringBuffer();
      tmp.append("<database:adapter xmlns:database='http://www.xmlBlaster.org/jdbc'>");
      tmp.append(" <database:url>").append(url).append("</database:url>");
      tmp.append(" <database:username>").append(user).append("</database:username>");
      tmp.append(" <database:password>").append(passwd).append("</database:password>");
      tmp.append(" <database:interaction type='").append(type).append("'/>");
      tmp.append(" <database:command><![CDATA[").append(queryStr).append("]]></database:command>");
      tmp.append(" <database:connectionlifespan ttl='1'/>");
      tmp.append(" <database:rowlimit max='").append(limit).append("'/>");
      tmp.append(" <database:confirmation confirm='").append(confirm).append("'/>");
      tmp.append("</database:adapter>");
      content = tmp.toString();
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
   public MsgUnit toMessage() throws XmlBlasterException
   {
      PublishQos qos = new PublishQos(glob, new Destination(new SessionName(Global.instance(), "__sys__jdbc")));
      PublishKey key = new PublishKey(glob, "", "text/xml", "SQL_QUERY");
      return new MsgUnit(key, toXml().getBytes(), qos);
   }
}
