#!/usr/bin/python
import json
import base64

def createRequest(uuid, marcxml, fname, outname):
    data = dict(filename=fname,
                uuid=uuid,
                __nt_name="ExportToKramerius",
                b64_data="",
                b64_marcxml=""
    )
    out = base64.encodestring(open(fname,'rb').read())
    data['b64_data'] = out

    out_marcxml = base64.encodestring(marcxml)
    data['b64_marcxml'] = out_marcxml
    open(outname,'wb').write(json.dumps(data))
    
uuid='e65d9072-2c9b-11e5-99fd-b8763f0a3d61'
createRequest(uuid, open('resources/oai_marc.xml','rb').read(),
              'resources/robotandbaby.pdf', 'resources/export-request.json')

