/*------------------------------------------------------------------------------
Name:      QueryQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;
import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;

import org.xml.sax.*;

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
 *   &lt;updateOneway>false&lt;/updateOneway> <!-- use the acknowledged update() or updateOneway() for callbacks -->
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
   private final Global glob;
   private static Logger log = Logger.getLogger(QueryQosSaxFactory.class.getName());

   private  QueryQosData queryQosData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   /*
   private boolean inSubscribe = false;
   private boolean inErase = false;
   private boolean inMeta = false;
   private boolean inContent = false;
   private boolean inMultiSubscribe = false;
   private boolean inLocal = false;
   private boolean inInitialUpdate = false;
   private boolean inUpdateOneway = false;
   private boolean inNotify = false;
   private boolean inFilter = false;
   private boolean inQuerySpec = false;
   private boolean inHistory = false;
   private boolean inIsPersistent = false;
   */
   private AccessFilterQos tmpFilter = null;
   private QuerySpecQos tmpQuerySpec = null;
   private HistoryQos tmpHistory = null;
   
   /**
    * Can be used as singleton. 
    */
   public QueryQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;

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

      this.tmpFilter = null;
      this.tmpQuerySpec = null;
      this.tmpHistory = null;
      
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

      if (name.equalsIgnoreCase(MethodName.SUBSCRIBE.getMethodName())) { // "subscribe"
         if (!inQos)
            return;
         //this.inSubscribe = true;
         if (attrs != null) {
            queryQosData.setSubscriptionId(attrs.getValue("id"));
         }
         return;
      }

      if (name.equalsIgnoreCase(MethodName.ERASE.getMethodName())) { // "erase"
         if (!inQos)
            return;
//       this.inErase = true;
         if (attrs != null) {
            queryQosData.setForceDestroy(new Boolean(attrs.getValue("forceDestroy")).booleanValue());
         }
         return;
      }

      if (name.equalsIgnoreCase("meta")) {
         if (!inQos)
            return;
//       this.inMeta = true;
         queryQosData.setWantMeta(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <meta> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("content")) {
         if (!inQos)
            return;
//       this.inContent = true;
         queryQosData.setWantContent(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <content> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("multiSubscribe")) {
         if (!inQos)
            return;
//       this.inMultiSubscribe = true;
         queryQosData.setMultiSubscribe(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <multiSubscribe> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("local")) {
         if (!inQos)
            return;
//       this.inLocal = true;
         queryQosData.setWantLocal(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <local> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("initialUpdate")) {
         if (!inQos)
            return;
//       this.inInitialUpdate = true;
         queryQosData.setWantInitialUpdate(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <initialUpdate> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("updateOneway")) {
         if (!inQos)
            return;
//       this.inUpdateOneway = true;
         queryQosData.setWantUpdateOneway(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <updateOneway> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("notify")) {
         if (!inQos)
            return;
//       this.inNotify = true;
         queryQosData.setWantNotify(true);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warning("Ignoring sent <notify> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("filter")) {
//       this.inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            queryQosData.addAccessFilter(tmpFilter);
         }
         else
            tmpFilter = null;
         return;
      }

      if (name.equalsIgnoreCase("querySpec")) {
//       this.this.inQuerySpec = true;
         this.tmpQuerySpec = new QuerySpecQos(glob);
         boolean ok = this.tmpQuerySpec.startElement(uri, localName, name, character, attrs);
         if (ok) {
            this.queryQosData.addQuerySpec(this.tmpQuerySpec);
         }
         else
            this.tmpQuerySpec = null;
         return;
      }

      if (name.equalsIgnoreCase("history")) {
//       this.inHistory = true;
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
      
      if (name.equalsIgnoreCase("persistent")) {
         if (!inQos)
            return;
//       this.inIsPersistent = true;
         character.setLength(0);
         queryQosData.setPersistent(true);
         return;
      }

   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (super.endElementBase(uri, localName, name) == true) {
         if (name.equalsIgnoreCase("clientProperty")) {
            this.queryQosData.addClientProperty(this.clientProperty);
         }
         return;
      }

      if (name.equalsIgnoreCase(MethodName.SUBSCRIBE.getMethodName())) { // "subscribe"
//       this.inSubscribe = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase(MethodName.ERASE.getMethodName())) { // "erase"
//       this.inErase = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("meta")) {
//       this.inMeta = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantMeta(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("content")) {
//       this.inContent = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantContent(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("multiSubscribe")) {
//       this.inMultiSubscribe = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setMultiSubscribe(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("local")) {
//       this.inLocal = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantLocal(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("initialUpdate")) {
//       this.inInitialUpdate = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantInitialUpdate(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("updateOneway")) {
//       this.inUpdateOneway = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantUpdateOneway(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("notify")) {
//       this.inNotify = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setWantNotify(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("filter")) {
//       this.inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("querySpec")) {
//       this.this.inQuerySpec = false;
         if (this.tmpQuerySpec != null)
            this.tmpQuerySpec.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("history")) {
//       this.inHistory = false;
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

      if(name.equalsIgnoreCase("persistent")) {
//       this.inIsPersistent = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            queryQosData.setPersistent(new Boolean(tmp).booleanValue());
         // if (log.isLoggable(Level.FINE)) log.trace(ME, "Found persistent = " + msgQosData.isPersistent());
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
   public final String writeObject(QueryQosData queryQosData, String extraOffset, Properties props) {
      return writeObject_(queryQosData, extraOffset, props);
   }

   public static final String writeObject_(QueryQosData queryQosData, String extraOffset, Properties props) {
      XmlBuffer sb = new XmlBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeQos, UnSubscribeQos, EraseQos, GetQos -->");
      if (queryQosData.getSubscriptionId() != null)
         sb.append(offset).append(" <").append(MethodName.SUBSCRIBE.getMethodName()).append(" id='").appendAttributeEscaped(queryQosData.getSubscriptionId()).append("'/>");

      if (queryQosData.getForceDestroyProp().isModified()) {
         sb.append(offset).append(" <").append(MethodName.ERASE.getMethodName()).append(" forceDestroy='").append(queryQosData.getForceDestroy()).append("'/>");
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

      if (queryQosData.getUpdateOnewayProp().isModified()) {
         if (queryQosData.getWantUpdateOneway())
            sb.append(offset).append(" <updateOneway/>");
         else
            sb.append(offset).append(" <updateOneway>false</updateOneway>");
      }

      if (queryQosData.getNotifyProp().isModified()) {
         if (queryQosData.getWantNotify())
            sb.append(offset).append(" <notify/>");
         else
            sb.append(offset).append(" <notify>false</notify>");
      }

      if (queryQosData.getPersistentProp().isModified()) {
         if (queryQosData.isPersistent())
            sb.append(offset).append(" <persistent/>");
         else
            sb.append(offset).append(" <persistent>false</persistent>");
      }


      AccessFilterQos[] list = queryQosData.getAccessFilterArr();
      for (int ii=0; list != null && ii<list.length; ii++) {
         sb.append(list[ii].toXml(extraOffset+Constants.INDENT));
      }

      QuerySpecQos[] querySpeclist = queryQosData.getQuerySpecArr();
      for (int ii=0; querySpeclist != null && ii< querySpeclist.length; ii++) {
         sb.append(querySpeclist[ii].toXml(extraOffset+Constants.INDENT));
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

      sb.append(queryQosData.writePropertiesXml(extraOffset+Constants.INDENT));
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

