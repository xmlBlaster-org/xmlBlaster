/*------------------------------------------------------------------------------
Name:      ThreadLister.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Part of this code is from the book _Java in a Nutshell_ by David Flanagan.
Version:   $Id: ThreadLister.java,v 1.2 2000/05/26 20:50:14 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;
import java.io.*;

/**
 * List all threads in this virtual machine. 
 */
public class ThreadLister
{
   /**
    * Display info about a thread. 
    */
   private static void print_thread_info(PrintStream out, Thread t, String indent)
   {
      if (t == null) return;
      out.println(indent + "Thread: " + t.getName() +
               "  Priority: " + t.getPriority() +
               (t.isDaemon()?" Daemon":"") +
               (t.isAlive()?"":" Not Alive"));
   }


   /**
    * Display info about a thread group and its threads and groups
    */
   private static void list_group(PrintStream out, ThreadGroup g, String indent)
   {
      if (g == null) return;
      int numThreads = g.activeCount();
      int num_groups = g.activeGroupCount();
      Thread[] threads = new Thread[numThreads];
      ThreadGroup[] groups = new ThreadGroup[num_groups];

      g.enumerate(threads, false);
      g.enumerate(groups, false);

      out.println(indent + "Thread Group: " + g.getName() +
              "  Max Priority: " + g.getMaxPriority() +
              (g.isDaemon()?" Daemon":""));

      for(int i = 0; i < numThreads; i++)
          print_thread_info(out, threads[i], indent + "    ");
      for(int i = 0; i < num_groups; i++)
          list_group(out, groups[i], indent + "    ");
   }


   /**
    * List all threads below the root thread group recursively. 
    */
   public static void listAllThreads(PrintStream out)
   {
       // And list it, recursively
       list_group(out, getRootThreadGroup(), "");
   }


   /**
     * Count all active threads in this virtual machine. 
     */
   public static int countThreads()
   {
      return getRootThreadGroup().activeCount();
      // return countThreads(0, getRootThreadGroup());
   }


   /**
     * Find the root thread group
     * @return The top level thread group
     */
   public static ThreadGroup getRootThreadGroup()
   {
       ThreadGroup current_thread_group;
       ThreadGroup root_thread_group;
       ThreadGroup parent;

       // Get the current thread group
       current_thread_group = Thread.currentThread().getThreadGroup();

       // Now go find the root thread group
       root_thread_group = current_thread_group;
       parent = root_thread_group.getParent();
       while(parent != null) {
           root_thread_group = parent;
           parent = parent.getParent();
       }

       return root_thread_group;
   }

   /**
    * java testsuite.org.xmlBlaster.ThreadLister
    */
   public static void main(String[] args)
   {
       ThreadLister.listAllThreads(System.out);
       System.out.println("There are " + countThreads() + " threads in use");
   }
}
