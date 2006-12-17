// PInvokeCE.cs  2006-12  http://www.xmlBlaster.org/
// Simple layer to delegate C# calls to Windows CE xmlBlaster client C library
// Using P/Invoke of the .NET 1.0 is not tested
// Using P/Invoke of the .NET 2.0 is fully supported
// Using P/Invoke of the .NET Compact Framework 2.0 (CF2) is fully supported
// Using P/Invoke of the .NET Compact Framework 1.0 does NOT support callbacks
//
// You need on CE:
//    xmlBlasterClientC-Arm4.dll
//    pthreads270-Arm4.dll
//    zlib123-Arm4.dll
// You need on Windows:
//    xmlBlasterClientC.dll
//    pthreads270.dll
//    zlib123.dll
//
// Currently only tested on normal Windows (.net 2) and Windows CE 4.2 and 5.1 (CF2) with ARM processor
//
// o All DLL C code is 'multibyte characters' of type UTF-8 written as 'char *'
// o All C# code is 'wchar_t' UTF-16
// o For 'string' we do all conversion from/to UTF-8 in C# and transfer byte[] to 'char *' in C
// o char * are allocated in the C-DLL and freed in the C-DLL
//
// Features: All features of the client C library (compression, tunnel callbacks), see
//           http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
//
// IMPORTANT: The dll are assumed in the current directory or in the %PATH%
//           On Linux set the LD_LIBRARY_PATH to find the .so libraries.
//           The ARM compatible dll's contain an '-Arm4' postfix like xmlBlasterClientC-Arm4.dll
//
// @todo     publishOneway crashes
//           logging with log4net
//           write a testsuite
//           write a requirement
//           create an assembly with ant or nant
//           create the same wrapper for the xmlBlaster C++ library
//
// @author   xmlBlaster@marcelruff.info
//
// @prepare  Compile the C client library first (see xmlBlaster\src\c\xmlBlasterClientC.sln)
//
// @see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
// @see      http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfintrointerp.asp?frame=true
// @see      http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfadvinterop.asp
//
// @see Callback function pointers: http://msdn2.microsoft.com/en-us/library/5zwkzwf4.aspx
// @c        http://www.xmlBlaster/org

//
/*
Usage:
XmlBlaster C SOCKET client

   -dispatch/connection/plugin/socket/hostname [localhost]
                       Where to find xmlBlaster.
   -dispatch/connection/plugin/socket/port [7607]
                       The port where xmlBlaster listens.
   -dispatch/connection/plugin/socket/localHostname [NULL]
                       Force the local IP, useful on multi homed computers.
   -dispatch/connection/plugin/socket/localPort [0]
                       Force the local port, useful to tunnel firewalls.
   -dispatch/connection/plugin/socket/compress/type []
                       Switch on compression with 'zlib:stream'.
   -dispatch/connection/plugin/socket/useUdpForOneway [false]
                       Use UDP for publishOneway() calls.
   -dispatch/callback/plugin/socket/hostname [localhost]
                       The IP where to establish the callback server.
                       Can be useful on multi homed hosts.
   -dispatch/callback/plugin/socket/port [7611]
                       The port of the callback server.
   -plugin/socket/multiThreaded  [true]
                       If true the update() call to your client code is a separate thread.
   -plugin/socket/responseTimeout  [60000 (one minute)]
                       The time in millis to wait on a response, 0 is forever.
   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]

Example:
  TestPInvoke -logLevel TRACE -dispatch/connection/plugin/socket/hostname 192.168.2.9
 * 
Preprocessor:
   XMLBLASTER_MONO
             Forces support in a Linux mono environment
   WINCE || Smartphone || PocketPC || WindowsCE || FORCE_PINVOKECE
             Any single of the above will force Windows CE compatibility
   CF1       To have Windows CE compact framework .net 1.x support,
             no callbacks are available in this case.
             Please choose to install CF2 on your PDA and leave this define away.
   DOTNET1   To force old .net 1.x, not tested, for Windows XP etc only
*/

// Initial defines cleanup:
// In our code we only use
//   XMLBLASTER_MONO
//   XMLBLASTER_WINCE
//   XMLBLASTER_WIN32
//   CF1
//   DOTNET1
//   FORCE_CDELC
// NOTE: mono compiler is buggy and can't handle nested #if !
#if XMLBLASTER_MONO
#  warning INFO: We compile on a Linux mono box
#endif

#if (WINCE || Smartphone || PocketPC || WindowsCE || CF1)
   // VC2005 automatically set 'WindowsCE' for Mobile (Windows CE 5.0)
   // and typically one of the other defines for Smart Devices 2003
#  define XMLBLASTER_WINCE
#  warning INFO: We compile for Windows CE compact framework .net
#endif

#if !(WINCE || Smartphone || PocketPC || WindowsCE || CF1) && !XMLBLASTER_MONO  // Assume WIN32
#  define XMLBLASTER_WIN32
#  warning INFO: We compile for Windows .net target
#endif

#if CF1
#  warning We compile for Windows CE compact framework .net 1.0, no xmlBlaster callback are available!
#endif

#if DOTNET1
#  warning We compile for Windows .net 1.x, no xmlBlaster callback are implemented!
#endif

// Setting local defines
#if (XMLBLASTER_WIN32 || XMLBLASTER_MONO) && !DOTNET1
#  define FORCE_CDELC // only supported in .net 2 (cdecl is default on WINCE)
// #  warning INFO: We use UnmanagedFunctionPointer
// CF1 and CF2 and .net 1.x don't support UnmanagedFunctionPointer
// CallingConvention=CallingConvention.Cdecl supported in .net CF1 and CF2
// UnmanagedFunctionPointer: new in .net 2.0
#endif

using System;
using System.Text;
using System.Runtime.InteropServices;


namespace org.xmlBlaster.client
{
   /// Calling unmanagegd code: xmlBlasterClientC.dll
   public class PInvokeCE : I_XmlBlasterAccess
   {
      static LogLevel localLogLevel = LogLevel.INFO; // TODO: log4net

#     if XMLBLASTER_MONO
         // Linux Debug libxmlBlasterClientCD.so, set LD_LIBRARY_PATH to find the shared library
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD";
#     elif XMLBLASTER_WINCE
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD-Arm4.dll";
#     else // XMLBLASTER_WIN32
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientC.dll";
         //const string XMLBLASTER_C_LIBRARY = "..\\..\\lib\\xmlBlasterClientC.dll";
#     endif

      // Helper struct for DLL calls (struct does return empty IntPtr from DLL, why?
      //[StructLayout(LayoutKind.Sequential/*, CharSet = CharSet.Unicode*/)]
      public struct MsgUnitUnmanagedCEpublish // publish() only works with a type 'struct'?!
      {
         public MsgUnitUnmanagedCEpublish(bool dummy/*if a stuct*/)
         {
            key = IntPtr.Zero;
            contentLen = 0;
            content = IntPtr.Zero;
            qos = IntPtr.Zero;
            responseQos = IntPtr.Zero;
         }
         public MsgUnitUnmanagedCEpublish(string key, byte[] content, string qos)
         {
            this.key = stringToUtf8IntPtr(key);
            this.contentLen = (content == null) ? 0 : content.Length;
            this.content = byteArrayToIntPtr(content);
            this.qos = stringToUtf8IntPtr(qos);
            responseQos = IntPtr.Zero;
         }
         public IntPtr key;
         public int contentLen;
         public IntPtr content;
         public IntPtr qos;
         public IntPtr responseQos;
         /* Has to be called exactly once if freeUnmanaged==true! */
         public byte[] getContent(bool freeUnmanaged)
         {
            if (content == IntPtr.Zero) return new byte[0];
            return byteArrayFromIntPtr(content, contentLen, freeUnmanaged);
         }
         /* Has to be called exactly once if freeUnmanaged==true! */
         public string getKey(bool freeUnmanaged)
         {  // If called never: memory leak, if called twice: double free
            if (key == IntPtr.Zero)
               return "";
            return stringFromUtf8IntPtr(key, freeUnmanaged);
         }
         /* Has to be called exactly once if freeUnmanaged==true! */
         public string getQos(bool freeUnmanaged)
         {
            if (qos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(qos, freeUnmanaged);
         }
         /* Has to be called exactly once! */
         public string getResponseQos()
         {
            if (responseQos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(key);
         }
      }

      [StructLayout(LayoutKind.Sequential/*, CharSet = CharSet.Unicode*/)]
      public class MsgUnitUnmanagedCEget // the get() only works with type 'class'! ?
      {
         public MsgUnitUnmanagedCEget()
         {
            key = IntPtr.Zero;
            contentLen = 0;
            content = IntPtr.Zero;
            qos = IntPtr.Zero;
            responseQos = IntPtr.Zero;
         }
         public MsgUnitUnmanagedCEget(string key, byte[] content, string qos)
         {
            this.key = stringToUtf8IntPtr(key);
            this.contentLen = (content == null) ? 0 : content.Length;
            this.content = byteArrayToIntPtr(content);
            this.qos = stringToUtf8IntPtr(qos);
            responseQos = IntPtr.Zero;
         }
         public IntPtr key;
         public int contentLen;
         public IntPtr content;
         public IntPtr qos;
         public IntPtr responseQos;
         /* Has to be called exactly once! */
         public byte[] getContent()
         {
            if (content == IntPtr.Zero) return new byte[0];
            return byteArrayFromIntPtr(content, contentLen, true);
         }
         /* Has to be called exactly once! */
         public string getKey()
         {  // If called never: memory leak, if called twice: double free
            if (key == IntPtr.Zero)
               return "";
            return stringFromUtf8IntPtr(key, true);
         }
         /* Has to be called exactly once! */
         public string getQos()
         {
            if (qos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(qos, true);
         }
         /* Has to be called exactly once! */
         public string getResponseQos()
         {
            if (responseQos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(responseQos, true);
         }
      }

      // Helper struct for DLL calls
      public struct XmlBlasterUnmanagedCEException
      {
         public XmlBlasterUnmanagedCEException(bool isRemote)
         {
            remote = (isRemote) ? 1 : 0;
            errorCode = IntPtr.Zero;
            message = IntPtr.Zero;
         }
         public int remote;
         public IntPtr errorCode;
         public IntPtr message;
         public bool CaughtException()
         {
            return errorCode != IntPtr.Zero;
         }
         public string GetErrorCode()
         {
            return stringFromUtf8IntPtr(errorCode);
         }
         public string GetMessage()
         {
            return stringFromUtf8IntPtr(message);
         }
      }

      //public struct QosArr
      //{
      //   public int len;  /* Number of XML QoS strings */
      //   public string[] qosArr;
      //}

      //public struct MsgUnitUnmanagedCEArr
      //{
      //   public string secretSessionId;
      //   public int len;
      //   public MsgUnit[] msgUnitArr;
      //}


      [StructLayout(LayoutKind.Sequential/*, CharSet = CharSet.Unicode*/)]
      public class StringArr
      {
         public IntPtr str;
      }

      public void log(String str)
      {
         logger(LogLevel.TRACE, "", str);
      }

      public void logger(LogLevel level, String location, String message)
      {
         //logLevel.ToString("d")
         if ((int)level <= (int)localLogLevel) { // TODO: log4net
            if (null != onLogging)
            {
               try
               {
                  onLogging((LogLevel)level, location, message); // Dispatch to C# clients
               }
               catch (Exception e)
               {
                  System.Diagnostics.Debug.WriteLine(e.ToString());
                  Console.WriteLine(e.ToString());
               }
            }
            else {
               string prefix = (location != null && location.Length > 0) ? location : "[PInvoke]";
                    prefix += " " + level + ": ";
               System.Diagnostics.Debug.WriteLine(prefix + message);
               Console.WriteLine(prefix + message);
            }
         }
      }

#     if FORCE_CDELC
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate void LoggerUnmanagedFp(int level, IntPtr location, IntPtr message);

      /// Callback by xmlBlaster C dll, see LoggerUnmanagedFp
      /// message pointer is NOT freed here, it is freed by the calling C DLL after this call
      void loggerUnmanaged(int level, IntPtr location, IntPtr message)
      {
         string loc = stringFromUtf8IntPtr(location, false);
         string msg = stringFromUtf8IntPtr(message, false);
         LogLevel logLevel = (LogLevel)level;
         logger(logLevel, loc, msg);
      }


#     if FORCE_CDELC
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate int FPtr(int value);

#     if FORCE_CDELC
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate void UpdateUnmanagedFp(IntPtr cbSessionId, ref MsgUnitUnmanagedCEpublish msgUnit, ref XmlBlasterUnmanagedCEException exception);

      /// Callback by xmlBlaster C dll, see UpdateUnmanagedFp and XmlBlasterUnmanagedCE.h
      /// The cbSessionId_ and msgUnitUnmanaged is freed by the caller
      /// The return should be a string, but this is difficult over P/Invoke, we would
      /// need to solve it by an out parameter. But xmlBlaster ignores it currently so it is an open TODO
      /// Info about callback function pointer: http://msdn2.microsoft.com/en-us/library/5zwkzwf4.aspx
      /// Nice example how to pass a pointer to a managed object to DLL
      /// and on callback cast it again to the managed object:
      ///  http://msdn2.microsoft.com/en-us/library/44ey4b32.aspx
      /// See http://msdn2.microsoft.com/en-us/library/ss9sb93t.aspx
      /// Samples: http://msdn2.microsoft.com/en-us/library/fzhhdwae.aspx
      void updateUnmanaged(IntPtr cbSessionId_, ref MsgUnitUnmanagedCEpublish msgUnitUnmanaged, ref XmlBlasterUnmanagedCEException exception)
      {
         logger(LogLevel.TRACE, "", "Entering updateUnmanaged()");
         string cbSessionId = stringFromUtf8IntPtr(cbSessionId_, false);
         //fillUnmanagedException(ref exception, "user.internal", "a test exception from C#");

         MsgUnit msgUnit = new MsgUnit(msgUnitUnmanaged.getKey(false),
            msgUnitUnmanaged.getContent(false), msgUnitUnmanaged.getQos(false));
         if (null != onUpdate)
         {
            try
            {
               string ret = onUpdate(cbSessionId, msgUnit);
               logger(LogLevel.TRACE, "", "Ignoring " + ret);
            }
            catch (XmlBlasterException e)
            {
               logger(LogLevel.WARN, "", "OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, e.ErrorCode, e.Message);
            }
            catch (Exception e)
            {
               logger(LogLevel.ERROR, "", "OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, "user.update.internalError", e.ToString());
            }
            return;
         }
         logger(LogLevel.INFO, "", "updateUnmanaged got message " + msgUnit.getKey());
         logger(LogLevel.TRACE, "", "updateUnmanaged invoked START ==================");
         logger(LogLevel.TRACE, "", msgUnit.key);
         logger(LogLevel.TRACE, "", msgUnit.getContentStr());
         logger(LogLevel.TRACE, "", msgUnit.qos);
         logger(LogLevel.TRACE, "", "updateUnmanaged invoked DONE ===================");
         //string ret = "<qos><state id='OK'/></qos>";
         //return ret;
      }

      /**
       * Allocates native C memory. 
       * You need to call
       *   xmlBlasterUnmanagedCEExceptionFree(XmlBlasterUnmanagedCEException *exception)
       * later!
       */
      void fillUnmanagedException(ref XmlBlasterUnmanagedCEException outE, string errorCode, string message)
      {
         outE.errorCode = stringToUtf8IntPtr(errorCode);
         outE.message = stringToUtf8IntPtr(message);
         outE.remote = 1;
      }

      /**
       * Copy the unmanaged data to our XmlBlasterException and
       * frees the native memory.
       */
      XmlBlasterException fillXmlBlasterException(ref XmlBlasterUnmanagedCEException exception)
      {
         XmlBlasterException managed = new XmlBlasterException(exception.remote != 0,
              stringFromUtf8IntPtr(exception.errorCode),
              stringFromUtf8IntPtr(exception.message));
         xmlBlasterUnmanagedCEExceptionFree(ref exception);
         return managed;
      }

      /**
       * Convert a string to a UTF-8 encoded byte array.
       * @param str UTF-16 C# string
       * @return UTF-8 multibyte array to be passed to C dll (zero terminated)
       */
      public static byte[] StringToUtf8ByteArray(string str)
      {
         byte[] data = System.Text.Encoding.UTF8.GetBytes(str); // TODO
         return data;
      }

      /**
       * Convert a UTF-8 byte array to a UTF-16 string
       * @param dBytes UTF-8 multibyte string from C (zero terminated)
       * @return string to be used in C#
       */
      public static string Utf8ByteArrayToString(byte[] dBytes)
      {
         string str;
         str = System.Text.Encoding.UTF8.GetString(dBytes, 0, dBytes.Length);
         return str;
      }

      /**
       * Allocates native memory
       * @param str The UTF-16 unicode string to transfer
       * @return Contains zero terminated UTF-8,
       * the pointer memory needs to be freed with xmlBlasterUnmanagedCEFree(IntPtr).
       * All xmlBlasterUnmanagedCEXXX() calls free the 'char *' or 'char **'
       * passed in the dll, so we don't have to do it here in C#
       */
      static IntPtr stringToUtf8IntPtr(string str)
      {
         byte[] bytes = StringToUtf8ByteArray(str);
         return byteArrayToIntPtr(bytes);
      }

      /**
       * Allocates native memory
       * @param e The byte array to transfer
       * @return C allocated char * of length e.Length() (+ 1 as we add a zero in case of strings)
       * the pointer memory needs to be freed with xmlBlasterUnmanagedCEFree(IntPtr).
       * All xmlBlasterUnmanagedCEXXX() calls free the 'char *' or 'char **'
       * passed in the dll, so we don't have to do it here in C#
       */
      unsafe static IntPtr byteArrayToIntPtr(byte[] e)
      {
         // See http://msdn2.microsoft.com/en-us/library/aa497275.aspx#Q4rlim632
         // "How do I convert a byte[] to an IntPtr?"
         IntPtr ptr = xmlBlasterUnmanagedCEMalloc(e.Length + 1); // is fixed(...) already
         byte* bp = (byte*)ptr.ToPointer();
         for (int i = 0; i < e.Length; i++)
         {
            bp[i] = e[i];
         }
         bp[e.Length] = 0;
         return ptr;
      }

      /**
       * Handling a DLL C function which returns a malloced char *
       * 
       * As the following throws NotSupportedException on Windows CE
       *   byte[] tmp = stringReturner();
       * we have a workaround and return an IntPtr.
       * The string which was allocated in the DLL C code is
       * extracted here and then then freed by a call to
       *   xmlBlasterFree(IntPtr)
       * @param ptr A C malloced 'char *' containing UTF-8 text
       * @return    A unicode 'wchar_t *' UTF-16 string
       */
      static string stringFromUtf8IntPtr(IntPtr ptr)
      {
         return stringFromUtf8IntPtr(ptr, true);
      }

      static string stringFromUtf8IntPtr(IntPtr ptr, bool freeUnmanaged)
      {
         if (ptr == IntPtr.Zero) return "";
         byte[] bytes = byteArrayFromIntPtr(ptr, -1, freeUnmanaged);
         return Utf8ByteArrayToString(bytes);
      }

      /**
       * @param contentLen 
       * @param freeUnmanaged If true we call the native C DLL and free() the memory pointed to by ptr
       */

      /// <summary>
      /// Convenience method for zero terminated string in IntPtr which shall be read and free()d
      /// </summary>
      /// <param name="contentLen">If -1 we parse until we reach the first 0, else we parse the given length</param>
      /// <param name="freeUnmanaged">If true we call the native C DLL and free() the memory pointed to by ptr</param>
      /// <returns></returns>
      unsafe static byte[] byteArrayFromIntPtr(IntPtr ptr, int contentLen, bool freeUnmanaged)
      {
         if (ptr == IntPtr.Zero) return new byte[0];
         //Can't cast type 'System.IntPtr' to 'byte[]'
         //  byte[] tmp = (byte[])stringReturner();
         //so we need to copy it manually:
         void* vPtr = ptr.ToPointer(); // is fixed(...) already
         byte* tmpP = (byte*)vPtr;
         int len = contentLen;
         if (contentLen < 0)
            for (len = 0; tmpP[len] != 0; len++) ;
         byte[] tmp = new byte[len];
         for (int i = 0; i < tmp.Length; i++)
            tmp[i] = tmpP[i];
         // Now free() the malloc() IntPtr in the C DLL ...
         if (freeUnmanaged) xmlBlasterUnmanagedCEFree(ptr);
         return tmp;
      }

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEMalloc(int size);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEFree(IntPtr p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCERegisterLogger(IntPtr xa, IntPtr loggerUnmanaged);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEFreePP(out IntPtr p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEExceptionFree(ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr getXmlBlasterAccessUnparsedUnmanagedCE(int argc, IntPtr[] argv);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void freeXmlBlasterAccessUnparsedUnmanagedCE(IntPtr xa);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEConnect(IntPtr xa, IntPtr qos, IntPtr updateUnmanaged, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedCEInitialize(IntPtr xa, IntPtr updateUnmanaged, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedCEDisconnect(IntPtr xa, IntPtr qos, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEPublish(IntPtr xa, out MsgUnitUnmanagedCEpublish msgUnit, ref XmlBlasterUnmanagedCEException exception);

      //[DllImport(XMLBLASTER_C_LIBRARY )]
      //private extern static QosArr xmlBlasterUnmanagedCEPublishArr(IntPtr xa, MsgUnitUnmanagedCEArr msgUnitArr, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEPublishOneway(IntPtr xa, IntPtr msgUnitArr, int length, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCESubscribe(IntPtr xa, IntPtr key, IntPtr qos, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEUnSubscribe(IntPtr xa, IntPtr key, IntPtr qos,
         ref XmlBlasterUnmanagedCEException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEErase(IntPtr xa, IntPtr key, IntPtr qos,
         ref XmlBlasterUnmanagedCEException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEGet(IntPtr xa, IntPtr key, IntPtr qos,
         ref XmlBlasterUnmanagedCEException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEPing(IntPtr xa, IntPtr qos, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static bool xmlBlasterUnmanagedCEIsConnected(IntPtr xa);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEUsage();

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEVersion();

      private IntPtr xa;
      private UpdateUnmanagedFp updateUnmanagedFp;
      private LoggerUnmanagedFp loggerUnmanagedFp;
      private IntPtr updateFpForDelegate;
      private IntPtr loggerFpForDelegate;

      public PInvokeCE(string[] argv)
      {
         if (argv == null) argv = new String[0];

         string deviceId = getDeviceUniqueId();
         logger(LogLevel.TRACE, "", "Using deviceUniqueId '" + deviceId + "'");

         updateUnmanagedFp = new UpdateUnmanagedFp(this.updateUnmanaged);
         updateFpForDelegate = Marshal.GetFunctionPointerForDelegate(updateUnmanagedFp);

         // Convert command line arguments: C client lib expects the executable name as first entry
         IntPtr[] c_argv = new IntPtr[argv.Length + 1];
         c_argv[0] = stringToUtf8IntPtr("PInvokeCE"); // TODO: my executable name
         for (int i = 0; i < argv.Length; ++i)
         {
            if (argv[i].ToLower().Equals("--help") || argv[i].ToLower().Equals("-help")
               || argv[i].ToLower().Equals("/?"))
            {
               string usage = "Usage:\nxmlBlaster C client v" + getVersion()
                  + " on " + System.Environment.OSVersion.ToString() +
               #if XMLBLASTER_WINCE
                  " compact framework .net " + System.Environment.Version.ToString();
               #else
                  " .net " + System.Environment.Version.ToString();
               #endif
               usage += "\n" + getUsage();
               logger(LogLevel.TRACE, "", usage);
               throw new XmlBlasterException("user.usage", usage);//"Good bye");
            }
            if ("-logLevel".Equals(argv[i]) && (i < argv.Length-1)) {
               string level = argv[i + 1];
#              if XMLBLASTER_WINCE   // Enum.GetValues is not supported
                  if ("INFO".Equals(level.ToUpper()))
                     localLogLevel = LogLevel.INFO;
                  else if ("WARN".Equals(level.ToUpper()))
                     localLogLevel = LogLevel.WARN;
                  else if ("ERROR".Equals(level.ToUpper()))
                     localLogLevel = LogLevel.ERROR;
                  else if ("TRACE".Equals(level.ToUpper()))
                     localLogLevel = LogLevel.TRACE;
                  else if ("DUMP".Equals(level.ToUpper()))
                     localLogLevel = LogLevel.DUMP;
#              else
                  Array logArray = Enum.GetValues(typeof(LogLevel));
                  foreach (LogLevel logLevel in logArray)
                     if (logLevel.ToString().Equals(level)) {
                        localLogLevel = logLevel;
                        break;
                     }
#              endif
            }
            c_argv[i + 1] = stringToUtf8IntPtr(argv[i]);
         }

         // Frees not c_argv (as it crashed)
         xa = getXmlBlasterAccessUnparsedUnmanagedCE(c_argv.Length, c_argv);
         for (int i = 0; i < c_argv.Length; ++i)
            xmlBlasterUnmanagedCEFree(c_argv[i]);

         loggerUnmanagedFp = new LoggerUnmanagedFp(this.loggerUnmanaged);
         loggerFpForDelegate = Marshal.GetFunctionPointerForDelegate(loggerUnmanagedFp);
         xmlBlasterUnmanagedCERegisterLogger(xa, loggerFpForDelegate);

         logger(LogLevel.INFO, "", "xmlBlaster C client v" + getVersion() 
            + " on " + System.Environment.OSVersion.ToString() +
#        if XMLBLASTER_WINCE
            " compact framework .net " + System.Environment.Version.ToString() +
            " deviceId=" + deviceId);
#        else
            " .net " + System.Environment.Version.ToString());
#        endif
      }

      ~PInvokeCE()
      {
         if (xa != IntPtr.Zero)
            freeXmlBlasterAccessUnparsedUnmanagedCE(xa);
         logger(LogLevel.TRACE, "", "~PInvokeCE() ...");
      }

      void check(string methodName)
      {
         logger(LogLevel.TRACE, "", methodName + "() check ...");
         if (xa == IntPtr.Zero) {
            logger(LogLevel.ERROR, "", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of PInvokeCE.cs class after a disconnect() call");
            throw new XmlBlasterException("internal.illegalState", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of PInvokeCE.cs class after a disconnect() call");
         }
      }


      private delegate void OnLogging(LogLevel logLevel, string location, string message);
      private event OnLogging onLogging;
      public void addLoggingListener(I_LoggingCallback listener)
      {
         if (listener != null)
         {
            onLogging += new OnLogging(listener.OnLogging);
         }
      }
      public void unregister(I_LoggingCallback listener)
      {
         if (listener != null)
         {
            onLogging -= new OnLogging(listener.OnLogging);
         }
      }

      private delegate string OnUpdate(string cbSessionId, MsgUnit msgUnit);
      private event OnUpdate onUpdate;
      public string connect(string qos, I_Callback listener)
      {
         check("connect");
         if (listener != null)
         {
            onUpdate += new OnUpdate(listener.OnUpdate);
         }
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
#           if CF1 // Compact Framework .net 1.0
               /*bool bb = */xmlBlasterUnmanagedCEInitialize(xa, IntPtr.Zero, ref exception);
#           else // Compact Framework 2
               /*bool bb = */xmlBlasterUnmanagedCEInitialize(xa, updateFpForDelegate, ref exception);
#           endif
            checkAndThrow("xmlBlasterUnmanagedCEInitialize", ref exception);

#           if CF1 // Compact Framework .net 1.0
               IntPtr retP = xmlBlasterUnmanagedCEConnect(xa, stringToUtf8IntPtr(qos), IntPtr.Zero, ref exception);
#           else // Compact Framework 2
               IntPtr retP = xmlBlasterUnmanagedCEConnect(xa, stringToUtf8IntPtr(qos), updateFpForDelegate, ref exception);
#           endif
            checkAndThrow("xmlBlasterUnmanagedCEConnect", ref exception);
            string ret = stringFromUtf8IntPtr(retP);
            //logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEConnect: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "connect failed", e);
         }
      }

      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      public bool disconnect(string qos)
      {
         check("disconnect");
         XmlBlasterException ex = null;
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            bool bb = xmlBlasterUnmanagedCEDisconnect(xa, stringToUtf8IntPtr(qos), ref exception);
            if (exception.CaughtException())
            {
               ex = fillXmlBlasterException(ref exception);
               // Important logging since we don't throw the exception below
               logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEDisconnect: Got exception from C: " + ex.ToString());
            }

            freeXmlBlasterAccessUnparsedUnmanagedCE(xa);
            xa = IntPtr.Zero;
            logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEDisconnect: SUCCESS freed all resources");

            //if (ex != null) throw ex;
            return bb;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "disconnect failed", e);
         }
      }

      private void checkAndThrow(string location, ref XmlBlasterUnmanagedCEException exception)
      {
         if (exception.CaughtException())
         {
            XmlBlasterException e = fillXmlBlasterException(ref exception);
            logger(LogLevel.WARN, "", location + ": Got exception from C: " + e.ToString());
            throw e;
         }
         logger(LogLevel.TRACE, "", location + ": SUCCESS");
      }

      public string publish(string key, string content, string qos)
      {
         return publish(new MsgUnit(key, content, qos));
      }

      public string publish(MsgUnit msgUnit)
      {
         check("publish");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            MsgUnitUnmanagedCEpublish msgUnitUnmanaged = new MsgUnitUnmanagedCEpublish(msgUnit.getKey(),
               msgUnit.getContent(), msgUnit.getQos());

            IntPtr retP = xmlBlasterUnmanagedCEPublish(xa, out msgUnitUnmanaged, ref exception);
            checkAndThrow("xmlBlasterUnmanagedCEPublish", ref exception);

            string ret = stringFromUtf8IntPtr(retP);
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "publish failed", e);
         }
      }

      // TODO: Crashs for array size > 2
      /*
       Invalid read of size 1
         at 0x63FFF14: encodeMsgUnitArr (xmlBlasterSocket.c:222)
            by 0x63FD11E: xmlBlasterPublishOneway (XmlBlasterConnectionUnparsed.c:1004)
            by 0x63F9125: xmlBlasterPublishOneway (XmlBlasterAccessUnparsed.c:726)
            by 0x63FF0B8: xmlBlasterUnmanagedCEPublishOneway (XmlBlasterUnmanaged.c:365)
            by 0x6524CC8: ???
      */
      public void publishOneway(MsgUnit[] msgUnitArr)
      {
         check("publishOneway");
         if (msgUnitArr.Length < 1)
            return;
         IntPtr arrP = IntPtr.Zero;
         try
         {
            IntPtr current = arrP;
            for (int i=0; i<msgUnitArr.Length; i++) {
               MsgUnitUnmanagedCEpublish msgUnitUnmanaged = new MsgUnitUnmanagedCEpublish(msgUnitArr[i].getKey(),
                        msgUnitArr[i].getContent(), msgUnitArr[i].getQos());
               if (i == 0) {
                  int len = Marshal.SizeOf(msgUnitUnmanaged);
                  arrP = Marshal.AllocHGlobal(msgUnitArr.Length * len);
                  current = arrP;
               } 
               Marshal.StructureToPtr(msgUnitUnmanaged, current, false);
               current = (IntPtr)((long)current + Marshal.SizeOf(msgUnitUnmanaged));
            }
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            xmlBlasterUnmanagedCEPublishOneway(xa, arrP, msgUnitArr.Length, ref exception);
            checkAndThrow("xmlBlasterUnmanagedCEPublishOneway", ref exception);
            logger(LogLevel.TRACE, "", "publishOneway: SUCCESS");
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "publishOneway failed", e);
         }
         finally {
            if (arrP != IntPtr.Zero) Marshal.FreeHGlobal(arrP);
         }
      }

      public string subscribe(string key, string qos)
      {
         check("subscribe");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            IntPtr retP = xmlBlasterUnmanagedCESubscribe(xa, stringToUtf8IntPtr(key),
                                           stringToUtf8IntPtr(qos), ref exception);
            checkAndThrow("xmlBlasterUnmanagedCESubscribe", ref exception);
            string ret = stringFromUtf8IntPtr(retP);
            logger(LogLevel.TRACE, "", ret);
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "subscribe failed", e);
         }
      }

      public string[] unSubscribe(string key, string qos)
      {
         check("unSubscribe");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            int size;
            IntPtr outArray = new IntPtr();
            xmlBlasterUnmanagedCEUnSubscribe(xa, stringToUtf8IntPtr(key), stringToUtf8IntPtr(qos), ref exception, out size, out outArray);
            checkAndThrow("xmlBlasterUnmanagedCEUnSubcribe", ref exception);
            if (size == 0)
            {
               logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEUnSubcribe: Done (no topics found)");
               return new String[0];
            }
            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               StringArr stringArr = new StringArr();
               Marshal.PtrToStructure(current, stringArr);
               current = (IntPtr)((long)current + Marshal.SizeOf(stringArr));
               retQosArr[i] = stringFromUtf8IntPtr(stringArr.str);
            }
            xmlBlasterUnmanagedCEFreePP(out outArray);
            return retQosArr;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public string[] erase(string key, string qos)
      {
         check("erase");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            int size;
            IntPtr outArray = new IntPtr();
            xmlBlasterUnmanagedCEErase(xa, stringToUtf8IntPtr(key), stringToUtf8IntPtr(qos), ref exception, out size, out outArray);
            checkAndThrow("xmlBlasterUnmanagedCEErase", ref exception);
            if (size == 0)
            {
               logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEErase: Done (no topics found)");
               return new String[0];
            }

            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               StringArr stringArr = new StringArr();
               Marshal.PtrToStructure(current, stringArr);
               current = (IntPtr)((long)current + Marshal.SizeOf(stringArr));
               retQosArr[i] = stringFromUtf8IntPtr(stringArr.str);
            }
            //logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEErase: FREEING WRONG POINTER??");
            xmlBlasterUnmanagedCEFreePP(out outArray);
            return retQosArr;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "erase failed", e);
         }
      }

      public MsgUnit[] get(string key, string qos)
      {
         check("get");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            int size;
            IntPtr outArray = new IntPtr();
            xmlBlasterUnmanagedCEGet(xa, stringToUtf8IntPtr(key), stringToUtf8IntPtr(qos),
               ref exception, out size, out outArray);
            checkAndThrow("xmlBlasterUnmanagedCEGet", ref exception);
            MsgUnit[] msgUnitArr = new MsgUnit[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEGet: parsing #" + i);
               MsgUnitUnmanagedCEget msgUnitUnmanaged = new MsgUnitUnmanagedCEget();
               Marshal.PtrToStructure(current, msgUnitUnmanaged);
               //Console.WriteLine("msgUnitUnmanaged.contentLen=" + msgUnitUnmanaged.contentLen + " sizeof="+Marshal.SizeOf(msgUnitUnmanaged));
               current = (IntPtr)((long)current + Marshal.SizeOf(msgUnitUnmanaged));
               // The getters free the memory in the C DLL:
               msgUnitArr[i] = new MsgUnit(
                  msgUnitUnmanaged.getKey(), msgUnitUnmanaged.getContent(), msgUnitUnmanaged.getQos());
               msgUnitUnmanaged.getResponseQos(); // dummy call to free memory in C DLL
            }
            //logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEGet: freeing now IntPtr SHOULD IT BE *outArray???");
            xmlBlasterUnmanagedCEFreePP(out outArray);
            return msgUnitArr;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "get failed", e);
         }
      }

      public string ping(string qos)
      {
         check("ping");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            IntPtr retP = xmlBlasterUnmanagedCEPing(xa, stringToUtf8IntPtr(qos), ref exception);
            checkAndThrow("xmlBlasterUnmanagedCEPing", ref exception);
            string ret = stringFromUtf8IntPtr(retP);
            return ret;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "ping failed", e);
         }
      }

      public bool isConnected()
      {
         if (xa == IntPtr.Zero) return false;
         try
         {
            bool bb = xmlBlasterUnmanagedCEIsConnected(xa);
            logger(LogLevel.TRACE, "", "xmlBlasterUnmanagedCEIsConnected: SUCCESS '" + bb + "'");
            return bb;
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "isConnected failed", e);
         }
      }

      public string getVersion()
      {
         IntPtr retP = xmlBlasterUnmanagedCEVersion();
         return stringFromUtf8IntPtr(retP);
      }

      public string getUsage()
      {
         IntPtr retP = xmlBlasterUnmanagedCEUsage();
         return stringFromUtf8IntPtr(retP);
      }

      public string getDeviceUniqueId() {
#        if XMLBLASTER_WINCE
            byte[] bytes = GetDeviceID("xmlBlasterClient");
            if (bytes == null) return null;
            string deviceId = toHexString(bytes);
            //{// Remove again
            //   throw new XmlBlasterException("bla", "My deviceId=" + deviceId);
            //}
            return deviceId;
#        else
            return null;
#        endif
      }

      public static string toHexString(byte[] bytes) {
         if (bytes == null) return null;
         StringBuilder temp = new StringBuilder();
         for (int i=0; i<bytes.Length; i++)
            temp.Append(bytes[i].ToString("X2")); // hex view
            //temp.Append(bytes[i].ToString("D3")).Append(" "); // Decimal view
         return temp.ToString();
      }

#if XMLBLASTER_WINCE
      /*
HRESULT GetDeviceUniqueID(
  LPBYTE pbApplicationData,
  DWORD cbApplictionData,
  DWORD dwDeviceIDVersion,
  LPBYTE pbDeviceIDOutput,
  DWORD* pcbDeviceIDOutput
);
      */
      //http://blogs.msdn.com/windowsmobile/archive/2006/01/09/510997.aspx#514959
      [DllImport("coredll.dll")]
      private extern static int GetDeviceUniqueID([In, Out] byte[] appdata,
                                                  int cbApplictionData,
                                                  int dwDeviceIDVersion,
                                                  [In, Out] byte[] deviceIDOuput,
                                                  out uint pcbDeviceIDOutput);

      /// Works only on Windows Mobile 5 and above
      private byte[] GetDeviceID(string AppString)
      {
         // Call the GetDeviceUniqueID
         byte[] AppData = new byte[AppString.Length];
         for (int count = 0; count < AppString.Length; count++)
            AppData[count] = (byte)AppString[count];

         int appDataSize = AppData.Length;
         byte[] DeviceOutput = new byte[20];
         uint SizeOut = 20;

         try {
            GetDeviceUniqueID(AppData, appDataSize, 1, DeviceOutput, out SizeOut);
         }
         catch (Exception e) {
            logger(LogLevel.WARN, "", "GetDeviceUniqueID() is not supported on this platform: " + e.ToString());
            return null;
         }

         if (SizeOut == 0)
            return null;

         return DeviceOutput;
      }
#endif
   }
}
