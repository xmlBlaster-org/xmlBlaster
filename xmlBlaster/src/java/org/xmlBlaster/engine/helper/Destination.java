/*------------------------------------------------------------------------------
Name:      Destination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
Version:   $Id: Destination.java,v 1.6 2002/09/13 23:18:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;


/**
 * Holding destination address attributes.
 * <p />
 * This class corresponds to the QOS destination tag
 */
public class Destination
{
   private String ME = "Destination";

   /** The destination address (==login name) or the XPath query string */
   private String destination = null;
   /** EXACT is default */
   private String queryType = "EXACT";
   /** No queuing is default */
   private boolean DEFAULT_forceQueuing = false;
   private boolean forceQueuing = DEFAULT_forceQueuing;


   /**
    * Constructs the specialized quality of service destination object.
    */
   public Destination()
   {
   }


   /**
    * Constructs the specialized quality of service destination object.
    * @param address The destination address (EXACT),
    *                this is typically the login name of another client
    */
   public Destination(String address)
   {
      setDestination(address);
   }


   /**
    * Constructs the specialized quality of service destination object.
    * @param address The destination address or query string
    * @param queryType "EXACT" or "XPATH"
    */
   public Destination(String address, String queryType)
   {
      setQueryType(queryType);
      setDestination(address);
   }


   /**
    * @return true/false
    */
   public boolean isXPathQuery()
   {
      return queryType.equals("XPATH");
   }


   /**
    * @return true/false
    */
   public boolean isExactAddress()
   {
      return queryType.equals("EXACT");
   }

   /**
    * Check if the address is a sessionId
    */
   public boolean isSessionId()
   {
      if (destination == null)
         return false;
      if (!isExactAddress())
         return false;
      return destination.startsWith(Constants.SESSIONID_PRAEFIX);
   }


   /**
    * @return true/false
    */
   public boolean forceQueuing()
   {
      return forceQueuing;
   }


   /**
    * Set queuing of messages.
    * <p />
    * true: If client is not logged in, messages will be queued until he comes. <br />
    * false: Default is that on PtP messages when the destination address is
    *        not online, an Exception is thrown
    */
   public void forceQueuing(boolean forceQueuing)
   {
      this.forceQueuing = forceQueuing;
   }


   /**
    * Set the destination address or the destination query string.
    * @param destination The destination address or the query string
    */
   public final void setDestination(String destination)
   {
      this.destination = destination;
   }


   /**
    * @param The destination address or XPath query string
    */
   public final String getDestination()
   {
      return destination;
   }


   /**
    * @param queryType The query type, one of "EXACT" | "XPATH"
    */
   public final void setQueryType(String queryType)
   {
      if (queryType.equalsIgnoreCase("EXACT"))
         this.queryType = queryType;
      else if (queryType.equalsIgnoreCase("XPATH"))
         org.xmlBlaster.util.Global.instance().getLog(null).error(ME, "Sorry, destination queryType='" + queryType + "' is not supported");
      else
         org.xmlBlaster.util.Global.instance().getLog(null).error(ME, "Sorry, destination queryType='" + queryType + "' is not supported");
   }


   /**
    * Get the XML ASCII representation of this object.
    * <br>
    * @return The destination as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The Destination as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<destination");
      if (!"EXACT".equals(queryType))
         sb.append(" queryType='" + queryType + "'");
      if (forceQueuing != DEFAULT_forceQueuing)
         sb.append(" forceQueuing='" + forceQueuing + "'");
      sb.append(">");
      sb.append(destination);
      sb.append("</destination>");

      return sb.toString();
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.engine.Destination
    */
   public static void main(String args[])
   {
      Destination dest = new Destination();
      dest.setDestination("Johann");
      dest.forceQueuing(true);
      System.out.println(dest.toXml());
   }
}
