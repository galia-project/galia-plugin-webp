/*
 * Copyright © 2024 Baird Creek Software LLC
 *
 * Licensed under the PolyForm Noncommercial License, version 1.0.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     https://polyformproject.org/licenses/noncommercial/1.0.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package is.galia.plugin.webp;

import com.google.libwebp.WebPConfig;
import com.google.libwebp.WebPData;
import com.google.libwebp.WebPMemoryWriter;
import com.google.libwebp.WebPPicture;
import com.google.libwebp.mux_h;
import is.galia.codec.Encoder;
import is.galia.image.Format;
import is.galia.operation.Encode;
import is.galia.plugin.Plugin;
import is.galia.plugin.webp.config.Key;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.libwebp.encode_h.VP8_ENC_ERROR_BAD_DIMENSION;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_BAD_WRITE;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_BITSTREAM_OUT_OF_MEMORY;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_FILE_TOO_BIG;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_INVALID_CONFIGURATION;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_LAST;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_NULL_PARAMETER;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_OUT_OF_MEMORY;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_PARTITION0_OVERFLOW;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_PARTITION_OVERFLOW;
import static com.google.libwebp.encode_h.VP8_ENC_ERROR_USER_ABORT;
import static com.google.libwebp.encode_h.VP8_ENC_OK;
import static com.google.libwebp.encode_h.WEBP_ENCODER_ABI_VERSION;
import static com.google.libwebp.encode_h.WEBP_PRESET_PHOTO;
import static com.google.libwebp.encode_h.WebPConfigInitInternal;
import static com.google.libwebp.encode_h.WebPEncode;
import static com.google.libwebp.encode_h.WebPFree;
import static com.google.libwebp.encode_h.WebPMemoryWrite$descriptor;
import static com.google.libwebp.encode_h.WebPMemoryWrite$handle;
import static com.google.libwebp.encode_h.WebPMemoryWriterInit;
import static com.google.libwebp.encode_h.WebPPictureFree;
import static com.google.libwebp.encode_h.WebPPictureImportBGR;
import static com.google.libwebp.encode_h.WebPPictureImportRGBA;
import static com.google.libwebp.encode_h.WebPPictureInitInternal;
import static com.google.libwebp.encode_h.WebPValidateConfig;
import static com.google.libwebp.mux_h.WEBP_MUX_ABI_VERSION;
import static com.google.libwebp.mux_h.WebPMuxAssemble;
import static com.google.libwebp.mux_h.WebPMuxDelete;
import static com.google.libwebp.mux_h.WebPMuxSetChunk;
import static com.google.libwebp.mux_h.WebPMuxSetImage;
import static com.google.libwebp.mux_h.WebPNewInternal;

/**
 * <p>Implementation using the Java Foreign Function & Memory API to call into
 * Google's libwebp library.</p>
 *
 * <p>The current libwebp version is 1.3.2.</p>
 *
 * @see <a href="https://developers.google.com/speed/webp/docs/api">
 *     WebP API Documentation</a>
 * @see <a href="https://developers.google.com/speed/webp/docs/container-api">
 *     WebP Container API Documentation</a>
 * @see <a href="https://developers.google.com/speed/webp/docs/riff_container">
 *     WebP Container Specification</a>
 */
public final class WebPEncoder implements Encoder, Plugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPEncoder.class);

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    private Arena arena;
    private Encode encode;

    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Arrays.stream(Key.values())
                .map(Key::toString)
                .filter(k -> k.contains(WebPEncoder.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPluginName() {
        return WebPEncoder.class.getSimpleName();
    }

    @Override
    public void onApplicationStart() {
        if (!IS_CLASS_INITIALIZED.getAndSet(true)) {
            System.loadLibrary("webpmux");
        }
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void initializePlugin() {
    }

    //endregion
    //region Encoder methods

    @Override
    public void close() {
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Set.of(WebPDecoder.FORMAT);
    }

    @Override
    public void setArena(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void setEncode(Encode encode) {
        this.encode = encode;
    }

    @Override
    public void encode(RenderedImage image,
                       OutputStream outputStream) throws IOException {
        final Stopwatch elapsedTimer = new Stopwatch();
        final Stopwatch lapTimer     = new Stopwatch();
        final int width              = image.getWidth();
        final int height             = image.getHeight();

        // Allocate a WebPConfig struct.
        StructLayout configStruct = (StructLayout) WebPConfig.layout();
        MemorySegment config      = arena.allocate(configStruct);
        // Initialize it with photo preset values, a few of which will be
        // overridden next.
        int code = WebPConfigInitInternal(config, WEBP_PRESET_PHOTO(),
                getQuality(), WEBP_ENCODER_ABI_VERSION()); // config_enc.c
        if (code == 0) {
            throw new IOException("WebPConfigInit() returned status code 0");
        }
        WebPConfig.lossless(config, isLossless() ? 1 : 0);
        WebPConfig.quality(config, getQuality());
        WebPConfig.method(config, getMethod());
        WebPConfig.autofilter(config, isAutofilter() ? 1 : 0);
        WebPConfig.thread_level(config, isMultithreading() ? 1 : 0);

        code = WebPValidateConfig(config); // config_enc.c
        if (code == 0) {
            throw new IOException("WebPValidateConfig() returned status code 0");
        }

        // Allocate a WebPPicture struct, which will need to get freed.
        StructLayout pictureStruct = (StructLayout) WebPPicture.layout();
        MemorySegment webPPicture  = arena.allocate(pictureStruct);
        try {
            if (WebPPictureInitInternal(webPPicture, WEBP_ENCODER_ABI_VERSION()) == 0) {
                throw new IOException("Library version error");
            }
            WebPPicture.use_argb(webPPicture, 1);
            WebPPicture.width(webPPicture, width);
            WebPPicture.height(webPPicture, height);

            MemorySegment nativeImage = copyImageIntoNativeMemory(image);
            final int numBands = image.getSampleModel().getNumBands();
            if (numBands == 4) {
                WebPPictureImportRGBA(webPPicture, nativeImage, image.getWidth() * 4);
            } else {
                WebPPictureImportBGR(webPPicture, nativeImage, image.getWidth() * 3);
            }

            StructLayout memoryWriterStruct = (StructLayout) WebPMemoryWriter.layout();
            MemorySegment memoryWriter      = arena.allocate(memoryWriterStruct);
            WebPMemoryWriterInit(memoryWriter);

            MemorySegment writeFunc = Linker.nativeLinker().upcallStub(
                    WebPMemoryWrite$handle(),
                    WebPMemoryWrite$descriptor(),
                    arena);
            WebPPicture.writer(webPPicture, writeFunc);
            WebPPicture.custom_ptr(webPPicture, memoryWriter);

            int ok = WebPEncode(config, webPPicture);
            if (ok != 0) {
                LOGGER.trace("Encoded image in {}", lapTimer);
                MemorySegment encodedImage = WebPMemoryWriter.mem(memoryWriter);
                long encodedSize           = WebPMemoryWriter.size(memoryWriter);

                // If we have any XMP data to write, we want to use the Mux API
                // to write the image in the Extended File Format. Otherwise,
                // we can just write what we have now (in the Simple File
                // Format).
                Optional<String> optXMP = encode.getXMP();
                if (optXMP.isPresent()) {
                    final String xmpStr = optXMP.get();

                    // Initialize a WebPData struct, which has only `bytes`
                    // (pointer) and `size` (long) members.
                    StructLayout webPDataStruct = (StructLayout) WebPData.layout();
                    MemorySegment webPData      = arena.allocate(webPDataStruct);

                    // Fill in its properties.
                    WebPData.bytes(webPData, encodedImage);
                    WebPData.size(webPData, encodedSize);

                    // Initialize a WebPMux. We are supposed to be able to do
                    // this using WebPMuxNew(), but jextract didn't generate
                    // that.
                    MemorySegment mux = WebPNewInternal(WEBP_MUX_ABI_VERSION());

                    // Attach the WebPData to the WebPMux.
                    code = WebPMuxSetImage(mux, webPData, 0);
                    handleWebPMuxError("WebPMuxSetImage", code);

                    // Create an XMP chunk to add to the WebPMux.
                    StructLayout xmpDataStruct = (StructLayout) WebPData.layout();
                    MemorySegment xmpWebPData  = arena.allocate(xmpDataStruct);
                    MemorySegment xmp          = arena.allocateFrom(xmpStr);
                    WebPData.bytes(xmpWebPData, xmp);
                    WebPData.size(xmpWebPData, xmp.byteSize());

                    // Add the XMP WebPData to the WebPMux.
                    code = WebPMuxSetChunk(mux, arena.allocateFrom("XMP "),
                            xmpWebPData, 0);
                    handleWebPMuxError("WebPMuxSetChunk", code);

                    // Allocate a WebPData struct to store the output from
                    // WebPMuxAssemble.
                    StructLayout assembledDataStruct = (StructLayout) WebPData.layout();
                    MemorySegment assembledData      = arena.allocate(assembledDataStruct);

                    // Assemble the mux.
                    code = WebPMuxAssemble(mux, assembledData);
                    handleWebPMuxError("WebPMuxAssemble", code);

                    // Copy the muxed data into the Java heap and write it to
                    // the output stream.
                    MemorySegment muxedData = WebPData.bytes(assembledData);
                    long muxSize            = WebPData.size(assembledData);
                    encode(muxedData, muxSize, outputStream);
                    LOGGER.trace("Wrote {}x{} image in {}", width, height, lapTimer);

                    WebPMuxDelete(mux);
                    // At this point we need to pass assembledData to WebPDataClear(),
                    // but jextract didn't generate that, so instead we try to do what
                    // it does internally.
                    WebPFree(muxedData);
                } else {
                    encode(encodedImage, encodedSize, outputStream);
                    WebPFree(encodedImage);
                }
                LOGGER.trace("Wrote {}x{} image in {}", width, height, lapTimer);
            } else {
                code = WebPPicture.error_code(webPPicture);
                handleVP8EncoderCode("WebPEncode", code);
            }
        } finally {
            WebPPictureFree(webPPicture);
        }
        LOGGER.trace("write(): completed in {}", elapsedTimer);
    }

    //endregion
    //region Private methods

    private MemorySegment copyImageIntoNativeMemory(RenderedImage image) {
        final Stopwatch watch       = new Stopwatch();
        final int width             = image.getWidth();
        final int height            = image.getHeight();
        final int numSrcBands       = image.getSampleModel().getNumBands();
        final int numDestBands      = (numSrcBands > 1) ? numSrcBands : 3;
        final Raster raster         = image.getData();
        final DataBuffer heapBuffer = raster.getDataBuffer();
        final long imageSize        = (long) numDestBands * width * height;
        MemorySegment destSegment   = arena.allocate(imageSize);

        // If the Raster's backing DataBuffer has 3 bands and is byte-type, we
        // can safely assume it's storing BGR samples, and we can just copy the
        // whole thing into native memory, which is a lot faster than going
        // through the Raster.
        if (numSrcBands == 3 && heapBuffer instanceof DataBufferByte) {
            destSegment.asByteBuffer()
                    .put(((DataBufferByte) heapBuffer).getData());
        } else if (numSrcBands > 1) {
            for (int i = 0, y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int b = 0; b < numSrcBands; b++) {
                        byte sample = (byte) raster.getSample(x, y, b);
                        destSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                    }
                }
            }
        } else {
            for (int i = 0, y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte sample = (byte) raster.getSample(x, y, 0);
                    destSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                    destSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                    destSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                }
            }
        }
        LOGGER.trace("Copied image into native memory in {}", watch);
        return destSegment;
    }

    private void encode(MemorySegment sourceSegment,
                        long length,
                        OutputStream target) throws IOException {
        byte[] bytes = sourceSegment.asSlice(0, length)
                .toArray(ValueLayout.JAVA_BYTE);
        target.write(bytes);
        // This is more memory-efficient but also significantly slower.
        //for (long i = 0; i < length; i++) {
        //    target.write(sourceSegment.getAtIndex(ValueLayout.JAVA_BYTE, i));
        //}
    }

    private void handleVP8EncoderCode(String function,
                                      int code) throws IOException {
        if (code == VP8_ENC_OK()) {
            return;
        }
        String message = null;
        if (code == VP8_ENC_ERROR_BAD_DIMENSION()) {
            message = "Bad dimension";
        } else if (code == VP8_ENC_ERROR_BAD_WRITE()) {
            message = "Bad write";
        } else if (code == VP8_ENC_ERROR_LAST()) {
            message = "Last";
        } else if (code == VP8_ENC_ERROR_FILE_TOO_BIG()) {
            message = "File too big";
        } else if (code == VP8_ENC_ERROR_INVALID_CONFIGURATION()) {
            message = "Invalid configuration";
        } else if (code == VP8_ENC_ERROR_BITSTREAM_OUT_OF_MEMORY()) {
            message = "Bitstream out of memory";
        } else if (code == VP8_ENC_ERROR_OUT_OF_MEMORY()) {
            message = "Out of memory";
        } else if (code == VP8_ENC_ERROR_PARTITION0_OVERFLOW()) {
            message = "Partition 0 overflow";
        } else if (code == VP8_ENC_ERROR_PARTITION_OVERFLOW()) {
            message = "Partition overflow";
        } else if (code == VP8_ENC_ERROR_USER_ABORT()) {
            message = "Aborted by user";
        } else if (code == VP8_ENC_ERROR_NULL_PARAMETER()) {
            message = "Null parameter";
        }
        throw new IOException(function + "() returned error " + code + ": " +
                message);
    }

    private void handleWebPMuxError(String function,
                                    int code) throws IOException {
        if (code == mux_h.WEBP_MUX_OK()) {
            return;
        }
        String message = null;
        if (code == mux_h.WEBP_MUX_MEMORY_ERROR()) {
            message = "Memory error";
        } else if (code == mux_h.WEBP_MUX_INVALID_ARGUMENT()) {
            message = "Invalid argument";
        } else if (code == mux_h.WEBP_MUX_BAD_DATA()) {
            message = "Bad data";
        } else if (code == mux_h.WEBP_MUX_NOT_ENOUGH_DATA()) {
            message = "Not enough data";
        } else if (code == mux_h.WEBP_MUX_NOT_FOUND()) {
            message = "Not found";
        }
        throw new IOException(
                function + "() returned error code " + code + ": " + message);
    }

    private boolean isAutofilter() {
        return encode.getOptions().getBoolean(Key.WEBPENCODER_AUTOFILTER.key(),
                false);
    }

    private boolean isLossless() {
        return encode.getOptions().getBoolean(Key.WEBPENCODER_LOSSLESS.key(),
                false);
    }

    private boolean isMultithreading() {
        return encode.getOptions().getBoolean(Key.WEBPENCODER_MULTITHREADING.key(),
                false);
    }

    private int getMethod() {
        return encode.getOptions().getInt(Key.WEBPENCODER_METHOD.key(), 3);
    }

    private float getQuality() {
        return encode.getOptions().getFloat(Key.WEBPENCODER_QUALITY.key(), 50f);
    }

}
