/*------------------------------------------------------------------------------
Name:      HistoryQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;


/**
 * Helper class holding QoS settings to acces historical message. 
 * <p />
 * <pre>
 *   &lt;history numEntries='20'/>
 * </pre>
 * <p>
 * Default is to deliver the most current entry (numEntries='1'),
 * '-1' would deliver all history entries available.
 * </p>
 * A future version could extend the query possibilities to e.g.
 * <pre>
 *   &lt;history>
 *      &lt;time from='yesterday' to='now'>
 *   &lt;/history>
 * </pre>
 */
public final class HistoryQos
{
   private static final String ME = "HistoryQos";
   private final Global glob;
   private final LogChannel log;

   public static final int DEFAULT_numEntries = 1;
   private int numEntries = DEFAULT_numEntries;

   /**
    * @param glob The global handle holding environment and logging objects
    */
   public HistoryQos(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("core");
      setNumEntries(this.glob.getProperty().get("history.numEntries", DEFAULT_numEntries));
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param numEntries The number of history entries you want
    */
   public HistoryQos(Global glob, int numEntries) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("core");
      setNumEntries(numEntries);
   }

   /**
    * @param numEntries The number of history entries, 
    *                   not more than the current size of the history queue are returned.<br />
    *            If -1 all entries in history queue are returned
    */
   public void setNumEntries(int numEntries) {
      this.numEntries = (numEntries < -1) ? -1 : numEntries;
   }

   /**
    * Returns the number of history entries.
    * @return e.g. 1
    */
   public int getNumEntries() {
      return numEntries;
   }

   /**
    * Called for SAX history start tag
    * @return true if ok, false on error
    */
   public boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      String tmp = character.toString().trim(); // The query
      if (tmp.length() > 0) {
         log.warn(ME, "Ignoring history QoS query data '" + tmp + "'");
      }
      character.setLength(0);

      if (name.equalsIgnoreCase("history")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if (attrs.getQName(i).equalsIgnoreCase("numEntries") ) {
                  String entryStr = attrs.getValue(i).trim();
                  try { setNumEntries(Integer.parseInt(entryStr)); } catch(NumberFormatException e) { log.error(ME, "Invalid history - numEntries =" + entryStr); };
               }
               else {
                  log.warn(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in history section.");
               }
            }
         }
         return true;
      }

      return false;
   }


   /**
    * Handle SAX parsed end element
    */
   public void endElement(String uri, String localName, String name, StringBuffer character) {
      if (name.equalsIgnoreCase("history")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0) {
            log.warn(ME, "Ignoring history QoS query data '" + tmp + "'");
         }
      }
      character.setLength(0);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    */
   public String toXml() {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation or "" if all settings are default
    */
   public String toXml(String extraOffset) {
      if (getNumEntries() == DEFAULT_numEntries) {
         return "";
      }

      StringBuffer sb = new StringBuffer(300);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<history numEntries='").append(getNumEntries()).append("'/>");

      return sb.toString();
   }
}


