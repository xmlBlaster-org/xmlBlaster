/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.java,v 1.6 2002/03/13 16:41:15 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           useForSubjectQueue='true'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/callback>
 * </pre>
 */
public class CallbackAddress
{
   private static final String ME = "CallbackAddress";

   /** The unique address, e.g. the CORBA IOR string */
   private String address = null;

   /** The unique protocol type, e.g. "IOR" */
   private String type;
   
   /** BurstMode: The time to collect messages for update */
   public static final long DEFAULT_collectTime = 0L;
   private long collectTime = XmlBlasterProperty.get("cb.collectTime", DEFAULT_collectTime);
   
   /** Ping interval: pinging every given milliseconds, defaults to one minute */
   public static final long DEFAULT_pingInterval = Constants.MINUTE_IN_MILLIS;
   private long pingInterval = XmlBlasterProperty.get("cb.pingInterval", DEFAULT_pingInterval);
   
   /** How often to retry if callback fails: defaults to 0 retries, on failure we give up */
   public static final int DEFAULT_retries = 0;
   private int retries = XmlBlasterProperty.get("cb.retries", DEFAULT_retries);
   
   /** Delay between callback retires in milliseconds: defaults to one minute */
   public static final long DEFAULT_delay = Constants.MINUTE_IN_MILLIS;
   private long delay = XmlBlasterProperty.get("cb.delay", DEFAULT_delay);
   
   /** Shall this session callback be used for subjectQueue messages as well? */
   public static final boolean DEFAULT_useForSubjectQueue = true;
   private boolean useForSubjectQueue = XmlBlasterProperty.get("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue);
   
   /** Compress messages if set to "gzip" or "zip" */
   public static final String DEFAULT_compressType = "";
   private String compressType = XmlBlasterProperty.get("cb.compress.type", DEFAULT_compressType);
   
   /** Messages bigger this size in bytes are compressed */
   public static final long DEFAULT_minSize = 0L;
   private long minSize = XmlBlasterProperty.get("cb.compress.minSize", DEFAULT_minSize);
   
   /** PtP messages wanted? Defaults to true, false prevents spamming */
   public static final boolean DEFAULT_ptpAllowed = true;
   private boolean ptpAllowed = XmlBlasterProperty.get("cb.ptpAllowed", DEFAULT_ptpAllowed);
   
   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public static final String DEFAULT_sessionId = "unknown";
   private String sessionId = XmlBlasterProperty.get("cb.sessionId", DEFAULT_sessionId);

   /**
    */
   public CallbackAddress()
   {
      setType(null);
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public CallbackAddress(String type)
   {
      setType(type);
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A callback address for your client, suitable to the protocol
    */
   public CallbackAddress(String type, String address)
   {
      setType(type);
      setAddress(address);
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public final void setType(String type)
   {
      this.type = type;
   }

   /**
    * Set the callback address, it should fit to the protocol-type.
    *
    * @param address The callback address, e.g. "et@mars.univers"
    */
   public final void setAddress(String address)
   {
      this.address = address;
   }

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...." or "et@universe.com" or null
    */
   public final String getAddress()
   {
      return address;
   }

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR"
    */
   public final String getType()
   {
      return type;
   }

   /**
    * BurstMode: Access the time to collect messages for update. 
    * @return The time to collect in milliseconds
    */
   public long getCollectTime()
   {
      return collectTime;
   }

   /**
    * BurstMode: The time to collect messages for one update. 
    * @param The time to collect in milliseconds
    */
   public void setCollectTime(long collectTime)
   {
      if (collectTime < 0L)
         this.collectTime = 0L;
      else
         this.collectTime = collectTime;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @return The pause time between pings in millis
    */
   public long getPingInterval()
   {
      return pingInterval;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @param pingInterval The pause time between pings in millis
    */
   public void setPingInterval(long pingInterval)
   {
      if (pingInterval < 0L)
         this.pingInterval = 0L;
      else
         this.pingInterval = pingInterval;
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @return -1 forever, 0 no retry, > 0 number of retries
    */
   public int getRetries()
   {
      return retries;
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @param -1 forever, 0 no retry, > 0 number of retries
    */
   public void setRetries(int retries)
   {
      if (retries < -1)
         this.retries = -1;
      else
         this.retries = retries;
   }

   /**
    * Delay between callback retires in milliseconds, defaults to one minute
    * @return The delay in millisconds
    */
   public long getDelay()
   {
      return delay;
   }

   /**
    * Delay between callback retires in milliseconds, defaults to one minute
    */
   public void setDelay(long delay)
   {
      if (delay < 0L)
         this.delay = 0L;
      else
         this.delay = delay;
   }

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   public boolean useForSubjectQueue()
   {
      return useForSubjectQueue;
   }

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   public void useForSubjectQueue(boolean useForSubjectQueue)
   {
      this.useForSubjectQueue = useForSubjectQueue;
   }

   public void setCompressType(String compressType)
   {
      if (compressType == null) compressType = "";
      this.compressType = compressType;

      // TODO !!!
      if (compressType.length() > 0)
         Log.warn(ME, "Compression of messages is not yet supported");
   }


   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public String getSessionId()
   {
      return sessionId;
   }

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public void setSessionId(String sessionId)
   {
      this.sessionId = sessionId;
   }

   /**
    * Get the compression method. 
    * @return "" No compression
    */
   public String getCompressType()
   {
      return compressType;
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only used if compressType is set to a supported value
    * @return size in bytes
    */
   public long getMinSize()
   {
      return minSize;
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only evaluated if compressType is set to a supported value
    * @return size in bytes
    */
   public void setMinSize(long minSize)
   {
      this.minSize = minSize;
   }

   /**
    * @return true if we may send PtP messages
    */
   public boolean isPtpAllowed()
   {
      return this.ptpAllowed;
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed)
   {
      this.ptpAllowed = ptpAllowed;
   }


   /**
    * Called for SAX callback start tag
    */
   public final void startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs)
   {
      String tmp = character.toString().trim(); // The address
      if (tmp.length() > 0) {
         setAddress(tmp);
      }
      character.setLength(0);

      if (name.equalsIgnoreCase("callback")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  setType(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("sessionId") ) {
                  setSessionId(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("pingInterval") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setPingInterval(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <callback pingInterval='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("retries") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setRetries(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <callback retries='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("delay") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setDelay(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <callback delay='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("useForSubjectQueue") ) {
                  useForSubjectQueue(new Boolean(attrs.getValue(i).trim()).booleanValue());
               }
               else {
                  Log.error(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in callback section.");
               }
            }
         }
         if (getType() == null) {
            Log.error(ME, "Missing 'callback' attribute 'type' in QoS");
            setType("IOR");
         }
         if (getSessionId() == null) {
            Log.warn(ME, "Missing 'callback' attribute 'sessionId' QoS");
         }
         return;
      }

      if (name.equalsIgnoreCase("burstMode")) {
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("collectTime")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setCollectTime(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <burstMode collectTime='" + ll + "'>, expected a long in milliseconds, burst mode is switched off.");
                  }
                  break;
               }
            }
            if (ii >= len)
               Log.error(ME, "Missing 'collectTime' attribute in login-qos <burstMode>");
         }
         else {
            Log.error(ME, "Missing 'collectTime' attribute in login-qos <burstMode>");
         }
         return;
      }

      if (name.equalsIgnoreCase("compress")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  setCompressType(attrs.getValue(ii).trim());
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("minSize")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setMinSize(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <compress minSize='" + ll + "'>, expected a long in bytes, compress is switched off.");
                  }
               }
            }
         }
         else {
            Log.error(ME, "Missing 'type' attribute in qos <compress>");
         }
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         return;
      }
   }


   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character)
   {
      if (name.equalsIgnoreCase("callback")) {
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmp.length() > 0)
            setAddress(tmp);
         else if (getAddress() == null)
            Log.error(ME, "callback QoS contains no address data");
      }
      else if (name.equalsIgnoreCase("burstMode")) {
      }
      else if (name.equalsIgnoreCase("compress")) {
      }
      else if (name.equalsIgnoreCase("ptp")) {
         setPtpAllowed(new Boolean(character.toString().trim()).booleanValue());
      }

      character.setLength(0);
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
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(300);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<callback type='").append(getType()).append("'");
      if (!DEFAULT_sessionId.equals(getSessionId()))
          sb.append(" sessionId='").append(getSessionId()).append("'");
      if (DEFAULT_pingInterval != getPingInterval())
          sb.append(" pingInterval='").append(getPingInterval()).append("'");
      if (DEFAULT_retries != getRetries())
          sb.append(" retries='").append(getRetries()).append("'");
      if (DEFAULT_delay != getDelay())
          sb.append(" delay='").append(getDelay()).append("'");
      if (DEFAULT_useForSubjectQueue != useForSubjectQueue())
          sb.append(" useForSubjectQueue='").append(useForSubjectQueue()).append("'");
      sb.append(">");
      sb.append(offset).append("   ").append(getAddress());
      if (getCollectTime() != DEFAULT_collectTime)
         sb.append(offset).append("   ").append("<burstMode collectTime='").append(getCollectTime()).append("'/>");
      if (!getCompressType().equals(DEFAULT_compressType))
         sb.append(offset).append("   ").append("<compress type='").append(getCompressType()).append("' minSize='").append(getMinSize()).append("'/>");
      if (ptpAllowed != DEFAULT_ptpAllowed)
         sb.append(offset).append("   ").append("<ptp>").append(isPtpAllowed()).append("</ptp>");
      sb.append(offset).append("</callback>");

      return sb.toString();
   }
}


