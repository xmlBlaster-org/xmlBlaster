/*------------------------------------------------------------------------------
Name:      SubscribeQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: SubscribeQoS.java,v 1.8 2002/03/13 16:41:21 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.QueueProperty;
import org.xmlBlaster.engine.helper.Constants;
import org.xml.sax.Attributes;
import java.util.Vector;


/**
 * Handling of subscribe() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the subscribe() method<br />
 * They are needed to control the xmlBlaster
 */
public class SubscribeQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "SubscribeQoS";

   // helper flags for SAX parsing

   /** <meta>false</meta> Don't send me the xmlKey meta data on updates */
   private boolean meta = true;     
   /** <content>false</content> Don't send me the content data on updates (notify only) */
   private boolean content = true;
   /** <local>false</local>  Inhibit the delivery of messages to myself if i have published it */
   private boolean local = true;

   private transient QueueProperty tmpProp = null;
   protected Vector queuePropertyVec = new Vector();
   private transient boolean inQueue = false;
   private transient CallbackAddress tmpAddr = null;
   private transient boolean inCallback = false;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public SubscribeQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Creating SubscribeQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }


   public QueueProperty[] getQueueProperties()
   {
      if (queuePropertyVec.size() < 1) {
         queuePropertyVec.addElement(new QueueProperty(Constants.RELATING_SESSION)); // defaults to session queue
      }
      QueueProperty[] arr = new QueueProperty[queuePropertyVec.size()];
      queuePropertyVec.toArray(arr);
      return arr;
   }


   /**
    * Does client wants to have the XmlKey meta tags on update?
    *
    * @return true if full XmlKey is sent
    *         false if only <key> tag with its attributes is sent
    */
   public final boolean sendMeta()
   {
      return meta;
   }


   /**
    * Does client wish the content data on updates?
    *
    * @return true if clients wishes the content on message update
    *         false if client wishes empty content updates (NOTIFICATION style)
    */
   public final boolean sendContent()
   {
      return content;
   }


   /**
    * Inhibit the delivery of messages to myself if i have published it (and am a subscriber as well)?
    * @return true/false
    */
   public final boolean sendLocal()
   {
      return local;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;

      if (name.equalsIgnoreCase("meta")) {
         meta = true;
         return;
      }
      if (name.equalsIgnoreCase("content")) {
         content = true;
         return;
      }
      if (name.equalsIgnoreCase("local")) {
         local = true;
         return;
      }

      if (inCallback) {
         tmpAddr.startElement(uri, localName, name, character, attrs);
         return;
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
         if (!inQueue) {
            tmpProp = new QueueProperty(null); // Use default queue properties for this callback address
            queuePropertyVec.addElement(tmpProp);
         }
         tmpAddr = new CallbackAddress();
         tmpAddr.startElement(uri, localName, name, character, attrs);
         tmpProp.setCallbackAddress(tmpAddr);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = true;
         tmpProp = new QueueProperty(null);
         queuePropertyVec.addElement(tmpProp);
         tmpProp.startElement(uri, localName, name, attrs);
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name)
   {
      super.endElement(uri, localName, name);

      if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if (name.equalsIgnoreCase("meta")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            meta = new Boolean(tmp).booleanValue();
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("content")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            content = new Boolean(tmp).booleanValue();
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("local")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            local = new Boolean(tmp).booleanValue();
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

      if (inCallback) {
         if (name.equalsIgnoreCase("callback")) inCallback = false;
         tmpAddr.endElement(uri, localName, name, character);
         return;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<" + ME + ">");
      if (!meta)
         sb.append(offset).append("   <meta>false</meta>");
      if (!content)
         sb.append(offset).append("   <content>false</content>");
      if (!local)
         sb.append(offset).append("   <local>false</local>");
      sb.append(offset + "</" + ME + ">\n");

      return sb.toString();
   }
}
