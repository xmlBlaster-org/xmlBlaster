/*------------------------------------------------------------------------------
Name:      QueryQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.qos.AccessFilterQos;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.ArrayList;

/**
 * Parsing xml QoS (quality of service) of return query. 
 * <p />
 * <pre>
 *&lt;qos>
 *   &lt;subscribe id='_subId:1'/>    &lt;!-- Force a subscription ID from client side -->
 *   &lt;erase forceDestroy='true'/>  &lt;!-- Kill a MsgUnit even if there are pending updates or subscriptions -->
 *   &lt;meta>false&lt;/meta>         &lt;!-- Don't send me the xmlKey meta data on updates -->
 *   &lt;content>false&lt;/content>   &lt;!-- Don't send me the content data on updates (notify only) -->
 *   &lt;multiSubscribe>false&lt;/multiSubscribe> &lt;!-- Ignore a second subscribe on same oid or XPATH -->
 *   &lt;local>false&lt;/local>       &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *   &lt;initialUpdate>false&lt;/initialUpdate> <!-- don't send an initial message after subscribe -->
 *   &lt;notify>false&lt;/notify>     &lt;!-- Suppress erase event to subcribers -->
 *   &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>
 *                                    &lt;!-- Filters messages i have subscribed as implemented in your plugin -->
 *   &lt;history numEntries='20'/>    &lt;!-- Default is to deliver the current entry (numEntries='1'), '-1' deliver all -->
 *&lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public class QueryQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_QueryQosFactory
{
   private String ME = "QueryQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private  QueryQosData queryQosData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inSubscribe = false;
   private boolean inErase = false;
   private boolean inMeta = false;
   private boolean inContent = false;
   private boolean inMultiSubscribe = false;
   private boolean inLocal = false;
   private boolean inInitialUpdate = false;
   private boolean inNotify = false;
   private boolean inFilter = false;
   private boolean inHistory = false;

   private AccessFilterQos tmpFilter = null;
   private HistoryQos tmpHistory = null;

   /**
    * Can be used as singleton. 
    */
   public QueryQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }

   /**
    * Parses the given xml Qos and returns a QueryQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized QueryQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      queryQosData = new QueryQosData(glob, this, xmlQos, MethodName.UNKNOWN);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return queryQosData;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("subscribe")) {
         if (!inQos)
            return;
         inSubscribe = true;
         if (attrs != null) {
            queryQosData.setSubscriptionId(attrs.getValue("id"));
         }
         return;
      }

      if (name.equalsIgnoreCase("erase")) {
         if (!inQos)
            return;
         inErase = true;
         if (attrs != null) {
            queryQosData.setForceDestroy(new Boolean(attrs.getValue("forceDestroy")).booleanValue());
         }
         return;
      }

      if (name.equalsIgnoreCase("meta")) {
         if (!inQos)
            return;
         inMeta = true;
         queryQosData.setWantMeta(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <meta> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("content")) {
         if (!inQos)
            return;
         inContent = true;
         queryQosData.setWantContent(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <content> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("multiSubscribe")) {
         if (!inQos)
            return;
         inMultiSubscribe = true;
         queryQosData.setMultiSubscribe(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <multiSubscribe> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("local")) {
         if (!inQos)
            return;
         inLocal = true;
         queryQosData.setWantLocal(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <local> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("initialUpdate")) {
         if (!inQos)
            return;
         inInitialUpdate = true;
         queryQosData.setWantInitialUpdate(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <initialUpdate> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("notify")) {
         if (!inQos)
            return;
         inNotify = true;
         queryQosData.setWantNotify(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <notify> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("filter")) {
         inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            queryQosData.addAccessFilter(tmpFilter);
         }
         else
            tmpFilter = null;
         return;
      }

      if (name.equalsIgnoreCase("history")) {
         inHistory = true;
         tmpHistory = new HistoryQos(glob);
         boolean ok = tmpHistory.startElement(uri, localName, name, character, attrs);
         if (ok) {
            queryQosData.setHistoryQos(tmpHistory);
         }
         else
            tmpHistory = null;
         return;
      }


      if (name.equalsIgnoreCase("isErase")) {
         if (!inQos)
            return;
         queryQosData.setMethod(MethodName.ERASE);
         return;
      }
      if (name.equalsIgnoreCase("isGet")) {
         if (!inQos)
            return;
         queryQosData.setMethod(MethodName.GET);
         return;
      }
      if (name.equalsIgnoreCase("isSubscribe")) {
         if (!inQos)
            return;
         queryQosData.setMethod(MethodName.SUBSCRIBE);
         return;
      }
      if (name.equalsIgnoreCase("isUnSubscribe")) {
         if (!inQos)
            return;
         queryQosData.setMethod(MethodName.UNSUBSCRIBE);
         return;
      }

   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if (name.equalsIgnoreCase("subscribe")) {
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("erase")) {
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("meta")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantMeta(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("content")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantContent(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("multiSubscribe")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setMultiSubscribe(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("local")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantLocal(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("initialUpdate")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantInitialUpdate(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("notify")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantNotify(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("history")) {
         inHistory = false;
         if (tmpHistory != null)
            tmpHistory.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("isErase")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isGet")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isSubscribe")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isUnSubscribe")) {
         character.setLength(0);
         return;
      }

      character.setLength(0); // reset data from unknown tags
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(QueryQosData queryQosData, String extraOffset) {
      return writeObject_(queryQosData, extraOffset);
   }

   public static final String writeObject_(QueryQosData queryQosData, String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeQos, UnSubscribeQos, EraseQos, GetQos -->");
      if (queryQosData.getSubscriptionId() != null)
         sb.append(offset).append(" <subscribe id='").append(queryQosData.getSubscriptionId()).append("'/>");

      if (queryQosData.getForceDestroyProp().isModified()) {
         sb.append(offset).append(" <erase forceDestroy='").append(queryQosData.getForceDestroy()).append("'/>");
      }

      if (queryQosData.getMetaProp().isModified()) {
         if (queryQosData.getWantMeta())
            sb.append(offset).append(" <meta/>");
         else
            sb.append(offset).append(" <meta>false</meta>");
      }

      if (queryQosData.getContentProp().isModified()) {
         if (queryQosData.getWantContent())
            sb.append(offset).append(" <content/>");
         else
            sb.append(offset).append(" <content>false</content>");
      }

      if (queryQosData.getMultiSubscribeProp().isModified()) {
         if (queryQosData.getMultiSubscribe())
            sb.append(offset).append(" <multiSubscribe/>");
         else
            sb.append(offset).append(" <multiSubscribe>false</multiSubscribe>");
      }

      if (queryQosData.getLocalProp().isModified()) {
         if (queryQosData.getWantLocal())
            sb.append(offset).append(" <local/>");
         else
            sb.append(offset).append(" <local>false</local>");
      }

      if (queryQosData.getInitialUpdateProp().isModified()) {
         if (queryQosData.getWantInitialUpdate())
            sb.append(offset).append(" <initialUpdate/>");
         else
            sb.append(offset).append(" <initialUpdate>false</initialUpdate>");
      }

      if (queryQosData.getNotifyProp().isModified()) {
         if (queryQosData.getWantNotify())
            sb.append(offset).append(" <notify/>");
         else
            sb.append(offset).append(" <notify>false</notify>");
      }

      AccessFilterQos[] list = queryQosData.getAccessFilterArr();
      for (int ii=0; list != null && ii<list.length; ii++) {
         sb.append(list[ii].toXml(extraOffset+Constants.INDENT));
      }

      HistoryQos historyQos = queryQosData.getHistoryQos();
      if (historyQos != null) {
         sb.append(historyQos.toXml(extraOffset+Constants.INDENT));
      }

      if (queryQosData.getMethod() == MethodName.ERASE) {
         sb.append(offset).append(" <isErase/>");
      }
      else if (queryQosData.getMethod() == MethodName.GET) {
         sb.append(offset).append(" <isGet/>");
      }
      else if (queryQosData.getMethod() == MethodName.SUBSCRIBE) {
         sb.append(offset).append(" <isSubscribe/>");
      }
      else if (queryQosData.getMethod() == MethodName.UNSUBSCRIBE) {
         sb.append(offset).append(" <isUnSubscribe/>");
      }

      sb.append(offset).append("</qos>");

      if (sb.length() < 16)
         return "<qos/>";  // minimal footprint

      return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "QueryQosSaxFactory"
    */
   public String getName() {
      return "QueryQosSaxFactory";
   }
}

