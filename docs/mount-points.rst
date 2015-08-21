Připojované adresáře
===================================================

Data, která ``Kramerius server`` vyžaduje, jsou uložena na  serveru
``Storage server``. Ten je součástí projektu ``eDeposit``.

``eDeposit storage server`` data poskytuje pomocí ``smb`` protokolu.


Připojované adresáře na ``Kramerius server``
------------------------------------------------------------

``/kramerius_edeposit_import``
  - v tomto adresáři jsou uloženy ``FOXML`` soubory čekající na import
    do Krameria
  - aplikace eDeposit je po úspěšném importu smaže
    
``/kramerius_edeposit_storage``
  - v tomto adresáři jsou uloženy originály ke zpřístupňování

  ::

    mount -t cifs //10.10.0.42/naki/originals t2 -o username=edeposit,domain=ULTRA_NT 
                  /kramerius_edeposit_originals

Připojované adresáře na ``Image server``
----------------------------------------------------

``/edeposit_storage``
  - v tomto adresáři jsou uloženy zdrojové obrázky první strany

  ::

    mount -t cifs //10.10.0.42/naki/originals t2 -o username=edeposit,domain=ULTRA_NT 
                  /kramerius_edeposit_archive
