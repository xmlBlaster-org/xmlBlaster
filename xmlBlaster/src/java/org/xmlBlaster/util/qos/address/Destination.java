/*------------------------------------------------------------------------------
Name:      Destination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.Constants;

/**
 * Holding destination address attributes.
 * <p />
 * This class corresponds to the QOS destination tag
 * @author xmlBlaster@marcelruff.info
 */
public class Destination implements java.io.Serializable
{
   private static final long serialVersionUID = 1L;
   private static Logger log = Logger.getLogger(Destination.class.getName());
   private String ME = "Destination";

   /** The destination address (==login name) or the XPath query string */
   private SessionName destination = null;
   /** EXACT is default */
   private String queryType = "EXACT";
   /** No queuing is default */
   private boolean DEFAULT_forceQueuing = false;
   private boolean forceQueuing = DEFAULT_forceQueuing;

   /** For SAX parser */
   public Destination() {
      //this(null, null);
   }

   /**
    * Constructs the specialized quality of service destination object.
    * @param address The destination address (EXACT),
    *                this is typically the login name of another client
    */
   public Destination(SessionName address) {
      this(null, address);
   }


   /**
    * Constructs the specialized quality of service destination object.
    * @param address The destination address (EXACT),
    *                this is typically the login name of another client
    */
   public Destination(Global glob, SessionName address) {
      setDestination(address);
   }


   /**
    * Constructs the specialized quality of service destination object.
    * @param address The destination address or query string
    * @param queryType "EXACT" or "XPATH"
    */
    // @exception IllegalArgumentException for XPATH (not implemented yet)
   public Destination(String address, String queryType) {
      setQueryType(queryType);
      if (isXPathQuery()) {
         log.severe("Query type " + queryType + " is not implemented");
         //throw new IllegalArgumentException(ME+": Query type " + queryType + " is not implemented");
      }
      else {
         setDestination(new SessionName(Global.instance(), address));
      }
   }

   /**
    * @return true/false
    */
   public boolean isXPathQuery() {
      return queryType.equals("XPATH");
   }

   /**
    * @return true/false
    */
   public boolean isExactAddress() {
      return queryType.equals("EXACT");
   }

   /**
    * Check if the address is a sessionId
   public boolean isSessionId() {
      if (destination == null)
         return false;
      if (!isExactAddress())
         return false;
      return destination.startsWith(Constants.SESSIONID_PREFIX);
   }
    */

   /**
    * @return true/false
    */
   public boolean forceQueuing() {
      return forceQueuing;
   }

   /**
    * Set queuing of messages.
    * <p />
    * true: If client is not logged in, messages will be queued until he comes. <br />
    * false: Default is that on PtP messages when the destination address is
    *        not online, an Exception is thrown
    */
   public void forceQueuing(boolean forceQueuing) {
      this.forceQueuing = forceQueuing;
   }

   /**
    * Set the destination address or the destination query string.
    * @param destination The destination address or the query string, may not be null
    */
   public final void setDestination(SessionName destination) {
      if (destination == null) {
         throw new IllegalArgumentException("Destination.setDestination with null value");
      }
      this.destination = destination;
   }


   /**
    * @param The destination address or XPath query string
    */
   public final SessionName getDestination() {
      return destination;
   }

   /**
    * @param queryType The query type, one of "EXACT" | "XPATH"
    * @exception IllegalArgumentException for unknown queryType
    */
   public final void setQueryType(String queryType) {
      if (queryType.equalsIgnoreCase("EXACT"))
         this.queryType = queryType;
      else if (queryType.equalsIgnoreCase("XPATH"))
         log.severe("Query type " + queryType + " is not implemented");
         //throw new IllegalArgumentException(ME+": Query type " + queryType + " is not implemented");
      else
         throw new IllegalArgumentException(ME+": Query type " + queryType + " is not implemented");
   }

   public final String toString() {
      return destination.getAbsoluteName();
   }

   /**
    * Get the XML ASCII representation of this object.
    * <br>
    * @return The destination as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The Destination as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<destination");
      if (!"EXACT".equals(queryType))
         sb.append(" queryType='").append(queryType).append("'");
      if (forceQueuing != DEFAULT_forceQueuing)
         sb.append(" forceQueuing='").append(forceQueuing).append("'");

      // Set the real used destination to support PtP routing
      if (destination.isNodeIdExplicitlyGiven())
         sb.append(">").append(destination.getAbsoluteName()).append("</destination>");
      else
         sb.append(">").append(destination.getRelativeName()).append("</destination>");

      return sb.toString();
   }

   /**
    * Only for testing
    *    java org.xmlBlaster.engine.Destination
    */
   public static void main(String args[]) {
      Destination dest = new Destination();
      dest.setDestination(new SessionName(Global.instance(), "Johann"));
      dest.forceQueuing(true);
      System.out.println(dest.toXml());
   }
}
