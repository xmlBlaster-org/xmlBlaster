/*------------------------------------------------------------------------------
Name:      CbQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.java,v 1.5 2002/11/26 12:38:45 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */
public class CbQueueProperty extends QueuePropertyBase
{
   private static final String ME = "CbQueueProperty";
   private final LogChannel log;

   /**
    * @param relating  To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public CbQueueProperty(Global glob, String relating, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("dispatch");
      initialize();
      setRelating(relating);
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentCallbackAddress() != null)
         buf.append(" ").append(getCurrentCallbackAddress().getSettings());
      return buf.toString();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {

      super.initialize();

      // Set the queue properties
      setMaxMsg(glob.getProperty().get("cb.queue.maxMsg", DEFAULT_maxMsgDefault));
      setMaxSize(glob.getProperty().get("cb.queue.maxSize", DEFAULT_sizeDefault));
      setMaxMsgCache(glob.getProperty().get("cb.queue.maxMsgCache", DEFAULT_maxMsgCacheDefault));
      setMaxSizeCache(glob.getProperty().get("cb.queue.maxSizeCache", DEFAULT_sizeCacheDefault));
      setStoreSwapLevel(glob.getProperty().get("cb.queue.storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio*this.maxSizeCache)));
      setStoreSwapSize(glob.getProperty().get("cb.queue.storeSwapSize", (long)(DEFAULT_storeSwapSizeRatio*this.maxSizeCache)));
      setReloadSwapLevel(glob.getProperty().get("cb.queue.reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio*this.maxSizeCache)));
      setReloadSwapSize(glob.getProperty().get("cb.queue.reloadSwapSize", (long)(DEFAULT_reloadSwapSizeRatio*this.maxSizeCache)));
      setExpires(glob.getProperty().get("cb.queue.expires", DEFAULT_maxExpires));
      setOnOverflow(glob.getProperty().get("cb.queue.onOverflow", DEFAULT_onOverflow));
      setOnFailure(glob.getProperty().get("cb.queue.onFailure", DEFAULT_onFailure));
      setType(glob.getProperty().get("cb.queue.type", DEFAULT_type));
      setVersion(glob.getProperty().get("cb.queue.version", DEFAULT_version));
      if (nodeId != null) {
         setMaxMsg(glob.getProperty().get("cb.queue.maxMsg["+nodeId+"]", getMaxMsg()));
         setMaxSize(glob.getProperty().get("cb.queue.maxSize["+nodeId+"]", getMaxSize()));
         setExpires(glob.getProperty().get("cb.queue.expires["+nodeId+"]", getExpires()));
         setOnOverflow(glob.getProperty().get("cb.queue.onOverflow["+nodeId+"]", getOnOverflow()));
         setOnFailure(glob.getProperty().get("cb.queue.onFailure["+nodeId+"]", getOnFailure()));
         setType(glob.getProperty().get("cb.queue.type["+nodeId+"]", getType()));
         setVersion(glob.getProperty().get("cb.queue.version["+nodeId+"]", getVersion()));
      }
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   public final void setRelating(String relating) {
      if (relating == null) {
         this.relating = Constants.RELATING_SESSION;
         return;
      }
      relating = relating.toLowerCase();
      if (Constants.RELATING_SESSION.equals(relating))
         this.relating = Constants.RELATING_SESSION;
      else if (Constants.RELATING_SUBJECT.equals(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else {
         log.warn(ME, "The queue relating attribute is invalid '" + relating + "', setting to session scope");
         this.relating = Constants.RELATING_SESSION;
      }
   }

   public final boolean isSubjectRelated() {
      return Constants.RELATING_SUBJECT.equals(getRelating());
   }
   public final boolean isSessionRelated() {
      return Constants.RELATING_SESSION.equals(getRelating());
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }


   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   public void setCallbackAddress(CallbackAddress address) {
      this.addressArr = new CallbackAddress[1];
      this.addressArr[0] = address;
   }

   /**
    */
   public void setCallbackAddresses(CallbackAddress[] addresses) {
      this.addressArr = addresses;
   }

   /**
    * @return array with size 0 if none available
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      CallbackAddress[] cba = new CallbackAddress[addressArr.length];
      for (int ii=0; ii<addressArr.length; ii++)
         cba[ii] = (CallbackAddress)addressArr[ii];
      return cba;
   }

   /**
    * @return null if none available
    */
   public CallbackAddress getCurrentCallbackAddress() {
      if (this.addressArr.length > 1)
         log.error(ME, "We have " + this.addressArr.length + " callback addresses, using the first only");
      if (this.addressArr.length > 0)
         return (CallbackAddress)this.addressArr[0];
      return null;
   }

   /**
    * Get a usage string for the connection parameters
    */
   public final String usage() {
      String text = "";
      text += "Control the callback queue properties:\n";
      text += "   -cb.queue.maxMsg       The maximum allowed number of messages in this queue [" + DEFAULT_maxMsgDefault + "].\n";
      text += "   -cb.queue.maxMsgCache  The maximum allowed number of messages in the cache of this queue [" + DEFAULT_maxMsgDefault + "].\n";
      text += "   -cb.queue.maxSize      The maximum size in kBytes of this queue [" + DEFAULT_sizeDefault + "].\n";
      text += "   -cb.queue.maxSizeCache The maximum size in kBytes in the cache of this queue [" + DEFAULT_sizeDefault + "].\n";
    //text += "   -cb.queue.expires  If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -cb.queue.onOverflow What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
      text += "   -cb.queue.onOverflow What happens if queue is full [" + DEFAULT_onOverflow + "]\n";
      text += "   -cb.queue.onFailure  Error handling when callback failed [" + DEFAULT_onFailure + "]\n";
      text += "   -cb.queue.type       The plugin type [" + DEFAULT_type + "]\n";
      text += "   -cb.queue.version    The plugin version [" + DEFAULT_version + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.engine.helper.CbQueueProperty */
   public static void main(String[] args) {
      CbQueueProperty prop = new CbQueueProperty(new Global(args), null, null);
      System.out.println(prop.toXml());
      CallbackAddress adr = new CallbackAddress(new Global(args), "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setCallbackAddress(adr);
      System.out.println(prop.toXml());
   }
}


