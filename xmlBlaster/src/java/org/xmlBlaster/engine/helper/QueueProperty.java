/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */
public class QueueProperty extends QueuePropertyBase
{
   private static final String ME = "QueueProperty";

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public QueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      setRelating(Constants.RELATING_CLIENT);
      initialize();
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentAddress() != null)
         buf.append(" ").append(getCurrentAddress().getSettings());
      return buf.toString();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {
      super.initialize(""); // todo: change to "client" (all REQs and docu as well)
   }

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   public void setAddress(Address address) {
      this.addressArr = new Address[1];
      this.addressArr[0] = address;
   }

   /**
    */
   public void setAddresses(Address[] addresses) {
      this.addressArr = addresses;
   }

   /**
    * @return null if none available
   public Address[] getAddresses() {
      return (Address[])this.addressArr;
   }
    */

   /**
    * @return null if none available
    */
   public Address getCurrentAddress() {
      if (this.addressArr.length > 0)
         return (Address)this.addressArr[0];
      return null;
   }

   /**
    * Get a usage string for the connection parameters
    */
   public final String usage() {
      String text = "";
      text += "Control client side fail save queue properties (message recorder):\n";
      text += "   -queue.maxMsg       The maximum allowed number of messages in this queue [" + DEFAULT_maxMsgDefault + "].\n";
      text += "                       0 switches recording of invocations off.\n";
      text += "                       -1 sets it to unlimited.\n";
      text += "   -queue.type         The queue plugin type [" + DEFAULT_type + "].\n";
      text += "   -queue.version      The queue plugin type [" + DEFAULT_version + "].\n";
      text += "   -recorder.type      The plugin type to use for tail back messages in fail save mode [FileRecorder]\n";
      text += "   -recorder.version   The version of the plugin [1.0]\n";
      text += "   -recorder.path      The path (without file name) for the file for FileRecorder [<is generated>]\n";
      text += "   -recorder.fn        The file name (without path) for the file for FileRecorder [<is generated unique>]\n";
      text += "   -recorder.rate      The playback rate in msg/sec on reconnect e.g. 200 is 200 msg/sec, -1 is as fast as possible [-1]\n";
      text += "   -recorder.mode      The on-overflow mode: " + Constants.ONOVERFLOW_EXCEPTION + " | " + Constants.ONOVERFLOW_DISCARD + " | " + Constants.ONOVERFLOW_DISCARDOLDEST + " [" + Constants.ONOVERFLOW_EXCEPTION + "]\n";
    //text += "   -queue.maxBytes      The maximum size in bytes of this queue [" + DEFAULT_bytesDefault + "].\n";
    //text += "   -queue.expires      If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -queue.onOverflow   What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
    //text += "   -queue.onFailure    What happens if the data sink connection has a failure [" + DEFAULT_onFailure + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.engine.helper.QueueProperty */
   public static void main(String[] args) {
      QueueProperty prop = new QueueProperty(new Global(args), null);
      System.out.println(prop.toXml());
      Address adr = new Address(new Global(args), "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setAddress(adr);
      System.out.println(prop.toXml());
   }
}


