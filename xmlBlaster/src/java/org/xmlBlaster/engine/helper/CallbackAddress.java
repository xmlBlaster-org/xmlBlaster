/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.java,v 1.1 2000/06/26 07:12:36 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 */
public class CallbackAddress
{
   private String address;
   private String type;

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
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<callback type='").append(getType()).append("'>");
      sb.append(offset + "   ").append(getAddress());
      sb.append(offset + "</callback>");

      return sb.toString();
   }
}


