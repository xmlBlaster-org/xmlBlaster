package org.xmlBlaster.util.qos;

import java.util.Properties;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.Global;
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
         if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, "Expected start object in QoS JSON");
         }

         while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken(); // move to value

            switch (fieldName) {
            case "subscribe":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                  while (parser.nextToken() != JsonToken.END_OBJECT) {
                     if ("id".equals(parser.currentName())) {
                        parser.nextToken();
                        queryQosData.setSubscriptionId(parser.getValueAsString());
                     }
                  }
               }
               break;

            case "erase":
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                  while (parser.nextToken() != JsonToken.END_OBJECT) {
                     if ("forceDestroy".equals(parser.currentName())) {
                        parser.nextToken();
                        queryQosData.setForceDestroy(parser.getBooleanValue());
                     }
                  }
               }
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
               if (parser.currentToken() == JsonToken.START_ARRAY) {
                  while (parser.nextToken() != JsonToken.END_ARRAY) {
                     AccessFilterQos tmpFilter = new AccessFilterQos(glob);
                     boolean ok = jsonToAcessFilterQos(tmpFilter, parser);
                     if (ok) {
                        queryQosData.addAccessFilter(tmpFilter);
                     } else {
                        tmpFilter = null;
                     }
                  }
               } else if (parser.currentToken() == JsonToken.START_OBJECT) {
                  AccessFilterQos tmpFilter = new AccessFilterQos(glob);
                  boolean ok = jsonToAcessFilterQos(tmpFilter, parser);
                  if (ok) {
                     queryQosData.addAccessFilter(tmpFilter);
                  } else {
                     tmpFilter = null;
                  }
               } else {
                  parser.skipChildren(); // ignore unexpected value
                  log.warning("Ignoring unknown filter field: " + fieldName);
               }
               break;

            case "history":
               // JSON "history" may be an object
               if (parser.currentToken() == JsonToken.START_OBJECT) {
                  HistoryQos tmpHistory = new HistoryQos(glob);
                  boolean ok = jsonToHistoryQos(tmpHistory, parser); // Assuming you have a method to parse JSON into
                                                                     // HistoryQos
                  if (ok) {
                     queryQosData.setHistoryQos(tmpHistory);
                  } else {
                     tmpHistory = null;
                  }
               } else {
                  parser.skipChildren(); // ignore unexpected value
                  log.warning("Ignoring unknown history field: " + fieldName);
               }
               break;

//                    case "querySpec":
//                        QuerySpecQos spec = new QuerySpecQos(glob);
//                        spec.fromJson(parser);
//                        queryQosData.addQuerySpec(spec);
//                        break;
//
//                    case "method":
//                        queryQosData.setMethod(MethodName.valueOf(parser.getValueAsString().toUpperCase()));
//                        break;

            default:
               log.warning("Ignoring unknown QoS field: " + fieldName);
               parser.skipChildren();
            }
         }

      } catch (IOException e) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT,
               "Failed to parse JSON QoS: " + e.getMessage());
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
            gen.writeObjectFieldStart("subscribe");
            gen.writeStringField("id", queryQosData.getSubscriptionId());
            gen.writeEndObject();
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

   /**
    * Helper function for parsing AcessFilterQos
    * 
    * @param filterQos
    * @param parser
    * @return
    * @throws IOException
    */
   private boolean jsonToAcessFilterQos(AccessFilterQos filterQos, JsonParser parser) throws IOException {
      if (parser.currentToken() != JsonToken.START_OBJECT) {
         log.warning("Expected start object in Filter Array JSON, skipping this entry");
         return false;
      }
      boolean typeSet = false;
      while (parser.nextToken() != JsonToken.END_OBJECT) {
         String filterFieldName = parser.currentName();
         parser.nextToken(); // move to value
         if (filterFieldName.equalsIgnoreCase("type")) {
            String typeValue = parser.getValueAsString();
            if (typeValue != null && !typeValue.isBlank()) {
               filterQos.setType(typeValue);
               typeSet = true;
            }
         } else if (filterFieldName.equalsIgnoreCase("version")) {
            filterQos.setVersion(parser.getValueAsString());
         } else if ("value".equalsIgnoreCase(filterFieldName)) {
            try {
               String valueString = filterValueToString(parser);
               filterQos.setQuery(new Query(glob, valueString));
            } catch (IOException e) {
               log.warning("Failed to parse 'value' insid FilterQos:");
               throw e;
            }
         } else {
            log.warning("Ignoring unknown attribute \"" + filterFieldName + "\" in " + filterQos.tagName + " section.");
         }
      }

      if (!typeSet) {
         // behave just like in the original QuerQosSaxFactory
         log.warning("Missing required 'type' attribute in " + filterQos.tagName + " section, ignoring this filter.");
         return false;
      }

      return true;
   }

   private String filterValueToString(JsonParser parser) throws IOException {
      String valueString = "";
      if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
         // read entire object/array as tree and convert to string
         ObjectMapper mapper = new ObjectMapper(); // slow, could be initialized once as a field
         JsonNode node = mapper.readTree(parser);
         valueString = node.toString();
      } else {
         // regular primitive/string value
         valueString = parser.getValueAsString();
      }
      return valueString;
   }

   /**
    * Helper function for parsing HistoryQos
    * 
    * @param historyQos
    * @param parser
    * @return
    */
   private boolean jsonToHistoryQos(HistoryQos historyQos, JsonParser parser) {
      try {
         // Move to the start of the object
         if (parser.currentToken() != JsonToken.START_OBJECT) {
            log.warning("Expected START_OBJECT for history QoS");
            return false;
         }

         // Iterate through the fields of the JSON object
         while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken(); // Move to the value

            switch (fieldName) {
            case "numEntries":
               int numEntries = parser.getIntValue();
               historyQos.setNumEntries(numEntries);
               break;

            case "newestFirst":
               boolean newestFirst = parser.getBooleanValue();
               historyQos.setNewestFirst(newestFirst);
               break;

            default:
               log.warning("Ignoring unknown attribute " + fieldName + " in history section.");
               parser.skipChildren(); // Skip the value of the unknown field
               break;
            }
         }
         return true; // Successfully parsed the history QoS
      } catch (IOException e) {
         log.severe("Error parsing history QoS: " + e.getMessage());
         return false; // Indicate failure
      }
   }

}
