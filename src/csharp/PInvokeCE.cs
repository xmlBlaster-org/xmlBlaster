// PInvokeCE.cs  2006-12  http://www.xmlBlaster.org/
// Simple layer to delegate C# calls to Windows CE xmlBlaster client C library
// Using P/Invoke of the .NET Compact Framework 1.0
//
// You need:
//    xmlBlasterClientC-Arm4.dll
//    pthreads270-Arm4.dll
//    zlib123-Arm4.dll
//
// Currently only tested on Windows CE 4.2 and 5.1 with ARM processor
//
// o All DLL C code is 'multibyte characters' of type UTF-8 written as 'char *'
// o All C# code is 'wchar_t' UTF-16
// o For 'string' we do all conversion from/to UTF-8 in C# and transfer byte[] to 'char *' in C
// o char * are allocated in the C-DLL and freed in the C-DLL
//
// Features: All features of the client C library (compression, tunnel callbacks), see
//           http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
//
// IMPORTANT: Please edit/change the lookup path below for xmlBlasterClientC.dll
//            and set the PATH to access it
//
// @todo     publishOneway crashes
//           OnUpdate() throwing exception seems not to be passed to C
//           logging with log4net
//           write a testsuite
//           write a requirement
//           create an assembly with ant or nant
//           create the same wrapper for the xmlBlaster C++ library
//
// @author   xmlBlaster@marcelruff.info
//
// @prepare Compile the C client library first (see xmlBlaster\src\c\xmlBlasterClientC.sln)
// @compile csc /d:NATIVE_C_MAIN -debug+ -out:PInvokeCE.exe PInvokeCE.cs   (Windows)
//
// @see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
// @c        http://www.xmlBlaster/org
// @see http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfintrointerp.asp?frame=true
// @see http://msdn.microsoft.com/mobility/understanding/articles/default.aspx?pull=/library/en-us/dnnetcomp/html/netcfadvinterop.asp
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
  PInvokeCE -logLevel TRACE -dispatch/connection/plugin/socket/hostname 192.168.2.9
 * 
Preprocessor:
   XMLBLASTER_CLIENT_MONO
   NET_2_0
   WINCE || Smartphone || PocketPC || WindowsCE || FORCE_PINVOKECE
   CF1
   DOTNET2
 * 
*/
using System;
using System.Text;
using System.Runtime.InteropServices;

namespace org.xmlBlaster
{
   /// Calling unmanagegd code: xmlBlasterClientC.dll
   public class PInvokeCE : I_XmlBlasterAccess
   {
      static bool verbose = true; // TODO: log4net

#     if XMLBLASTER_CLIENT_MONO // Linux Debug, set LD_LIBRARY_PATH to find the shared library
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD"; //libxmlBlasterClientCD.so
#     elif WINCE || Smartphone || PocketPC || WindowsCE //|| FORCE_PINVOKECE
      const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD-Arm4.dll";
#     else // Windows
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
            return byteArrayFromIntPtr(content, freeUnmanaged);
         }
         /* Has to be called exactly once if freeUnmanaged==true! */
         public string getKey(bool freeUnmanaged)
         {  // If called never: memory leak, if called twice: double free
            if (key == IntPtr.Zero)
            {
               logger("MsgUnitUnmanagedCE.key is Zero!!!!!!!!!!!!!!");
               return "";
            }
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
            return byteArrayFromIntPtr(key);
         }
         /* Has to be called exactly once! */
         public string getKey()
         {  // If called never: memory leak, if called twice: double free
            if (key == IntPtr.Zero)
            {
               logger("MsgUnitUnmanagedCE.key is Zero!!!!!!!!!!!!!!");
               return "";
            }
            return stringFromUtf8IntPtr(key);
         }
         /* Has to be called exactly once! */
         public string getQos()
         {
            if (qos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(qos);
         }
         /* Has to be called exactly once! */
         public string getResponseQos()
         {
            if (responseQos == IntPtr.Zero) return "";
            return stringFromUtf8IntPtr(key);
         }
      }

      // Helper struct for DLL calls
      unsafe struct XmlBlasterUnmanagedCEException
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

      public struct QosArr
      {
         public int len;  /* Number of XML QoS strings */
         public string[] qosArr;
      }

      public struct MsgUnitUnmanagedCEArr
      {
         public string secretSessionId;
         public int len;
         public MsgUnit[] msgUnitArr;
      }



      [StructLayout(LayoutKind.Sequential/*, CharSet = CharSet.Unicode*/)]
      public class StringArr
      {
         public IntPtr str;
      }

      public void log(String str)
      {
         logger(str);
      }

      public static void logger(String str)
      {
         string prefix = "[C#] ";
         if (str.StartsWith("[")) prefix = "";
         if (verbose) System.Diagnostics.Debug.WriteLine(prefix + str);
         if (verbose) Console.WriteLine(prefix + str);
      }

      // CF1 and CF2 don't support UnmanagedFunctionPointer
      // CallingConvention=CallingConvention.Cdecl supported in .net CF1 and CF2
      // UnmanagedFunctionPointer: new in .net 2.0
#     if DOTNET2
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate void UpdateUnmanagedFp(IntPtr cbSessionId, ref MsgUnitUnmanagedCEpublish msgUnit, ref XmlBlasterUnmanagedCEException exception);

#     if DOTNET2
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate void LoggerUnmanagedFp(int level, IntPtr message);
      /// Callback by xmlBlaster C dll, see LoggerUnmanagedFp
      /// message is freed here by a call to the DLL native free()
      void loggerUnmanaged(int level, IntPtr message)
      {
         string msg = stringFromUtf8IntPtr(message, false);
         logger("Received DLL log #" + level + ": " + msg);
      }


#     if DOTNET2
      [System.Runtime.InteropServices.UnmanagedFunctionPointer(System.Runtime.InteropServices.CallingConvention.Cdecl)]
#     endif
      public delegate int FPtr(int value);

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

      /// Callback by xmlBlaster C dll, see UpdateUnmanagedFp
      /// and XmlBlasterUnmanagedCE.h
      /// typedef void (CALLBACK *XmlBlasterUnmanagedCELoggerFp)(int32_t level, const char *msg);
      /// The cbSessionId_ and msgUnitUnmanaged is freed by the caller
      void updateUnmanaged(IntPtr cbSessionId_, ref MsgUnitUnmanagedCEpublish msgUnitUnmanaged, ref XmlBlasterUnmanagedCEException exception)
      {
         logger("Entering updateUnmanaged()");
         string cbSessionId = stringFromUtf8IntPtr(cbSessionId_, false);
         //fillUnmanagedException(ref exception, "user.internal", "a test exception from C#");

         MsgUnit msgUnit = new MsgUnit(msgUnitUnmanaged.getKey(false),
            msgUnitUnmanaged.getContent(false), msgUnitUnmanaged.getQos(false));
         if (null != onUpdate)
         {
            try
            {
               string ret = onUpdate(cbSessionId, msgUnit);
               logger("Ignoring " + ret);
            }
            // TODO: Exception seems not to reach the C code
            catch (XmlBlasterException e)
            {
               logger("OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, e.ErrorCode, e.Message);
            }
            catch (Exception e)
            {
               logger("OnUpdate() exception: " + e.ToString());
               fillUnmanagedException(ref exception, "user.update.internalError", e.ToString());
            }
            return;
         }
         logger("updateUnmanaged invoked START ==================");
         logger(msgUnit.key);
         logger(msgUnit.getContentStr());
         logger(msgUnit.qos);
         logger("updateUnmanaged invoked DONE ===================");
         //string ret = "<qos><state id='OK'/></qos>";
         //return ret;
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
      unsafe static IntPtr stringToUtf8IntPtr(string str)
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
         byte[] bytes = byteArrayFromIntPtr(ptr, freeUnmanaged);
         return Utf8ByteArrayToString(bytes);
      }

      unsafe static byte[] byteArrayFromIntPtr(IntPtr ptr)
      {
         return byteArrayFromIntPtr(ptr, true);
      }

      unsafe static byte[] byteArrayFromIntPtr(IntPtr ptr, bool freeUnmanaged)
      {
         if (ptr == IntPtr.Zero) return new byte[0];
         //Can't cast type 'System.IntPtr' to 'byte[]'
         //  byte[] tmp = (byte[])stringReturner();
         //so we need to copy it manually:
         void* vPtr = ptr.ToPointer(); // is fixed(...) already
         byte* tmpP = (byte*)vPtr;
         int len = 0;
         for (len = 0; tmpP[len] != 0; len++) ;
         byte[] tmp = new byte[len];
         for (int i = 0; i < tmp.Length; i++)
            tmp[i] = tmpP[i];
         // Now free() the malloc() IntPtr in the C DLL ...
         if (freeUnmanaged) xmlBlasterUnmanagedCEFree(ptr);
         return tmp;
      }

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHello();

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloLPCT(string p);


      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloP(int size, IntPtr p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void sayHelloEx(ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr sayHelloRet();
      //private extern static byte[] sayHelloRet(); // throws NotSupportedException on WINCE

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static IntPtr xmlBlasterUnmanagedCEMalloc(int size);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEFree(IntPtr p);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCERegisterLogger(ref IntPtr xa, IntPtr loggerUnmanaged);

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
      //private extern static QosArr xmlBlasterUnmanagedCEPublishArr(IntPtr xa, MsgUnitArr msgUnitArr, ref XmlBlasterUnmanagedCEException exception);

      [DllImport(XMLBLASTER_C_LIBRARY)]
      private extern static void xmlBlasterUnmanagedCEPublishOneway(IntPtr xa, ref MsgUnit[] msgUnitArr, int length, ref XmlBlasterUnmanagedCEException exception);

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
      private extern static string xmlBlasterUnmanagedCEUsage();

      private IntPtr xa;
      private UpdateUnmanagedFp updateUnmanagedFp;
      private LoggerUnmanagedFp loggerUnmanagedFp;
      private bool isGCHandled = false;
      private GCHandle gch;
      private IntPtr updateFpForDelegate;
      private IntPtr loggerFpForDelegate;

      // Callback Test code only:
      // From http://msdn2.microsoft.com/en-us/library/5zwkzwf4.aspx
      public class LibWrap
      {// Declares managed prototypes for unmanaged functions.
         // System.Runtime.InteropServices: CallingConvention
         // On Windows CE CF1 or CF2 it defaults to Cdecl (on Windows it is StdCall)
         [DllImport(XMLBLASTER_C_LIBRARY, CallingConvention = CallingConvention.Cdecl)]
         //[DllImport(XMLBLASTER_C_LIBRARY)]
         //public static extern void TestCallBack(FPtr cb, int value);
         public static extern void TestCallBack(IntPtr cb, int value);
      }
      public static int DoSomething(int value)
      {
         Console.WriteLine("\n[C#]: Callback called with param: {0}", value);
         return 66;
      }
      // Nice example how to pass a pointer to a managed object to DLL
      // and on callback cast it again to the managed object
      //http://msdn2.microsoft.com/en-us/library/44ey4b32.aspx

      unsafe public PInvokeCE(string[] argv)
      {
         logger("Entering PInvokeCE() C++ CF=" + System.Environment.Version.ToString() + " OS=" +
            System.Environment.OSVersion.ToString() + "....");
         FPtr cb = new FPtr(DoSomething);
         logger("Testing callback GetFunctionPointerForDelegate()");
         IntPtr cbForDelegate = Marshal.GetFunctionPointerForDelegate(cb);
         logger("Testing callback DoSomething()");
         LibWrap.TestCallBack(cbForDelegate, 99);
         logger("Done testing callback DoSomething()");

         if (argv == null) argv = new String[0];

         // See http://msdn2.microsoft.com/en-us/library/ss9sb93t.aspx
         // Samples: http://msdn2.microsoft.com/en-us/library/fzhhdwae.aspx
         updateUnmanagedFp = new UpdateUnmanagedFp(this.updateUnmanaged);
         // prevents the managed object from being collected
         gch = GCHandle.Alloc(updateUnmanagedFp);
         isGCHandled = true;
         updateFpForDelegate = Marshal.GetFunctionPointerForDelegate(updateUnmanagedFp);
         logger("All updateUnmanagedFp done");


         // Convert command line arguments: C client lib expects the executable name as first entry
         IntPtr[] c_argv = new IntPtr[argv.Length + 1];
         c_argv[0] = stringToUtf8IntPtr("PInvokeCE"); // TODO: my executable name
         for (int i = 0; i < argv.Length; ++i)
         {
            if (argv[i] == "--help")
            {
               logger("Usage:\n" + xmlBlasterUnmanagedCEUsage());
               throw new XmlBlasterException("user.usage", "Good bye");
            }
            c_argv[i + 1] = stringToUtf8IntPtr(argv[i]);
         }

         // Frees not c_argv (as it crashed)
         xa = getXmlBlasterAccessUnparsedUnmanagedCE(c_argv.Length, c_argv);
         for (int i = 0; i < c_argv.Length; ++i)
            xmlBlasterUnmanagedCEFree(c_argv[i]);

         Console.WriteLine("Hit a key to register logger ...");
         Console.ReadLine();

         loggerUnmanagedFp = new LoggerUnmanagedFp(this.loggerUnmanaged);
         loggerFpForDelegate = Marshal.GetFunctionPointerForDelegate(loggerUnmanagedFp);
         xmlBlasterUnmanagedCERegisterLogger(ref xa, loggerFpForDelegate);

         {
            /*
            sayHello();
            sayHelloLPCT("Mein LPCTSTR");
            string txt = "Text transfer";
            sayHelloP(txt.Length, StringToUtf8ByteArray(txt));

            logger("Before sayHelloRet() ...");
            string ret = stringFromUtf8IntPtr(sayHelloRet());
            logger("sayHelloRet(): " + ret);

             [DllImport("TheirDLL.dll", SetLastError=true)]
            public static extern void TheirDllFunc( byte[] Path );

            string sPath = @"C:\MyFolder\MySubFolder\";
            ASCIIEncoding ascii = new ASCIIEncoding();
            byte[] byPath = ascii.GetBytes( sPath );
            TheirDllFunc( byPath );

            The external function's signature is:
                    void TheirDllFunc( char *path); 
            logger("PInvokeCE() ...");
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            logger("calling now sayHelloEx()");
            sayHelloEx(ref exception);
            if (exception.CaughtException())
            {
               XmlBlasterException e = fillXmlBlasterException(ref exception);
               logger("Got exception: " + e.ToString());
            }
            */
         }
      }

      ~PInvokeCE()
      {
         logger("TODO: GCHandle.Free(updateUnmanagedFp)!!!");
         if (isGCHandled)
            gch.Free();
         if (xa != new IntPtr(0))
            freeXmlBlasterAccessUnparsedUnmanagedCE(xa);
         logger("~PInvokeCE() ...");
      }

      void check(string methodName)
      {
         logger(methodName + "() check ...");
         if (xa == new IntPtr(0))
            throw new XmlBlasterException("internal.illegalState", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of PInvokeCE.cs class after a disconnect() call");
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
               bool bb = xmlBlasterUnmanagedCEInitialize(xa, IntPtr.Zero, ref exception);
#           else // Compact Framework 2
            logger("Calling now CF2 initialize");
            bool bb = xmlBlasterUnmanagedCEInitialize(xa, updateFpForDelegate, ref exception);
            logger("Calling now CF2 done");
#           endif
            checkAndThrow("xmlBlasterUnmanagedCEInitialize", ref exception);

#           if CF1 // Compact Framework .net 1.0
               IntPtr retP = xmlBlasterUnmanagedCEConnect(xa, stringToUtf8IntPtr(qos), IntPtr.Zero, ref exception);
#           else // Compact Framework 2
            IntPtr retP = xmlBlasterUnmanagedCEConnect(xa, stringToUtf8IntPtr(qos), updateFpForDelegate, ref exception);
#           endif
            checkAndThrow("xmlBlasterUnmanagedCEConnect", ref exception);
            string ret = stringFromUtf8IntPtr(retP);
            //logger("xmlBlasterUnmanagedCEConnect: SUCCESS '" + ret + "'");
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
               logger("xmlBlasterUnmanagedCEDisconnect: Got exception from C: " + ex.ToString());
            }

            freeXmlBlasterAccessUnparsedUnmanagedCE(xa);
            xa = IntPtr.Zero;
            logger("xmlBlasterUnmanagedCEDisconnect: SUCCESS freed all resources");

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
            logger(location + ": Got exception from C: " + e.ToString());
            throw e;
         }
         logger(location + ": SUCCESS");
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
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            xmlBlasterUnmanagedCEPublishOneway(xa, ref msgUnitArr, msgUnitArr.Length, ref exception);
            checkAndThrow("xmlBlasterUnmanagedCEPublishOneway", ref exception);
            logger("publishOneway: SUCCESS");
         }
         catch (XmlBlasterException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new XmlBlasterException("internal.unknown", "publishOneway failed", e);
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
            //logger(ret);
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
               logger("xmlBlasterUnmanagedCEUnSubcribe: Done (no topics found)");
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
               logger("xmlBlasterUnmanagedCEErase: Done (no topics found)");
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
            //logger("xmlBlasterUnmanagedCEErase: FREEING WRONG POINTER??");
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

#if DEMOCODE
      // -> Not in CF!!!
		Marshal.AllocCoTaskMem(iStructSize*10);

      //The following example demonstrates how to marshal an unmanaged blovk
      //of memory to a managed structure using the PtrToStructure method
      [StructLayout(LayoutKind.Sequential)]
		public class  INNER
		{
			[MarshalAs(UnmanagedType.ByValTStr, SizeConst =  10)]
			public string field1 = "Test";
		}	

		[StructLayout(LayoutKind.Sequential)]
		public struct OUTER
		{
			[MarshalAs(UnmanagedType.ByValTStr, SizeConst =  10)]
			public string field1;
			[MarshalAs(UnmanagedType.ByValArray, SizeConst =  100)]
			public byte[] inner;
		}

		[DllImport(@"SomeTestDLL.dll")]
		public static extern void CallTest( ref OUTER po);

		static void Main(string[] args)
		{
			OUTER ed = new OUTER();
			INNER[] inn=new INNER[10];
			INNER test = new INNER();
			int iStructSize = Marshal.SizeOf(test);
			int sz =inn.Length * iStructSize;
			ed.inner = new byte[sz];
			try
			{
				CallTest( ref ed);
			}
			catch(Exception e)
			{
				Console.WriteLine(e.Message);
			}

			IntPtr buffer = Marshal.AllocCoTaskMem(iStructSize*10);
			Marshal.Copy(ed.inner,0,buffer,iStructSize*10);
			int iCurOffset = 0;
			for(int i=0;i<10;i++)
			{
				inn[i] = (INNER)Marshal.PtrToStructure(new
IntPtr(buffer.ToInt32()+iCurOffset),typeof(INNER) );
				iCurOffset += iStructSize;
			}
			Console.WriteLine(ed.field1);
			Marshal.FreeCoTaskMem(buffer);

		}
#endif
      public MsgUnit[] get(string key, string qos)
      {
         check("get");
         try
         {
            XmlBlasterUnmanagedCEException exception = new XmlBlasterUnmanagedCEException(false);
            int size;
            IntPtr outArray = new IntPtr();
            xmlBlasterUnmanagedCEGet(xa, stringToUtf8IntPtr(key), stringToUtf8IntPtr(qos), ref exception, out size, out outArray);
            checkAndThrow("xmlBlasterUnmanagedCEGet", ref exception);
            MsgUnit[] msgUnitArr = new MsgUnit[size];
            IntPtr current = outArray;
            for (int i = 0; i < size; i++)
            {
               logger("xmlBlasterUnmanagedCEGet: parsing #" + i);
               MsgUnitUnmanagedCEget msgUnitUnmanaged = new MsgUnitUnmanagedCEget();
               Marshal.PtrToStructure(current, msgUnitUnmanaged);
               current = (IntPtr)((long)current + Marshal.SizeOf(msgUnitUnmanaged));
               // The getters free the memory in the C DLL:
               msgUnitArr[i] = new MsgUnit(
                  msgUnitUnmanaged.getKey(), msgUnitUnmanaged.getContent(), msgUnitUnmanaged.getQos());
               msgUnitUnmanaged.getResponseQos(); // dummy call to free memory in C DLL
            }
            //logger("xmlBlasterUnmanagedCEGet: freeing now IntPtr SHOULD IT BE *outArray???");
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
         if (xa == new IntPtr(0)) return false;
         try
         {
            bool bb = xmlBlasterUnmanagedCEIsConnected(xa);
            //logger("xmlBlasterUnmanagedCEIsConnected: SUCCESS '" + bb + "'");
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

      public static string usage()
      {
         return xmlBlasterUnmanagedCEUsage();
      }
   }
}
