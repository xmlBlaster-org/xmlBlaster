/*------------------------------------------------------------------------------
Name:      Execute.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Starts a program
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Starts a program and delivers the exit value and some out parameter.
 */
public class Execute {
   private static Logger log = Logger.getLogger(Execute.class.getName());
   private Process process;
   private String[] commandArr;
   private String[] envArr;
   private String errorText;
   private I_ExecuteListener outListener;

   private int BUFFERED_READER_SIZE = 200000; // 200 kBytes, must be big enough to collect stdout/stderr
   /** Thread to collect stdout of a process */
   private OutputThread stdoutThread;
   private StringBuffer stdout = new StringBuffer();
   /** Thread to collect stderr of a process */
   private OutputThread stderrThread;
   private StringBuffer stderr = new StringBuffer();

   private int exitValue;

   /**
    * Construct an instance which can execute a program with the given parameters.
    */
   public Execute(String[] commandArr, String[] envArr) {
      this.commandArr = commandArr;
      this.envArr = envArr;
      if (this.commandArr == null || this.commandArr.length < 1) {
         throw new IllegalArgumentException("Please provide the process to start");
      }
   }

   /**
    * Not thread safe, don't set to null during operation
    */
   public void setExecuteListener(I_ExecuteListener l) {
      this.outListener = l;
   }

   /**
    * Start
    */
   public void run() {
      log.fine("Entering Method Execute.run()");
      errorText = null;
      try {
         Runtime runtime = Runtime.getRuntime();
         process = runtime.exec(commandArr, envArr); // start the process
         // This exec() is not blocking, it returns even when the command still executes


         // get command's output stream and collect it in stdout
         InputStream istr = process.getInputStream();
         BufferedReader ibr = new BufferedReader(new InputStreamReader(istr), BUFFERED_READER_SIZE);
         stdoutThread = new OutputThread(this, ibr, stdout);
         stdoutThread.start();

         // get command's stderr stream and collect it in stderr
         InputStream estr = process.getErrorStream();
         BufferedReader ebr = new BufferedReader(new InputStreamReader(estr), BUFFERED_READER_SIZE);
         stderrThread = new OutputThread(this, ebr, stderr);
         stderrThread.start();

         // Wait for the threads to be up and running (HACK, NOT THREAD SAFE!, needs to be resolved)
         while (true) {
            if (this.stdoutThread.isReady() && this.stderrThread.isReady())
               break;
         }

         // wait for command to terminate
         try {
            process.waitFor();
         }
         catch (InterruptedException e) {
            log.warning("Process [" + commandArr[0] + "] was interrupted");
         }

         this.exitValue = this.process.exitValue();

         log.fine("Process [" + commandArr[0] + "] finished its work, exit=" + this.exitValue + ", good bye");

         this.stdoutThread.stopIt();
         this.stderrThread.stopIt();
         this.process.destroy();

         stdoutThread.join();
         stderrThread.join();

         this.process = null;

         return;
      }
      catch (Exception e) {
         errorText = "Process [" + commandArr[0] + "] could not be started: " + e.toString();
         log.severe(errorText);
         //e.printStackTrace();
      }
      finally {
         if (stdoutThread != null) { stdoutThread.stopIt(); stdoutThread=null; } // necessary?
         if (stderrThread != null) { stderrThread.stopIt(); stderrThread=null; }
      }
   }

   public void stop() {
      try {
         if (this.process != null)
            this.process.destroy();
      }
      catch (Exception e) {
         log.severe("Process kill failed: " + e.toString());
      }

      if (stdoutThread != null) { stdoutThread.stopIt(); stdoutThread=null; } // necessary?
      if (stderrThread != null) { stderrThread.stopIt(); stderrThread=null; }
   }

   public String getStdout() {
      return this.stdout.toString();
   }

   public String getStderr() {
      return this.stderr.toString();
   }

   public int getExitValue() {
      return this.exitValue;
   }

   /**
    * If not null an error occurred in run()
    */
   public String getErrorText() {
      return errorText;
   }

   /**
    * Inner class of Execute
    */
   private class OutputThread extends Thread
   {
      private final String ME = "OutputThread";
      private Execute boss;
      private BufferedReader br;
      private StringBuffer result;
      private boolean isReady = false;
      private boolean stopIt = false;

      /**
       */
      OutputThread(Execute boss, BufferedReader br, StringBuffer result) {
         this.boss = boss;
         this.br = br;
         this.result = result;
      }

      public void run() {
         log.fine("Start reading lines from process ...");
         String str;
         try {
            isReady = true;
            while (true) {
               if (br.ready()) { // some data here?
                  while ((str = br.readLine()) != null) {
                     result.append(str).append("\n");
                     if (outListener != null) {
                        if (this == stdoutThread)
                           outListener.stdout(str);
                        else
                           outListener.stderr(str);
                     }
                     if (!br.ready())
                        break;      // no more data, would possibly block
                  }
               }
               if (stopIt)
                  break;
            }
            br.close();
            log.fine("End reading lines from process.");
         }
         catch (IOException e) {
            log.severe("Could not read process output: " + e.toString());
         }
      }

      void stopIt() {
         this.stopIt = true;
      }

      boolean isReady() {
         return this.isReady;
      }
   } // class OutputThread


   /**
    *  org.xmlBlaster.util.Execute ls
    */
   public static void main( String[] args )
   {
      String[] commandArr = { args[0] };
      String[] envArr = new String[0];
      String[] ar = { "-trace", "true" };
      Global glob = new Global(ar);
      Execute execute = new Execute(commandArr, envArr);
      execute.run();
      System.out.println("Stdout of " + args[0] + " is:\n" + execute.getStdout());
      System.out.println("Stderr of " + args[0] + " is:\n" + execute.getStderr());
      System.out.println("Exit   of " + args[0] + " is: " + execute.getExitValue());
   }
}
