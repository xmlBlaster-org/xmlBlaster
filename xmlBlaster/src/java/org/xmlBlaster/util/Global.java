/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
Version:   $Id: Global.java,v 1.2 2002/03/13 16:41:34 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.xmlBlaster.util.Log;

import java.util.Properties;

import java.applet.Applet;

/**
 * Global variables to avoid singleton. 
 */
public class Global
{
   private final static String ME = "Global";

   private String[] args;
   private XmlBlasterProperty property = new XmlBlasterProperty();
   private Log log = new Log();

   public Global()
   {
      this.args = new String[0];
   }

   public Global(String[] args)
   {
      init(args);
   }

   public XmlBlasterProperty getProperty()
   {
      return property;
   }

   public Log getLog()
   {
      return log;
   }

   /**
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      this.args = args;
      try {
         boolean showUsage = XmlBlasterProperty.init(args);  // initialize
         if (showUsage) return 1;
         return 0;
      } catch (JUtilsException e) {
         Log.error(ME, e.toString());
         return -1;
      }
   }

 
   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.Global -Persistence.Dummy true -info true
    */
   public static void main(String args[])
   {
      String ME = "Global";
      Global glob = new Global(args);
      Log.info(ME, "Persistence.Dummy=" + glob.getProperty().get("Persistence.Dummy", false));
   }
}
