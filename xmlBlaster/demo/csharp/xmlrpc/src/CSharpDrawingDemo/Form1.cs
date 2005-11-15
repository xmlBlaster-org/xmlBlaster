using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Collections;
//using System.ComponentModel;
using System.Windows.Forms;
//using System.Data;
using System.Diagnostics ;
using System.Text ;
using System.Xml ;

using XmlBlaster ;

namespace xmlrpc
{
	/// <summary>
	/// Description résumée de Form1.
	/// </summary>
	public class Form1 : System.Windows.Forms.Form
	{
		#region Code généré par le Concepteur Windows Form

		private System.Windows.Forms.GroupBox groupBox1;
		private System.Windows.Forms.Label label2;
		private System.Windows.Forms.TextBox server_url;
		private System.Windows.Forms.TextBox username;
		private System.Windows.Forms.Label label3;
		private System.Windows.Forms.TextBox password;
		private System.Windows.Forms.Label label4;
		private System.Windows.Forms.RichTextBox report;
		private System.Windows.Forms.Button connect;
		private System.Windows.Forms.PictureBox pictureBox1;
		private System.Windows.Forms.GroupBox groupBox2;
		private System.Windows.Forms.ImageList imageList;
		private System.Windows.Forms.Label label5;
		private System.Windows.Forms.PictureBox pictureBox2;
		private System.Windows.Forms.Button toolColor;
		private System.Windows.Forms.RadioButton toolEllipse;
		private System.Windows.Forms.CheckBox toolFill;
		private System.Windows.Forms.Button toolErase;
		private System.Windows.Forms.RadioButton toolTriangle;
		private System.Windows.Forms.RadioButton toolRectangle;
		private System.Windows.Forms.RadioButton toolLine;
		private System.Windows.Forms.ColorDialog colorDialog1;
		private System.ComponentModel.IContainer components;

		/// <summary>
		/// Méthode requise pour la prise en charge du concepteur - ne modifiez pas
		/// le contenu de cette méthode avec l'éditeur de code.
		/// </summary>
		private void InitializeComponent()
		{
			this.components = new System.ComponentModel.Container();
			System.Resources.ResourceManager resources = new System.Resources.ResourceManager(typeof(Form1));
			this.groupBox1 = new System.Windows.Forms.GroupBox();
			this.pictureBox2 = new System.Windows.Forms.PictureBox();
			this.connect = new System.Windows.Forms.Button();
			this.password = new System.Windows.Forms.TextBox();
			this.label4 = new System.Windows.Forms.Label();
			this.username = new System.Windows.Forms.TextBox();
			this.label3 = new System.Windows.Forms.Label();
			this.server_url = new System.Windows.Forms.TextBox();
			this.label2 = new System.Windows.Forms.Label();
			this.report = new System.Windows.Forms.RichTextBox();
			this.pictureBox1 = new System.Windows.Forms.PictureBox();
			this.groupBox2 = new System.Windows.Forms.GroupBox();
			this.toolColor = new System.Windows.Forms.Button();
			this.label5 = new System.Windows.Forms.Label();
			this.imageList = new System.Windows.Forms.ImageList(this.components);
			this.toolEllipse = new System.Windows.Forms.RadioButton();
			this.toolFill = new System.Windows.Forms.CheckBox();
			this.toolErase = new System.Windows.Forms.Button();
			this.toolTriangle = new System.Windows.Forms.RadioButton();
			this.toolRectangle = new System.Windows.Forms.RadioButton();
			this.toolLine = new System.Windows.Forms.RadioButton();
			this.colorDialog1 = new System.Windows.Forms.ColorDialog();
			this.groupBox1.SuspendLayout();
			this.groupBox2.SuspendLayout();
			this.SuspendLayout();
			// 
			// groupBox1
			// 
			this.groupBox1.Controls.Add(this.pictureBox2);
			this.groupBox1.Controls.Add(this.connect);
			this.groupBox1.Controls.Add(this.password);
			this.groupBox1.Controls.Add(this.label4);
			this.groupBox1.Controls.Add(this.username);
			this.groupBox1.Controls.Add(this.label3);
			this.groupBox1.Controls.Add(this.server_url);
			this.groupBox1.Controls.Add(this.label2);
			this.groupBox1.Dock = System.Windows.Forms.DockStyle.Top;
			this.groupBox1.Location = new System.Drawing.Point(0, 0);
			this.groupBox1.Name = "groupBox1";
			this.groupBox1.Size = new System.Drawing.Size(488, 76);
			this.groupBox1.TabIndex = 1;
			this.groupBox1.TabStop = false;
			this.groupBox1.Text = "connection";
			// 
			// pictureBox2
			// 
			this.pictureBox2.Image = ((System.Drawing.Image)(resources.GetObject("pictureBox2.Image")));
			this.pictureBox2.Location = new System.Drawing.Point(424, 12);
			this.pictureBox2.Name = "pictureBox2";
			this.pictureBox2.Size = new System.Drawing.Size(56, 52);
			this.pictureBox2.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage;
			this.pictureBox2.TabIndex = 8;
			this.pictureBox2.TabStop = false;
			// 
			// connect
			// 
			this.connect.Location = new System.Drawing.Point(348, 44);
			this.connect.Name = "connect";
			this.connect.Size = new System.Drawing.Size(64, 24);
			this.connect.TabIndex = 6;
			this.connect.Text = "connect";
			this.connect.Click += new System.EventHandler(this.connect_Click);
			// 
			// password
			// 
			this.password.Location = new System.Drawing.Point(208, 48);
			this.password.Name = "password";
			this.password.Size = new System.Drawing.Size(88, 20);
			this.password.TabIndex = 5;
			this.password.Text = "password";
			// 
			// label4
			// 
			this.label4.AutoSize = true;
			this.label4.Location = new System.Drawing.Point(160, 48);
			this.label4.Name = "label4";
			this.label4.Size = new System.Drawing.Size(47, 16);
			this.label4.TabIndex = 4;
			this.label4.Text = "pasword";
			// 
			// username
			// 
			this.username.Location = new System.Drawing.Point(64, 48);
			this.username.Name = "username";
			this.username.Size = new System.Drawing.Size(88, 20);
			this.username.TabIndex = 3;
			this.username.Text = "username";
			// 
			// label3
			// 
			this.label3.AutoSize = true;
			this.label3.Location = new System.Drawing.Point(8, 48);
			this.label3.Name = "label3";
			this.label3.Size = new System.Drawing.Size(55, 16);
			this.label3.TabIndex = 2;
			this.label3.Text = "username";
			// 
			// server_url
			// 
			this.server_url.Location = new System.Drawing.Point(64, 20);
			this.server_url.Name = "server_url";
			this.server_url.Size = new System.Drawing.Size(348, 20);
			this.server_url.TabIndex = 1;
			this.server_url.Text = "server_url";
			// 
			// label2
			// 
			this.label2.AutoSize = true;
			this.label2.Location = new System.Drawing.Point(8, 20);
			this.label2.Name = "label2";
			this.label2.Size = new System.Drawing.Size(51, 16);
			this.label2.TabIndex = 0;
			this.label2.Text = "server url";
			// 
			// report
			// 
			this.report.Dock = System.Windows.Forms.DockStyle.Bottom;
			this.report.Location = new System.Drawing.Point(0, 333);
			this.report.Name = "report";
			this.report.ReadOnly = true;
			this.report.Size = new System.Drawing.Size(488, 72);
			this.report.TabIndex = 2;
			this.report.Text = "report";
			// 
			// pictureBox1
			// 
			this.pictureBox1.Anchor = ((System.Windows.Forms.AnchorStyles)((((System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Bottom) 
				| System.Windows.Forms.AnchorStyles.Left) 
				| System.Windows.Forms.AnchorStyles.Right)));
			this.pictureBox1.BackColor = System.Drawing.Color.White;
			this.pictureBox1.Location = new System.Drawing.Point(60, 80);
			this.pictureBox1.Name = "pictureBox1";
			this.pictureBox1.Size = new System.Drawing.Size(424, 248);
			this.pictureBox1.TabIndex = 3;
			this.pictureBox1.TabStop = false;
			this.pictureBox1.Resize += new System.EventHandler(this.pictureBox1_Resize);
			this.pictureBox1.Paint += new System.Windows.Forms.PaintEventHandler(this.pictureBox1_Paint);
			this.pictureBox1.MouseMove += new System.Windows.Forms.MouseEventHandler(this.pictureBox1_MouseMove);
			this.pictureBox1.MouseDown += new System.Windows.Forms.MouseEventHandler(this.pictureBox1_MouseDown);
			// 
			// groupBox2
			// 
			this.groupBox2.Controls.Add(this.toolColor);
			this.groupBox2.Controls.Add(this.label5);
			this.groupBox2.Controls.Add(this.toolEllipse);
			this.groupBox2.Controls.Add(this.toolFill);
			this.groupBox2.Controls.Add(this.toolErase);
			this.groupBox2.Controls.Add(this.toolTriangle);
			this.groupBox2.Controls.Add(this.toolRectangle);
			this.groupBox2.Controls.Add(this.toolLine);
			this.groupBox2.Dock = System.Windows.Forms.DockStyle.Left;
			this.groupBox2.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.groupBox2.Location = new System.Drawing.Point(0, 76);
			this.groupBox2.Name = "groupBox2";
			this.groupBox2.Size = new System.Drawing.Size(60, 257);
			this.groupBox2.TabIndex = 16;
			this.groupBox2.TabStop = false;
			this.groupBox2.Text = "tools";
			// 
			// toolColor
			// 
			this.toolColor.BackColor = System.Drawing.Color.Black;
			this.toolColor.FlatStyle = System.Windows.Forms.FlatStyle.Flat;
			this.toolColor.Location = new System.Drawing.Point(28, 192);
			this.toolColor.Name = "toolColor";
			this.toolColor.Size = new System.Drawing.Size(20, 20);
			this.toolColor.TabIndex = 22;
			this.toolColor.Click += new System.EventHandler(this.toolColor_Click);
			// 
			// label5
			// 
			this.label5.ImageIndex = 7;
			this.label5.ImageList = this.imageList;
			this.label5.Location = new System.Drawing.Point(24, 188);
			this.label5.Name = "label5";
			this.label5.Size = new System.Drawing.Size(27, 28);
			this.label5.TabIndex = 23;
			// 
			// imageList
			// 
			this.imageList.ColorDepth = System.Windows.Forms.ColorDepth.Depth32Bit;
			this.imageList.ImageSize = new System.Drawing.Size(27, 28);
			this.imageList.ImageStream = ((System.Windows.Forms.ImageListStreamer)(resources.GetObject("imageList.ImageStream")));
			this.imageList.TransparentColor = System.Drawing.Color.Empty;
			// 
			// toolEllipse
			// 
			this.toolEllipse.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.toolEllipse.ImageIndex = 2;
			this.toolEllipse.ImageList = this.imageList;
			this.toolEllipse.Location = new System.Drawing.Point(8, 84);
			this.toolEllipse.Name = "toolEllipse";
			this.toolEllipse.Size = new System.Drawing.Size(48, 32);
			this.toolEllipse.TabIndex = 21;
			this.toolEllipse.CheckedChanged += new System.EventHandler(this.radioButtonX_CheckedChanged);
			// 
			// toolFill
			// 
			this.toolFill.BackColor = System.Drawing.Color.Transparent;
			this.toolFill.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.toolFill.ImageIndex = 5;
			this.toolFill.ImageList = this.imageList;
			this.toolFill.Location = new System.Drawing.Point(8, 152);
			this.toolFill.Name = "toolFill";
			this.toolFill.Size = new System.Drawing.Size(48, 32);
			this.toolFill.TabIndex = 20;
			this.toolFill.CheckedChanged += new System.EventHandler(this.radioButtonX_CheckedChanged);
			// 
			// toolErase
			// 
			this.toolErase.FlatStyle = System.Windows.Forms.FlatStyle.Flat;
			this.toolErase.ForeColor = System.Drawing.SystemColors.Control;
			this.toolErase.ImageIndex = 6;
			this.toolErase.ImageList = this.imageList;
			this.toolErase.Location = new System.Drawing.Point(20, 216);
			this.toolErase.Name = "toolErase";
			this.toolErase.Size = new System.Drawing.Size(32, 32);
			this.toolErase.TabIndex = 19;
			this.toolErase.Click += new System.EventHandler(this.toolErase_Click);
			// 
			// toolTriangle
			// 
			this.toolTriangle.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.toolTriangle.ImageIndex = 3;
			this.toolTriangle.ImageList = this.imageList;
			this.toolTriangle.Location = new System.Drawing.Point(8, 120);
			this.toolTriangle.Name = "toolTriangle";
			this.toolTriangle.Size = new System.Drawing.Size(48, 32);
			this.toolTriangle.TabIndex = 18;
			this.toolTriangle.CheckedChanged += new System.EventHandler(this.radioButtonX_CheckedChanged);
			// 
			// toolRectangle
			// 
			this.toolRectangle.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.toolRectangle.ImageIndex = 1;
			this.toolRectangle.ImageList = this.imageList;
			this.toolRectangle.Location = new System.Drawing.Point(8, 52);
			this.toolRectangle.Name = "toolRectangle";
			this.toolRectangle.Size = new System.Drawing.Size(48, 32);
			this.toolRectangle.TabIndex = 17;
			this.toolRectangle.CheckedChanged += new System.EventHandler(this.radioButtonX_CheckedChanged);
			// 
			// toolLine
			// 
			this.toolLine.BackColor = System.Drawing.SystemColors.Control;
			this.toolLine.Checked = true;
			this.toolLine.FlatStyle = System.Windows.Forms.FlatStyle.Popup;
			this.toolLine.ImageIndex = 0;
			this.toolLine.ImageList = this.imageList;
			this.toolLine.Location = new System.Drawing.Point(8, 20);
			this.toolLine.Name = "toolLine";
			this.toolLine.Size = new System.Drawing.Size(48, 32);
			this.toolLine.TabIndex = 16;
			this.toolLine.TabStop = true;
			this.toolLine.CheckedChanged += new System.EventHandler(this.radioButtonX_CheckedChanged);
			// 
			// Form1
			// 
			this.AutoScaleBaseSize = new System.Drawing.Size(5, 13);
			this.BackColor = System.Drawing.SystemColors.Control;
			this.ClientSize = new System.Drawing.Size(488, 405);
			this.Controls.Add(this.groupBox2);
			this.Controls.Add(this.pictureBox1);
			this.Controls.Add(this.report);
			this.Controls.Add(this.groupBox1);
			this.MinimumSize = new System.Drawing.Size(496, 432);
			this.Name = "Form1";
			this.Text = "XmlBlaster C# demo using XML-RPC";
			this.Closing += new System.ComponentModel.CancelEventHandler(this.Form1_Closing);
			this.Load += new System.EventHandler(this.Form1_Load);
			this.groupBox1.ResumeLayout(false);
			this.groupBox2.ResumeLayout(false);
			this.ResumeLayout(false);

		}
		#endregion

		public Form1()
		{
			//
			// Requis pour la prise en charge du Concepteur Windows Forms
			//
			InitializeComponent();

		}

		/// <summary>
		/// Nettoyage des ressources utilisées.
		/// </summary>
		protected override void Dispose( bool disposing )
		{
			if( disposing )
			{
				if (components != null) 
				{
					components.Dispose();
				}
			}
			base.Dispose( disposing );
		}

		/// <summary>
		/// Point d'entrée principal de l'application.
		/// </summary>
		[STAThread]
		static void Main() 
		{
			Application.EnableVisualStyles();
			Application.Run(new Form1());
		}


		static readonly string EOL = Environment.NewLine ;

		XmlBlasterClient xmlBlaster ;

		void HandleException( Exception ex )
		{
			printError( ex.Message );
			printError( ex.StackTrace );
			//MessageBox.Show("Error: " + ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
		}


		public void printLog( string msg )
		{
			string s = "[LOG] " + msg ;
			this.report.AppendText( s + EOL );
			Debug.WriteLine( s );
		}
		public void printError( string msg )
		{

			// Change some text properties

			Color fc = this.report.SelectionColor ;
			Font currentFont = this.report.SelectionFont ;
			FontStyle newFontStyle;
			newFontStyle = FontStyle.Bold;
			this.report.SelectionColor = Color.Red ;
			this.report.SelectionFont = new Font(
				currentFont.FontFamily, 
				currentFont.Size, 
				newFontStyle
				);

			this.report.AppendText( "[ERROR] " );
			
			// restore text properties
			this.report.SelectionColor = fc ;
			this.report.SelectionFont = currentFont ;

			this.report.AppendText( msg + EOL );

			Debug.WriteLine( "[ERROR] "+msg );
		}


		private void Form1_Load(object sender, System.EventArgs e)
		{
			this.report.Text = "" ;
			//this.server_url.Text = "http://192.168.0.151:8080/" ;
			this.server_url.Text = "http://127.0.0.1:8080/" ;
			this.username.Text = "guest" ;
			this.password.Text = "guest" ;

			GraphicsInit();

			numclicks=0;
			color=toolColor.BackColor;

			xmlBlaster = new XmlBlasterClient();
		}


		private void connect_Click(object sender, System.EventArgs e)
		{
			try
			{
				this.xmlBlaster.Connect( this.server_url.Text, this.username.Text, this.password.Text );

				printLog( "Connect successful. sessionId = "+this.xmlBlaster.SessionId );
			}
			catch( XmlBlasterException ex )
			{
				HandleException( ex );
			}

			try
			{
				XmlBlasterCallback.messageArrived += new XmlBlasterCallback.MessageArrivedDelegate( MessageArrived );

				this.xmlBlaster.Subscribe( MessageDrawing.message_topic );
				printLog( "Subscribe successful. Topic : "+MessageDrawing.message_topic );
			}
			catch( Exception ex )
			{
				HandleException( ex );
			}

		}


		void MessageArrived( string key, string xmlMessage )
		{
			printLog( "MessageArrived()" );
			//printLog( "\t key: "+key );

			try 
			{
				MessageDrawing msg = MessageDrawing.CreateFromXml( xmlMessage );

				msg.Draw( graphics );

				pictureBox1.Invalidate();
			}
			catch( Exception ex )
			{
				HandleException( ex );
			}

		}


		private Color color;
		private Point[] pts;
		private int mpx,mpy,numclicks;
		private Bitmap bitmap;
		private Graphics graphics;

		void GraphicsInit()
		{
			Bitmap oldBitmap = null ;

			if( bitmap != null )
			{
				oldBitmap = bitmap ;
			}

			// Create and clear buffer
			bitmap=new Bitmap( this.pictureBox1.Width, this.pictureBox1.Height );

			graphics=Graphics.FromImage(bitmap);
			graphics.SmoothingMode=SmoothingMode.HighQuality;
			graphics.Clear(Color.White);

			if( oldBitmap != null )
			{
				graphics.DrawImage( oldBitmap, 0, 0 );
			}

		}


		private void pictureBox1_Paint(object sender, System.Windows.Forms.PaintEventArgs e)
		{
			e.Graphics.SmoothingMode=SmoothingMode.HighQuality;
			e.Graphics.DrawImageUnscaled(bitmap,0,0);
			
			// Show clicks (progress)
			if(numclicks!=0)
			{
				if(toolLine.Checked)
				{
					e.Graphics.DrawLine(new Pen(color),pts[1],new Point(mpx,mpy));

				}
				else if(toolRectangle.Checked)
				{
					int x=pts[1].X;
					int y=pts[1].Y;
					int w=mpx-x;
					int h=mpy-y;
					if(w<0)
					{
						x=mpx;
						w=-w;
					}
					if(h<0)
					{
						y=mpy;
						h=-h;
					}
					if(toolFill.Checked)
						e.Graphics.FillRectangle(new SolidBrush(color),x,y,w,h);
					else
						e.Graphics.DrawRectangle(new Pen(color),x,y,w,h);
				}
				else if(toolTriangle.Checked)
				{
					if(numclicks==2)
						e.Graphics.DrawLine(new Pen(color),pts[2],new Point(mpx,mpy));
					else
					{
						if(toolFill.Checked)
							e.Graphics.FillPolygon(new SolidBrush(color),new Point[]{pts[2],pts[1],new Point(mpx,mpy)});	
						else
							e.Graphics.DrawPolygon(new Pen(color),new Point[]{pts[2],pts[1],new Point(mpx,mpy)});
					}
				}
				else if(toolEllipse.Checked)
				{
					int x=pts[1].X;
					int y=pts[1].Y;
					int w=mpx-x;
					int h=mpy-y;
					if(w<0)
					{
						x=mpx;
						w=-w;
					}
					if(h<0)
					{
						y=mpy;
						h=-h;
					}
					if(toolFill.Checked)
						e.Graphics.FillEllipse(new SolidBrush(color),x,y,w,h);
					else
						e.Graphics.DrawEllipse(new Pen(color),x,y,w,h);
				}
			}
		}

		private void pictureBox1_MouseDown(object sender, System.Windows.Forms.MouseEventArgs e)
		{
			if(e.Button==MouseButtons.Left)
			{
				if(numclicks==0)
				{
					// Set numclicks and first point to line
					if(toolLine.Checked)
					{
						pts=new Point[2];
						numclicks=1;
						pts[numclicks]=new Point(e.X,e.Y);
					}
					// Set numclicks and first point to rectangle
					if(toolRectangle.Checked)
					{
						pts=new Point[2];
						numclicks=1;
						pts[numclicks]=new Point(e.X,e.Y);
					}
					// Set numclicks and first point to triangle
					if(toolTriangle.Checked)
					{
						pts=new Point[3];
						numclicks=2;
						pts[numclicks]=new Point(e.X,e.Y);
					}
					// Set numclicks and first point to ellipse
					if(toolEllipse.Checked)
					{
						pts=new Point[2];
						numclicks=1;
						pts[numclicks]=new Point(e.X,e.Y);
					}
				}
				else
				{
					MessageDrawing messageDrawing = null ;

					numclicks--;
					pts[numclicks]=new Point(e.X,e.Y);

					if(numclicks==0)
					{
						// draw line
						if(toolLine.Checked)
						{
							graphics.DrawLine(new Pen(color),pts[1],pts[0]);

							messageDrawing = new MessageDrawingLine( color, pts[1], pts[0] );
						}
						// draw rectangle
						else if(toolRectangle.Checked)
						{
							int x=pts[1].X;
							int y=pts[1].Y;
							int w=mpx-x;
							int h=mpy-y;
							if(w<0)
							{
								x=mpx;
								w=-w;
							}
							if(h<0)
							{
								y=mpy;
								h=-h;
							}
							if(toolFill.Checked)
								graphics.FillRectangle(new SolidBrush(color),x,y,w,h);
							else
								graphics.DrawRectangle(new Pen(color),x,y,w,h);

							messageDrawing = new MessageDrawingRectangle( toolFill.Checked, color, new Point(x,y), new Size(w,h) );
						}
						else if(toolTriangle.Checked)
						// draw triangle
						{
							if(toolFill.Checked)
								graphics.FillPolygon(new SolidBrush(color),new Point[]{pts[2],pts[1],pts[0]});	
							else
								graphics.DrawPolygon(new Pen(color),new Point[]{pts[2],pts[1],pts[0]});

							messageDrawing = new MessageDrawingTriangle( toolFill.Checked, color, pts[2],pts[1],pts[0] );
						}
						else if(toolEllipse.Checked)
						// draw ellipse
						{
							int x=pts[1].X;
							int y=pts[1].Y;
							int w=mpx-x;
							int h=mpy-y;
							if(w<0)
							{
								x=mpx;
								w=-w;
							}
							if(h<0)
							{
								y=mpy;
								h=-h;
							}
							if(toolFill.Checked)
								graphics.FillEllipse(new SolidBrush(color),x,y,w,h);
							else
								graphics.DrawEllipse(new Pen(color),x,y,w,h);

							messageDrawing = new MessageDrawingEllipse( toolFill.Checked, color, new Point(x,y), new Size(w,h) );
						}
					}

					if( messageDrawing != null ){
						messageDrawing.Send( this, this.xmlBlaster );
					}
				}
			}
		}

		private void pictureBox1_MouseMove(object sender, System.Windows.Forms.MouseEventArgs e)
		{
			if(numclicks!=0)
			{
				mpx=e.X;
				mpy=e.Y;

				pictureBox1.Invalidate();
			}
		}

		private void pictureBox1_Resize(object sender, System.EventArgs e)
		{
			this.GraphicsInit();
		}


		private void radioButtonX_CheckedChanged(object sender, System.EventArgs e)
		{
			numclicks=0;
			if( this.toolFill.Checked )
			{
				this.toolFill.ImageIndex = 4;
			}
			else
			{
				this.toolFill.ImageIndex = 5;
			}
		}

		private void toolColor_Click(object sender, System.EventArgs e)
		{
			numclicks=0;
			if(colorDialog1.ShowDialog()==DialogResult.OK)
				color=toolColor.BackColor=colorDialog1.Color;

		}

		private void toolErase_Click(object sender, System.EventArgs e)
		{
			numclicks=0;
			graphics.Clear(Color.White);
			pictureBox1.Invalidate();
		}

		private void Form1_Closing(object sender, System.ComponentModel.CancelEventArgs e)
		{
			try 
			{
				this.xmlBlaster.Disconnect();
			}
			catch(Exception ex)
			{
				Debug.WriteLine( "Failed to disconnect : " + ex.Message );
			}

		}

	}
}
