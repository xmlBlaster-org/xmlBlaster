/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: QueueProperty.java,v 1.2 2002/03/13 16:41:15 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;

/**
 * Helper class holding callback address string and protocol string.
 * <p />
 */
public class QueueProperty
{
   private static final String ME = "QueueProperty";

   /** The max setting allowed for queue maxMsg is adjustable with property "queue.maxMsg=1000" (1000 messages is default) */
   private static final int maxMsgDefault = XmlBlasterProperty.get("queue.maxMsg", 1000);
   
   /** The max setting allowed for queue maxSize in kBytes is adjustable with property "queue.maxSize=4000" (4 MBytes is default) */
   private static final int maxSizeDefault = XmlBlasterProperty.get("queue.maxSize", 2000);

   /** The min span of life is one second, changeable with property e.g. "queue.expires.min=2000" milliseconds */
   private static final long minExpires = XmlBlasterProperty.get("queue.expires.min", 1000L);

   /** The max span of life of a queue is currently forever (=0), changeable with property e.g. "queue.expires.max=3600000" milliseconds */
   private static final long maxExpires = XmlBlasterProperty.get("queue.expires.max", 0L); // Long.MAX_VALUE);

   /** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue.expires=3600000" milliseconds */
   private static final long defaultExpires = XmlBlasterProperty.get("queue.expires", maxExpires);

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   private String cbSessionId = null;
   /** The unique protocol relating, e.g. "IOR" */
   private String relating = Constants.RELATING_SESSION;
   /** Span of life of this queue in milliseconds */
   private long expires = defaultExpires;
   /** The max. capacity of the queue in number of entries */
   private int maxMsg = maxMsgDefault;
   /** The max. capacity of the queue in kBytes */
   private int maxSize = maxSizeDefault;

   /** Error handling when queue is full: Constants.ONOVERFLOW_BLOCK | Constants.ONOVERFLOW_DEADLETTER | Constants.ONOVERFLOW_DISCARDOLDEST */
   private String onOverflow = XmlBlasterProperty.get("queue.onOverflow", Constants.ONOVERFLOW_BLOCK);

   /** Error handling when callback failed (after all retries etc.): Constants.ONOVERFLOW_DEADLETTER */
   private String onFailure = XmlBlasterProperty.get("queue.onFailure", Constants.ONOVERFLOW_DEADLETTER);

   /** The corresponding callback address */
   private CallbackAddress[] addressArr = new CallbackAddress[0];

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_UNRELATED
    */
   public QueueProperty(String relating)
   {
      setRelating(relating);
   }


   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_UNRELATED
    */
   public final void setRelating(String relating)
   {
      if (relating == null) {
         this.relating = Constants.RELATING_SESSION;
         return;
      }
      relating = relating.toLowerCase();
      if (Constants.RELATING_SESSION.equals(relating))
         this.relating = Constants.RELATING_SESSION;
      else if (Constants.RELATING_SUBJECT.equals(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else if (Constants.RELATING_UNRELATED.equals(relating))
         this.relating = Constants.RELATING_UNRELATED;
      else {
         Log.warn(ME, "The queue relating attribute is invalid '" + relating + "', setting to session scope");
         this.relating = Constants.RELATING_SESSION;
      }
   }

   /**
    * Returns the queue type. 
    * @return relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_UNRELATED
    */
   public final String getRelating()
   {
      return this.relating;
   }

   public final boolean isSubjectRelated() {
      return Constants.RELATING_SUBJECT.equals(getRelating());
   }
   public final boolean isSessionRelated() {
      return Constants.RELATING_SESSION.equals(getRelating());
   }
   public final boolean isUnrelated() {
      return Constants.RELATING_UNRELATED.equals(getRelating());
   }

   /** 
    * Span of life of this queue.
    * @return Expiry time in milliseconds or 0L if forever
    */
   public long getExpires()
   {
      return expires;
   }

   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   public void setExpires(long expires)
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


   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public String getCbSessionId()
   {
      return cbSessionId;
   }

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public void setCbSessionId(String cbSessionId)
   {
      this.cbSessionId = cbSessionId;
   }

   /** 
    * Max number of messages for this queue. 
    * <br />
    * @return number of messages
    */
   public int getMaxMsg()
   {
      return maxMsg;
   }

   /** 
    * Max number of messages for this queue. 
    * <br />
    * @param maxMsg
    */
   public void setMaxMsg(int maxMsg)
   {
      this.maxMsg = maxMsg;
   }


   /** 
    * Max message queue size. 
    * <br />
    * @return Get max. message queue size in kBytes
    */
   public int getMaxSize()
   {
      return maxSize;
   }

   /** 
    * Max message queue size. 
    * <br />
    * @return Set max. message queue size in kBytes
    */
   public void setMaxSize(int maxSize)
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

         Log.error(ME, "queue onOverflow='" + Constants.ONOVERFLOW_DISCARDOLDEST + "' is not implemented, switching to blocking mode");
         this.onOverflow = Constants.ONOVERFLOW_BLOCK; // TODO !!!
      }
      else {
         Log.warn(ME, "The queue onOverflow attribute is invalid '" + onOverflow + "', setting to 'deadLetter'");
         this.onOverflow = Constants.ONOVERFLOW_BLOCK;
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

   public final boolean onOverflowDeadLetter()
   {
      if (Constants.ONOVERFLOW_DEADLETTER.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
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
         Log.warn(ME, "The queue onFailure attribute is invalid '" + onFailure + "', setting to 'deadLetter'");
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
    * The default mode is to send a dead letter if callback fails permanently
    */
   public final boolean onFailureDeadLetter()
   {
      if (Constants.ONOVERFLOW_DEADLETTER.equalsIgnoreCase(getOnFailure()))
         return true;
      return false;
   }

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   public void setCallbackAddress(CallbackAddress address)
   {
      this.addressArr = new CallbackAddress[1];
      this.addressArr[0] = address;
   }

   /**
    */
   public void setCallbackAddresses(CallbackAddress[] addresses)
   {
      this.addressArr = addresses;
   }

   /**
    * @return null if none available
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      return this.addressArr;
   }

   /**
    * @return null if none available
    */
   public CallbackAddress getCurrentCallbackAddress()
   {
      if (this.addressArr.length > 0)
         return this.addressArr[0];
      return null;
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
                  Log.error(ME, "Wrong format of <queue maxMsg='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxSize")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxSize(new Integer(tmp).intValue());
               } catch (NumberFormatException e) {
                  Log.error(ME, "Wrong format of <queue maxSize='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("expires")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setExpires(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  Log.error(ME, "Wrong format of <queue expires='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onOverflow")) {
               setOnOverflow(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onFailure")) {
               setOnFailure(attrs.getValue(ii).trim());
            }
            else
               Log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' in connect QoS <queue>");
         }
      }
      else {
         Log.warn(ME, "Missing 'relating' attribute in connect QoS <queue>");
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

      buf.append(offset).append("<!-- QueueProperty -->");
      buf.append(offset).append("<queue relating='").append(getRelating());
      buf.append("' maxMsg='").append(getMaxMsg());
      buf.append("' maxSize='").append(getMaxSize());
      buf.append("' expires='").append(getExpires());
      buf.append("' onOverflow='").append(getOnOverflow());
      buf.append("' onFailure='").append(getOnFailure());
      if (addressArr.length > 0 && addressArr[0] != null) {
         buf.append("'>");
         for (int ii=0; ii<addressArr.length; ii++) {
            CallbackAddress ad = addressArr[ii];
            buf.append(ad.toXml(extraOffset+"   "));
         }
         buf.append(offset).append("</queue>");
      }
      else
         buf.append("'/>");

      return buf.toString();
   }


   /** For testing: java org.xmlBlaster.engine.helper.QueueProperty */
   public static void main(String[] args)
   {
      QueueProperty prop = new QueueProperty(null);
      System.out.println(prop.toXml());
      CallbackAddress adr = new CallbackAddress("EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setCallbackAddress(adr);
      System.out.println(prop.toXml());
   }
}


