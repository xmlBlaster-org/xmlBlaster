/*------------------------------------------------------------------------------
Name:      BigXmlKeyDOM.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MessageUnit xmlKey
Version:   $Id: BigXmlKeyDOM.java,v 1.14 2000/06/05 10:46:31 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKeyDom;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
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
public class BigXmlKeyDOM extends XmlKeyDom implements I_ClientListener, MessageEraseListener
{
   final private static String ME = "BigXmlKeyDOM";

   private Authenticate authenticate = null;


   /**
    * A singleton for each xmlBlaster server.
    */
   BigXmlKeyDOM(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      super(requestBroker);

      this.requestBroker = requestBroker;
      this.authenticate = authenticate;

      authenticate.addClientListener(this);
      requestBroker.addMessageEraseListener(this);
   }


   /**
    * Removing a node from the xmlBlaster xmlKey tree
    * @param The node removed
    */
   public org.w3c.dom.Node removeKeyNode(org.w3c.dom.Node node)
   {
      return xmlKeyRootNode.removeChild(node);
   }


   /**
    * Invoked on successful client login (interface I_ClientListener)
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString());
   }


   /**
    * Invoked when client does a logout (interface I_ClientListener)
    */
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString());
   }


   /**
    * Invoked on message erase() invocation (interface MessageEraseListener)
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Erase event occured ...");
      MessageUnitHandler msgUnitHandler = e.getMessageUnitHandler();
      org.w3c.dom.Node node = removeKeyNode(msgUnitHandler.getRootNode());
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
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
         xmlKeyDoc.write(out/*, encoding*/); // !!!
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</BigXmlKeyDOM>\n");

      return sb;
   }
}
