/*------------------------------------------------------------------------------
Name:      XmlBlasterException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
Version:   $Id: XmlBlasterException.java,v 1.1 2000/06/13 13:02:06 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * The basic exception handling class for xmlBlaster.
 * <p />
 * @author "Marcel Ruff" <ruff@swand.lake.de>
 */
public class XmlBlasterException extends Exception
{
   public String id;
   public String reason;

   public XmlBlasterException(String id, String reason)
   {
      this.id = id;
      this.reason = reason;
   }

   public String toString()
   {
      return toXml();
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
