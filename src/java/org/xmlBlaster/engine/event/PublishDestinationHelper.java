package org.xmlBlaster.engine.event;

import java.util.Map;

import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.engine.EventPlugin;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;

/**
 * Helper class to publish messages.
 */
public class PublishDestinationHelper {
   private final EventPlugin eventPlugin;
   // private String destination;
   private String key, qos, keyOid;
   private String contentTemplate;

   /**
    * @param eventPlugin
    * @param configuration
    *           &lt;attribute id='destination.publish'>
    *           "publish.key=&lt;key oid='__queueFillingUp'>&lt;__sys__internal/>&lt;/key>"
    *           ,
    *           "publish.qos=&lt;qos>&lt;expiration lifeTime='0'/>&lt;topic destroyDelay='30000'>&lt;persistence relating='msgUnitStore' type='RAM'/>&lt;queue relating='history' type='RAM'/>&lt;/topic>&lt;/qos>"
    *           &lt;/attribute>
    * 
    * @throws XmlBlasterException
    */
   public PublishDestinationHelper(EventPlugin eventPlugin, String configuration) throws XmlBlasterException {
      this.eventPlugin = eventPlugin;
      @SuppressWarnings("unchecked")
      Map<String, String> map = StringPairTokenizer.parseLineToProperties(configuration);
      if (map.containsKey("publish.key")) {
         this.key = (String) map.get("publish.key");
         MsgKeyData msgKey = this.eventPlugin.getServerScope().getMsgKeyFactory().readObject(this.key);
         this.keyOid = msgKey.getOid();
      }
      if (map.containsKey("publish.qos"))
         this.qos = (String) map.get("publish.qos");
      if (map.containsKey("publish.content"))
         this.contentTemplate = (String) map.get("publish.content");
      else
         this.contentTemplate = "$_{eventType}";
   }

   public String getKeyOid() {
      return this.keyOid;
   }

   public MsgKeyData getPublishKey(String summary, String description,
         String eventType, String errorCode) throws XmlBlasterException {
      if (this.key != null) {
         return this.eventPlugin.getServerScope().getMsgKeyFactory().readObject(this.key);
      }
      //PublishKey publishKey = new PublishKey(engineGlob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
      // TODO: invent an oid depending on the eventType:
      PublishKey publishKey = new PublishKey(this.eventPlugin.getServerScope(), "__sys__Event", "text/plain", "1.0");
      publishKey.setClientTags("<org.xmlBlaster><event/></org.xmlBlaster>");
      return publishKey.getData();
   }

   public MsgQosData getPublishQos(String summary, String description,
         String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
      MsgQosData msgQosData = null;
      if (this.qos != null) {
         msgQosData = this.eventPlugin.getServerScope().getMsgQosFactory().readObject(this.qos);
      }
      else {
         PublishQos publishQos = new PublishQos(this.eventPlugin.getServerScope());
         publishQos.setLifeTime(-1L);
         publishQos.setForceUpdate(true);
         // TODO: Configure history depth to 0 only on first publish
         TopicProperty topicProperty = new TopicProperty(this.eventPlugin.getServerScope());
         HistoryQueueProperty historyQueueProperty = new HistoryQueueProperty(this.eventPlugin.getServerScope(),
               this.eventPlugin.getServerScope().getId());
         historyQueueProperty.setMaxEntriesCache(2);
         historyQueueProperty.setMaxEntries(2);
         topicProperty.setHistoryQueueProperty(historyQueueProperty);
         publishQos.setTopicProperty(topicProperty);
         msgQosData = publishQos.getData();
      }
      if (summary != null && summary.length() > 0)
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_SUMMARY, summary); // "_summary"
      if (description != null && description.length() > 0)
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_DESCRIPTION, description);
      if (eventType != null && eventType.length() > 0)
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_EVENTTYPE, eventType);
      if (errorCode != null && errorCode.length() > 0)
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_ERRORCODE, errorCode);
      if (sessionName != null) {
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_PUBSESSIONID,
               sessionName.getPublicSessionId());
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_SUBJECTID,
               sessionName.getLoginName());
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_ABSOLUTENAME,
               sessionName.getAbsoluteName());
         /*
         // To be backwards compatible with loginEvent=true setting:
         // deprecated:
         msgQosData.addClientProperty("__publicSessionId",
               sessionName.getPublicSessionId());
         msgQosData.addClientProperty("__subjectId",
               sessionName.getLoginName());
         msgQosData.addClientProperty("__absoluteName",
               sessionName.getAbsoluteName());
         // TODO: backwards compatible?
         //msgUnit.setContent(sessionName.getLoginName().getBytes());
         // To be backwards compatible with loginEvent=true setting:
         */
      }
      msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_NODEID, this.eventPlugin.getServerScope().getId());

      return msgQosData;
   }

   public MsgUnit getMsgUnit(String summary, String description,
         String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
      String content = this.eventPlugin.replaceTokens(
         this.contentTemplate, summary, description, eventType, errorCode, sessionName);
      return new MsgUnit(
            getPublishKey(summary, description, eventType, errorCode),
            content.getBytes(),
            getPublishQos(summary, description, eventType, errorCode, sessionName));

   }
}