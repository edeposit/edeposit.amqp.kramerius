Export do Krameria
===================================================

Kramerius vyžaduje
----------------------------------

:original file:  
   - ``PDF`` soubor s ePublikací

:image source file:  
   - hlavní náhled na ePublikaci
   - ``preview file`` a ``thumbnail file`` jsou generováný za běhu
     pomocí ``image server``
   - soubor je formátu ``jpeg2000``

:preview file:  
   - Kramerius potřebuje alespoň jeden obrázek k zobrazení
   - uživatel na něj v Krameriovi klikne
   - ... a Kramerius zpřístupní hlavní PDF s ePublikací

   .. note::
      ``preview file`` je generován na požádání přes ``image server``.
      ``FOXML`` soubor obsahuje linku na ``image server``.

:thumbnail file:
   - Kramerius soubor používá jaho náhled

   .. note::
      soubor je generován stejně jako ``preview file`` přes ``image server``.

:FOXML file:
   - soubor ve formátu ``FOXML``
   - obsahuje všechny informace o zobrazovaných souborech
   - obsahuje všechny informace o samotné ePublikaci
   - obsahuje linky na ``original file``, ``preview file``,
     ``thumbnail file``
   - obsahuje všechny informace o umístění zobrazení ePublikace. Jinak
     řečeno, obsahuje informace o stromu uložení ePublikace v Krameriovi

.. note::

   Každý balíček má své ``UUID``. Adresář s importními daty je podle
   něj pojmenovaný. Stejně tak i ``FOXML`` soubor.


Struktura datového balíčku
-------------------------------------------------

Adresář pro import obsahuje datové balíčky připravené k importu:

::

   jan@jan-XPS-L421X:~/$ tree e65d9072-2c9b-11e5-99fd-b8763f0a3d61
   e65d9072-2c9b-11e5-99fd-b8763f0a3d61/
   └── e65d9072-2c9b-11e5-99fd-b8763f0a3d61.xml
   └── first-page.jp2
   └── edeposit-url.txt

   0 directories, 3 files


jednotlivé soubory:

 ========================================   =======================================
 e65d9072-2c9b-11e5-99fd-b8763f0a3d61.xml   ``FOXML`` soubor                         
 edeposit-url.txt                           soubor s odkazem do  aplikace eDeposit
 first-page.jp2                             náhled první strany
 ========================================   =======================================  


.. note::

   Náhled první strany je v balíčku jen pro pořádek. Bude uložen v archivu a odtud bude používán.
   

Externí odkazy ve FOXML
---------------------------------------

Umístění ePublikace v Krameriovi je popsáné v sekci ``RelsExt``.

Tato sekce musí obsahovat celou cestu k ePublikaci v Krameriovi.
Všechny nové větve stromu (cesta k ePublikaci) Kramerius vygeneruje.
   
Uložení dat
--------------------

:edeposit storage:
   - poskytuje všechny originály ePublikací
   - poskytuje náhled první strany pro ``image server``. Ten z něj
     generuje náhledy.
   - poskytuje archiv dat, která byla importována do Krameria

:kramerius server:
   - na tomto serveru jsou uloženy ``FOXML`` soubory k importu

Průběh exportu
--------------------------

1. eDeposit Plone aplikace připraví data pro export do Krameria
   viz :ref:`create-export-request`

2. eDeposit Plone aplikace volá ``AMQP`` službu k exportu do Krameria
   - a poskytne informace k vytvoření ``FOXML``
   viz :ref:`send-export-request`

3. ``edeposit.amqp.kramerius`` převede ``MARCXML`` data do ``MODS``
   pomocí ``AMQP`` služby ``edeposit.amqp.marcxml2mods``

4. ``edeposit.amqp.kramerius`` vytvoří ``FOXML`` soubor

5. ``edeposit.amqp.kramerius`` odešle importní balíček do 
   archivu ``storage serveru`` přes ``AMQP`` protokol 

6. ``edeposit.amqp.kramerius`` zkopíruje importní balíček na
   ``Kramerius server``
   viz :ref:`copy-export-data`

7. ``edeposit.amqp.kramerius`` volá ``REST API`` na ``Kramerius server``

   - aplikace spustí na ``Kramerius server`` proces importu
   - aplikace se pravidelně dotazuje na ``Kramerius server`` jak
     proces importu probíhá

8. ``edeposit.amqp.kramerius`` smaže data k importu
   - jakmile proces importu úspěšně skončí
   viz :ref:`delete-imported-data`

9. ``edeposit.amqp.kramerius`` posílá zprávu s odpovědí
   - odesílá jí do aplikace ``RabbitMQ`` přes ``AMQP`` protokol.


Omezení procesu importu
------------------------------------------

eDeposit může v Krameriovi nastarovat jen jeden proces importu.
Poté co proces importu skončí, může nastarovat další.

Data v importním adresáři mohou být uložena všechna a čekají na
spuštění odpovídajícího importu.
