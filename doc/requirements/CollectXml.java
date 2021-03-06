/*------------------------------------------------------------------------------
Name:      CollectXml.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Collects all xml requirement files into the all.xml master file
Version:   $Id$
------------------------------------------------------------------------------*/
package doc.requirements;

import java.io.*;


/**
 * Collects all xml requirement files into the all.xml master file.
 * <p />
 * This master file is used by html.xsl to generate HTML output
 * Example:
 * <pre>
 *  &lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *  &lt;!-- all.xml, generated by CollectXml.java -->
 *  &lt;files>
 *     &lt;url>engine.get.no.xml&lt;/url>
 *     &lt;url>util.recorder.xml&lt;/url>
 *     &lt;url>util.property.env.xml&lt;/url>
 *     &lt;url>engine.qos.destination.offline.xml&lt;/url>
 *  &lt;/files>
 * </pre>
 * Invoke example:<br />
 * <pre>
 *    java doc.requirements.CollectXml
 * </pre>
 */
public class CollectXml
{
   /**
    */
   public CollectXml(String[] args)
   {
      try {
         File dir = new File(".");
         if (!dir.canWrite()) {
            System.err.println("Sorry, no write permissions for directory");
            System.exit(1);
         }

         File[] files = dir.listFiles(new MyFilenameFilter());

         PrintWriter fout = new PrintWriter(new FileOutputStream("all.xml"));
         fout.println("<?xml version='1.0' encoding='ISO-8859-1' ?>");
         fout.println("<!-- all.xml, generated by CollectXml.java -->");
         fout.println("<files>");
         String currentPath = System.getProperty("user.dir");
         fout.println("   <dir>" + currentPath + "</dir>");
         for (int ii=0; ii<files.length; ii++) {
            fout.println("   <url>" + files[ii].getName() + "</url>");
         }
         fout.println("</files>");
         fout.close();
         System.err.println("Created all.xml with " + files.length + " entries");
      }
      catch (Exception e) {
         System.err.println("Can't create all.xml: " + e.toString());
         System.exit(1);
      }

      System.exit(0);
   }

   private class MyFilenameFilter implements FilenameFilter
   {
      public MyFilenameFilter() {}
      public boolean accept(File dir, String name)
      {
         if (name.endsWith(".xml") && !name.equals("all.xml"))
            return true;
         return false;
      }

   }

   /**
    * Invoke:   java doc.requirements.CollectXml
    */
   public static void main(String args[])
   {
      new CollectXml(args);
   }
}

