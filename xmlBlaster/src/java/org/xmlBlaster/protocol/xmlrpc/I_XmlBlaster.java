/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster for the xml-rpc protocol
Version:   $Id: I_XmlBlaster.java,v 1.2 2000/09/01 21:18:55 laghi Exp $
Author:    "Michele Laghi" <michele.laghi@attglobal.net>
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.Vector;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This is the native interface to xmlBlaster for the xml-rpc protocol.
 * <p />
 * @see xmlBlaster.idl
 * @see org.xmlBlaster.engine.RequestBroker
 * @see org.xmlBlaster.protocol.I_XmlBlaster
 * @author michele.laghi@attglobal.net
 */
public interface I_XmlBlaster
{
   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String subscribe (String sessionId, String xmlKey_literal, 
                           String subscribeQoS_literal) throws XmlBlasterException;


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe (String sessionId, String xmlKey_literal, 
                           String unSubscribeQoS_literal) throws XmlBlasterException;


   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish (String sessionId, String xmlKey_literal, Vector msgUnitWrap,
                          String publishQoS_literal)
      throws XmlBlasterException;


   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish (String sessionId, Vector msgUnitWrap)
      throws XmlBlasterException;



   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public Vector publishArr (String sessionId, Vector msgUnitArr)
      throws XmlBlasterException;
   // String[] is not supported by helma

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public Vector erase (String sessionId, String xmlKey_literal, String eraseQoS_literal)
      throws XmlBlasterException;
   // String[] is not supported by helma

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public Vector get (String sessionId, String xmlKey_literal, String getQoS_literal)
      throws XmlBlasterException;
   // note that Vector[] is not a supported type in xml-rpc. That's why I opted
   // for a Vector of Vectors as a return type.

   /**
    * Check the server.
    * <p />
    */
   public int ping () throws XmlBlasterException;



   //   public String toXml() throws XmlBlasterException;

   public String toXml(String extraOffset) throws XmlBlasterException;


}

