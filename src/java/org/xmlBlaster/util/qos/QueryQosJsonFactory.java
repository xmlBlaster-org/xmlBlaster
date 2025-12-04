package org.xmlBlaster.util.qos;

import java.util.Properties;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Logger;

/**
 * JSON-based parser for QueryQosData using Jackson streaming API.
 */
public class QueryQosJsonFactory implements I_QueryQosFactory {

   private static final Logger log = Logger.getLogger(QueryQosJsonFactory.class.getName());
   private final Global glob;

   public QueryQosJsonFactory(Global glob) {
      this.glob = glob;
   }

   @Override
   public QueryQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         jsonQos = "{}";
      }

      QueryQosData queryQosData = new QueryQosData(glob, this, jsonQos, MethodName.UNKNOWN);

      JsonFactory factory = new JsonFactory();
      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {
         //step to first object
         parser.nextToken();
         JacksonUtils.safeObjectLoop(glob, parser, "QueryQos", (fieldName) -> {
            switch (fieldName) {
            case "subscribe":
               queryQosData.setSubscriptionId(JacksonUtils.notNullValueAsString(glob, parser));
               break;

            case "erase":
               JacksonUtils.safeObjectLoop(glob, parser, fieldName, (f) -> {
                  if (f == "forceDestroy") {
                     queryQosData.setForceDestroy(parser.getBooleanValue());
                  }
               });
               break;

            case "meta":
               queryQosData.setWantMeta(parser.getBooleanValue());
               break;

            case "content":
               queryQosData.setWantContent(parser.getBooleanValue());
               break;

            case "multiSubscribe":
               queryQosData.setMultiSubscribe(parser.getBooleanValue());
               break;

            case "local":
               queryQosData.setWantLocal(parser.getBooleanValue());
               break;

            case "subIdGeneratedIncludeClusterNodeId":
               queryQosData.setSubIdGeneratedIncludeClusterNodeId(parser.getBooleanValue());
               break;

            case "initialUpdate":
               queryQosData.setWantInitialUpdate(parser.getBooleanValue());
               break;

            case "updateOneway":
               queryQosData.setWantUpdateOneway(parser.getBooleanValue());
               break;

            case "notify":
               queryQosData.setWantNotify(parser.getBooleanValue());
               break;

            case "persistent":
               queryQosData.setPersistent(parser.getBooleanValue());
               break;
            case "filter":
               // JSON "filter" may be an object or an array of objects
               JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                  AccessFilterQos tmpFilter = new AccessFilterQos(glob);
                  tmpFilter.fromJson(parser);
                  queryQosData.addAccessFilter(tmpFilter);
               });
               break;

            case "history":
               try {
                  // JSON "history" may be an object
               HistoryQos tmpHistory = new HistoryQos(glob);
               tmpHistory.fromJson(parser);
               queryQosData.setHistoryQos(tmpHistory);
               } catch (IOException | XmlBlasterException e) {
                  log.warning("Error parsing 'history': " +  e.getMessage());
                  throw e;
               }
               break;

            case "querySpec":
               JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                  QuerySpecQos spec = new QuerySpecQos(glob);
                  spec.fromJson(parser);
                  queryQosData.addQuerySpec(spec);
               });
               break;

            case "methodName":
               try {
                  String val = JacksonUtils.notNullValueAsString(glob, parser);
                  
                  switch (val) {
                  case ("isErase"): {
                     queryQosData.setMethod(MethodName.ERASE);
                     break;
                  }
                  case ("isGet"): {
                     queryQosData.setMethod(MethodName.GET);
                     break;
                  }
                  case ("isSubscribe"): {
                     queryQosData.setMethod(MethodName.SUBSCRIBE);
                     break;
                  }
                  case ("isUnSubscribe"): {
                     queryQosData.setMethod(MethodName.UNSUBSCRIBE);
                     break;
                  }
                  default:
                     throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE, "Error at '" + fieldName + "'", "Unexpected value: " + val);
                  }

               } catch (IOException | XmlBlasterException e) {
                  throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE, "Error parsing '" + fieldName + "'", e.getMessage());
               }
               break;

            case "clientProperties":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ClientProperty cp = ClientProperty.parseCompactClientProperties(glob, parser);
                        queryQosData.addClientProperty(cp);
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
               System.out.println("currentValue: " + parser.getValueAsString());
               parser.skipChildren();
            }

         });

      } catch (IOException | XmlBlasterException e) {
         log.warning(e.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Failed to parse JSON QoS: ", e.getMessage());
      }

      return queryQosData;
   }

   /**
    * Dump state of this object into a XML ASCII string. <br>
    * 
    * @param deprecated, does nothing
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   @Override
   public String writeObject(QueryQosData queryQosData, String extraOffset, Properties props) {
      return writeObjectJson(queryQosData, extraOffset, props);
   }

   public static final String writeObjectJson(QueryQosData queryQosData, String extraOffset, Properties props) {
      try {
         StringWriter writer = new StringWriter();
         JsonFactory factory = new JsonFactory();
         JsonGenerator gen;
         gen = factory.createGenerator(writer);

         gen.useDefaultPrettyPrinter();

         gen.writeStartObject(); // root

         if (queryQosData.getSubscriptionId() != null) {
            gen.writeStringField("subscribe", queryQosData.getSubscriptionId());
         }

         if (queryQosData.getForceDestroyProp().isModified()) {
            gen.writeObjectFieldStart("erase");
            gen.writeBooleanField("forceDestroy", queryQosData.getForceDestroy());
            gen.writeEndObject();
         }

         if (queryQosData.getMetaProp().isModified()) {
            gen.writeBooleanField("meta", queryQosData.getWantMeta());
         }

         if (queryQosData.getContentProp().isModified()) {
            gen.writeBooleanField("content", queryQosData.getWantContent());
         }

         if (queryQosData.getMultiSubscribeProp().isModified()) {
            gen.writeBooleanField("multiSubscribe", queryQosData.getMultiSubscribe());
         }

         if (queryQosData.getSubIdGeneratedIncludeClusterNodeId().isModified()) {
            gen.writeBooleanField("subIdGeneratedIncludeClusterNodeId",
                  queryQosData.isSubIdGeneratedIncludeClusterNodeId());
         }

         if (queryQosData.getLocalProp().isModified()) {
            gen.writeBooleanField("local", queryQosData.getWantLocal());
         }

         if (queryQosData.getInitialUpdateProp().isModified()) {
            gen.writeBooleanField("initialUpdate", queryQosData.getWantInitialUpdate());
         }

         if (queryQosData.getUpdateOnewayProp().isModified()) {
            gen.writeBooleanField("updateOneway", queryQosData.getWantUpdateOneway());
         }

         if (queryQosData.getNotifyProp().isModified()) {
            gen.writeBooleanField("notify", queryQosData.getWantNotify());
         }

         if (queryQosData.getPersistentProp().isModified()) {
            gen.writeBooleanField("persistent", queryQosData.isPersistent());
         }

         // filters
         AccessFilterQos[] list = queryQosData.getAccessFilterArr();
         if (list != null && list.length > 0) {
            gen.writeArrayFieldStart("filter");
            for (AccessFilterQos filter : list) {
               filter.toJson(gen);
            }
            gen.writeEndArray();
         }

         // query specs
         QuerySpecQos[] querySpecList = queryQosData.getQuerySpecArr();
         if (querySpecList != null && querySpecList.length > 0) {
            gen.writeArrayFieldStart("querySpec");
            for (QuerySpecQos spec : querySpecList) {
               spec.toJson(gen);
            }
            gen.writeEndArray();
         }

         // history
         HistoryQos historyQos = queryQosData.getHistoryQos();
         if (historyQos != null && historyQos.getNumEntries() != HistoryQos.DEFAULT_numEntries) {
            gen.writeObjectFieldStart("history");
            gen.writeNumberField("numEntries", historyQos.getNumEntries());
            gen.writeBooleanField("newestFirst", historyQos.getNewestFirst());
            gen.writeEndObject();
         }
         
         // detirmine method name
         String methodName = null;
         if (queryQosData.getMethod() == MethodName.ERASE) {
            methodName = "isErase";
         }
         else if (queryQosData.getMethod() == MethodName.GET) {
            methodName = "isGet";
         }
         else if (queryQosData.getMethod() == MethodName.SUBSCRIBE) {
            methodName = "isSubscribe";
         }
         else if (queryQosData.getMethod() == MethodName.UNSUBSCRIBE) {
            methodName = "isUnSubscribe";
         }
         if (methodName != null) {gen.writeStringField("methodName", methodName);}
         
         // clientProperty
         queryQosData.writePropertiesJson(gen);

         gen.writeEndObject(); // root

         gen.close();
         return writer.toString();
      } catch (IOException e) {
         // should be safe
         log.warning("Unexpected I/O error writing JSON in QueryQosJsonFactory");
         e.printStackTrace();
         return "{}";
      }
   }
//       QueryQosSaxFactory workingFactory = new QueryQosSaxFactory(glob);
//       return workingFactory.writeObject(queryQosData, extraOffset, props);
//    }

   @Override
   public String getName() {
      return "QueryQosJsonFactory";
   }

}
