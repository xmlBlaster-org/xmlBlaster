package org.xmlBlaster.contrib;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

// import java.util.concurrent.ArrayBlockingQueue;
// import java.util.concurrent.TimeUnit;
import EDU.oswego.cs.dl.util.concurrent.BoundedBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;


/**
 * 
 * AppTerm is an application which invokes a java class main method and redirects the System.in and
 * System.out to a graphical window. XmlBlaster colorful logging are supported.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class AppTerm extends JFrame implements KeyListener {

   public static final String END = "\033[0m";
   public static final String ESC = "\033[";
   
   private final static Color[] COLORS = new Color[] { Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.PINK, Color.LIGHT_GRAY, Color.WHITE};
   
   Logger log = Logger.getLogger(AppTerm.class.getName());
   
   public class AsyncWriter implements Runnable {
      private Document doc;
      private int maxSize;
      private int sizeToFlush;
      private String txt;
      private MutableAttributeSet attr;
      private JTextPane pane;
      
      public AsyncWriter(String txt, JTextPane pane, Document doc, int maxSize, int sizeToFlush, MutableAttributeSet attr) {
         this.pane = pane;
         this.doc = doc;
         this.maxSize = maxSize;
         this.txt = txt;
         this.attr = attr;
         this.sizeToFlush = sizeToFlush;
      }
      
      public void run() {
         try {
            int end = this.doc.getLength() - 1;
            if (end < 0)
               end = 0;
            this.doc.insertString(end, txt, attr);
            end = this.doc.getLength();
            if (end > this.maxSize) {
               this.doc.remove(0, this.sizeToFlush);
            }
            end = this.doc.getLength() - 1;
            if (end < 0)
               end = 0;
            this.pane.setCaretPosition(end);
         }
         catch (BadLocationException ex) {
            ex.printStackTrace();
         }
      }
   }
   
   public class PanelStream extends OutputStream {

      StringBuffer buf = new StringBuffer(128);
      private Document doc;
      private JTextPane pane;
      MutableAttributeSet attr;
      boolean inStyle;
      Map styles = new HashMap();
      int maxSize;
      int sizeToFlush;
      
      public PanelStream(JTextPane pane, int maxSize) {
         this.pane = pane;
         this.doc = pane.getDocument();
         this.maxSize = maxSize;
         this.sizeToFlush = (int)(0.8 * maxSize);
      }

      private Color getColor(String txt, int offset) {
         try {
            int col = Integer.parseInt(txt) - offset;
            if (col < 0 || col > 7)
               return null;
            return COLORS[col];
         }
         catch (Throwable ex) {
            return null;
         }
      }

      private MutableAttributeSet registerNewStyle(String token) {
         String tmp1 = token.substring(2, 4);
         String tmp2 = token.substring(5, 7);

         Color foreground = getColor(tmp1, 30);
         Color background = getColor(tmp2, 40);
         if (foreground == null)
            foreground = Color.WHITE;
         if (background == null)
            background = Color.BLACK;
         
         MutableAttributeSet newAttr = new SimpleAttributeSet();
         StyleConstants.setForeground(newAttr, foreground);
         StyleConstants.setBackground(newAttr, background);
         // StyleConstants.setBold(newAttr, true);
         return newAttr;
      }
      
      private void setStyle(String token) {
         this.attr = (MutableAttributeSet)this.styles.get(token);
         if (this.attr == null)
            this.attr = registerNewStyle(token);
         this.inStyle = true;
      }

      private final void inNormalText(String in) {
         int pos = in.indexOf(ESC);
         if (pos > -1) {
            String pre = in.substring(0, pos);
            writeToDoc(pre);
            String token = in.substring(pos, pos + 8);
            in = in.substring(pos + 8);
            setStyle(token);
            filter(in);
         }
         else
            writeToDoc(in);
      }
      
      private final void inStyledText(String in) {
         int pos = in.indexOf(END);
         if (pos > -1) {
            String pre = in.substring(0, pos);
            writeToDoc(pre);
            in = in.substring(pos + 4);
            this.attr = null;
            this.inStyle = false;
            filter(in);
         }
         else
            writeToDoc(in);
      }
      
      
      private final void filter(String in) {
         if (in == null || in.length() < 1)
            return;
         if (this.inStyle)
            inStyledText(in);
         else
            inNormalText(in);
      }
      
      private final void writeToDoc(String txt) {
         if (txt == null)
            return;
         EventQueue.invokeLater(new AsyncWriter(txt, pane, this.doc, this.maxSize, this.sizeToFlush, this.attr));
      }
      
      public void write(int b) throws IOException {
         this.buf.append((char)b);
         if (b == 10) {
            filter(this.buf.toString());
            this.buf = new StringBuffer(128);
         }
      }
   }

   public class PanelInputStream extends InputStream {
      
      private BoundedBuffer queue;
      // private ArrayBlockingQueue queue;

      public PanelInputStream(int maxSize) {
         // boolean orderedFIFO = true;
         // this.queue = new ArrayBlockingQueue(maxSize, orderedFIFO);
         this.queue = new BoundedBuffer(maxSize);
      }
      
      public void write(int val) {
         try {
            this.queue.put(new Integer(val));
            // hack to force the read in readLine to break. Otherwise readLine will 
            // not return. This has not been tested on windows.
            if (val == 10)
               this.queue.put(new Integer(-1));
         }
         catch (InterruptedException ex) {
            ex.printStackTrace();
         }
      }
      
      public int read() throws IOException {
         Object obj = null;
         try {
            while (obj == null) {
               obj = this.queue.poll(5L /*, TimeUnit.SECONDS*/);
            }
         }
         catch (InterruptedException ex) {
            throw new IOException("Interrupted Exception occured: " + ex.getMessage());
         }
         int val = ((Integer)obj).intValue();
         return val;
      }
      
      /**
       * This is not really needed but may help for performance.
       */
      public int available() throws IOException {
         return this.queue.size();
      }
   }
   
   
  /**
    * 
    */
   private static final long serialVersionUID = 1L;

   private JTextPane pane;
   PanelInputStream panelInputStream = new PanelInputStream(2048);
   
   public AppTerm() {
      super("Horizontal Box Test Frame");
   }
   
   private int getInt(String val, int def) {
      try {
         return Integer.parseInt(val);
      }
      catch (Throwable ex) {
         log.warning("could not parse value '" + val + "' since not an integer. Will take its default '" + def + "'");
         return def;
      }
   }

   private void setFont() {
      String fontProp = System.getProperty("font");
      if (fontProp != null) {
         StringTokenizer tokenizer = new StringTokenizer(fontProp.trim(), ",");
         if (!tokenizer.hasMoreElements())
            return;
         String fontName = tokenizer.nextToken().trim();
         if (!tokenizer.hasMoreElements())
            return;
         try {
            int size = Integer.parseInt(tokenizer.nextToken().trim());
            Font newFont = new Font(fontName, Font.PLAIN, size);
            this.pane.setFont(newFont);
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }
   }
   
   public void init(String[] args) {
      setBounds(getBounds(args));
      JPanel box = new JPanel();
      // Use BoxLayout.Y_AXIS below if you want a vertical box
      box.setLayout(new GridLayout(1,1)); 
      setContentPane(box);
      this.pane = new JTextPane();
      this.pane.setBackground(getColor(args));
      JScrollPane pane = new JScrollPane(this.pane);
      // this.pane.setBorder(BorderFactory.createRaisedBevelBorder());
      this.pane.setAutoscrolls(true);
      
      // this.pane.setLineWrap(true);
      String name = args[0];
      this.setTitle(name);

      setFont();
      this.pane.setName(name);
      this.pane.setToolTipText(name);
      box.add(pane);
      // box.add(this.pane);
      
      setDefaultCloseOperation(EXIT_ON_CLOSE);
      setVisible(true);
      
      PanelStream panelStream = new PanelStream(this.pane, 1024*1024);
      System.setErr(new PrintStream(panelStream));
      System.setOut(new PrintStream(panelStream));
      System.setIn(this.panelInputStream);
      this.pane.addKeyListener(this);
      
      loadAppl(args);
      System.out.println("END OF INIT");
   }

   private Rectangle getBounds(String[] args) {
      if (args.length < 5)
         return new Rectangle(0, 0, 800, 600);
      return new Rectangle(getInt(args[1], 0), getInt(args[2], 0), getInt(args[3], 800), getInt(args[4], 600));
   }
   
   private Color getColor(String[] args) {
      try {
         return Color.decode(args[5]);
      }
      catch (Throwable ex) {
         log.severe("Error when trying to decode the color '" + args[5] + "'");
         return Color.WHITE;
      }
   }
   
   private void loadAppl(String[] args) {
      try {
         String className = args[6];
         // URLClassLoader urlClassLoader = URLClassLoader.newInstance(null, ClassLoader.getSystemClassLoader());
         ClassLoader classLoader = ClassLoader.getSystemClassLoader();
         Class clazz = classLoader.loadClass(className);
         Object obj = clazz.newInstance();
         Method main = clazz.getDeclaredMethod("main", new Class[] {String[].class });
         String[] vals = stripArgs(args);
         main.invoke(obj, new Object[] { vals });
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   
   private String[] stripArgs(String[] args) {
      int offs = 7;
      if (args.length < (offs+1))
         return new String[0];
      int nmax = args.length -offs;
      String[] ret = new String[nmax];
      for (int i=0; i < ret.length; i++)
         ret[i] = args[i+offs];
      return ret;
   }
   
   public void keyPressed(KeyEvent e) {
      char c = e.getKeyChar();
      this.panelInputStream.write(c);
   }

   public void keyReleased(KeyEvent e) {
   }

   public void keyTyped(KeyEvent e) {
   }

   // java org.xmlBlaster.contrib.AppTerm XMLBLASTER 0 100 1500 500 192192192 org.xmlBlaster.Main
   public static void main(String args[]) {

      if (args.length < 1) {
         System.err.println("usage: " + AppTerm.class.getName() + " title x0 y0 width height color className [args]");
         System.err.println("Example: java org.xmlBlaster.contrib.AppTerm XMLBLASTER 0 100 1500 500 192192192 org.xmlBlaster.Main");
         Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
         System.err.println("Fonts recognized by this system: ");
         for (int i=0; i < fonts.length; i++)
            System.err.println(fonts[i].getName());
         System.exit(-1);
      }
      try {
         AppTerm bt = new AppTerm();
         bt.init(args);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}
