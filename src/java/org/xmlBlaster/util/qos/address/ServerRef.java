/*------------------------------------------------------------------------------
Name:      ServerRef.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding serverRef address string and protocol string to
           access XmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.xmlBlaster.util.def.Constants;

/**
 * Helper class holding serverRef address string and protocol string.
 * <p />
 * Holds example a CORBA "IOR:00012..." string
 * @author xmlBlaster@marcelruff.info
 */
public class ServerRef
{
   private static final String ME = "ServerRef";

   /** The unique address, e.g. the CORBA IOR string */
   private String address;
   /** The unique protocol type, e.g. "IOR" */
   private String type;

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    */
   public ServerRef(String type) {
      this.type = type;
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    * @param address A serverRef address for your client, suitable to the protocol
    *                for email e.g. "xmlblaster@xmlBlaster.org"
    */
   public ServerRef(String type, String address) {
      this.type = type;
      setAddress(address);
   }

   /**
    * Set the serverRef address, it should fit to the protocol-type.
    *
    * @param address The serverRef address, e.g. "et@mars.univers"
    */
   public final void setAddress(String address) {
      this.address = address;
   }

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...."
    */
   public final String getAddress() {
      return this.address;
   }

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR"
    */
   public final String getType() {
      return this.type;
   }

   public final boolean equals(ServerRef other) {
      return this.type.equals(other.getType()) && this.address.equals(other.getAddress());
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1200);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<serverRef type='").append(getType()).append("'>");
      sb.append(offset).append(getAddress());
      sb.append(offset).append("</serverRef>");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.util.qos.address.ServerRef */
   public static void main(String[] args) {
      try {
         ServerRef ref = new ServerRef("IOR", "IOR:000102111000");
         System.out.println(ref.toXml());
      }
      catch(Throwable e) {
         System.err.println("TestFailed: " + e.toString());
      }
   }
}


