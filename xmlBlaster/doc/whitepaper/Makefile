#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for doc types
# $id$
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

all: html ps

clean:
	@echo "Removing all dvi,ps and html ..."
	$(RM) *.html *.dvi *.ps 

ps: dvi
	dvips -o whitepaper.ps whitepaper.dvi

dvi: 
	sgml2latex whitepaper.sgml

html:
	sgml2html whitepaper.sgml




