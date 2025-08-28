package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;

public class QueryQosJsonFactory implements I_QueryQosFactory {
   private Global glob;
   private  QueryQosData queryQosData;

   public QueryQosJsonFactory(Global glob) {
      this.glob = glob;

   }
   @Override
   public QueryQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null) {
         jsonQos = "<qos/>";
      }

      // what does this do in toXml?
//      this.tmpFilter = null;
//      this.tmpQuerySpec = null;
//      this.tmpHistory = null;
      
      //pass null as factory, so the usual factory from `glob` is used
      queryQosData = new QueryQosData(glob, null, jsonQos, MethodName.UNKNOWN);
      
      // what does this do in toXml?
//      if (!isEmpty(jsonQos)) // if possible avoid expensive SAX parsing
//         init(jsonQos);      // use SAX parser to parse it (is slow)

      return queryQosData;
   }

   @Override
   public String writeObject(QueryQosData queryQosData, String extraOffset, Properties props) {
      // parseJson
      // todo Interact with extraOffset and props Fields
      String jsonQos;
      try {
          jsonQos = JacksonUtils.MAPPER.writeValueAsString(queryQosData);
      } catch (Exception e) {
          // Hacky fallback (avoid crashing)
          jsonQos = "{}";  // or maybe `null`
          e.printStackTrace(); // or log properly
      }      
      return jsonQos;
   }

   @Override
   public String getName() {
      return "QueryQosJsonFactory";
   }

}
//package org.xmlBlaster.util.qos;
//
//import java.util.Map;
//import java.util.HashMap;
//import java.util.Set;
//
//import org.xmlBlaster.util.Global;
//import org.xmlBlaster.util.def.MethodName;
//
//public abstract class QueryQosDataBuilder {
//
//   /**
//    * Manually map a generic Map to QueryQosData, ignoring unsafe fields.
//    */
//   public static QueryQosData fromMap(Map<String, Object> map, Global glob) {
//      QueryQosData qos = new QueryQosData(glob, MethodName.SUBSCRIBE);
//
//      if (map == null) return qos;
//
//      // -----------------------------
//      // Safe scalar fields
//      // -----------------------------
//      Object state = map.get("state");
//      if (state instanceof String) qos.setState((String) state);
//
//      Object stateInfo = map.get("stateInfo");
//      if (stateInfo instanceof String) qos.setStateInfo((String) stateInfo);
//      
//      Object persistent = map.get("persistent");
//      if (persistent instanceof Boolean) qos.setPersistent((Boolean) persistent);
//      
//      // -----------------------------
//      // Safe clientProperties mapping
//      // -----------------------------
//      Object clientPropsObj = map.get("clientProperties");
//      if (clientPropsObj instanceof Map) {
//         // should be safe because if clientPropsObj is a Map it can only be of this type
//         Map<String, Object> rawProps = (Map<String, Object>) clientPropsObj;
//         for (Map.Entry<String, Object> entry : rawProps.entrySet()) {
//            Object prop = entry.getValue();
//            if (prop instanceof Map) {
//               Map<String, Object> propertyMap = (Map<String, Object>) prop;
//                  String name = (String) propertyMap.get("name");
//                  Object value = propertyMap.get("value");
//                  qos.addClientProperty(name, value);
////                  String key = propEntry.getKey().toString();
////                  Object val = propEntry.getValue();
////                  
////                  if (name == "name") {
////                     // todo
////                     qos.addClientProperty("ToDo", "ToDo");
////                  } else {
////                     // Fallback: store simple scalar
////                     qos.addClientProperty(key, val);
////                  }
//            }
//         }
//      }
//      
//      
//         // chatgpt boilerplate:
//         // -----------------------------
//         // RouteInfo list is tricky — ignore unsafe types like ThreadPoolExecutor
//         // -----------------------------
////       Object routeNodeList = map.get("routeNodeList");
////       if (routeNodeList instanceof Iterable) {
////           for (Object riObj : (Iterable<?>) routeNodeList) {
////               if (riObj instanceof RouteInfo) {
////                   qos.addRouteInfo((RouteInfo) riObj);
////               }
////           }
////       }
//         
//         return qos;
//      }
//   /**
//    * Extract name and value of a ClientProperty, that has been turned into a Map<String, Object>
//    * TODO: make this somehow more typesafe
//    */
//   private static String extractNameAndValue(Map<String, Object> property) {
//      // Extracting the name and value from the property map
//      String name = (String) property.get("name");
//      String value = (String) property.get("value");
//
//      // Returning the result in a sensible way
//      return String.format("Name: %s, Value: %s", name, value);
//  }         
//}
