/*------------------------------------------------------------------------------
Name:      ThreadUtility.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Reads and compares two files containing stack traces. Output are only such stack traces which did not change (which are
 * the same in both)
 * 
 * ThreadUtility
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class ThreadUtility {

   // step one: read all threads
   private String name;
   private boolean daemon;
   private int prio;
   private String threadId;
   private String nId;
   private String lwpId;
   private String tail;
   private String head;
   private String[] stack;

   public ThreadUtility() {
      
   }

   private final static String getNextToken(StringTokenizer tokenizer) {
      String txt = tokenizer.nextToken();
      if (txt.indexOf("\"") == 0 && txt.charAt(txt.length()-1) != '"') {
         StringBuffer buf = new StringBuffer();
         buf.append(txt).append(" ");
         String tmp = null;
         while (tokenizer.hasMoreTokens()) {
            tmp = tokenizer.nextToken();
            buf.append(tmp);
            if (tmp.charAt(tmp.length()-1) == '"')
               break;
            else
               buf.append(" ");
         }
         txt = buf.toString();
      }
      return afterEquality(txt);
   }
   
   private final static String getTail(StringTokenizer tokenizer) {
      StringBuffer buf = new StringBuffer();
      boolean isFirst = true;
      while (tokenizer.hasMoreTokens()) {
         if (!isFirst)
            buf.append(" ");
         else
            isFirst = false;
         buf.append(tokenizer.nextToken());
      }
      return buf.toString();
   }
   
   private final static String afterEquality(String txt) {
      int pos = txt.indexOf("=");
      if (pos < 0)
         return txt;
      return txt.substring(pos+1);
   }

   public final static boolean isHead(String line) {
      if (line == null || line.length() < 1)
         return false;
      return line.indexOf("tid=") > -1;
   }
   
   /**
    * Returns key/values where the key is the threadId and the value is the ThreadUtility object. Never returns null.
    * @param reader
    * @return
    */
   public static Map getThreads(Reader reader) throws IOException {
      Map map = new TreeMap();
      BufferedReader br = new BufferedReader(reader);
      String line = null;
      ThreadUtility thread = null;
      List stack = new ArrayList();
      while ( (line=br.readLine()) != null) {
         if (isHead(line)) {
            if (thread != null) {
               thread.setStack((String[])stack.toArray(new String[stack.size()]));
               map.put(thread.getThreadId(), thread);
            }
            thread = new ThreadUtility();
            thread.setHead(line);
            stack.clear();
         }
         else {
            if (line.length() > 0)
               stack.add(line);
         }
      }
      return map;
   }
   
   public static String dumpUnchangedThreads(String file1, String file2) throws IOException {
      StringBuffer ret = new StringBuffer(1024);
      
      FileReader f1 = new FileReader(file1);
      FileReader f2 = new FileReader(file2);
      
      Map threadsMap1 = getThreads(f1);
      Map threadsMap2 = getThreads(f2);
      
      String[] keys = (String[])threadsMap1.keySet().toArray(new String[threadsMap1.size()]);
      for (int i=0; i < keys.length; i++) {
         String key = keys[i];
         ThreadUtility t1 = (ThreadUtility)threadsMap1.get(key);
         ThreadUtility t2 = (ThreadUtility)threadsMap2.get(key);
         if (t2 != null) {
            ret.append("Thread '").append(key).append("' (").append(t1.getName()).append(") exists on both stack traces same='");
            if (t1.isUnchanged(t2)) {
               ret.append("true'\n");
               ret.append(t1);
            }
            else
               ret.append("false'\n");
         }
      }
      return ret.toString();
   }
   
   private boolean isUnchanged(ThreadUtility other) {
      if (other == null)
         return false;
      if (!this.threadId.equals(other.getThreadId()))
         return false;
      if (this.stack.length != other.getStack().length)
         return false;
      if (!this.head.equals(other.getHead()))
         return false;
      String[] otherStack = other.getStack();
      for (int i=0; i < otherStack.length; i++) {
         if (!this.stack[i].equals(otherStack[i]))
            return false;
      }
      return true;
   }
   
   // "pool-1-thread-372" prio=10 tid=000638e0 nid=33621 lwp_id=1653384 in Object.wait() [2f6f1000..2f6f1578]
   // "XmlBlaster.SOCKET" daemon prio=10 tid=00ea17b8 nid=33247 lwp_id=1646483 runnable [2ef62000..2ef62878]
   public void setHead(String head) {
      this.head = head;
      StringTokenizer tokenizer = new StringTokenizer(head, " ");
      this.name = getNextToken(tokenizer);
      String txt = getNextToken(tokenizer);
      if ("daemon".equals(txt)) {
         this.daemon = true;
         this.prio = Integer.parseInt(getNextToken(tokenizer));
      }
      else
         this.prio = Integer.parseInt(txt);
         
      this.threadId = getNextToken(tokenizer);
      this.nId = getNextToken(tokenizer);
      this.lwpId = getNextToken(tokenizer);
      this.tail = getTail(tokenizer);
   }

   
   public String getHead() {
      return head;
   }

   public boolean isDaemon() {
      return daemon;
   }

   public String getLwpId() {
      return lwpId;
   }

   public String getName() {
      return name;
   }

   public String getNId() {
      return nId;
   }

   public int getPrio() {
      return prio;
   }

   public String getTail() {
      return tail;
   }

   public String[] getStack() {
      return stack;
   }

   public void setStack(String[] stack) {
      this.stack = stack;
   }

   public String getThreadId() {
      return threadId;
   }
   
   public String toString() {
      StringBuffer ret = new StringBuffer(512);
      ret.append(this.head).append("\n");
      for (int i=0; i < this.stack.length; i++)
         ret.append(this.stack[i]).append("\n");
      return ret.toString();
   }

   public static void main(String[] args) {
      if (args.length < 2) {
         System.err.println("usage: java " + ThreadUtility.class.getName() + " stackFilename1 stackFilename2");
         System.exit(-1);
      }
      String file1 = args[0];
      String file2 = args[1];
      try {
         System.out.println(ThreadUtility.dumpUnchangedThreads(file1, file2));
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   
}
