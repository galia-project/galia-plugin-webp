# WebP Plugin for Galia

Provides WebPDecoder and WebPEncoder.

See the [WebP Plugin page on the website](https://galia.is/plugins/webp/)
for more information.

## Development

The native binding to libwebp was generated using jextract 22:

```sh
jextract --target-package com.google.libwebp \
    --output /path/to/src/main/java \
    /path/to/include/webp/encode.h
jextract --target-package com.google.libwebp \
    --output /path/to/src/main/java \
    /path/to/include/webp/decode.h
jextract --target-package com.google.libwebp \
    --output /path/to/src/main/java \
    /path/to/include/webp/mux.h
jextract --target-package com.google.libwebp \
    --output /path/to/src/main/java \
    /path/to/include/webp/demux.h
```

# License

See the file [LICENSE.txt](LICENSE.txt) for license information.
