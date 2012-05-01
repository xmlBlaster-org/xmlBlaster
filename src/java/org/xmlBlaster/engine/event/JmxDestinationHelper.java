package org.xmlBlaster.engine.event;

import java.util.Map;

import org.xmlBlaster.engine.EventPlugin;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Helper class to send a JMX notification.
 */
public class JmxDestinationHelper {
   private final EventPlugin eventPlugin;
   // private String destination;
   private String contentTemplate;
   public JmxDestinationHelper(EventPlugin eventPlugin, String destination) throws XmlBlasterException {
      this.eventPlugin = eventPlugin;
      @SuppressWarnings("unchecked")
      Map<String, String> map = StringPairTokenizer.parseLineToProperties(destination);
      if (map.containsKey("jmx.content"))
         this.contentTemplate = (String) map.get("jmx.content");
      else
         this.contentTemplate = "$_{eventType}: $_{summary}";
   }

   public String getMessage(String summary, String description,
         String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
      String content = this.eventPlugin.replaceTokens(
         this.contentTemplate, summary, description, eventType, errorCode, sessionName);
      return content;

   }
}