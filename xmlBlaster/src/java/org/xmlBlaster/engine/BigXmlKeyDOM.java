/*------------------------------------------------------------------------------
Name:      BigXmlKeyDOM.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MsgUnit xmlKey
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.XmlKeyDom;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import java.util.StringTokenizer;

/**
 * Building a huge DOM tree for all known MsgUnit.xmlKeys.
 * <p />
 * This huge DOM tree contains all meta data about the known messages.<br />
 * Since the message content is a BLOB, messages may only be queried through<br />
 * this DOM tree using XPath.
 * <p />
 * Full text search scanning the content BLOB may be available through MIME based plugins.
 * @author xmlBlaster@marcelruff.info
 */
public class BigXmlKeyDOM extends XmlKeyDom
{
   final private static String ME = "BigXmlKeyDOM";

   private final LogChannel log;

   /**
    * A singleton for each xmlBlaster server.
    */
   BigXmlKeyDOM(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      super(requestBroker);
      this.log = requestBroker.getLog();
   }


   /**
    * Removing a node from the xmlBlaster xmlKey tree
    * @param The node removed
    */
   public org.w3c.dom.Node removeKeyNode(org.w3c.dom.Node node)
   {
      return xmlKeyDoc.getDocumentElement().removeChild(node);
   }


   /**
    * Invoked on message erase() invocation.
    */
   public void messageErase(TopicHandler topicHandler) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Erase event occured ...");
      if (!topicHandler.isUnconfigured()) {
         /*org.w3c.dom.Node node =*/removeKeyNode(topicHandler.getRootNode());
      }
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of BigXmlKeyDOM
    */
   public final String toXml() throws XmlBlasterException {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of BigXmlKeyDOM
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer();
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<BigXmlKeyDOM>");
      try {
         java.io.ByteArrayOutputStream out = XmlNotPortable.write(xmlKeyDoc);
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset).append(Constants.INDENT).append(st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset).append("</BigXmlKeyDOM>\n");

      return sb.toString();
   }
}
