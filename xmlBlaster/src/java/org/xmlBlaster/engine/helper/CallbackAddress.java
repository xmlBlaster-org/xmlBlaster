/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.java,v 1.4 2001/08/31 15:25:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;

/**
 * Helper class holding callback address string and protocol string.
 * <p />
 */
public class CallbackAddress
{
   private static final String ME = "CallbackAddress";

   /** The unique address, e.g. the CORBA IOR string */
   private String address;
   /** The unique protocol type, e.g. "IOR" */
   private String type;
   /** BurstMode: The time to collect messages for update */
   private long collectTime = 0L;
   /** Compress messages if set to "gzip" or "zip" */
   private String compressType = "";
   /** Messages bigger this size in bytes are compressed */
   private long minSize = 0L;
   /** PtP messages wanted? defaults to true */
   private boolean ptpAllowed = true;    // <PtP>false</PtP>    <!-- Don't send me any PtP messages (prevents spamming) -->

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public CallbackAddress(String type)
   {
      this.type = type;
   }


   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A callback address for your client, suitable to the protocol
    */
   public CallbackAddress(String type, String address)
   {
      this.type = type;
      setAddress(address);
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
    * @return e.g. "IOR:00001100022...."
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


   public void setCompressType(String compressType)
   {
      if (compressType == null) compressType = "";
      this.compressType = compressType;

      // TODO !!!
      if (compressType.length() > 0)
         Log.warn(ME, "Compression of messages is not yet supported");
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
      StringBuffer sb = new StringBuffer(300);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<callback type='").append(getType()).append("'>");
      sb.append(offset).append("   ").append(getAddress());
      sb.append(offset).append("   ").append("<burstMode collectTime='").append(getCollectTime()).append("' />");
      sb.append(offset).append("   ").append("<compress type='").append(getCompressType()).append("' minSize='").append(getMinSize()).append("' />");
      sb.append(offset).append("   ").append("<PtP>").append(isPtpAllowed()).append("</PtP>");
      sb.append(offset).append("</callback>");

      return sb.toString();
   }
}


