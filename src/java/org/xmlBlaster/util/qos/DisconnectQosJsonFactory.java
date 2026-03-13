package org.xmlBlaster.util.qos;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Global.FactoryType;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

public class DisconnectQosJsonFactory implements I_DisconnectQosFactory {

   private final Global glob;
   private static Logger log = Logger.getLogger(MsgQosJsonFactory.class.getName());


   public DisconnectQosJsonFactory(Global glob) {
      this.glob = glob;
   }

   /**
    * Parses the given Qos and returns a DisconnectQosData holding the data. 
    * Parsing of disconnect() QoS is supported here.
    * @param jsonQos e.g. the <b>JSON </b>based ASCII string
    */
   @Override
   public DisconnectQosData readObject(String jsonQos) throws XmlBlasterException {
      if (jsonQos == null || jsonQos.trim().isEmpty()) {
         return new DisconnectQosData(glob, this, "{}");
      } 
      
      DisconnectQosData disconnectQosData = new DisconnectQosData(glob, this);
      
      JsonFactory factory = glob.getJsonFactory();

      try (JsonParser parser = factory.createParser(new StringReader(jsonQos))) {

         // step to first object
         parser.nextToken();

         JacksonUtils.safeObjectLoop(glob, parser, "DisconnectQosData", (fieldName) -> {
            switch (fieldName) {

            case "deleteSubjectQueue":
                 disconnectQosData.deleteSubjectQueue(parser.getBooleanValue());
               break;

            case "clearSessions":
                  disconnectQosData.clearSessions(parser.getBooleanValue());
               break;

            case "clientProperties":
                  JacksonUtils.safeArrayLoop(glob, parser, fieldName, () -> {
                        ClientProperty cp =
                           ClientProperty.parseCompactClientProperties(glob, parser);
                        disconnectQosData.addClientProperty(cp);
                  });
               break;

            default:
               try {
                  log.warning("Ignoring unknown DisconnectQos field: " + fieldName);
                  JacksonUtils.skipArrayOrObject(parser);
               } catch (IOException e) {
                  log.severe(
                     "Skipping children failed in DisconnectQosJsonFactory, aborting"
                  );
                  throw e;
               }
            }
         });

      } catch (IOException e) {
         log.severe("Failed to parse JSON DisconnectQos: " + e.getMessage());
         log.info("faulty JSON: " + jsonQos);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT,
               "Failed to parse JSON QoS at: " + e.getMessage());
      }

      return disconnectQosData;
   }

   @Override
   public String writeObject(DisconnectQosData qosData, String extraOffset, Properties props) {
      return toJson(qosData, props);
   }

   @Override
   public String getName() {
      return "DisconnectQosJsonFactory";
   }

   /**
    * Dump state of this object into a JSON string.
    *
    * @return internal state of the DisconnectQos as a JSON string
    */
   public final String toJson(DisconnectQosData data, Properties props) {
      try {
         StringWriter writer = new StringWriter();
         JsonFactory factory = glob.getJsonFactory();
         JsonGenerator gen = factory.createGenerator(writer);
         gen.useDefaultPrettyPrinter();

         gen.writeStartObject();

         // deleteSubjectQueue
         if (data.deleteSubjectQueueProp().isModified()) {
            gen.writeBooleanField("deleteSubjectQueue", data.deleteSubjectQueue());
         }

         // clearSessions
         if (data.clearSessionsProp().isModified()) {
            gen.writeBooleanField("clearSessions", data.clearSessions());
         }

         // clientProperty
         data.writePropertiesJson(gen);

         gen.writeEndObject();
         gen.close();

         return writer.toString();

      } catch (IOException e) {
         log.warning("Unexpected I/O error writing JSON in DisconnectQosJsonFactory: " + e.getMessage());
         return "{}";
      }
   }
}
