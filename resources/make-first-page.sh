#!/bin/bash
pdftk robotandbaby.pdf cat 1 output robotandbaby_001.pdf verbose

# Command Line Data is valid.

# Input PDF Filenames & Passwords in Order
# ( <filename>[, <password>] ) 
#    robotandbaby.pdf

# The operation to be performed: 
#    cat - Catenate given page ranges into a new PDF.

# The output file will be named:
#    robotandbaby_001.pdf

# Output PDF encryption settings:
#    Output PDF will not be encrypted.

# No compression or uncompression being performed on output.

# Creating Output ...
#    Adding page 1 X0X  from robotandbaby.pdf

convert robotandbaby_001.pdf robotandbaby_001.jp2
