#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for doc types
# $Id: Makefile,v 1.3 2000/03/25 00:08:47 ruff Exp $
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

all: html

html:
	java -Dcom.jclark.xsl.sax.parser=com.jclark.xsl.dom.SunXMLProcessorImpl com.jclark.xsl.sax.Driver whitepaper.xml /opt/docbook/html/docbook.xsl whitepaper.html
	@echo -e "whitepaper.html created"

doc: html

#all: html ps
#
#clean:
#	@echo "Removing all dvi,ps and html ..."
#	$(RM) *.html *.dvi *.ps 
#
#ps: dvi
#	dvips -o whitepaper.ps whitepaper.dvi
#
#dvi: 
#	sgml2latex whitepaper.sgml
#
#html:
#	sgml2html whitepaper.sgml




