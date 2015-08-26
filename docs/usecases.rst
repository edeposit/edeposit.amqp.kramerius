Případy užití
=====================================


Timeout procesu zpracování ``FOXML`` souboru na ``Kramerius server``
---------------------------------------------------------------------------------

*účastnící*:
- systém

*vstupní podmínky*:

- objevil se nějaký problém při importu do Krameria
- v importním adresáři jsou odpovídající data k importu

*sled událostí*:
- systém počká 1den
- pokud problém trvá,
  - systém import zopakuje
- pokud již proběhl 1 pokus o import
  - ePublikace skončí ve stavu "Chyba exportu do Krameria"

*následné podmínky*:

- na serveru Kramerius budou přepsané odpovídající importní data
- na serveru Kramerius bude spuštěn importní proces (pokud není omezení)


Import do ``Kramerius serveru`` skončil s chybou
------------------------------------------------------------------------------------------

*účastnící*:
- správce Krameria
- správce eDeposit

*vstupní podmínky*:

- objevil se nějaký problém při importu do Krameria
- v importním adresáři jsou odpovídající data k importu

*sled událostí*:
- správce Krameria se obrátí na správce eDepositu
- správce eDepositu si zobrazí detail ePublikace
- správce eDepositu klikne na linku "Zopakovat export do Krameria"
- systém zopakuje export do Krameria

*následné podmínky*:

- na serveru Kramerius budou přepsané odpovídající importní data
- na serveru Kramerius bude spuštěn importní proces (pokud není omezení)

Zopakování exportu z eDepositu do Krameria
--------------------------------------------------------------------------

*účastnící*:
- správce Krameria
- správce eDeposit

*vstupní podmínky*:

- objevil se nějaký problém při importu do Krameria
- v importním adresáři jsou odpovídající data k importu

*sled událostí*:
- správce Krameria se obrátí na správce eDepositu
- správce eDepositu si zobrazí detail ePublikace
- správce eDepositu klikne na linku "Zopakovat export do Krameria"
- systém zopakuje export do Krameria

*následné podmínky*:

- na serveru Kramerius budou přepsané odpovídající importní data
- na serveru Kramerius bude spuštěn importní proces (pokud není omezení)

Již proběhlo jedno automatické zopakování importu
--------------------------------------------------------------------------------------------

*účastnící*:
- systém
- správce eDepositu

*vstupní podmínky*:

- objevil se nějaký problém při importu do Krameria
- v importním adresáři jsou odpovídající data k importu
- již proběhl jeden pokus o zopakování importu

*sled událostí*:
- systém přesune ePublikaci do stavu "Chyba exportu do Krameria"
- systém odešle email správci eDepositu
- správce eDepositu se pokusí problém vyřešit

*následné podmínky*:

- na serveru Kramerius budou smazány odpovídající importní data
