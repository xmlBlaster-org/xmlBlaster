using System;
using System.Collections;
using System.Text;
using System.IO;
using System.Reflection;
using System.Globalization;
using System.Runtime.InteropServices;
using org.xmlBlaster.client; // for XmlBlasterException TODO: move to util

namespace org.xmlBlaster.util {
   ///
   /// @author mr@marcelruff.info 2007 http://www.xmlBlaster.org
   ///
   public class Stuff {
      /// <summary>
      /// Converts a DateTime to an ISO 8601 datetime string with timezone indicator
      /// See http://en.wikipedia.org/wiki/ISO_8601#Time_zones for info
      /// The result is of type "yyyy'-'MM'-'dd HH':'mm':'ss'Z'"
      /// </summary>
      /// <param name="DateTimeToConvert">The DateTime to be converted</param>
      /// <returns>2007-01-01 14:26:20Z</returns>
      public static string ToIsoDateTimeString(DateTime dateTimeToConvert) {
         DateTime utc = dateTimeToConvert.ToUniversalTime();
         // "s" creates "2007-01-01T14:26:20": Note the missing 'Z' which means it is local time
         // "u" creates "2007-01-01 14:26:20Z": Note the valid ' ' instead of 'T' and correct UTC ending
         string isoUtc = utc.ToString("u");
         //if (!isoUtc.EndsWith("Z"))
         //   isoUtc += "Z";
         //isoUtc.Replace(' ', 'T');
         return isoUtc;
      }

      /// <summary>
      /// </summary>
      /// <param name="dateTimeIso8601">2007-01-01T14:26:20Z</param>
      /// <returns></returns>
      public static DateTime FromIsoDateTimeString(string dateTimeIso8601) {
         //DateTime newDate = DateTime.TryParse(dateTimeIso8601, "u", null);
         return DateTime.Parse(dateTimeIso8601, CultureInfo.InvariantCulture);
      }

      public readonly static string AMP = "&amp;";
      public readonly static string LT = "&lt;";
      public readonly static string GT = "&gt;";
      public readonly static string QUOT = "&quot;";
      public readonly static string APOS = "&apos;";

      public readonly static string SLASH_R = "&#x0D;";
      public readonly static string NULL = "&#x0;";

      /**
       * Escape predefined xml entities (&, <, >, ', ").
       * Additionally the '\0' is escaped.
       * @param text
       * @return The escaped text is appended to the given StringBuffer.
       */
      public static string EscapeXml(String text) {
         string ret = "";
         if (text == null) return ret;
         int length = text.Length;
         for (int i = 0; i < length; i++) {
            char c = text[i];
            switch (c) {
               case '\0':
                  ret += NULL;
                  break;
               case '&':
                  ret += AMP;
                  break;
               case '<':
                  ret += LT;
                  break;
               case '>':
                  ret += GT;
                  break;
               case '"':
                  ret += QUOT;
                  break;
               case '\'':
                  ret += APOS;
                  break;
               case '\r':
                  ret += SLASH_R;
                  break;
               default:
                  ret += c;
                  break;
            }
         }
         return ret;
      }

      /// <summary>
      /// Creates an XML string with <clientProperty name='key'>value</clientProperty>
      /// format which can be used e.g. for the ConnectQos. 
      /// </summary>
      /// <param name="h"></param>
      /// <param name="addNewline"></param>
      /// <returns></returns>
      public static string ToClientPropertiesXml(Hashtable h, bool addNewline) {
         string nl = (addNewline) ? "\n" : "";
         string xml = "";
         foreach (string key in h.Keys) {
            string value = (string)h[key];
            xml += nl;
            xml += " <clientProperty name='";
            xml += EscapeXml(key);
            xml += ("'>");
            xml += EscapeXml(value);
            xml += ("</clientProperty>");
         }
         return xml;
      }

      /**
       * If this clientProperty is send with ConnectQos the clientProperties are
       * copied to the SessionInfo.remoteProperty map (which are manipulatable by jconsole and EventPlugin)
       */
      public static readonly String CLIENTPROPERTY_REMOTEPROPERTIES = "__remoteProperties";

      /// <summary>
      /// Adds some environment information to the Hashtable
      /// </summary>
      /// <param name="info">"version.OS", "version.net" and more</param>
      public static void GetInfoProperties(Hashtable info) {
         if (info == null) return;
         info[CLIENTPROPERTY_REMOTEPROPERTIES] = "true";
         info["version.OS"] = System.Environment.OSVersion.ToString();
         info["version.net"] = System.Environment.Version.ToString();
         try {
            //info["version.xmlBlasterC#"] = xb.GetVersion();
            info["info.OEM"] = GetOemInfo();
            //info["logical.drive"] = Info.GetLogicalDrives();
            //info["machine.name"] = Info.MachineName;
            info["platform.name"] = GetPlatformName();
         }
         catch (Exception) {
         }
      }

#if (WINCE || Smartphone || PocketPC || WindowsCE || CF1)
//#if XMLBLASTER_WINCE (was not activated, why? see PInvoke.cs)
      struct SYSTEMTIME {
          public void LoadDateTime(DateTime dateTime) {
              this.Year = (UInt16)dateTime.Year;
              this.Month = (UInt16)dateTime.Month;
              this.Day = (UInt16)dateTime.Day;
              this.Hour = (UInt16)dateTime.Hour;
              this.Minute = (UInt16)dateTime.Minute;
              this.Second = (UInt16)dateTime.Second;
              this.MilliSecond = (UInt16)dateTime.Millisecond;
          }
          public UInt16 Year;
          public UInt16 Month;
          public UInt16 DayOfWeek;
          public UInt16 Day;
          public UInt16 Hour;
          public UInt16 Minute;
          public UInt16 Second;
          public UInt16 MilliSecond;
      }

      [DllImport("Coredll.dll", EntryPoint = "SetSystemTime")]
      private static extern bool SetSystemTime(ref SYSTEMTIME st);

      public bool UpdateSystemTime(DateTime serverTime) {
          serverTime = serverTime.ToUniversalTime();
          SYSTEMTIME sysTime = new SYSTEMTIME();
          sysTime.LoadDateTime(serverTime);
          bool worked = SetSystemTime(ref sysTime);
          return worked;
      }

        [DllImport("coredll.dll", EntryPoint = "SystemParametersInfo", SetLastError = true)]
        internal static extern bool SystemParametersInfo(int action, int size, byte[] buffer, int winini);
        
        /// <summary>
        /// Returns a string which identifies the device platform, e.g. "PocketPC" or "SmartPhone" or "CEPC platform" (emulator)
        /// </summary>
        public static string GetPlatformName() {
               byte[] buffer = new byte[48];
               int SystemParametersInfoAction_GetPlatformType = 257;
               int SystemParametersInfoFlags_None = 0;
               if (!SystemParametersInfo(SystemParametersInfoAction_GetPlatformType, buffer.Length, buffer, SystemParametersInfoFlags_None)) {
                  int err = Marshal.GetLastWin32Error();
                  throw new XmlBlasterException(XmlBlasterException.INTERNAL_UNKNOWN, "Retrieving platform name failed: " + err);
               }
               string platformname = System.Text.Encoding.Unicode.GetString(buffer, 0, buffer.Length);
               return platformname.Substring(0, platformname.IndexOf("\0")); // trim trailing null
        }

        /// <summary>
        /// Returns OEM specific information from the device, e.g. "hp iPAQ hw6910"
        /// </summary>
        public static string GetOemInfo() {
             byte[] buffer = new byte[128];
             int SystemParametersInfoAction_GetOemInfo = 258;
             int SystemParametersInfoFlags_None = 0;
             if (!SystemParametersInfo(SystemParametersInfoAction_GetOemInfo, buffer.Length, buffer, SystemParametersInfoFlags_None)) {
                int err = Marshal.GetLastWin32Error();
                throw new XmlBlasterException(XmlBlasterException.INTERNAL_UNKNOWN, "Retrieving OEM info failed: " + err);
             } 
             string oeminfo = System.Text.Encoding.Unicode.GetString(buffer, 0, buffer.Length);
             return oeminfo.Substring(0, oeminfo.IndexOf("\0"));
        }

        /*
        /// Returns an array of string containing the names of the logical drives on the current computer.
        /// <returns>An array of string where each element contains the name of a logical drive.</returns>
        public static string[] GetLogicalDrives() {
            // storage cards are directories with the temporary attribute
            System.IO.FileAttributes attrStorageCard = System.IO.FileAttributes.Directory | System.IO.FileAttributes.Temporary;
            ArrayList drives = new ArrayList();
            drives.Add("\\");
            DirectoryInfo rootDir = new DirectoryInfo(@"\");
            foreach (DirectoryInfo di in rootDir.GetDirectories()) {
                // only directory and temporary
                if ((di.Attributes & attrStorageCard) == attrStorageCard) {
                    drives.Add(di.Name);
                }
            }
            return (string[])drives.ToArray(typeof(string));

        }
         */
        /*
        public static string GetMachineName() {
             string machineName = "";
             try {
                 RegistryKey ident = Registry.LocalMachine.OpenSubKey("Ident");
                 machineName = ident.GetValue("Name").ToString();
                 ident.Close();
             }
             catch {
                 throw new PlatformNotSupportedException();
             }
             return machineName;
        }
        */
#else // !XMLBLASTER_WINCE
      public static string GetPlatformName() {
         return ""; // TODO
      }
      public static string GetOemInfo() {
         return ""; // TODO
      }
#endif
   }
}
