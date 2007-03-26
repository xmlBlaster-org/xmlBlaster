using System;
using System.Collections;
using System.Text;
using System.IO;
using System.Reflection;

namespace org.xmlBlaster.util
{
   class FileLocator
   {
      public static string getFileAsString(string fileName)
      {
         StreamReader sReader = null;
         string contents = null;
         try
         {
            FileStream fileStream = new FileStream(fileName, FileMode.Open, FileAccess.Read);
            sReader = new StreamReader(fileStream);
            contents = sReader.ReadToEnd();
         }
         finally
         {
            if (sReader != null)
            {
               sReader.Close();
            }
         }
         return contents;
      }

      /// <summary>
      /// Write a file
      /// </summary>
      /// <param name="fileName">"c:\\tmp\\WriteFileStuff.txt"</param>
      /// <param name="text">The data to write</param>
      public static void writeAsciiFile(string fileName, string text)
      {
         FileStream fs = new FileStream(fileName, FileMode.OpenOrCreate, FileAccess.Write);
         StreamWriter sw = new StreamWriter(fs);
         try
         {
            sw.Write(text);
         }
         finally
         {
            if (sw != null) { sw.Close(); }
         }
      }

      public static bool moveFile(string origName, string destName, bool overwrite)
      {
         try
         {
            if (File.Exists(destName))
            {
               if (!overwrite) return false;
               File.Delete(destName);
            }
            if (File.Exists(origName))
            {
               File.Move(origName, destName);
               return true;
            }
         }
         catch (Exception ex)
         {
            Console.WriteLine("Move '" + origName + "' to '" + destName + "' failed: " + ex.ToString());
         }
         return false;
      }

      /*
      public static byte[] readBinaryFile(string fileName)
      {
         if (!File.Exists(fileName))
            return new byte[0];
         FileStream fs = File.OpenRead(fileName);
         if (fs == null) return new byte[0];
         BinaryReader br = null;
         try {
            br = new BinaryReader(fs);
            if (br == null) return new byte[0];
            // TODO!! loop until complete file is read
            byte[] bytes = br.ReadBytes(10);
            return bytes;
         }
         finally {
            if (br != null) br.Close();
            fs.Close();
         }
      }
      */

      /// <summary>
      /// Write a file
      /// </summary>
      /// <param name="fileName">"c:\\tmp\\WriteFileStuff.txt"</param>
      /// <param name="text">The data to write</param>
      public static bool writeBinaryFile(string fileName, byte[] text)
      {
         FileStream fs = File.Create(fileName);
         if (fs == null) return false;
         try
         {
            BinaryWriter bw = new BinaryWriter(fs);
            bw.Write(text);
            bw.Close();
            return true;
         }
         finally
         {
            fs.Close();
         }
      }
   }
}
