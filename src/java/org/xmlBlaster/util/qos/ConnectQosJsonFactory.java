package org.xmlBlaster.util.qos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConnectQosJsonFactory implements I_ConnectQosFactory {

   private final Global glob;
   private static Logger log = Logger.getLogger(ConnectQosSaxFactory.class.getName());

   public ConnectQosJsonFactory(Global glob) {
      this.glob = glob;
   }

   @Override
   public ConnectQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         jsonQos = "{}";
      }

      ConnectQosData connectQosData = new ConnectQosData(glob);
      JsonFactory factory = new JsonFactory();

      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {
         // step to first Object:
         parser.nextToken();
         JacksonUtils.safeObjectLoop(glob, parser, "ConnectQosData", (fieldName) -> {
            switch (fieldName) {
            case "serverRefs":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ServerRef serverRef = jsonToServerRef(parser);
                        connectQosData.addServerRef(serverRef);
                     } catch (IOException e) {
                        log.severe("Parsing failed inside 'serverRefs");
                        log.severe("Skipping parsing...");
                        JacksonUtils.skipArrayOrObject(parser);
                        throw e;
                     }
                  });

               } catch (Exception e) {
                  log.severe("Error while parsing 'serverRef': " + e.getMessage());
               }
               break;

            case "securityService":
               try {
                  // determine Type of securityService before creation
                  ObjectMapper mapper = new ObjectMapper();
                  JsonNode node = mapper.readTree(parser);
                  JsonNode scSvTypeNode = node.findValue("type");
                  if (scSvTypeNode == null || scSvTypeNode.asText().isEmpty()) {
                     log.severe("Missing 'type' attribute in 'securityService'");
                     throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                           "ConnectQosJson 'SecurityService' missing 'type'");
                  }
                  JsonNode scSrvVersionNode = node.findValue("version");
                  if (scSrvVersionNode == null || scSrvVersionNode.asText().isEmpty()) {
                     log.severe("Missing 'version' attribute in 'securityService'");
                     throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                           "ConnectQosJson 'SecurityService' missing 'version'");
                  }
                  String scSrvType = scSvTypeNode.asText();
                  String scSrvVersion = node.findValue("version").asText();
                  I_SecurityQos securityQos = connectQosData.getClientPlugin(scSrvType, scSrvVersion)
                        .createSecurityQos();
                  securityQos.parseJson(node);
                  connectQosData.setSecurityQos(securityQos);

                  // code to get type of SecurityPlugin here!
               } catch (Exception e) {
                  log.severe("Error while parsing 'securityService': " + e.getMessage());
               }
               break;

            case "clientQueueArr":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ClientQueueProperty tmpProp = new ClientQueueProperty(glob, null);
                        tmpProp.parseJson(parser);
                        connectQosData.addClientQueueProperty(tmpProp);
                     } catch (IOException | XmlBlasterException e) {
                        log.severe("Parsing failed inside the Array of client cqQueuepropertie Objects");
                        log.severe("Skipping parsing...");
                        JacksonUtils.skipArrayOrObject(parser);
                        throw e;
                     }
                  });
               } catch (Exception e) {
                  log.severe("Error while parsing 'clientQueueArr': " + e.getMessage());
               }
               break;

            case "subjectQueue":
               try {
                  CbQueueProperty tmpCbProp = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, null);
                  tmpCbProp.parseJson(parser);
                  connectQosData.setSubjectQueueProperty(tmpCbProp);
               } catch (Exception e) {
                  log.severe("Error while parsing 'subjectQueue': " + e.getMessage());
               }
               break;

            case "sessionQueue":
               try {
                  CbQueueProperty tmpCbProp = new CbQueueProperty(glob, null, null);
                  tmpCbProp.parseJson(parser);
                  connectQosData.setSessionCbQueueProperty(tmpCbProp);
               } catch (Exception e) {
                  log.severe("Error while parsing 'sessionQueue': " + e.getMessage());
               }
               break;

            case "ptp":
               try {
                  connectQosData.setPtpAllowed(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'ptp': " + e.getMessage());
               }
               break;

            case "clusterNode":
               try {
                  connectQosData.setClusterNode(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'clusterNode': " + e.getMessage());
               }
               break;

            case "refreshSession":
               try {
                  connectQosData.setRefreshSession(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'refreshSession': " + e.getMessage());
               }
               break;

            case "duplicateUpdates":
               try {
                  connectQosData.setDuplicateUpdates(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'duplicateUpdates': " + e.getMessage());
               }
               break;

            case "reconnected":
               try {
                  connectQosData.setReconnected(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'reconnected': " + e.getMessage());
               }
               break;

            case "instanceId":
               try {
                  connectQosData.setInstanceId(JacksonUtils.notNullValueAsString(glob, parser));
               } catch (Exception e) {
                  log.severe("Error while parsing 'instanceId': " + e.getMessage());
               }
               break;

            case "persistent":
               try {
                  connectQosData.setPersistent(parser.getBooleanValue());
               } catch (Exception e) {
                  log.severe("Error while parsing 'persistent': " + e.getMessage());
               }
               break;

            case "session":
               try {
                  jsonToSessionQos(parser, connectQosData.getSessionQos());
               } catch (Exception e) {
                  log.severe("Error while parsing 'session': " + e.getMessage());
               }
               break;

            case "clientProperties":
               try {
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                     try {
                        ClientProperty cp = ClientProperty.parseCompactClientProperties(glob, parser);
                        connectQosData.addClientProperty(cp);
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
               try {
                  log.warning("Ignoring unknown ConnectQos field: " + fieldName);
                  JacksonUtils.skipArrayOrObject(parser);
               } catch (IOException e) {
                  log.severe("skipping childeren failed in ConnectQos 'default', aborting");
                  return;
               }
            }
         });
      } catch (IOException e) {
         log.severe("Failed to parse JSON ConnectQos: " + e.getMessage());
      }
      return connectQosData;
   }

   /**
    * parse json from `parser` into the sessionQos object
    * 
    * @param parser
    * @param sessionQos
    * @throws IOException
    */
   private void jsonToSessionQos(JsonParser parser, SessionQos sessionQos) throws IOException, XmlBlasterException {
      JacksonUtils.safeObjectLoop(glob, parser, "sessionQos", (fieldName) -> {
         switch (fieldName) {
         case "name":
            try {
               if (glob.isServerSide()) { // Force the server node ID on connect
                  sessionQos.setSessionName(new SessionName(glob, glob.getNodeId(), JacksonUtils.notNullValueAsString(glob, parser)));
               } else {
                  sessionQos.setSessionName(new SessionName(glob, JacksonUtils.notNullValueAsString(glob, parser)));
               }
            } catch (IOException e) {
               log.severe("Error while parsing 'id'");
               throw e;
            }
            break;

         case "timeout":
            try {
               sessionQos.setSessionTimeout(parser.getLongValue());
            } catch (Exception e) {
               log.severe("Error while parsing 'timeout': " + e.getMessage());
               throw new IOException("Error parsing 'timeout'", e);
            }
            break;

         case "maxSessions":
            try {
               sessionQos.setMaxSessions(parser.getIntValue());
            } catch (Exception e) {
               log.severe("Error while parsing 'maxSessions': " + e.getMessage());
               throw new IOException("Error parsing 'maxSessions'", e);
            }
            break;

         case "clearSessions":
            try {
               sessionQos.clearSessions(parser.getBooleanValue());
            } catch (Exception e) {
               log.severe("Error while parsing 'clearSessions': " + e.getMessage());
               throw new IOException("Error parsing 'clearSessions'", e);
            }
            break;

         case "reconnectSameClientOnly":
            try {
               sessionQos.setReconnectSameClientOnly(parser.getBooleanValue());
            } catch (Exception e) {
               log.severe("Error while parsing 'reconnectSameClientOnly': " + e.getMessage());
               throw new IOException("Error parsing 'reconnectSameClientOnly'", e);
            }
            break;

         case "sessionId":
            try {
               sessionQos.setSecretSessionId(JacksonUtils.notNullValueAsString(glob, parser));
            } catch (Exception e) {
               log.severe("Error while parsing 'sessionId': " + e.getMessage());
               throw new IOException("Error parsing 'sessionId'", e);
            }
            break;

         default:
            log.warning("Ignoring unknown ConnectQos field: " + fieldName);
            JacksonUtils.skipArrayOrObject(parser);
         }

      });
   }

   private ServerRef jsonToServerRef(JsonParser parser) throws IOException, XmlBlasterException {

      // get access to Fields inside the loop
      class Holder {
         String type;
         String addr;
     }
     Holder h = new Holder();
     
      JacksonUtils.safeObjectLoop(glob, parser, "serverRef", (fieldName) -> {
         switch (fieldName) {
         case "type":
            h.type = JacksonUtils.notNullValueAsString(glob, parser);
            break;

         case "address":
            h.addr = JacksonUtils.notNullValueAsString(glob, parser);
            break;

         default:
            log.warning("ignoring unknown field '" + fieldName + "' inside 'serverRef'");
            JacksonUtils.skipArrayOrObject(parser); // ignore unknown fields
            break;
         }

      });

//Validation
      if (h.type == null || h.type.isEmpty()) {
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION,
               "Missing mandatory field 'type' in ServerRef");
      }

      if (h.addr == null || h.addr.isEmpty()) {
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION,
               "Missing mandatory field 'address' in ServerRef");
      }
      
      return new ServerRef(h.type, h.addr);
   }

   /**
    * Dump state of this object into a JSON string. <br>
    * 
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the ConnectQos as a XML ASCII string
    */
   @Override
   public String writeObject(ConnectQosData qosData, String extraOffset, Properties props) {
      return toJson(qosData, extraOffset, props);
   }

   public static final String toJson(ConnectQosData data, String extraOffset, Properties props) {
      final boolean noSecurity = (props != null) && props.containsKey(Constants.TOXML_NOSECURITY)
            ? Boolean.parseBoolean(props.getProperty(Constants.TOXML_NOSECURITY))
            : false;

      try {
         StringWriter writer = new StringWriter();
         JsonFactory factory = new JsonFactory();
         JsonGenerator gen = factory.createGenerator(writer);
         gen.useDefaultPrettyPrinter();

         gen.writeStartObject(); // root {

         // securityService
         if (data.getSecurityQos() != null && !noSecurity) {
            gen.writeFieldName("securityService");
            data.getSecurityQos().toJson(gen);
         }

         // ptpAllowed
         if (data.isPtpAllowedProp().isModified()) {
            gen.writeBooleanField("ptp", data.isPtpAllowed());
         }

         // clusterNode
         if (data.getClusterNodeProp().isModified()) {
            gen.writeBooleanField("clusterNode", data.isClusterNode());
         }

         // refreshSession
         if (data.getRefreshSessionProp().isModified()) {
            gen.writeBooleanField("refreshSession", data.getRefreshSession());
         }

         // duplicateUpdates
         if (data.duplicateUpdatesProp().isModified()) {
            gen.writeBooleanField("duplicateUpdates", data.duplicateUpdates());
         }

         // reconnected
         if (data.getReconnectedProp().isModified()) {
            gen.writeBooleanField("reconnected", data.isReconnected());
         }

         // instanceId
         if (data.getInstanceId() != null) {
            gen.writeStringField("instanceId", data.getInstanceId());
         }

         // persistent
         if (data.getPersistentProp().isModified()) {
            gen.writeBooleanField("persistent", data.isPersistent());
         }

         // session
         if (data.getSessionQos() != null) {
            gen.writeFieldName("session");
            data.getSessionQos().toJson(gen);
         }

         // clientQueueArr
         // only contains cbQueueProperty with relating->client!
         ClientQueueProperty[] cpQueueArr = data.getClientQueuePropertyArr();
         if (cpQueueArr != null && cpQueueArr.length > 0) {
            gen.writeArrayFieldStart("clientQueueArr");
            for (ClientQueueProperty cpQueue : cpQueueArr) {
               cpQueue.toJson(gen);
            }
            gen.writeEndArray();
         }

         // subjectQueueProperty
         if (data.getSubjectQueueProperty() != null) {
            gen.writeFieldName("subjectQueue");
            data.getSubjectQueueProperty().toJson(gen);
         }

         // sessionQueue
         if (data.getSessionCbQueueProperty() != null) {
            gen.writeFieldName("sessionQueue");
            data.getSessionCbQueueProperty().toJson(gen);
         }

         // serverRefs
         ServerRef[] serverRefs = data.getServerRefs();
         if (serverRefs != null && serverRefs.length > 0) {
            gen.writeArrayFieldStart("serverRefs");
            for (ServerRef ref : serverRefs) {
               ref.toJson(gen);
            }
            gen.writeEndArray();
         }

         // clientProperty
         data.writePropertiesJson(gen);

         gen.writeEndObject(); // root }
         gen.close();

         return writer.toString();

      } catch (IOException e) {
         log.warning("Unexpected I/O error writing JSON in ConnectQosJsonFactory: " + e.getMessage());
         return "{}";
      }
   }

   /**
    * A human readable name of this factory
    * 
    * @return "ConnectQosSaxFactory"
    */
   @Override
   public String getName() {
      return "ConnectQosSaxFactory";
   }

}
