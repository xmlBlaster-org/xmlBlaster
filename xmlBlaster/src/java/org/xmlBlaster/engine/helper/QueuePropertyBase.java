/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyBase.java,v 1.4 2002/09/13 23:18:01 ruff Exp $
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
public abstract class QueuePropertyBase
{
   private static final String ME = "QueuePropertyBase";
   protected final Global glob;
   protected final LogChannel log;

   /** The max setting allowed for queue maxMsg is adjustable with property "queue.maxMsg=1000" (1000 messages is default) */
   public static final int DEFAULT_maxMsgDefault = 1000;
   protected int maxMsgDefault;
   
   /** The max setting allowed for queue maxSize in kBytes is adjustable with property "queue.maxSize=4000" (4 MBytes is default) */
   public static final int DEFAULT_sizeDefault = 2000;
   protected int maxSizeDefault;

   /** The min span of life is one second, changeable with property e.g. "queue.expires.min=2000" milliseconds */
   public static final long DEFAULT_minExpires = 1000L;
   protected long minExpires;

   /** The max span of life of a queue is currently forever (=0), changeable with property e.g. "queue.expires.max=3600000" milliseconds */
   public static final long DEFAULT_maxExpires = 0L;
   protected long maxExpires;

   /** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue.expires=3600000" milliseconds */
   public long DEFAULT_expires;

   /** The unique protocol relating, e.g. "IOR" */
   protected String relating = Constants.RELATING_SESSION;
   /** Span of life of this queue in milliseconds */
   protected long expires = DEFAULT_expires;
   /** The max. capacity of the queue in number of entries */
   protected int maxMsg;
   /** The max. capacity of the queue in kBytes */
   protected int maxSize;

   /** Error handling when queue is full: Constants.ONOVERFLOW_BLOCK | Constants.ONOVERFLOW_DEADLETTER | Constants.ONOVERFLOW_DISCARDOLDEST */
   public static final String DEFAULT_onOverflow = Constants.ONOVERFLOW_DEADLETTER;
   protected String onOverflow;

   /** Error handling when callback failed (after all retries etc.): Constants.ONOVERFLOW_DEADLETTER */
   public static final String DEFAULT_onFailure = Constants.ONOVERFLOW_DEADLETTER;
   protected String onFailure;

   /** The corresponding callback address */
   protected AddressBase[] addressArr = new AddressBase[0];

   /** To allow specific configuration parameters for specific cluster nodes */
   protected String nodeId = null;

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public QueuePropertyBase(Global glob, String nodeId)
   {
      if (glob == null) {
         Thread.currentThread().dumpStack();
         this.glob = new Global();
      }
      else
         this.glob = glob;
      this.log = glob.getLog("core");
      this.nodeId = nodeId;
   }

   /**
    * Show some important settings for logging
    */
    /*
   public String getSettings()
   {
      StringBuffer buf = new StringBuffer(256);
      buf.append("onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentCallbackAddress() != null)
         buf.append(" ").append(getCurrentCallbackAddress().getSettings());
      return buf.toString();
   }  */

   /**
    * Configure property settings, add your own defaults in the derived class
    */
   protected void initialize() {
      // Do we need this range settings?
      setMinExpires(glob.getProperty().get("queue.expires.min", DEFAULT_minExpires));
      setMaxExpires(glob.getProperty().get("queue.expires.max", DEFAULT_maxExpires)); // Long.MAX_VALUE);
      if (nodeId != null) {
         setMinExpires(glob.getProperty().get("queue.expires.min["+nodeId+"]", getMinExpires()));
         setMaxExpires(glob.getProperty().get("queue.expires.max["+nodeId+"]", getMaxExpires())); // Long.MAX_VALUE);
      }
   }

   protected void setMaxExpires(long maxExpires) { this.maxExpires = maxExpires; }
   protected long getMaxExpires() { return this.maxExpires; }

   protected void setMinExpires(long minExpires) { this.minExpires = minExpires; }
   protected long getMinExpires() { return this.minExpires; }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_UNRELATED
    */
   public void setRelating(String relating)
   {
      log.warn(ME, "Ignoring relating=" + relating);
      Thread.currentThread().dumpStack();
   }

   /**
    * Returns the queue type. 
    * @return relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_UNRELATED
    */
   public final String getRelating() {
      return this.relating;
   }

   /** 
    * Span of life of this queue.
    * @return Expiry time in milliseconds or 0L if forever
    */
   public final long getExpires()
   {
      return expires;
   }

   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   public final void setExpires(long expires)
   {
      if (maxExpires <= 0L)
         this.expires = expires;
      else if (expires > 0L && maxExpires > 0L && expires > maxExpires)
         this.expires = maxExpires;
      else if (expires <= 0L && maxExpires > 0L)
         this.expires = maxExpires;
         
      if (expires > 0L && expires < minExpires)
         this.expires = minExpires;
   }


   /** 
    * Max number of messages for this queue. 
    * <br />
    * @return number of messages
    */
   public final int getMaxMsg()
   {
      return maxMsg;
   }

   /** 
    * Max number of messages for this queue. 
    * <br />
    * @param maxMsg
    */
   public final void setMaxMsg(int maxMsg)
   {
      this.maxMsg = maxMsg;
   }


   /** 
    * Max message queue size. 
    * <br />
    * @return Get max. message queue size in kBytes
    */
   public final int getMaxSize()
   {
      return maxSize;
   }

   /** 
    * Max message queue size. 
    * <br />
    * @return Set max. message queue size in kBytes
    */
   public final void setMaxSize(int maxSize)
   {
      this.maxSize = maxSize;
   }


   /**
    * Set the callback onOverflow, it should fit to the protocol-relating.
    *
    * @param onOverflow The callback onOverflow, e.g. "et@mars.univers"
    */
   public final void setOnOverflow(String onOverflow)
   {
      if (Constants.ONOVERFLOW_BLOCK.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_BLOCK;
      }
      else if (Constants.ONOVERFLOW_DEADLETTER.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_DEADLETTER;
      }
      else if (Constants.ONOVERFLOW_DISCARDOLDEST.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_DISCARDOLDEST;

         this.onOverflow = Constants.ONOVERFLOW_DEADLETTER; // TODO !!!
         log.error(ME, "queue onOverflow='" + Constants.ONOVERFLOW_DISCARDOLDEST + "' is not implemented, switching to " + this.onOverflow + " mode");
      }
      else {
         this.onOverflow = Constants.ONOVERFLOW_DEADLETTER;
         log.warn(ME, "The queue onOverflow attribute is invalid '" + onOverflow + "', setting to '" + this.onOverflow + "'");
      }
   }

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnOverflow()
   {
      return onOverflow;
   }

   /**
    * The default mode, when queue is full the publisher blocks until
    * there is space again. 
    */
   public final boolean onOverflowBlock()
   {
      if (Constants.ONOVERFLOW_BLOCK.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }

   /**
    * Set the callback onFailure, it should fit to the protocol-relating.
    *
    * @param onFailure The callback onFailure, e.g. "et@mars.univers"
    */
   public final void setOnFailure(String onFailure)
   {
      if (Constants.ONOVERFLOW_DEADLETTER.equalsIgnoreCase(onFailure))
         this.onFailure = Constants.ONOVERFLOW_DEADLETTER;
      else {
         log.warn(ME, "The queue onFailure attribute is invalid '" + onFailure + "', setting to 'deadLetter'");
         this.onFailure = Constants.ONOVERFLOW_DEADLETTER;
      }
   }

   /**
    * Returns the onFailure.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnFailure()
   {
      return onFailure;
   }

   /**
    * Called for queue start tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (attrs != null) {
         int len = attrs.getLength();
         int ii=0;
         for (ii = 0; ii < len; ii++) {
            if (attrs.getQName(ii).equalsIgnoreCase("relating")) {
               setRelating(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxMsg")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxMsg(new Integer(tmp).intValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxMsg='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxSize")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxSize(new Integer(tmp).intValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxSize='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("expires")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setExpires(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue expires='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onOverflow")) {
               setOnOverflow(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onFailure")) {
               setOnFailure(attrs.getValue(ii).trim());
            }
            else
               log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' in connect QoS <queue>");
         }
      }
      else {
         log.warn(ME, "Missing 'relating' attribute in connect QoS <queue>");
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer buf = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset != null) offset += extraOffset;
      else extraOffset = "";

      buf.append(offset).append("<!-- QueuePropertyBase -->");

      buf.append(offset).append("<queue relating='").append(getRelating());
      if (DEFAULT_maxMsgDefault != getMaxMsg())
         buf.append("' maxMsg='").append(getMaxMsg());
      if (DEFAULT_sizeDefault != getMaxSize())
         buf.append("' maxSize='").append(getMaxSize());
      if (DEFAULT_expires != getExpires())
         buf.append("' expires='").append(getExpires());
      if (DEFAULT_onOverflow != getOnOverflow())
         buf.append("' onOverflow='").append(getOnOverflow());
      if (DEFAULT_onFailure != getOnFailure())
         buf.append("' onFailure='").append(getOnFailure());

      if (addressArr.length > 0 && addressArr[0] != null) {
         buf.append("'>");
         for (int ii=0; ii<addressArr.length; ii++) {
            AddressBase ad = addressArr[ii];
            buf.append(ad.toXml(extraOffset+"   "));
         }
         buf.append(offset).append("</queue>");
      }
      else
         buf.append("'/>");

      return buf.toString();
   }
}


