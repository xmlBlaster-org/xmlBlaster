package org.xmlBlaster.util.qos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

public class StatusQosJsonFactory implements I_StatusQosFactory {

   private final Global glob;
   private static Logger log = Logger.getLogger(MsgQosJsonFactory.class.getName());


   public StatusQosJsonFactory(Global glob) {
      this.glob = glob;
   }

   @Override
   public StatusQosData readObject(String jsonQos) throws XmlBlasterException {

      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         jsonQos = "{}";
      }

      StatusQosData statusQosData =
            new StatusQosData(glob, this, jsonQos, MethodName.UNKNOWN);

      JsonFactory factory = new JsonFactory();

      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {

         parser.nextToken(); // move to first object

         JacksonUtils.safeObjectLoop(glob, parser, "StatusQosJson", (fieldName) -> {

            switch (fieldName) {

            case "state":
               JacksonUtils.safeObjectLoop(glob, parser, "state", (innerFieldName) -> {
                  switch (innerFieldName) {
                  case "id":
                     statusQosData.setState(
                           JacksonUtils.notNullValueAsString(glob, parser));
                     break;

                  case "info":
                     statusQosData.setStateInfo(
                           JacksonUtils.notNullValueAsString(glob, parser));
                     break;

                  default:
                     log.severe("Ignoring unknown field '" + innerFieldName
                           + "' in object 'state'");
                     JacksonUtils.skipArrayOrObject(parser);
                  }
               });
               break;

            case "subscribe":
               JacksonUtils.safeObjectLoop(glob, parser, "subscribe", (innerFieldName) -> {
                  if ("id".equals(innerFieldName)) {
                     statusQosData.setSubscriptionId(
                           JacksonUtils.notNullValueAsString(glob, parser));
                  } else {
                     log.severe("Ignoring unknown field '" + innerFieldName
                           + "' in object 'subscribe'");
                     JacksonUtils.skipArrayOrObject(parser);
                  }
               });
               break;

            case "key":
               JacksonUtils.safeObjectLoop(glob, parser, "key", (innerFieldName) -> {
                  if ("oid".equals(innerFieldName)) {
                     statusQosData.setKeyOid(
                           JacksonUtils.notNullValueAsString(glob, parser));
                  } else {
                     log.severe("Ignoring unknown field '" + innerFieldName
                           + "' in object 'key'");
                     JacksonUtils.skipArrayOrObject(parser);
                  }
               });
               break;

            case "rcvTimestamp":
                  String nanos =
                        JacksonUtils.notNullValueAsString(glob, parser).trim();
                  try {
                     statusQosData.setRcvTimestamp(
                           new RcvTimestamp(Long.parseLong(nanos)));
                  } catch (NumberFormatException e) {
                     log.warning("Invalid rcvTimestamp - nanos=" + nanos);
                     throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                           "Check nanos in Warning above, Malformed 'rcvTimestamp': " + e.getMessage());
                  }
               break;

            case "isErase":
                  if (parser.getBooleanValue()) {
                     statusQosData.setMethod(MethodName.ERASE);
                  }
               break;

            case "isPublish":
                  if (parser.getBooleanValue()) {
                     statusQosData.setMethod(MethodName.PUBLISH);
                  }
               break;

            case "isSubscribe":
                  if (parser.getBooleanValue()) {
                     statusQosData.setMethod(MethodName.SUBSCRIBE);
                  }
               break;

            case "isUnSubscribe":
                  if (parser.getBooleanValue()) {
                     statusQosData.setMethod(MethodName.UNSUBSCRIBE);
                  }
               break;

            case "isUpdate":
                  if (parser.getBooleanValue()) {
                     statusQosData.setMethod(MethodName.UPDATE);
                  }
               break;

            case "clientProperties":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ClientProperty cp =
                              ClientProperty.parseCompactClientProperties(
                                    glob, parser);
                        statusQosData.addClientProperty(cp);
                     } catch (XmlBlasterException e) {
                        log.severe("Parsing failed inside 'clientProperties': "
                              + e.getMessage());
                        throw e;
                     }
                  });
               } catch (Exception e) {
                  log.severe("Error while parsing 'clientProperties': "
                        + e.getMessage());
                  throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                        "Malformed 'clientProperties': " + e.getMessage());
               }
               break;

            default:
               log.warning("Ignoring unknown StatusQos field: " + fieldName);
               JacksonUtils.skipArrayOrObject(parser);
            }
         });

      } catch (IOException e) {
         throw new XmlBlasterException(
               glob,
               ErrorCode.USER_WRONG_API_USAGE,
               "Failed to parse JSON StatusQos at: " + e.getMessage());
      }

      return statusQosData;
   }


   @Override
   public String writeObject(StatusQosData statusQosData, String extraOffset, Properties props) {
      return toJson(statusQosData, extraOffset, props, false);
   }

   @Override
   public String writeObject(StatusQosData statusQosData, String extraOffset, Properties props, boolean dumpClientProperties) {
      return toJson(statusQosData, extraOffset, props, dumpClientProperties);
   }
   
   public static final String toJson(
         StatusQosData statusQosData,
         String extraOffset,
         Properties props,
         boolean dumpClientProperties) {

      try {
         StringWriter writer = new StringWriter();
         JsonFactory factory = new JsonFactory();
         JsonGenerator gen = factory.createGenerator(writer);
         gen.useDefaultPrettyPrinter();

         gen.writeStartObject(); // {

         // state
         if (!statusQosData.isOk() || statusQosData.hasStateInfo()) {
            gen.writeObjectFieldStart("state");
            gen.writeStringField("id", statusQosData.getState());
            if (statusQosData.getStateInfo() != null) {
               gen.writeStringField("info", statusQosData.getStateInfo());
            }
            gen.writeEndObject();
         }

         // subscribe id
         if (statusQosData.getSubscriptionId() != null) {
            gen.writeObjectFieldStart("subscribe");
            gen.writeStringField("id", statusQosData.getSubscriptionId());
            gen.writeEndObject();
         }

         // key oid
         if (statusQosData.getKeyOid() != null) {
            gen.writeObjectFieldStart("key");
            gen.writeStringField("oid", statusQosData.getKeyOid());
            gen.writeEndObject();
         }

         // receive timestamp
         if (statusQosData.getRcvTimestamp() != null) {
            gen.writeNumberField("rcvTimestamp", statusQosData.getRcvTimestamp().getTimestamp());
         }


         // method flags (exactly one)
         if (statusQosData.getMethod() == MethodName.ERASE) {
            gen.writeBooleanField("isErase", true);
         }
         else if (statusQosData.getMethod() == MethodName.PUBLISH) {
            gen.writeBooleanField("isPublish", true);
         }
         else if (statusQosData.getMethod() == MethodName.SUBSCRIBE) {
            gen.writeBooleanField("isSubscribe", true);
         }
         else if (statusQosData.getMethod() == MethodName.UNSUBSCRIBE) {
            gen.writeBooleanField("isUnSubscribe", true);
         }
         else if (statusQosData.getMethod() == MethodName.UPDATE) {
            gen.writeBooleanField("isUpdate", true);
         }

         // properties passed in explicitly
         if (props != null && !props.isEmpty()) {
            gen.writeArrayFieldStart("clientProperties");
            Enumeration<?> en = props.propertyNames();
            while (en.hasMoreElements()) {
               String key = (String) en.nextElement();
               String value = props.getProperty(key);
               ClientProperty cp =
                     new ClientProperty(key, Constants.TYPE_STRING,
                           Constants.UTF8_ENCODING, value);
               cp.toCompactJson(gen);
            }
            gen.writeEndArray();
         }

         // dump client properties from StatusQosData
         if (dumpClientProperties) {
            statusQosData.writePropertiesJson(gen);
         }

         gen.writeEndObject(); // }
         gen.close();

         String json = writer.toString();
         return (json.length() < 4) ? "{}" : json;

      } catch (IOException e) {
         log.warning("Unexpected I/O error writing JSON in StatusQosJsonFactory: "
               + e.getMessage());
         return "{}";
      }
   }


   @Override
   public String getName() {
      // TODO Auto-generated method stub
      return null;
   }

}
