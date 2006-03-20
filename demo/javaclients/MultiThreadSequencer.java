/*------------------------------------------------------------------------------
 Name:      MultiThreadPublisher.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package javaclients;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.xmlBlaster.util.Global;

/**
 * MultiThreadSequencer
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MultiThreadSequencer extends Thread {

   public static int COUNT = 0;
   private boolean isPublish;
   private long initialSleep;
   private Global global;
   
   public MultiThreadSequencer(boolean isPublish, long initialSleep, String[] args) {
      super();
      this.global = new Global();
      this.global.init(args);
      this.initialSleep = initialSleep;
      this.isPublish = isPublish;
   }

   public void process() {
      try {
         Thread.sleep(this.initialSleep);
         start(); // here a new thread is started
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   public void run() {
      if (this.isPublish)
         new HelloWorldPublish(this.global);
      else 
         new HelloWorldSubscribe(this.global);
      
      synchronized(MultiThreadSequencer.class) {
         COUNT++;
      }
   }
   
   public static String[] parseInitialLine(String line) {
      String[] ret = new String[3];
      int pos = line.indexOf(' ');
      if (pos > 0) {
         ret[0] = line.substring(0, pos);
         line = line.substring(pos+1);
         pos = line.indexOf(' ');
         if (pos > 0) {
            ret[1] = line.substring(0, pos);
            ret[2] = line.substring(pos+1);
         }
      }
      return ret;
   }
   
   public static String[] getLineAsArgs(String line) {
      StringTokenizer tokenizer = new StringTokenizer(line, " ");
      int nmax = tokenizer.countTokens();
      String[] ret = new String[nmax];
      int i = 0;
      while (tokenizer.hasMoreTokens()) {
         ret[i] = tokenizer.nextToken();
         i++;
      }
      return ret;
   }
   
   public static MultiThreadSequencer[] createPublishers(String filename) throws IOException {
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      String line = null;
      ArrayList list = new ArrayList();
      while ( (line = reader.readLine()) != null) {
         line = line.trim();
         if (line.length() == 0)
            continue;
         String[] tmpLines = parseInitialLine(line);
         boolean isPublish = true;
         if ("SUB".equalsIgnoreCase(tmpLines[0]))
            isPublish = false;
         long initialSleep = Long.parseLong(tmpLines[1]);
         MultiThreadSequencer publisher = new MultiThreadSequencer(isPublish, initialSleep, getLineAsArgs(tmpLines[2]));
         list.add(publisher);
      }
      return (MultiThreadSequencer[])list.toArray(new MultiThreadSequencer[list.size()]);
   }

   
   public static void main(String[] args) {
      if (args.length != 1) {
         System.err.println("usage: java javaclients.MultiThreadSequencer filename");
         System.err.println("where the format of the file is 'PUB/SUB initialDelayInMillis args[]");
         String[] tmpArgs = new String[] { "-help" };
         System.err.println("where the parameters passed as args are:");
         HelloWorldPublish.main(tmpArgs);
         System.err.println("or where the parameters passed as args are:");
         HelloWorldSubscribe.main(tmpArgs);
         System.exit(-1);
      }
      try {
         MultiThreadSequencer[] pubs = MultiThreadSequencer.createPublishers(args[0]);
         for (int i=0; i < pubs.length; i++) {
            pubs[i].process();
         }
         while (MultiThreadSequencer.COUNT < pubs.length) {
            Thread.sleep(500L);
         }
         System.err.println("Publishing is finished: CTRL-C to terminate");
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
      
      
   }
   
   
}
