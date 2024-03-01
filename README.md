# TFTP server

Start by locating yourself in the same folder as the TFTPServer, using CMD (or similar).

Compilation:
"javac TFTPServer.java"

Run:
java TFTPServer (if no arguments are passed, port 69 and servding directory rw/ will be set)
or
java TFTPServer [port] [serving_directory]

Example for running:
java TFTPServer 69 myfolder/
