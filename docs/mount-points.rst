Mount points
===================================================================

There are more servers that uses data for Kramerius.
They are offered by ``eDeposit storage server``.

``eDeposit storage server`` offers ``smb`` protocol to mount directories with
data.


Mount points at Kramerius server
------------------------------------------------------------

``/kramerius_edeposit_import``
  - there are ``FOXML`` files waiting for import ePublication into
    Kramerius in the directory
  - eDeposit removes files that were imported successfully
    
``/kramerius_edeposit_archive``
  - there are all data that were imported into Kramerius.
  - the data does not contains ``original files``.
  - it is due to securing access to ``original files``.

  ::

    mount -t cifs //10.10.0.42/naki/archive t2 -o username=edeposit,domain=ULTRA_NT 
                  /kramerius_edeposit_archive

``/kramerius_edeposit_originals``
  - there are all original files that Kramerius can offer

  ::

    mount -t cifs //10.10.0.42/naki/originals t2 -o username=edeposit,domain=ULTRA_NT 
                  /kramerius_edeposit_originals

Mount points at Image server
----------------------------------------------------

``/edeposit_storage``
  - there are all images to create ``thumbnails`` or ``preview files``

  ::

    mount -t cifs //10.10.0.42/naki/archive t2 -o username=edeposit,domain=ULTRA_NT 
                  /kramerius_edeposit_archive
