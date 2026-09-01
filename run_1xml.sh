#!/bin/bash
XML_DIR=Lab/testdata
java -cp /compiled/xml2txt_new.jar XML2TXT -p Lab/BluPrint.bp -s "$XML_DIR" -d output_1 -t 1 -N 7 -b 1
ls -l output_1/*.txt 2>/dev/null | head -2
