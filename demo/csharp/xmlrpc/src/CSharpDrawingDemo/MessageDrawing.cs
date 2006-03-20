using System;
using System.Drawing ;
using System.Text ;
using System.Xml ;
using System.Diagnostics ;

using XmlBlaster ;

namespace xmlrpc
{

	abstract class MessageDrawing
	{
		static readonly string EOL = Environment.NewLine ;
		public static readonly string message_topic = "demo.csharp.drawing" ;

		public enum DrawFormType
		{
			Line = 1 ,
			Rectangle ,
			Ellipse ,
			Triangle
		}
		protected DrawFormType drawForm ;
		public DrawFormType DrawForm
		{
			get { return drawForm ; }
		}

		protected int penRgb ;
		protected bool filled ;
		protected int fillRgb ;

		string GetXmlString()
		{
			StringBuilder sb = new StringBuilder(255);

			sb.Append( "<drawingMessage>\n" );

			sb.Append( "<pen color=\""+ this.penRgb.ToString() +"\" />\n" );
			if( filled )
			{
				sb.Append( "<fill color=\""+ this.fillRgb.ToString() +"\" />\n" );
			}

			AddDataXmlString( sb );

			sb.Append( "</drawingMessage>" );

			return sb.ToString();
		}


		protected abstract void AddDataXmlString( StringBuilder sb );
		public abstract void Draw( Graphics graphics );

		public static MessageDrawing CreateFromXml( string xml )
		{
			string me="MessageDrawing.CreateFromXml()";

			//Debug.WriteLine( "MessageDrawing.CreateFromXml()" );
			Debug.WriteLine( me+" : " + xml );

			MessageDrawing msg = null ;

			XmlDocument xmlDoc = new XmlDocument();
			xmlDoc.LoadXml( xml );
			XmlNode root = xmlDoc.DocumentElement ;

			bool filled = false ;
			Color fillColor = Color.Black, penColor = Color.Black ;

			XmlNodeList nodes ;
			XmlNode node ;

			node = root.SelectSingleNode("fill");
			if( node != null )
			{
				try
				{
					filled = true ;
					int rgb = Convert.ToInt32( node.Attributes[ "color" ].Value );
					fillColor = Color.FromArgb( rgb );
				}
				catch( Exception ex )
				{
					throw new Exception( "Malformed MessageDrawing : fill element has error." );
				}
			}
			node = root.SelectSingleNode("pen");
			if( node != null )
			{
				try
				{
					int rgb = Convert.ToInt32( node.Attributes[ "color" ].Value );
					penColor = Color.FromArgb( rgb );
				}
				catch( Exception ex )
				{
					throw new Exception( "Malformed MessageDrawing : fill element has error." );
				}
			}

			XmlNode data_node ;
			string type = null ;

			try
			{
				//node = root.SelectSingleNode("descendant::book[author/last-name='Austen']");
				data_node = root.SelectSingleNode("data");
				type = data_node.Attributes[ "type" ].Value ;
			}
			catch( Exception ex )
			{
				throw new Exception( "Malformed MessageDrawing : missing data element or type attribute." );
			}

			switch( type )
			{
				case "line":
					Debug.WriteLine( me+" : create a MessageDrawingLine" );

					try 
					{
						nodes = data_node.SelectNodes("point");
						Point p1, p2 ;
						p1 = new Point( Convert.ToInt32(nodes[0].Attributes["x"].Value), Convert.ToInt32( nodes[0].Attributes["y"].Value) );
						p2 = new Point( Convert.ToInt32(nodes[1].Attributes["x"].Value), Convert.ToInt32( nodes[1].Attributes["y"].Value) );

						msg = new MessageDrawingLine( penColor, p1, p2 );
					}
					catch( Exception ex )
					{
						throw new Exception( "Malformed MessageDrawingLine : "+ex.Message +EOL +ex.StackTrace );
					}
					break;

				case "rectangle":
					try 
					{
						node = data_node.SelectSingleNode("point");
						Point point = new Point( Convert.ToInt32(node.Attributes["x"].Value), Convert.ToInt32( node.Attributes["y"].Value) );
						node = data_node.SelectSingleNode("size");
						Size size = new Size( Convert.ToInt32(node.Attributes["w"].Value), Convert.ToInt32( node.Attributes["h"].Value) );

						if( filled )
						{
							msg = new MessageDrawingRectangle( filled, fillColor, point, size );
						}
						else
						{
							msg = new MessageDrawingRectangle( filled, penColor, point, size );
						}
					}
					catch( Exception ex )
					{
						throw new Exception( "Malformed MessageDrawingRectangle : "+ex.Message +EOL +ex.StackTrace );
					}
					break;

				case "ellipse":
					try 
					{
						node = data_node.SelectSingleNode("point");
						Point point = new Point( Convert.ToInt32(node.Attributes["x"].Value), Convert.ToInt32( node.Attributes["y"].Value) );
						node = data_node.SelectSingleNode("size");
						Size size = new Size( Convert.ToInt32(node.Attributes["w"].Value), Convert.ToInt32( node.Attributes["h"].Value) );

						if( filled )
						{
							msg = new MessageDrawingEllipse( filled, fillColor, point, size );
						}
						else
						{
							msg = new MessageDrawingEllipse( filled, penColor, point, size );
						}
					}
					catch( Exception ex )
					{
						throw new Exception( "Malformed MessageDrawingEllipse : "+ex.Message +EOL +ex.StackTrace );
					}
					break;

				case "triangle":
					try 
					{
						nodes = data_node.SelectNodes("point");
						Point p1, p2, p3 ;
						p1 = new Point( Convert.ToInt32(nodes[0].Attributes["x"].Value), Convert.ToInt32( nodes[0].Attributes["y"].Value) );
						p2 = new Point( Convert.ToInt32(nodes[1].Attributes["x"].Value), Convert.ToInt32( nodes[1].Attributes["y"].Value) );
						p3 = new Point( Convert.ToInt32(nodes[2].Attributes["x"].Value), Convert.ToInt32( nodes[2].Attributes["y"].Value) );

						if( filled )
						{
							msg = new MessageDrawingTriangle( filled, fillColor, p1, p2, p3 );
						}
						else
						{
							msg = new MessageDrawingTriangle( filled, penColor, p1, p2, p3 );
						}
					}
					catch( Exception ex )
					{
						throw new Exception( "Malformed MessageDrawingTriangle : "+ex.Message+EOL +ex.StackTrace  );
					}
					break;
			}

			if( msg == null )
			{
				throw new Exception( "Malformed MessageDrawing : Unknow drawing type" );
			}

			return msg ;
		}

		public void Send( Form1 form, XmlBlasterClient xmlBlaster )
		{
			try
			{
				xmlBlaster.Publish( MessageDrawing.message_topic, this.GetXmlString() );
			}
			catch( XmlBlasterException ex )
			{
				form.printError( ex.Message );
				//MessageBox.Show("Error: " + ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
			}
		}

	}
	class MessageDrawingLine : MessageDrawing
	{
		Point p1, p2 ;
		public MessageDrawingLine( Color color, Point p1, Point p2 )
		{
			this.drawForm = DrawFormType.Line ;

			this.filled = false ;
			this.penRgb = color.ToArgb() ;
			this.p1 = p1 ;
			this.p2 = p2 ;
		}
		protected override void AddDataXmlString( StringBuilder sb )
		{
			sb.Append( "<data type=\"line\">\n" );
			sb.Append( " <point x=\""+this.p1.X+"\" y=\""+this.p1.Y+"\" />\n" );
			sb.Append( " <point x=\""+this.p2.X+"\" y=\""+this.p2.Y+"\" />\n" );
			sb.Append( "</data>\n" );
		}
		public override void Draw( Graphics graphics )
		{
			Debug.WriteLine( "MessageDrawingLine.Draw()" );
			graphics.DrawLine( new Pen(Color.FromArgb(this.penRgb)),this.p2,this.p1);
		}
	}
	class MessageDrawingRectangle : MessageDrawing
	{
		Point orig ;
		Size size ;
		public MessageDrawingRectangle( bool filled, Color color, Point point, Size size )
		{
			drawForm = DrawFormType.Rectangle ;

			this.penRgb = color.ToArgb() ;
			this.filled = filled ;
			this.fillRgb = this.penRgb ;
			this.orig = point ;
			this.size = size ;
		}
		protected override void AddDataXmlString( StringBuilder sb )
		{
			sb.Append( "<data type=\"rectangle\">\n" );
			sb.Append( " <point x=\""+this.orig.X+"\" y=\""+this.orig.Y+"\" />\n" );
			sb.Append( " <size w=\""+this.size.Width+"\" h=\""+this.size.Height+"\" />\n" );
			sb.Append( "</data>\n" );
		}
		public override void Draw( Graphics graphics )
		{
			if(this.filled)
			{
				graphics.FillRectangle( new SolidBrush(Color.FromArgb(this.fillRgb)),orig.X,orig.Y,size.Width,size.Height);
			}
			else
			{
				graphics.DrawRectangle( new Pen(Color.FromArgb(this.penRgb)),orig.X,orig.Y,size.Width,size.Height);
			}

		}
	}
	class MessageDrawingEllipse : MessageDrawing
	{
		Point orig ;
		Size size ;
		public MessageDrawingEllipse( bool filled, Color color, Point point, Size size )
		{
			drawForm = DrawFormType.Ellipse ;

			this.penRgb = color.ToArgb() ;
			this.filled = filled ;
			this.fillRgb = this.penRgb ;
			this.orig = point ;
			this.size = size ;
		}
		protected override void AddDataXmlString( StringBuilder sb )
		{
			sb.Append( "<data type=\"ellipse\">\n" );
			sb.Append( " <point x=\""+this.orig.X+"\" y=\""+this.orig.Y+"\" />\n" );
			sb.Append( " <size w=\""+this.size.Width+"\" h=\""+this.size.Height+"\" />\n" );
			sb.Append( "</data>\n" );
		}
		public override void Draw( Graphics graphics )
		{
			if(this.filled)
				graphics.FillEllipse(new SolidBrush(Color.FromArgb(this.fillRgb)),orig.X,orig.Y,size.Width,size.Height);
			else
				graphics.DrawEllipse(new Pen(Color.FromArgb(this.penRgb)),orig.X,orig.Y,size.Width,size.Height);
		}
	}
	class MessageDrawingTriangle : MessageDrawing
	{
		Point p1, p2, p3 ;
		public MessageDrawingTriangle( bool filled, Color color, Point p1, Point p2, Point p3 )
		{
			drawForm = DrawFormType.Triangle ;

			this.penRgb = color.ToArgb() ;
			this.filled = filled ;
			this.fillRgb = this.penRgb ;
			this.p1 = p1 ;
			this.p2 = p2 ;
			this.p3 = p3 ;
		}
		protected override void AddDataXmlString( StringBuilder sb )
		{
			sb.Append( "<data type=\"triangle\">\n" );
			sb.Append( " <point x=\""+this.p1.X+"\" y=\""+this.p1.Y+"\" />\n" );
			sb.Append( " <point x=\""+this.p2.X+"\" y=\""+this.p2.Y+"\" />\n" );
			sb.Append( " <point x=\""+this.p3.X+"\" y=\""+this.p3.Y+"\" />\n" );
			sb.Append( "</data>\n" );
		}
		public override void Draw( Graphics graphics )
		{
			if(this.filled)
				graphics.FillPolygon(new SolidBrush(Color.FromArgb(this.fillRgb)),new Point[]{p3,p2,p1});	
			else
				graphics.DrawPolygon(new Pen(Color.FromArgb(this.penRgb)),new Point[]{p3,p2,p1});
		}
	}

}
