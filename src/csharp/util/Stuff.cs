using System;
using System.Collections;
using System.Text;
using System.IO;
using System.Reflection;

namespace org.xmlBlaster.util
{
   ///
   /// @author mr@marcelruff.info 2007 http://www.xmlBlaster.org
   ///
   class Stuff
   {
      /// <summary>
      /// Converts a DateTime to an ISO 8601 datetime string with timezone indicator
      /// See http://en.wikipedia.org/wiki/ISO_8601#Time_zones for info
      /// </summary>
      /// <param name="DateTimeToConvert">The DateTime to be converted</param>
      /// <returns>2007-01-01T14:26:20Z</returns>
      public static string ToIsoDateTimeString(DateTime dateTimeToConvert) {
         return dateTimeToConvert.ToUniversalTime().ToString("s");
         /*
         TimeSpan timeSpan = TimeZone.CurrentTimeZone.GetUtcOffset(dateTimeToConvert);

         if (timeSpan.TotalMilliseconds == 0)
            dateTimeToConvert = dateTimeToConvert.ToUniversalTime();

         return dateTimeToConvert.ToString("o");
          */
      }

      /// <summary>
      /// </summary>
      /// <param name="dateTimeIso8601">2007-01-01T14:26:20Z</param>
      /// <returns></returns>
      public static DateTime FromIsoDateTimeString(string dateTimeIso8601) {
         return DateTime.TryParse(sDate, "s", null);
      }

      /*
       * If you want round-trip guaranteed to a data file in text format, then
use DateTime.ToString("s"). The "s" format conforms to ISO 8601, and
what's more, it's character-by-character sortable in proper order.
Alternatively, use the "u" or "U" formats, which are similarly sortable
but slightly different.
       * 
      // Get the current date & time
DateTime today = DateTime.Now;

// Convert into a string using the ISO8601 format
string sDate = today.ToString("s");

// Convert the string interpretation of the date back into DateTime format
DateTime newDate = DateTime.TryParse(sDate, "s", null);

Since the "s" format string is culture independent (same goes for "u" btw)
you don't need to worry about the culture
      */
      private static const char[] AMP = "&amp;".ToCharArray();
    private static const char[] LT = "&lt;".ToCharArray();
    private static const char[] GT = "&gt;".ToCharArray();
    private static const char[] QUOT = "&quot;".ToCharArray();
    private static const char[] APOS = "&apos;".ToCharArray();

    private static const char[] SLASH_R = "&#x0D;".ToCharArray();
    private static const char[] NULL = "&#x0;".ToCharArray();

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the given StringBuffer.
     */
    public static string EscapeXml(String text) {
        if (text == null) return;
        int length = text.length();
        string ret = "";
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
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
            }
        }
     }

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

      public void UpdateSystemTime(DateTime serverTime) {
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
                  throw new Win32Exception(Marshal.GetLastWin32Error(), "Retrieving platform name failed");
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
                 throw new Win32Exception(Marshal.GetLastWin32Error(), "Retrieving OEM info failed");
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
   }
}
