#!/usr/bin/python
import json
import base64

marcxml=open('resources/oai_marc.xml','rb').read()
first_page_fname = 'resources/robotandbaby_001.jp2'
original_fname = "robotandbaby.pdf"
location_at_kramerius = "/monografie/2001/John McCarthy/Robot and Baby"
storage_path = "/monografie/01/01-robotandbaby.pdf"
open('resources/export-request.json','wb').write(
    json.dumps(
        dict( __nt_name="ExportToKramerius",
              uuid='e65d9072-2c9b-11e5-99fd-b8763f0a3d61',
              urnnbn='urn:nbn:cz:mzk-0005ol',
              b64_marcxml=base64.encodestring(marcxml),
              first_page = dict(filename=first_page_fname.split('/')[-1],
                                mimetype='image/jp2',
                                b64_data = base64.encodestring(open(first_page_fname,'rb').read())),
              original = dict(filename=original_fname,
                              storage_path = storage_path,
                              mimetype='application/pdf'),
              edeposit_url = "http://edeposit-application.nkp.cz/some-original",
              location_at_kramerius = location_at_kramerius,
              is_private = False,
          )
    )
)
