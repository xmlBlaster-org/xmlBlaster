/*------------------------------------------------------------------------------
Name:      LoaderInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;


/**
 * Helper struct holding infos about a plugin class and its pathes
 */
public class LoaderInfo {
   /** "org.xmlBlaster.protocol.corba.CorbaDriver" */
   public String pluginName;

   /**
    * The path only:
    * if from "/home/xmlblast/xmlBlaster/lib/xmlBlaster.jar" -> "/home/xmlblast/xmlBlaster/lib/"
    * if from class file -> "/home/xmlblast/xmlBlaster/classes/"
    */
   public String rootPath;

   /** "/home/xmlblast/xmlBlaster/lib/xmlBlaster.jar" or null if not from jar file loaded */
   public String jarPath;

   /** "xmlBlaster.jar" or null if not from jar file */
   public String jarName;

   /** "org/xmlBlaster/protocol/corba/CorbaDriver" */
   public String pluginSlashed;

   /**
    * Path where we search for jar files for this plugin
    *  "/home/xmlblast/xmlBlaster/lib/org/xmlBlaster/protocol/corba/CorbaDriver"
    */
   public String basePath;

   public LoaderInfo(String pluginName, String rootPath, String jarPath,
                     String jarName, String pluginSlashed) {
      this.pluginName = pluginName;
      this.rootPath = rootPath;
      this.jarPath = jarPath;
      this.jarName = jarName;
      this.pluginSlashed = pluginSlashed;
      this.basePath = this.rootPath + this.pluginSlashed;
   }

   public String toString() {
      return "pluginName=" + pluginName + " rootPath=" + rootPath + 
         " jarPath=" + jarPath + " jarName=" + jarName +
         " pluginSlashed=" + pluginSlashed + 
         " basePath=" + basePath;
   }
}
