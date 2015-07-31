#!/usr/bin/python
import json
import base64

def createRequest(uuid, urnnbn, marcxml, full_fname, preview_fname, outname):
    data = dict( __nt_name="ExportToKramerius",
                 uuid=uuid,
                 urnnbn=urnnbn,
                 b64_marcxml=base64.encodestring(marcxml),
                 img_full = dict(filename=full_fname.split('/')[-1],
                                 mimetype='application/pdf',
                                 b64_data = base64.encodestring(open(full_fname,'rb').read())),
                 img_preview = dict(filename=preview_fname.split('/')[-1],
                                    mimetype='image/jpeg',
                                    b64_data = base64.encodestring(open(preview_fname,'rb').read()))
             )
    open(outname,'wb').write(json.dumps(data))
    
uuid='e65d9072-2c9b-11e5-99fd-b8763f0a3d61'
urnnbn='urn:nbn:cz:mzk-0005ol'
createRequest(uuid, urnnbn,
              open('resources/oai_marc.xml','rb').read(),
              'resources/robotandbaby.pdf', 
              'resources/robotandbaby_001.jpg',
              'resources/export-request.json')
