/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.xmlBlaster.util.classloader;
//package org.jboss.mx.loading;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//import org.jboss.logging.Logger;
//import org.jboss.mx.loading.LoadMgr3.PkgClassLoader;

/** Utility methods for class loader to package names, etc.
 *
 * @author Scott.Stark@jboss.org
 * @version $Revision: 1.19.4.2 $
 */
public class ClassLoaderUtils
{
   //private static Logger log = Logger.getLogger(ClassLoaderUtils.class);

   /** Format a string buffer containing the Class, Interfaces, CodeSource,
    and ClassLoader information for the given object clazz.

    @param clazz the Class
    @param results - the buffer to write the info to
    */
   public static void displayClassInfo(Class clazz, StringBuffer results)
   {
      // Print out some codebase info for the ProbeHome
      ClassLoader cl = clazz.getClassLoader();
      results.append("\n"+clazz.getName()+"("+Integer.toHexString(clazz.hashCode())+").ClassLoader="+cl);
      ClassLoader parent = cl;
      CodeSource clazzCS = clazz.getProtectionDomain().getCodeSource();
      if( clazzCS != null )
         results.append("\n++++CodeSource: "+clazzCS);
      else
         results.append("\n++++Null CodeSource");

      results.append("\nImplemented Interfaces:");
      Class[] ifaces = clazz.getInterfaces();
      for(int i = 0; i < ifaces.length; i ++)
      {
         Class iface = ifaces[i];
         results.append("\n++"+iface+"("+Integer.toHexString(iface.hashCode())+")");
         ClassLoader loader = ifaces[i].getClassLoader();
         results.append("\n++++ClassLoader: "+loader);
         ProtectionDomain pd = ifaces[i].getProtectionDomain();
         CodeSource cs = pd.getCodeSource();
         if( cs != null )
            results.append("\n++++CodeSource: "+cs);
         else
            results.append("\n++++Null CodeSource");
      }
      while( parent != null )
      {
         results.append("\n.."+parent);
         URL[] urls = getClassLoaderURLs(parent);
         int length = urls != null ? urls.length : 0;
         for(int u = 0; u < length; u ++)
         {
            results.append("\n...."+urls[u]);
         }
         if( parent != null )
            parent = parent.getParent();
      }
   }

   /** Use reflection to access a URL[] getURLs or URL[] getClasspath method so
    that non-URLClassLoader class loaders, or class loaders that override
    getURLs to return null or empty, can provide the true classpath info.
    */
   public static URL[] getClassLoaderURLs(ClassLoader cl)
   {
      URL[] urls = {};
      try
      {
         Class returnType = urls.getClass();
         Class[] parameterTypes = {};
         Class clClass = cl.getClass();
         Method getURLs = clClass.getMethod("getURLs", parameterTypes);
         if( returnType.isAssignableFrom(getURLs.getReturnType()) )
         {
            Object[] args = {};
            urls = (URL[]) getURLs.invoke(cl, args);
         }
         if( urls == null || urls.length == 0 )
         {
            Method getCp = clClass.getMethod("getClasspath", parameterTypes);
            if( returnType.isAssignableFrom(getCp.getReturnType()) )
            {
               Object[] args = {};
               urls = (URL[]) getCp.invoke(cl, args);               
            }
         }
      }
      catch(Exception ignore)
      {
      }
      return urls;
   }


   /** Get all of the URLClassLoaders from cl on up the hierarchy
    *
    * @param cl the class loader to start from
    * @return The possibly empty array of URLClassLoaders from cl through
    *    its parent class loaders
    */
   public static URLClassLoader[] getClassLoaderStack(ClassLoader cl)
   {
      ArrayList stack = new ArrayList();
      while( cl != null )
      {
         if( cl instanceof URLClassLoader )
         {
            stack.add(cl);
         }
         cl = cl.getParent();
      }
      URLClassLoader[] ucls = new URLClassLoader[stack.size()];
      stack.toArray(ucls);
      return ucls;
   }

   /** Translates a dot class name (java.lang.String) into a path form
    * suitable for a jar entry (java/lang/String.class)
    *
    * @param className java.lang.String
    * @return java/lang/String.class
    */
   public static String getJarClassName(String className)
   {
      String jarClassName = className.replace('.', '/');
      return jarClassName + ".class";
   }

   /** Parse a class name into its resource form. This has to handle
      array classes whose name is prefixed with [L.
    */
   public static String getResourceName(String className)
   {
      int startIndex = 0;
      // Strip any leading "[+L" found in array class names
      if( className.length() > 0 && className.charAt(0) == '[' )
      {
         // Move beyond the [...[L prefix
         startIndex = className.indexOf('L') + 1;
      }
           // Now extract the package name
      String resName = "";
      int endIndex = className.lastIndexOf('.');
      if( endIndex > 0 )
         resName = className.substring(startIndex, endIndex);
      return resName.replace('.', '/');
   }

   /**
   */
   static class FileIterator
   {
      LinkedList subDirectories = new LinkedList();
      FileFilter filter;
      File[] currentListing;
      int index = 0;

      FileIterator(File start)
      {
         String name = start.getName();
         // Don't recurse into wars
         boolean isWar = name.endsWith(".war");
         if( isWar )
            currentListing = new File[0];
         else
            currentListing = start.listFiles();
      }
      FileIterator(File start, FileFilter filter)
      {
         String name = start.getName();
         // Don't recurse into wars
         boolean isWar = name.endsWith(".war");
         if( isWar )
            currentListing = new File[0];
         else
            currentListing = start.listFiles(filter);
         this.filter = filter;
      }

      File getNextEntry()
      {
         File next = null;
         if( index >= currentListing.length && subDirectories.size() > 0 )
         {
            do
            {
               File nextDir = (File) subDirectories.removeFirst();
               currentListing = nextDir.listFiles(filter);
            } while( currentListing.length == 0 && subDirectories.size() > 0 );
            index = 0;
         }
         if( index < currentListing.length )
         {
            next = currentListing[index ++];
            if( next.isDirectory() )
               subDirectories.addLast(next);
         }
         return next;
      }
   }

   /**
    */
   static class ClassPathEntry
   {
      String name;
      ZipEntry zipEntry;
      File fileEntry;

      ClassPathEntry(ZipEntry zipEntry)
      {
         this.zipEntry = zipEntry;
         this.name = zipEntry.getName();
      }
      ClassPathEntry(File fileEntry, int rootLength)
      {
         this.fileEntry = fileEntry;
         this.name = fileEntry.getPath().substring(rootLength);
      }

      String getName()
      {
         return name;
      }
      /** Convert the entry path to a package name
       */
      String toPackageName()
      {
         String pkgName = name;
         char separatorChar = zipEntry != null ? '/' : File.separatorChar;
         int index = name.lastIndexOf(separatorChar);
         if( index > 0 )
         {
            pkgName = name.substring(0, index);
            pkgName = pkgName.replace(separatorChar, '.');
         }
         else
         {
            // This must be an entry in the default package (e.g., X.class)
            pkgName = "";
         }
         return pkgName;
      }

      boolean isDirectory()
      {
         boolean isDirectory = false;
         if( zipEntry != null )
            isDirectory = zipEntry.isDirectory();
         else
            isDirectory = fileEntry.isDirectory();
         return isDirectory;
      }
   }

   /** An iterator for jar entries or directory structures.
   */
   static class ClassPathIterator
   {
      ZipInputStream zis;
      FileIterator fileIter;
      File file;
      int rootLength;

      ClassPathIterator(URL url) throws IOException
      {
         String protocol = url != null ? url.getProtocol() : null;
         if( protocol == null )
         {
         }
         else if( protocol.equals("file") )
         {
            File tmp = new File(url.getFile());
            if( tmp.isDirectory() )
            {
               rootLength = tmp.getPath().length() + 1;
               fileIter = new FileIterator(tmp);
            }
            else
            {
               // Assume this is a jar archive
               InputStream is = new FileInputStream(tmp);
               zis = new ZipInputStream(is);
            }
         }
         else
         {
            // Assume this points to a jar
            InputStream is = url.openStream();
            zis = new ZipInputStream(is);
         }
      }

      ClassPathEntry getNextEntry() throws IOException
      {
         ClassPathEntry entry = null;
         if( zis != null )
         {
            ZipEntry zentry = zis.getNextEntry();
            if( zentry != null )
               entry = new ClassPathEntry(zentry);
         }
         else if( fileIter != null )
         {
            File fentry = fileIter.getNextEntry();
            if( fentry != null )
               entry = new ClassPathEntry(fentry, rootLength);
            file = fentry;
         }

         return entry;
      }

      InputStream getInputStream() throws IOException
      {
         InputStream is = zis;
         if( zis == null )
         {
            is = new FileInputStream(file);
         }
         return is;
      }

      void close() throws IOException
      {
         if( zis != null )
            zis.close();
      }

   }
   
}
