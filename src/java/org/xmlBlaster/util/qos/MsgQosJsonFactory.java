package org.xmlBlaster.util.qos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
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

   @Override
   public MsgQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         jsonQos = "{}";
      }

      MsgQosData msgQosData = new MsgQosData(glob, this, jsonQos, MethodName.UNKNOWN);

      JsonFactory factory = new JsonFactory();
      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {
         if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, "Expected start object in QoS JSON");
         }

         while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken(); // move to value

            switch (fieldName) {
            case "state":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                   while (parser.nextToken() != JsonToken.END_OBJECT) {
                       switch (parser.currentName()) {
                           case "id":
                               try {
                                   parser.nextToken();
                                   msgQosData.setState(parser.getValueAsString());
                               } catch (IOException e) {log.severe("Error while parsing 'id' in state");}
                               break;

                           case "info":
                               try {
                                   parser.nextToken();
                                   msgQosData.setStateInfo(parser.getValueAsString());
                               } catch (IOException e) {log.severe("Error while parsing 'info' in state");}
                               break;

                           default:
                               parser.skipChildren();
                       }
                   }
               } else log.severe("Error state key not followed by an object");
               break;
            case "subscribable":
               try {
                   msgQosData.setSubscribable(parser.getBooleanValue());
               } catch (IOException e) {log.severe("Error while parsing 'subscribable'");}
               break;

           case "destinations":
               if (parser.currentToken() == JsonToken.START_ARRAY) {
                   while (parser.nextToken() != JsonToken.END_ARRAY) {
                       try {
                           Destination dest = jsonToDestination(parser);
                           msgQosData.addDestination(dest);
                       } catch (IOException e) {log.severe("Error while parsing destinations");}
                   }
               } else log.severe("Error: 'destinations' key not followed by an array");
               break;

           case "sender":
               try {
                   msgQosData.setSender(new SessionName(glob, parser.getValueAsString()));
               } catch (IOException e) {log.severe("Error while parsing 'sender'");}
               break;

           case "priority":
               try {
                   msgQosData.setPriority(PriorityEnum.parsePriority(parser.getValueAsString()));
               } catch (IOException e) {log.severe("Error while parsing 'priority'");}
               break;

           case "subscribe":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                   while (parser.nextToken() != JsonToken.END_OBJECT) {
                       if ("id".equals(parser.currentName())) {
                           try {
                               parser.nextToken();
                               msgQosData.setSubscriptionId(parser.getValueAsString());
                           } catch (IOException e) {log.severe("Error while parsing 'id' in subscribe");}
                       }
                   }
               } else log.severe("Error: subscribe key not followed by an object");
               break;

            case "expiration":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                  boolean containsLifetime = false;
                  while (parser.nextToken() != JsonToken.END_OBJECT) {
                     switch (parser.currentName()) {
                     case "lifeTime":
                        try {
                           parser.nextToken();
                           msgQosData.setLifeTime(parser.getLongValue());
                           containsLifetime = true;
                        } catch (IOException e) {log.severe("Error while parsing 'lifeTime' in experiation");}
                        break;

                     case "remainingLife":
                        try {
                           parser.nextToken();
                           msgQosData.setRemainingLifeStatic(parser.getLongValue());
                        } catch (IOException e) {log.severe("Error while parsing 'remainingLife' in experiation");}
                        break;

                     case "forceDestroy":
                        try {
                           parser.nextToken();
                           msgQosData.setForceDestroy(parser.getBooleanValue());
                        } catch (IOException e) {log.severe("Error while parsing 'forceDestroy' in experiation");}
                        break;

                     default:
                        log.severe("Invalid expiration key = " + parser.currentName());
                        parser.skipChildren();
                     }
                  }
                  if (!containsLifetime) {
                     log.warning("QoS <expiration> misses lifeTime attribute, setting default of "
                           + MsgQosData.getMaxLifeTime());
                     msgQosData.setLifeTime(MsgQosData.getMaxLifeTime());
                  }
               }
               break;

            case "rcvTimestamp":
               String timestamp = parser.getValueAsString().trim();
               try {
                  msgQosData.setRcvTimestamp(new RcvTimestamp(Long.parseLong(timestamp)));
               } catch (NumberFormatException e) {log.severe("Invalid rcvTimestamp - nanos =" + timestamp);}
               ;
               break;

            case "queue":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                  while (parser.nextToken() != JsonToken.END_OBJECT) {
                     switch (parser.currentName()) {
                     case "index":
                        parser.nextToken();
                        try {
                           msgQosData.setQueueIndex(parser.getIntValue());
                        } catch (IOException e) {log.severe("Invalid queue - index =" + parser.getValueAsString());}
                        break;
                     case "size":
                        parser.nextToken();
                        try {
                           msgQosData.setQueueSize(parser.getIntValue());
                        } catch (IOException e) {log.severe("Invalid queue - size =" + parser.getValueAsString());}
                        break;
                     default:
                        log.severe("Error unknown key insinde queue: " + parser.currentName());
                        parser.skipChildren();
                     }
                  }
               }
               break;

            case "administrative":
               try {
                  msgQosData.setAdministrative(parser.getBooleanValue());
               } catch (IOException e) {log.severe("Error while parsing administrative: " + e.getMessage());}
               break;

            case "persistent":
               try {
                  msgQosData.setPersistent(parser.getBooleanValue());
               } catch (IOException e) {log.severe("Error while parsing persistent: " + e.getMessage());}
               break;

            case "forceUpdate":
               try {
                  msgQosData.setForceUpdate(parser.getBooleanValue());
               } catch (IOException e) {log.severe("Error while parsing forceUpdate: " + e.getMessage());}
               break;

            case "redeliver":
               try {
                  msgQosData.setRedeliver(parser.getIntValue());
               } catch (IOException e) {log.severe("Error while parsing redeliver: " + e.getMessage());}
               break;

            case "route":
               if (parser.currentToken() == JsonToken.START_ARRAY) {
                  while (parser.nextToken() != JsonToken.END_ARRAY) {
                     try { 
                        RouteInfo routeInfo = jsonToRouteInfo(parser); 
                        msgQosData.addRouteInfo(routeInfo);
                     } catch (IOException e) {log.severe("Error while parsing redeliver: " + e.getMessage());}
                  }
               }
               break;

            case "isPublish":
               try {
                  if (parser.getBooleanValue()) msgQosData.setMethod(MethodName.PUBLISH);
               } catch (IOException e) {log.severe("Error while parsing isPublish");}
               break;

            case "isUpdate":
               try {
                  if (parser.getBooleanValue()) msgQosData.setMethod(MethodName.UPDATE);
               } catch (IOException e) {log.severe("Error while parsing isUpdate");}
               break;

            case "isGet":
               try {
                  if (parser.getBooleanValue()) msgQosData.setMethod(MethodName.GET);
               } catch (IOException e) {log.severe("Error while parsing isGet");}
               break;

            case "topicProperty":
               try {
               msgQosData.setTopicProperty(parseTopicProperty(parser));
               } catch (IOException e) {log.severe("Error while parsing topicProperty");}
               break;

            default:
               log.warning("Ignoring unknown QoS field: " + fieldName);
               parser.skipChildren();
            }
         }

      } catch (IOException e) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT,
               "Failed to parse JSON QoS at: " + e.getMessage());
      }

      return msgQosData;
   }
   
   private TopicProperty parseTopicProperty(JsonParser parser) throws IOException {
      TopicProperty topic = new TopicProperty(glob);

      // We assume parser is currently at START_OBJECT: "topicProperty": { <--- parser here)
      while (parser.nextToken() != JsonToken.END_OBJECT) {
         String fieldName = parser.currentName();
         if (fieldName == null) {
            continue;
         }

         parser.nextToken(); // move to the value of this field

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
            if (parser.currentToken() == JsonToken.START_OBJECT) {
               while (parser.nextToken() != JsonToken.END_OBJECT) {
                  String subField = parser.currentName();
                  parser.nextToken();
                  try {
                     if ("typeVersion".equals(subField)) {
                        topic.setMsgDistributor(parser.getText());
                     } else {
                        parser.skipChildren();
                     }
                  } catch (IOException e) {
                     log.severe("Error while parsing 'msgDistributor' subfield");
                     throw e;
                  }
               }
            } else {
               log.severe("Error while parsing 'msgDistributor' subfield");
               parser.skipChildren();
            }
            break;

         case "persistence":
            MsgUnitStoreProperty tmpMsgUnitStoreProp = new MsgUnitStoreProperty(glob, glob.getId());
            try {
               tmpMsgUnitStoreProp.parseJson(parser);
            } catch (IOException e) {
               log.severe("Error while parsing 'persistence'");
               throw e;
            }
            topic.setMsgUnitStoreProperty(tmpMsgUnitStoreProp);
            break;

         case "queue":
            HistoryQueueProperty tmpHistoryProp = new HistoryQueueProperty(glob, glob.getId());
            try {
               tmpHistoryProp.parseJson(parser);
            } catch (IOException e) {
               log.severe("Error while parsing 'queue'");
               throw e;
            }
            topic.setHistoryQueueProperty(tmpHistoryProp);
            break;

         default:
            log.warning("Ignoring unknown QoS, topicProperty field: " + fieldName);
            parser.skipChildren(); // ignore unknown fields
         }
      }
      return topic;
   }

   /**
    * Helper to parse one RouteInfo 'route' object from JSON
    */
   private RouteInfo jsonToRouteInfo(JsonParser parser) throws IOException {
      NodeId nodeId = null;
      int stratum = 0;
      Timestamp timestamp = new Timestamp(0L);
      boolean dirtyRead = RouteInfo.DEFAULT_dirtyRead;

      if (parser.currentToken() == JsonToken.START_OBJECT) {
         while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.currentName()) {
            case "id":
               parser.nextToken();
               try {
                  nodeId = new NodeId(parser.getValueAsString());
               } catch (IOException e) {
                  log.severe("Error while parsing 'id'");
                  throw e;
               }
               break;

            case "stratum":
               parser.nextToken();
               try {
                  stratum = parser.getIntValue();
               } catch (IOException e) {
                  log.severe("Error while parsing 'stratum', expected number");
                  throw e;
               }
               break;

            case "timestamp":
               parser.nextToken();
               try {
                  timestamp = new Timestamp(parser.getLongValue());
               } catch (IOException e) {
                  log.severe("Error while parsing 'timestamp', expected number");
                  throw e;
               }
               break;

            case "dirtyRead":
               parser.nextToken();
               try {
                  dirtyRead = parser.getBooleanValue();
               } catch (IOException e) {
                  log.severe("Error while parsing 'dirtyRead'");
                  throw e;
               }
               break;

            default:
               log.warning("Ignoring unknown QoS, topicProperty field: " + parser.currentName());
               parser.skipChildren();
            }
         }
      } else
         log.severe("Error while parsing routeinfo, excpected startobject after 'route' key, usin default values...");

      RouteInfo routeInfo = new RouteInfo(nodeId, stratum, timestamp);
      routeInfo.setDirtyRead(dirtyRead);
      return routeInfo;
   }

   private Destination jsonToDestination(JsonParser parser) throws IOException {
      Destination dest = new Destination();

      if (parser.currentToken() != JsonToken.START_OBJECT) {
         parser.skipChildren(); // not a proper object, skip
         return dest;
      }

      while (parser.nextToken() != JsonToken.END_OBJECT) {
         String fieldName = parser.currentName();
         parser.nextToken(); // move to value

         if ("queryType".equalsIgnoreCase(fieldName)) {
            String queryType = parser.getValueAsString();
            if ("EXACT".equalsIgnoreCase(queryType) || "XPATH".equalsIgnoreCase(queryType)) {
               dest.setQueryType(queryType);
            } else {
               log.severe("Sorry, destination queryType='" + queryType + "' is not supported");
            }
         } else if ("forceQueuing".equalsIgnoreCase(fieldName)) {
            dest.forceQueuing(parser.getBooleanValue());
         } else if ("value".equalsIgnoreCase(fieldName)) {
            dest.setDestination(new SessionName(glob, parser.getValueAsString()));
         } else {
            log.warning("Skipping unknown Destination field name: " + fieldName);
            // unknown field, skip gracefully
            parser.skipChildren();
         }
      }

      return dest;
   }
   

   @Override
   public String writeObject(MsgQosData msgQosData, String extraOffset, Properties props) {
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
         JsonFactory factory = new JsonFactory();
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

         // extra properties (serialized as XML string here for simplicity)
         String propsXml = msgQosData.writePropertiesXml(null, forceReadable || forceReadableBase64);
         if (propsXml != null && !propsXml.isEmpty()) {
            gen.writeStringField("properties", propsXml);
         }

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
          topic.getMsgUnitStoreProperty().toJson(gen); // delegate
      }

      if (topic.hasHistoryQueueProperty()) {
          topic.getHistoryQueueProperty().toJson(gen); // delegate
      }

      gen.writeEndObject(); // end "topic"
  }

   @Override
   public String getName() {
      return "MsgQosJsonFactory";
   }

}
