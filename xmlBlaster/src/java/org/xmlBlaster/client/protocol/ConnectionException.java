/*------------------------------------------------------------------------------
Name:      ConnectionException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
Version:   $Id: ConnectionException.java,v 1.1 2000/10/18 20:45:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.jutils.JUtilsException;

/**
 * The basic, protocol independent, connection exception handling class for java-xmlBlaster clients.
 * <p />
 * This exception will be thrown in remote RMI calls as well.
 * @author "Marcel Ruff" <ruff@swand.lake.de>
 */
public class ConnectionException extends Exception implements java.io.Serializable
{
   public String id;
   public String reason;

   public ConnectionException(String id, String reason)
   {
      this.id = id;
      this.reason = reason;
   }

   public ConnectionException(JUtilsException e)
   {
      this.id = e.id;
      this.reason = e.reason;
   }

   public String toString()
   {
      return "id=" + id + " reason=" + reason;
   }

   /**
    * Create a XML representation of the Exception.
    * <pre>
    *   &lt;exception id='" + id + "'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;reason>&lt;![cdata[
    *        bla bla
    *      ]]>&lt;/reason>
    *   &lt;/exception>
    * </pre>
    */
   public String toXml()
   {
      StringBuffer buf = new StringBuffer(reason.length() + 256);
      buf.append("<exception id='").append(id).append("'>\n");
      buf.append("   <class>").append(getClass().getName()).append("</class>\n");
      buf.append("   <reason><![CDATA[").append(reason).append("]]></reason>\n");
      buf.append("</exception>");
      return buf.toString();
   }
}
