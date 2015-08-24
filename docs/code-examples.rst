Příklady kódu
=====================================


.. _create-export-request:

Vytvoření žádosti o export
-------------------------------------------------

.. code:: python

   import json
   import base64
   
   marcxml=open('resources/oai_marc.xml','rb').read()
   first_page_fname = 'resources/robotandbaby_001.jp2'
   location_at_kramerius = "/monografie/2001/John McCarthy/Robot and Baby"
   open('resources/export-request.json','wb').write(
       json.dumps(
           dict( __nt_name="ExportToKramerius",
                 uuid='e65d9072-2c9b-11e5-99fd-b8763f0a3d61',
                 urnnbn='urn:nbn:cz:mzk-0005ol',
                 b64_marcxml=base64.encodestring(marcxml),
                 first_page = dict(filename=first_page_fname.split('/')[-1],
                                   mimetype='image/jp2',
                                   b64_data = base64.encodestring(
                                              open(first_page_fname,'rb').read())),
                 location_at_kramerius = location_at_kramerius,
                 is_private = False,
             )
       )
   )

.. _send-export-request:

Odeslání žádosti o export
----------------------------------------------

.. code:: python

   import pika
   msg = open("resources/export-request.json","rb").read()
   conn = pika.BlockingConnection(
             pika.URLParameters("http://guest:guest@localhost:5672/kramerius"))
   channel = conn.channel()
   channel.basic_publish("export", "request", msg,
                         pika.BasicProperties(content_type="application/json",
                                              delivery_mode=2))


.. _copy-export-data:

Uložení dat k importu do Krameria
-------------------------------------------------------------

.. code:: shell

   scp -i ~/.ssh/id_rsa-edeposit-to-kramerius -r
          resources/e65d9072-2c9b-11e5-99fd-b8763f0a3d61
          edeposit@HAPPKRAM2.nkp.cz:/home/kramerius/kramerius_edeposit_import

.. _delete-imported-data:

Smazání importovaných dat v Krameriovi
-----------------------------------------------------------------------

.. code:: shell

   ssh -i ~/.ssh/id_rsa-edeposit-to-kramerius
          edeposit@HAPPKRAM2.nkp.cz 
          "cd /home/kramerius/kramerius_edeposit_import;
          rm -rf e65d9072-2c9b-11e5-99fd-b8763f0a3d61"
