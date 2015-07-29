How Export to Kramerius Works
===================================================================

Data Package
----------------------

Data package consists of a few parts:

:original file:  
   - ``PDF`` file with ePublication
   - the file contains main content of en ePublication

:preview file:  
   - Kramerius needs to have at least one image to show.
   - It is stored as ``jpeg`` file.
   - user clicks on a thumbnail at Kramerius
   - and Kramerius provides main PDF to view a content of an
     ePublication

:FOXML file:
   - in a format ``FOXML``
   - it contains all informations about attached files
   - it contains all informations about ePublication
   - it contains all links to ``original file``, ``preview file``

.. note::

   Each package has its own ``UUID``. Package file is named by its own UUID. for e.g.

   ``e65d9072-2c9b-11e5-99fd-b8763f0a3d61.zip``

Where It is Stored
----------------------------------------

:edeposit storage:
   - it offers all data packages
   - it offers thubmail files to download
   - it offers preview files to download

:kramerius server:
   - it stores ``FOXML`` file of a data package to import
     


What is Called
--------------------------

:AMQP to prepare data package:
   eDeposit Plone applications calls ``AMQP`` service to export data
   to Kramerius

:scp to kramerius:
   AMQP service stores ``FOXML`` file with all links and metadata

:REST API at Kramerius server:
   an application notifies a Kramerius server to start import by
   calling REST API at Kramerius server.

Structure of a Data Package
-------------------------------------------------

   ``e65d9072-2c9b-11e5-99fd-b8763f0a3d61.zip``

::

   jan@jan-XPS-L421X:~/$ tree e65d9072-2c9b-11e5-99fd-b8763f0a3d61
   e65d9072-2c9b-11e5-99fd-b8763f0a3d61/
   ├── img
   │   ├── 8025102254-the-robot-and-the-baby.pdf
   │   └── 8025102254-the-robot-and-the-baby_001.jpg
   └── xml
       └── e65d9072-2c9b-11e5-99fd-b8763f0a3d61.xml

   2 directories, 3 files

