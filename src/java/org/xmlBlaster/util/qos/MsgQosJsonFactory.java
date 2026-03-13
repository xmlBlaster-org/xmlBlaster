package org.xmlBlaster.util.qos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Global.FactoryType;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

public class MsgQosJsonFactory implements I_MsgQosFactory {
   private final Global glob;
   private static Logger log = Logger.getLogger(MsgQosJsonFactory.class.getName());
   private boolean sendRemainingLife = true;

   /**
    * Configure if remaingLife is sent in Qos (redesign approach to work with all
    * QoS attributes
    */
   public void sendRemainingLife(boolean sendRemainingLife) {
      this.sendRemainingLife = sendRemainingLife;
   }

   public boolean sendRemainingLife() {
      return this.sendRemainingLife;
   }

   /**
    * Can be used as singleton.
    */
   public MsgQosJsonFactory(Global glob) {
      this.glob = glob;

   }

   /**
    * Parses the given Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param jsonQos e.g. the <b>JSON</b> based ASCII string
    */
   @Override
   public MsgQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         return new MsgQosData(glob, this, "{}", MethodName.UNKNOWN);
      }

      MsgQosData msgQosData = new MsgQosData(glob, this, jsonQos, MethodName.UNKNOWN);
      // strict mode für das werfen von Exceptions
      JsonFactory factory = glob.getJsonFactory();
      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {

         parser.nextToken(); // move to first Object
         JacksonUtils.safeObjectLoop(glob, parser, "MsgQosJson", (fieldName) -> {
            switch (fieldName) {
            case "state":
               JacksonUtils.safeObjectLoop(glob, parser, "state", (innerFieldName) -> {
                     switch (innerFieldName) {
                     case "id":
                           msgQosData.setState(JacksonUtils.notNullValueAsString(glob, parser));
                           break;

                     case "info":
                           msgQosData.setStateInfo(JacksonUtils.notNullValueAsString(glob, parser));
                        break;

                     default:
                        log.severe("Ignoring Unknown field '" + innerFieldName + "' in Object '" + fieldName + "'");
                        JacksonUtils.skipArrayOrObject(parser);
                        break;
                     }
                  });
               break;
               
            case "subscribable":
               // remove try-catch to stop parsing if 'getBooleanValue' throws
               try {
                  msgQosData.setSubscribable(parser.getBooleanValue());
               } catch (IOException e) {
                  log.severe("Error while parsing 'subscribable'");
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "destinations":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     Destination dest = jsonToDestination(parser);
                     msgQosData.addDestination(dest);
                  });
               } catch (IOException e) {
                  log.severe("Error while parsing destinations");
               }
               break;

            case "sender":
               try {
                  msgQosData.setSender(new SessionName(glob, JacksonUtils.notNullValueAsString(glob, parser)));
               } catch (IOException e) {
                  log.severe("Error while parsing 'sender'");
               }
               break;

            case "priority":
               try {
                  msgQosData.setPriority(PriorityEnum.parsePriority(JacksonUtils.notNullValueAsString(glob, parser)));
               } catch (IOException e) {
                  log.severe("Error while parsing 'priority'");
               }
               break;

            case "subscribe":
               JacksonUtils.safeObjectLoop(glob, parser, "subscribe", (innerFieldName) -> {
                     if ("id".equals(innerFieldName)) {
                           msgQosData.setSubscriptionId(JacksonUtils.notNullValueAsString(glob, parser));
                     }});
               break;

            case "expiration":
               class Holder {
                  boolean containsLifetime = false;
               }
               Holder h = new Holder();
               JacksonUtils.safeObjectLoop(glob, parser, fieldName, (innerFieldName) -> {
                  switch (innerFieldName) {
                  case "lifeTime":
                     try {
                        msgQosData.setLifeTime(parser.getLongValue());
                        h.containsLifetime = true;
                     } catch (IOException e) {
                        log.severe("Error while parsing 'lifeTime' in experiation");
                     }
                     break;

                  case "remainingLife":
                     try {
                        msgQosData.setRemainingLifeStatic(parser.getLongValue());
                     } catch (IOException e) {
                        log.severe("Error while parsing 'remainingLife' in experiation");
                     }
                     break;

                  case "forceDestroy":
                     try {
                        msgQosData.setForceDestroy(parser.getBooleanValue());
                     } catch (IOException e) {
                        log.severe("Error while parsing 'forceDestroy' in experiation");
                     }
                     break;

                  default:
                     log.severe("Invalid expiration key = " + parser.currentName());
                     JacksonUtils.skipArrayOrObject(parser);
                  }
               });
               if (!h.containsLifetime) {
                  log.warning("QoS <expiration> misses lifeTime attribute, setting default of "
                        + MsgQosData.getMaxLifeTime());
                  msgQosData.setLifeTime(MsgQosData.getMaxLifeTime());
               }
               break;

            case "rcvTimestamp":
               String timestamp = JacksonUtils.notNullValueAsString(glob, parser).trim();
               try {
                  msgQosData.setRcvTimestamp(new RcvTimestamp(Long.parseLong(timestamp)));
               } catch (NumberFormatException e) {
                  log.severe("Invalid rcvTimestamp - nanos =" + timestamp);
               };
               break;

            case "queue":
               JacksonUtils.safeObjectLoop(glob, parser, fieldName, (innerFieldName) -> {
                  switch (innerFieldName) {
                  case "index":
                     try {
                        msgQosData.setQueueIndex(parser.getIntValue());
                     } catch (IOException e) {
                        log.severe("Invalid queue - index =" + parser.getValueAsString());
                        JacksonUtils.skipArrayOrObject(parser);
                     }
                     break;
                  case "size":
                     try {
                        msgQosData.setQueueSize(parser.getIntValue());
                     } catch (IOException e) {
                        log.severe("Invalid queue - size =" + parser.getValueAsString());
                        JacksonUtils.skipArrayOrObject(parser);
                     }
                     break;
                  default:
                     log.severe("Skipping unknown filed name insinde queue: " + parser.currentName());
                     JacksonUtils.skipArrayOrObject(parser);
                     break;
                  }
               });
               break;

            case "administrative":
               try {
                  msgQosData.setAdministrative(parser.getBooleanValue());
               } catch (IOException e) {
                  log.severe("Error while parsing administrative: " + e.getMessage());
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "persistent":
               try {
                  msgQosData.setPersistent(parser.getBooleanValue());
               } catch (IOException e) {
                  log.severe("Error while parsing persistent: " + e.getMessage());
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "forceUpdate":
               try {
                  msgQosData.setForceUpdate(parser.getBooleanValue());
               } catch (IOException e) {
                  log.severe("Error while parsing forceUpdate: " + e.getMessage());
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "redeliver":
               try {
                  msgQosData.setRedeliver(parser.getIntValue());
               } catch (IOException e) {
                  log.severe("Error while parsing redeliver: " + e.getMessage());
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "route":
               JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                  try {
                     RouteInfo routeInfo = jsonToRouteInfo(parser);
                     msgQosData.addRouteInfo(routeInfo);
                  } catch (IOException e) {
                     log.severe("Error while parsing redeliver: " + e.getMessage());
                     JacksonUtils.skipArrayOrObject(parser);
                  }
               });
               break;

            case "isPublish":
               try {
                  if (parser.getBooleanValue())
                     msgQosData.setMethod(MethodName.PUBLISH);
               } catch (IOException e) {
                  log.severe("Error while parsing isPublish");
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "isUpdate":
               try {
                  if (parser.getBooleanValue())
                     msgQosData.setMethod(MethodName.UPDATE);
               } catch (IOException e) {
                  log.severe("Error while parsing isUpdate");
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "isGet":
               try {
                  if (parser.getBooleanValue())
                     msgQosData.setMethod(MethodName.GET);
               } catch (IOException e) {
                  log.severe("Error while parsing isGet");
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;

            case "topicProperty":
               try {
                  msgQosData.setTopicProperty(parseTopicProperty(parser));
               } catch (IOException e) {
                  log.severe("Error while parsing topicProperty");
                  JacksonUtils.skipArrayOrObject(parser);
               }
               break;
               
            case "clientProperties":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ClientProperty cp = ClientProperty.parseCompactClientProperties(glob, parser);
                        msgQosData.addClientProperty(cp);
                     } catch (XmlBlasterException e) {
                        log.severe("Parsing failed inside the Array of 'clientProperty' with Error: " + e.getMessage());
                        throw e;
                     }
                  });
               } catch (Exception e) {
                  log.severe("Error while parsing 'clientProperty': " + e.getMessage());
               }
               break;

            default:
               log.warning("Ignoring unknown QoS field: " + fieldName);
            }
         });
      } catch (IOException e) {
         log.severe("Failed to parse JSON MsgQos: " + e.getMessage());
         log.info("faulty JSON: " + jsonQos);
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Failed to parse JSON QoS at: " + e.getMessage());
      }

      return msgQosData;
   }

   private TopicProperty parseTopicProperty(JsonParser parser) throws IOException, XmlBlasterException {
      TopicProperty topic = new TopicProperty(glob);

      JacksonUtils.safeObjectLoop(glob, parser, "topicProperty", (fieldName) -> {
         switch (fieldName) {
         case "readonly":
            try {
               topic.setReadonly(parser.getBooleanValue());
            } catch (IOException e) {
               log.severe("Error while parsing 'readonly'");
               throw e;
            }
            break;

         case "destroyDelay":
            try {
               topic.setDestroyDelay(parser.getLongValue());
            } catch (IOException e) {
               log.severe("Error while parsing 'destroyDelay'");
               throw e;
            }
            break;

         case "createDomEntry":
            try {
               topic.setCreateDomEntry(parser.getBooleanValue());
            } catch (IOException e) {
               log.severe("Error while parsing 'createDomEntry'");
               throw e;
            }
            break;

         case "msgDistributor":
            JacksonUtils.safeObjectLoop(glob, parser, fieldName, (subField) -> {
               try {
                  if ("typeVersion".equals(subField)) {
                     topic.setMsgDistributor(JacksonUtils.notNullValueAsString(glob, parser));
                  } else {
                     parser.skipChildren();
                  }
               } catch (IOException e) {
                  log.severe("Error while parsing 'msgDistributor' subfield");
                  throw e;
               }

            });
            break;

         case "persistence":
            MsgUnitStoreProperty tmpMsgUnitStoreProp = new MsgUnitStoreProperty(glob, null);
            try {
               tmpMsgUnitStoreProp.parseJson(parser);
            } catch (IOException | XmlBlasterException e) {
               log.severe("Error while parsing 'persistence'");
               throw e;
            }
            topic.setMsgUnitStoreProperty(tmpMsgUnitStoreProp);
            break;

         case "queue":
            HistoryQueueProperty tmpHistoryProp = new HistoryQueueProperty(glob, glob.getId());
            try {
               tmpHistoryProp.parseJson(parser);
            } catch (IOException | XmlBlasterException e) {
               log.severe("Error while parsing 'queue'");
               throw e;
            }
            topic.setHistoryQueueProperty(tmpHistoryProp);
            break;

         default:
            log.warning("Ignoring unknown QoS, topicProperty field: " + fieldName);
            parser.skipChildren(); // ignore unknown fields
         }
      });

      return topic;
   }

   /**
    * Helper to parse one RouteInfo 'route' object from JSON
    */
   private RouteInfo jsonToRouteInfo(JsonParser parser) throws IOException, XmlBlasterException {
      class Holder {
         NodeId nodeId = null;
         int stratum = 0;
         Timestamp timestamp = new Timestamp(0L);
         boolean dirtyRead = RouteInfo.DEFAULT_dirtyRead;
      }
      Holder h = new Holder();

      try {
      JacksonUtils.safeObjectLoop(glob, parser, "route", (fieldName) -> {
         switch (fieldName) {
         case "id":
               h.nodeId = new NodeId(JacksonUtils.notNullValueAsString(glob, parser));
            break;

         case "stratum":
               h.stratum = parser.getIntValue();
            break;

         case "timestamp":
               h.timestamp = new Timestamp(parser.getLongValue());
            break;

         case "dirtyRead":
               h.dirtyRead = parser.getBooleanValue();
            break;

         default:
            log.warning("Ignoring unknown QoS, topicProperty field: " + parser.currentName());
            JacksonUtils.skipArrayOrObject(parser);
         }

      });
      } catch (XmlBlasterException | IOException e) {
         log.warning("Error parsing RouteInfo: " + e.getLocalizedMessage());
         log.warning("Attempting to continue with default values ...");
      }

      RouteInfo routeInfo = new RouteInfo(h.nodeId, h.stratum, h.timestamp);
      routeInfo.setDirtyRead(h.dirtyRead);
      return routeInfo;
   }

   private Destination jsonToDestination(JsonParser parser) throws IOException, XmlBlasterException {
      Destination dest = new Destination();

      JacksonUtils.safeObjectLoop(glob, parser, "destination", (fieldName) -> {
         if ("queryType".equalsIgnoreCase(fieldName)) {
            String queryType = JacksonUtils.notNullValueAsString(glob, parser);
            if ("EXACT".equalsIgnoreCase(queryType) || "XPATH".equalsIgnoreCase(queryType)) {
               dest.setQueryType(queryType);
            } else {
               log.severe("Sorry, destination queryType='" + queryType + "' is not supported");
            }
         } else if ("forceQueuing".equalsIgnoreCase(fieldName)) {
            dest.forceQueuing(parser.getBooleanValue());
         } else if ("value".equalsIgnoreCase(fieldName)) {
            dest.setDestination(new SessionName(glob, JacksonUtils.notNullValueAsString(glob, parser)));
         } else {
            log.warning("Skipping unknown Destination field name: " + fieldName);
            // unknown field, skip gracefully
            JacksonUtils.skipArrayOrObject(parser);
         }
      });

      return dest;
   }

   @Override
   public String writeObject(MsgQosData msgQosData, String extraOffset, Properties props) {
      // Copied from toXML of <a>org.xmlBlaster.util.qos.MsgQosSaxFactory
      final boolean forceReadable = (props != null) && props.containsKey(Constants.TOXML_FORCEREADABLE)
            ? Boolean.parseBoolean(props.getProperty(Constants.TOXML_FORCEREADABLE))
            : false;
      final boolean forceReadableTimestamp = (props != null)
            && props.containsKey(Constants.TOXML_FORCEREADABLE_TIMESTAMP)
                  ? Boolean.parseBoolean(props.getProperty(Constants.TOXML_FORCEREADABLE_TIMESTAMP))
                  : false;
      final boolean forceReadableBase64 = (props != null) && props.containsKey(Constants.TOXML_FORCEREADABLE_BASE64)
            ? Boolean.parseBoolean(props.getProperty(Constants.TOXML_FORCEREADABLE_BASE64))
            : false;

      try {
         StringWriter writer = new StringWriter();
         JsonFactory factory = glob.getJsonFactory();
         JsonGenerator gen = factory.createGenerator(writer);
         gen.useDefaultPrettyPrinter();

         gen.writeStartObject(); // root {

         // state
         if (!msgQosData.isOk() || (msgQosData.getStateInfo() != null && !msgQosData.getStateInfo().isEmpty())) {
            gen.writeObjectFieldStart("state");
            gen.writeStringField("id", msgQosData.getState());
            if (msgQosData.getStateInfo() != null) {
               gen.writeStringField("info", msgQosData.getStateInfo());
            }
            gen.writeEndObject();
         }

         // subscribable
         if (msgQosData.getSubscribableProp().isModified()) {
            gen.writeBooleanField("subscribable", msgQosData.isSubscribable());
         }

         // destinations
         Destination[] destArr = msgQosData.getDestinationArr();
         if (destArr.length == 0) {
            // do nothing...
         } else {
            gen.writeArrayFieldStart("destinations");
            for (int i = 0; i < destArr.length; i++) {
               Destination destination = (Destination) destArr[i];
               destination.toJson(gen);
            }
            gen.writeEndArray();
         }

         // sender
         if (msgQosData.getSender() != null) {
            gen.writeStringField("sender", msgQosData.getSender().getAbsoluteName());
         }

         // priority
         if (PriorityEnum.NORM_PRIORITY != msgQosData.getPriority()) {
            gen.writeStringField("priority", msgQosData.getPriority().toString());
         }

         // subscription id
         if (msgQosData.getSubscriptionId() != null) {
            gen.writeObjectFieldStart("subscribe");
            gen.writeStringField("id", msgQosData.getSubscriptionId());
            gen.writeEndObject();
         }

         // expiration
         if (msgQosData.getLifeTimeProp().isModified() || msgQosData.getForceDestroyProp().isModified()) {
            gen.writeObjectFieldStart("expiration");
            if (msgQosData.getLifeTimeProp().isModified()) {
               gen.writeNumberField("lifeTime", msgQosData.getLifeTime());
            }
            if (sendRemainingLife()) {
               long remainCached = msgQosData.getRemainingLife();
               if (remainCached > 0) {
                  gen.writeNumberField("remainingLife", remainCached);
               } else if (msgQosData.getRemainingLifeStatic() >= 0) {
                  gen.writeNumberField("remainingLife", msgQosData.getRemainingLifeStatic());
               }
            }
            if (msgQosData.getForceDestroyProp().isModified()) {
               gen.writeBooleanField("forceDestroy", msgQosData.isForceDestroy());
            }
            gen.writeEndObject();
         }

         // rcvTimestamp
         if (msgQosData.getRcvTimestamp() != null) {
            gen.writeNumberField("rcvTimestamp", msgQosData.getRcvTimestamp().getTimestamp());
         }

         // queue
         if (msgQosData.getQueueSize() > 0) {
            gen.writeObjectFieldStart("queue");
            gen.writeNumberField("index", msgQosData.getQueueIndex());
            gen.writeNumberField("size", msgQosData.getQueueSize());
            gen.writeEndObject();
         }

         // administrative
         if (msgQosData.getAdministrativeProp().isModified()) {
            gen.writeBooleanField("administrative", msgQosData.isAdministrative());
         }

         // persistent
         if (msgQosData.getPersistentProp().isModified()) {
            gen.writeBooleanField("persistent", msgQosData.isPersistent());
         }

         // forceUpdate
         if (msgQosData.getForceUpdateProp().isModified()) {
            gen.writeBooleanField("forceUpdate", msgQosData.isForceUpdate());
         }

         // redeliver
         if (msgQosData.getRedeliver() > 0) {
            gen.writeNumberField("redeliver", msgQosData.getRedeliver());
         }

         // route
         RouteInfo[] routeInfoArr = msgQosData.getRouteNodes();
         if (routeInfoArr != null && routeInfoArr.length > 0) {
            gen.writeArrayFieldStart("route");
            for (RouteInfo route : routeInfoArr) {
               gen.writeStartObject();
               gen.writeStringField("id", route.getNodeId().getId());
               gen.writeNumberField("stratum", route.getStratum());
               gen.writeNumberField("timestamp", route.getTimestamp().getTimestamp());
               // if (dirtyRead != DEFAULT_dirtyRead)
               gen.writeBooleanField("dirtyRead", route.getDirtyRead());
               if (forceReadable) {
                  gen.writeStringField("value", route.getTimestamp().toString());
               }
               gen.writeEndObject();
            }
            gen.writeEndArray();
         }

         // method marker
         if (msgQosData.getMethod() == MethodName.PUBLISH) {
            gen.writeBooleanField("isPublish", true);
         } else if (msgQosData.getMethod() == MethodName.UPDATE) {
            gen.writeBooleanField("isUpdate", true);
         } else if (msgQosData.getMethod() == MethodName.GET) {
            gen.writeBooleanField("isGet", true);
         }

         // topic property
         if (msgQosData.hasTopicProperty()) {
            topicToJson(gen, msgQosData.getTopicProperty());
         }

         // clientProperty
         msgQosData.writePropertiesJson(gen);

         // isVolatile is ignored because it is deprecated
         gen.writeEndObject(); // close root
         gen.close();

         return writer.toString();

      } catch (IOException e) {
         log.warning("Unexpected I/O error writing JSON in MsgQosJsonFactory: " + e.getMessage());
         return "{}";
      }
   }

   public void topicToJson(JsonGenerator gen, TopicProperty topic) throws IOException {
      if (topic == null) {
         return;
      }

      gen.writeObjectFieldStart("topicProperty"); // "topicProperty": { ... }

      if (topic.isReadonlyModified()) {
         gen.writeBooleanField("readonly", topic.isReadonly());
      }
      if (topic.isDestroyDelayModified()) {
         gen.writeNumberField("destroyDelay", topic.getDestroyDelay());
      }
      if (topic.isCreateDomEntryModified()) {
         gen.writeBooleanField("createDomEntry", topic.createDomEntry());
      }

      if (topic.isMsgDistributorModified()) {
         gen.writeObjectFieldStart("msgDistributor");
         gen.writeStringField("typeVersion", topic.getMsgDistributor());
         gen.writeEndObject();
      }

      if (topic.hasMsgUnitStoreProperty()) {
         gen.writeFieldName("persistence");
         topic.getMsgUnitStoreProperty().toJson(gen);
      }

      if (topic.hasHistoryQueueProperty()) {
         gen.writeFieldName("queue");
         topic.getHistoryQueueProperty().toJson(gen);
      }

      gen.writeEndObject(); // end "topic"
   }

   @Override
   public String getName() {
      return "MsgQosJsonFactory";
   }

}
