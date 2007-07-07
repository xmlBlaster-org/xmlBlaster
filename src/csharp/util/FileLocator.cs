using System;
using System.Collections;
using System.Text;
using System.IO;
using System.Reflection;

namespace org.xmlBlaster.util
{
   public class FileLocator
   {
      public static byte[] getFileAsBytes(string fileName) {
         BinaryReader sReader = null;
         byte[] contents = null;
         try {
            FileStream fileStream = new FileStream(fileName, FileMode.Open, FileAccess.Read);
            //sReader = new BinaryReader(fileStream);
            contents = ReadFully(fileStream, 0);
         }
         finally {
            if (sReader != null) {
               sReader.Close();
            }
         }
         return contents;
      }

      public static FileContainer[] GetFiles(string directory, string fileName) {
         string[] fileNames = Directory.GetFiles(directory, fileName);
         FileContainer[] fileContainers = new FileContainer[fileNames.Length];
         for (int i = 0; i < fileNames.Length; i++) {
            fileContainers[i] = new FileContainer(directory, fileNames[i]);
         }
         return fileContainers;
      }

      public static type[] GetObjectsFromFiles<type>(string directory, string fileName) {
         FileContainer[] ff = GetFiles(directory, fileName);
         type[] types = new type[ff.Length];
         for (int i=0; i<types.Length; i++) {
            types[i] = ff[i].Deserialize<type>();
         }
         return types;
      }

      public static byte[] ReadFully(Stream stream, int initialLength) {
         // If we've been passed an unhelpful initial length, just
         // use 32K.
         if (initialLength < 1) {
            initialLength = 32768;
         }

         byte[] buffer = new byte[initialLength];
         int read = 0;

         int chunk;
         while ((chunk = stream.Read(buffer, read, buffer.Length - read)) > 0) {
            read += chunk;

            // If we've reached the end of our buffer, check to see if there's
            // any more information
            if (read == buffer.Length) {
               int nextByte = stream.ReadByte();

               // End of stream? If so, we're done
               if (nextByte == -1) {
                  return buffer;
               }

               // Nope. Resize the buffer, put in the byte we've just
               // read, and continue
               byte[] newBuffer = new byte[buffer.Length * 2];
               Array.Copy(buffer, newBuffer, buffer.Length);
               newBuffer[read] = (byte)nextByte;
               buffer = newBuffer;
               read++;
            }
         }
         // Buffer is now too big. Shrink it.
         byte[] ret = new byte[read];
         Array.Copy(buffer, ret, read);
         return ret;
      }


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
         //FileStream fs = new FileStream(fileName, FileMode.OpenOrCreate, FileAccess.Write);
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
   } // FileLocator

   public class FileContainer {
      string directoy;
      string fileName;

      public FileContainer(string d, string f) {
         this.directoy = d;
         this.fileName = f;
      }

       public string Directory
       {
           get { return directoy; }
           set { directoy = value; }
       }

      public string FileName {
         get { return fileName; }
         set { fileName = value; }
      }

      byte[] xmlContent;

      public byte[] GetXmlContent() {
         if (xmlContent == null) {
            string name = Path.Combine(this.directoy, this.fileName);
            if (name != null)
               xmlContent = org.xmlBlaster.util.FileLocator.getFileAsBytes(name);
         }
         if (xmlContent == null) return new byte[0];
         return xmlContent;
      }

      public string GetXmlContentUtf8Str() {
         return org.xmlBlaster.util.Serialization.UTF8ByteArrayToString(GetXmlContent());
      }

      public type Deserialize<type>() {
         return org.xmlBlaster.util.Serialization.Deserialize<type>(GetXmlContent());
      }
   } // FileContainer
}
