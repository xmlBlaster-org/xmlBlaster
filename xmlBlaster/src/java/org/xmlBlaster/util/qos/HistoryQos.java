/*------------------------------------------------------------------------------
Name:      HistoryQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;


/**
 * Helper class holding QoS settings to acces historical message. 
 * <p />
 * <pre>
 *   &lt;history numEntries='20'/>   <!-- type="index" (is default) -->
 * </pre>
 * <p>
 * Default is to deliver the most current entry (numEntries='1'),
 * '-1' would deliver all history entries available.
 * </p>
 * A future version could extend the query possibilities to e.g.
 * <pre>
 *   &lt;history type="sql92">  <!-- client properties -->
 *      myKey=2000
 *   &lt;/history>
 *
 *   &lt;history type="regex">  <!-- content check -->
 *      a.*
 *   &lt;/history>
 *
 *   &lt;history type="special">
 *      &lt;time from='yesterday' to='now'>
 *   &lt;/history>
 * </pre>
 */
public final class HistoryQos
{
   private static final String ME = "HistoryQos";
   private final Global glob;
   private static Logger log = Logger.getLogger(HistoryQos.class.getName());

   public static final int DEFAULT_numEntries = 1;
   private int numEntries = DEFAULT_numEntries;

   public static final boolean DEFAULT_newestFirst = true;
   private boolean newestFirst = DEFAULT_newestFirst;

   /**
    * @param glob The global handle holding environment and logging objects
    */
   public HistoryQos(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;

      setNumEntries(this.glob.getProperty().get("history.numEntries", DEFAULT_numEntries));
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param numEntries The number of history entries you want
    */
   public HistoryQos(Global glob, int numEntries) {
      this.glob = (glob == null) ? Global.instance() : glob;

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
    * The sorting order in which the history entries are delivered. 
    * The higher priority messages are always delivered first.
    * In one priority the newest message is delivered first with 'true', setting 'false'
    * reverts the delivery sequence in this priority.
    * @param newestFirst defaults to true. 
    */
   public void setNewestFirst(boolean newestFirst) {
      this.newestFirst = newestFirst;
   }

   /**
    * @return defaults to true
    * @see #setNewestFirst(boolean)
    */
   public boolean getNewestFirst() {
      return newestFirst;
   }

   /**
    * Called for SAX history start tag
    * @return true if ok, false on error
    */
   public boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      String tmp = character.toString().trim(); // The query
      if (tmp.length() > 0) {
         log.warning("Ignoring history QoS query data '" + tmp + "'");
      }
      character.setLength(0);

      if (name.equalsIgnoreCase("history")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if (attrs.getQName(i).equalsIgnoreCase("numEntries") ) {
                  String entryStr = attrs.getValue(i).trim();
                  try { setNumEntries(Integer.parseInt(entryStr)); } catch(NumberFormatException e) { log.severe("Invalid history - numEntries =" + entryStr); };
               }
               else if (attrs.getQName(i).equalsIgnoreCase("newestFirst") ) {
                  setNewestFirst((new Boolean(attrs.getValue(i).trim())).booleanValue());
               }
               else {
                  log.warning("Ignoring unknown attribute " + attrs.getQName(i) + " in history section.");
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
            log.warning("Ignoring history QoS query data '" + tmp + "'");
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

      sb.append(offset).append("<history");
      sb.append(" numEntries='").append(getNumEntries()).append("'");
      sb.append(" newestFirst='").append(getNewestFirst()).append("'");
      sb.append("/>");

      return sb.toString();
   }
}


