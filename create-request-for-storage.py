#!/usr/bin/python
import json
import base64
import sh

sh.sh("./make-zip.sh")
open('resources/communication-with-storage/request/payload.bin','wb').write(
    json.dumps(
        dict( __nt_name="ExportToKramerius",
              uuid='e65d9072-2c9b-11e5-99fd-b8763f0a3d61',
              b64_data=base64.encodestring(open("/tmp/e65d9072-2c9b-11e5-99fd-b8763f0a3d61.zip","rb").read())
          )
    )
)
