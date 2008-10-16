/*------------------------------------------------------------------------------
Name:      FileWatcherFeeder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.filewatcher;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class is used to write files to the location where one or more file watchers are getting their data from.
 * It additionally to the common properties the following properties: 
 * - filewatcher.test.sourceFile = the file from which to read the input data
 * - filewatcher.test.host.* = prio (the higher the oftener)
 * - filewatcher.test.delay = the time to sleep
 * - filewatcher.test.counter = the variable to use in the file for an incrementing counter (to be unique) (in the properties it indicates where to start)
 * - filewatcher.test.hostname = the current hostname 
 * - filewatcher.test.repository = the base directory where to write (a subdirectory for each host is created). 
 *
 */
public class FileWatcherFeeder extends GlobalInfo {

   class Replacer implements I_ReplaceVariable {
      
      public Replacer() {
      }
      
      public String get(String key) {
         if (key == null)
            return null;
         String tmp = global.getProperty().get(key, (String)null);
         if (tmp == null)
            return null;
         return tmp.trim();
      }
   }
   
   private ReplaceVariable replaceVariable;
   private Replacer replacer;
   private String content;
   private Global global;
   private String[] hosts;
   private long sleepDelay;
   private long counter;
   private String repository;
   
   public FileWatcherFeeder(String[] args) throws Exception {
      super((String[])null);
      global = new Global(args, true, false, false);
      this.replaceVariable = new ReplaceVariable();
      this.replacer = new Replacer();
      String filename = global.getProperty().get("filewatcher.test.sourceFile", (String)null);
      if (filename != null) {
         content = readFile(filename);
      }
      init(global, null);
      hosts = getHosts();
      sleepDelay = global.getProperty().get("filewatcher.test.delay", 0L);
      counter = global.getProperty().get("filewatcher.test.counter", 0L);
      repository = global.getProperty().get("filewatcher.test.repository", "repository");
      if (repository.charAt(repository.length()-1) != '/')
         repository += "/";
   }
   
   
   protected void doInit(Global global, PluginInfo pluginInfo)
         throws XmlBlasterException {
   }


   private String[] getHosts() {
      String prefix = "filewatcher.test.host.";
      Map hosts = InfoHelper.getPropertiesStartingWith(prefix, this, null);
      String[] keys = (String[])hosts.keySet().toArray(new String[hosts.size()]);
      TreeMap newHosts = new TreeMap();
      
      // we want them in a random order
      Random random = new Random();
      for (int i=0; i < keys.length; i++) {
         String tmp = (String)hosts.get(keys[i]);
         int nmax = Integer.parseInt(tmp);
         if (nmax < 1)
            nmax = 1;
         else if (nmax > 10)
            nmax = 10;
         for (int j=0; j < nmax; j++) {
            Double newKey = new Double(random.nextDouble());
            newHosts.put(newKey, keys[i]);
         }
      }
      return (String[])newHosts.values().toArray(new String[newHosts.size()]);
   }
   
   public final String replace(String txt) {
      return this.replaceVariable.replace(txt, this.replacer);
   }


   
   private String readFile(String filename) throws Exception {
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      String line = "";
      StringBuffer buf = new StringBuffer(1024);
      while ( (line=reader.readLine()) != null) {
         buf.append(line).append("\n");
      }
      return buf.toString();
   }
   
   public void process() throws Exception {
      if (hosts == null)
         return;
      Random random = new Random();
      for (int i=0; i < hosts.length; i++) {
         if (random.nextBoolean())
            continue;
         global.getProperty().set("filewatcher.test.counter", "" + (++counter));
         global.getProperty().set("filewatcher.test.hostname", hosts[i]);
         String tmp = replace(content);
         if (tmp != null) {
            FileOutputStream fos = new FileOutputStream(repository + hosts[i] + "/" + System.currentTimeMillis() + ".dat");
            fos.write(tmp.getBytes("UTF-8"));
            fos.close();
            if (sleepDelay > 0L)
               Thread.sleep(sleepDelay);
         }
      }
   }
   
   public static void main(String[] args) {
    
      try {
         FileWatcherFeeder feeder = new FileWatcherFeeder(args);
         while (true)
            feeder.process();
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   
}
