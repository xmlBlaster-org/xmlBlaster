/*------------------------------------------------------------------------------
Name:      BigXmlKeyDOM.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MessageUnit xmlKey
Version:   $Id: BigXmlKeyDOM.java,v 1.28 2002/07/09 18:12:09 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.XmlKeyDom;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import java.util.*;
import java.io.*;

/**
 * Building a huge DOM tree for all known MessageUnit.xmlKeys.
 * <p />
 * This huge DOM tree contains all meta data about the known messages.<br />
 * Since the message content is a BLOB, messages may only be queried through<br />
 * this DOM tree using XPath.
 * <p />
 * Full text search scanning the content BLOB may be available through MIME based plugins.
 */
public class BigXmlKeyDOM extends XmlKeyDom
{
   final private static String ME = "BigXmlKeyDOM";

   private final Authenticate authenticate;
   private final LogChannel log;


   /**
    * A singleton for each xmlBlaster server.
    */
   BigXmlKeyDOM(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      super(requestBroker);

      this.authenticate = authenticate;
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
   public void messageErase(MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Erase event occured ...");
      if (msgUnitHandler.isPublishedWithData()) {
         org.w3c.dom.Node node = removeKeyNode(msgUnitHandler.getRootNode());
      }
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of BigXmlKeyDOM
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of BigXmlKeyDOM
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<BigXmlKeyDOM>");
      try {
         java.io.ByteArrayOutputStream out = XmlNotPortable.write(xmlKeyDoc);
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</BigXmlKeyDOM>\n");

      return sb;
   }
}
