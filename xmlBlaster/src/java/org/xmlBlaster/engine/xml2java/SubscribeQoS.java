/*------------------------------------------------------------------------------
Name:      SubscribeQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: SubscribeQoS.java,v 1.15 2002/05/16 15:34:50 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.Constants;
import org.xml.sax.Attributes;
import java.util.Vector;


/**
 * Handling of subscribe() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the subscribe() method<br />
 * They are needed to control xmlBlaster
 * <p />
 * A full example:
 * <pre>
 *  &lt;qos>
 *     &lt;meta>false&lt;/meta>
 *     &lt;content>false&lt;/content>
 *     &lt;local>false&lt;/local>
 *     &lt;filter type='ContentLength' version='1.0'>
 *        8000
 *     &lt;/filter>
 *     &lt;filter type='ContainsChecker' version='7.1' xy='true'>
 *        bug
 *     &lt;/filter>
 *     &lt;filter>
 *        invalid filter without type
 *     &lt;/filter>
 *     &lt;queue relating='unrelated' maxMsg='1000' maxSize='4000' onOverflow='deadLetter'>
 *        &lt;callback type='EMAIL' sessionId='sd3lXjs9Fdlggh'>
 *           et@mars.universe   &lt;!-- Sends messages to et with specified queue attributes -->
 *        &lt;/callback>
 *     &lt;/queue>
 *  &lt;/qos>
 * </pre>
 */
public class SubscribeQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "SubscribeQoS";

   private final Global glob;

   // helper flags for SAX parsing

   /** <meta>false</meta> Don't send me the xmlKey meta data on updates */
   private boolean meta = true;     
   /** <content>false</content> Don't send me the content data on updates (notify only) */
   private boolean content = true;
   /** <local>false</local>  Inhibit the delivery of messages to myself if i have published it */
   private boolean local = true;

   private transient AccessFilterQos tmpFilter = null;
   protected Vector filterVec = null;                      // To collect the filter when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array
   private transient boolean inFilter = false;

   private transient CbQueueProperty tmpProp = null;
   protected Vector queuePropertyVec = new Vector();
   protected transient CbQueueProperty[] queuePropertyArr = null; // To cache the properties in an array
   private transient boolean inQueue = false;
   private transient CallbackAddress tmpAddr = null;
   private transient boolean inCallback = false;

   private String nodeId = null; // TODO: set in constructor


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public SubscribeQoS(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      this.glob = glob;
      if (Log.TRACE) Log.trace(ME, "Creating SubscribeQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }

   /**
    * Return the subscribe filters or null if none is specified. 
    */
   public final AccessFilterQos[] getFilterQos()
   {
      if (filterArr != null || filterVec == null || filterVec.size() < 1)
         return filterArr;

      filterArr = new AccessFilterQos[filterVec.size()];
      filterVec.toArray(filterArr);
      return filterArr;
   }

   /**
    * The properties of the specified queues
    */
   public CbQueueProperty[] getQueueProperties()
   {
      if (queuePropertyArr != null)
         return queuePropertyArr;

      if (queuePropertyVec.size() < 1)
         queuePropertyVec.addElement(new CbQueueProperty(glob, Constants.RELATING_SESSION, nodeId)); // defaults to session queue

      queuePropertyArr = new CbQueueProperty[queuePropertyVec.size()];
      queuePropertyVec.toArray(queuePropertyArr);
      return queuePropertyArr;
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
            tmpProp = new CbQueueProperty(glob, null, nodeId); // Use default queue properties for this callback address
            queuePropertyVec.addElement(tmpProp);
         }
         tmpAddr = new CallbackAddress(glob);
         tmpAddr.startElement(uri, localName, name, character, attrs);
         tmpProp.setCallbackAddress(tmpAddr);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = true;
         tmpProp = new CbQueueProperty(glob, null, nodeId);
         queuePropertyVec.addElement(tmpProp);
         tmpProp.startElement(uri, localName, name, attrs);
         return;
      }

      if (name.equalsIgnoreCase("filter")) {
         inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            if (filterVec == null) filterVec = new Vector();
            filterVec.addElement(tmpFilter);
         }
         else
            tmpFilter = null;
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

      if (name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
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
      StringBuffer sb = new StringBuffer(512);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<").append("qos").append("> <!-- SubscribeQos -->");
      if (!meta)
         sb.append(offset).append("   <meta>false</meta>");
      if (!content)
         sb.append(offset).append("   <content>false</content>");
      if (!local)
         sb.append(offset).append("   <local>false</local>");

      AccessFilterQos[] filterArr = getFilterQos();
      for (int ii=0; filterArr != null && ii<filterArr.length; ii++)
         sb.append(filterArr[ii].toXml(extraOffset+"   "));

      for (int ii=0; ii<queuePropertyVec.size(); ii++) {
         CbQueueProperty ad = (CbQueueProperty)queuePropertyVec.elementAt(ii);
         sb.append(ad.toXml(extraOffset+"   "));
      }
      sb.append(offset).append("</").append("qos").append(">");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.engine.xml2java.SubscribeQoS */
   public static void main(String[] args)
   {
      try {
         SubscribeQoS qos = null;
         String xml =
            "<qos>\n" +
            "   <meta>false</meta>\n" +
            "   <content>false</content>\n" +
            "   <local>false</local>\n" +
            "   <filter type='ContentLength' version='1.0'>\n" +
            "      8000\n" +
            "   </filter>\n" +
            "   <filter type='ContainsChecker' version='7.1' xy='true'>\n" +
            "      bug\n" +
            "   </filter>\n" +
            "   <filter>\n" +
            "      invalid filter without type\n" +
            "   </filter>\n" +
            "   <queue relating='unrelated' maxMsg='1000' maxSize='4000' onOverflow='deadLetter'>\n" +
            "      <callback type='EMAIL' sessionId='sd3lXjs9Fdlggh'>\n" +
            "         et@mars.universe   <!-- Sends messages to et with specified queue attributes -->\n" +
            "      </callback>\n" +
            "   </queue>\n" +
            "</qos>\n";
         System.out.println("=====Original XML========\n");
         System.out.println(xml);
         qos = new SubscribeQoS(new Global(args), xml);
         System.out.println("=====Parsed and dumped===\n");
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("TestFailed", e.toString());
      }
   }
}
