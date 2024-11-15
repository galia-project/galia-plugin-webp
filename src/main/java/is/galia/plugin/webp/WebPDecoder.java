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

import com.google.libwebp.WebPBitstreamFeatures;
import com.google.libwebp.WebPChunkIterator;
import com.google.libwebp.WebPData;
import com.google.libwebp.WebPDecBuffer;
import com.google.libwebp.WebPDecoderConfig;
import com.google.libwebp.WebPDecoderOptions;
import com.google.libwebp.decode_h;
import com.google.libwebp.demux_h;
import is.galia.codec.AbstractDecoder;
import is.galia.codec.Decoder;
import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.codec.tiff.DirectoryReader;
import is.galia.codec.tiff.EXIFBaselineTIFFTagSet;
import is.galia.codec.tiff.EXIFGPSTagSet;
import is.galia.codec.tiff.EXIFInteroperabilityTagSet;
import is.galia.codec.tiff.EXIFTagSet;
import is.galia.config.Configuration;
import is.galia.image.ComponentOrder;
import is.galia.image.Format;
import is.galia.image.MutableMetadata;
import is.galia.image.MediaType;
import is.galia.image.Metadata;
import is.galia.image.ReductionFactor;
import is.galia.image.Region;
import is.galia.image.Size;
import is.galia.plugin.Plugin;
import is.galia.plugin.webp.config.Key;
import is.galia.processor.Java2DUtils;
import is.galia.stream.ByteArrayImageInputStream;
import is.galia.stream.PathImageInputStream;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.stream.ImageInputStream;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.libwebp.decode_h.MODE_RGB;
import static com.google.libwebp.decode_h.MODE_RGBA;
import static com.google.libwebp.decode_h.WebPDecode;
import static com.google.libwebp.decode_h.WebPFreeDecBuffer;
import static com.google.libwebp.decode_h.WebPGetFeaturesInternal;
import static com.google.libwebp.demux_h.WebPDemuxGetI;
import static com.google.libwebp.demux_h.WebPDemuxDelete;
import static com.google.libwebp.demux_h.WebPDemuxGetChunk;
import static com.google.libwebp.demux_h.WebPDemuxInternal;
import static com.google.libwebp.demux_h.WebPDemuxReleaseChunkIterator;

/**
 * <p>Implementation using the Java Foreign Function & Memory API to call into
 * Google's libwebp library.</p>
 *
 * <p>The current libwebp version is 1.3.2 (ABI version 1.7).</p>
 *
 * @see <a href="https://developers.google.com/speed/webp/docs/api">
 *     WebP API Documentation</a>
 * @see <a href="https://developers.google.com/speed/webp/docs/riff_container">
 *     WebP Container Specification</a>
 */
public final class WebPDecoder extends AbstractDecoder
        implements Decoder, Plugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPDecoder.class);

    private static final double DELTA = 0.0000001;

    static final Format FORMAT = new Format(
            "webp",                                  // key
            "WebP",                                  // name
            List.of(new MediaType("image", "webp")), // media types
            List.of("webp"),                         // extensions
            true,                                    // isRaster
            false,                                   // isVideo
            true);                                   // supportsTransparency

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    private MemorySegment imageSegment;
    private long imageLength;
    private int width, height;
    private boolean hasAlpha;
    private MutableMetadata metadata;
    private ICC_Profile iccProfile;

    //endregion
    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Arrays.stream(Key.values())
                .map(Key::toString)
                .filter(k -> k.contains(WebPDecoder.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPluginName() {
        return WebPDecoder.class.getSimpleName();
    }

    @Override
    public void onApplicationStart() {
        if (!IS_CLASS_INITIALIZED.getAndSet(true)) {
            System.loadLibrary("webpdemux");
        }
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void initializePlugin() {
    }

    //endregion
    //region Decoder methods

    @Override
    public void close() {
        super.close();
    }

    @Override
    public Format detectFormat() throws IOException {
        ImageInputStream iis = null;
        boolean close        = false;
        try {
            if (imageFile != null) {
                iis = new PathImageInputStream(imageFile);
                close = true;
            } else {
                iis = inputStream;
            }
            if (iis.length() >= 12) {
                byte[] magicBytes = new byte[12];
                iis.seek(0);
                iis.readFully(magicBytes);
                if (magicBytes[0] == 'R' && magicBytes[1] == 'I' &&
                        magicBytes[2] == 'F' && magicBytes[3] == 'F' &&
                        magicBytes[8] == 'W' && magicBytes[9] == 'E' &&
                        magicBytes[10] == 'B' && magicBytes[11] == 'P') {
                    return FORMAT;
                }
            }
        } finally {
            if (iis != null) {
                if (close) {
                    iis.close();
                } else {
                    iis.seek(0);
                }
            }
        }
        return Format.UNKNOWN;
    }

    @Override
    public int getNumImages() {
        return 1;
    }

    @Override
    public int getNumResolutions() {
        return 1;
    }

    @Override
    public Size getSize(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        readInfo();
        return new Size(width, height);
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Set.of(FORMAT);
    }

    @Override
    public Size getTileSize(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        return getSize(imageIndex);
    }

    @Override
    public BufferedImage decode(int imageIndex,
                                Region region,
                                double[] scales,
                                ReductionFactor reductionFactor,
                                double[] diffScales,
                                Set<DecoderHint> decoderHints) throws IOException {
        validateImageIndex(imageIndex);

        region = region.oriented(getSize(imageIndex),
                readMetadata(imageIndex).getOrientation());

        final Stopwatch elapsedWatch = new Stopwatch();
        bufferImageData();

        // Initialize a WebPDecoderConfig struct.
        StructLayout decoderConfigStruct = (StructLayout) WebPDecoderConfig.layout();
        MemorySegment decoderConfig      = arena.allocate(decoderConfigStruct);
        // Fill in some of its options.
        MemorySegment decoderOptions = WebPDecoderConfig.options(decoderConfig);
        WebPDecoderOptions.no_fancy_upsampling(decoderOptions, 1);
        WebPDecoderOptions.use_threads(decoderOptions, isMultithreading() ? 1 : 0);
        MemorySegment decBuffer = WebPDecoderConfig.output(decoderConfig);
        WebPDecBuffer.colorspace(decBuffer, hasAlpha ? MODE_RGBA() : MODE_RGB());
        WebPDecBuffer.is_external_memory(decBuffer, 0);

        if (region != null) {
            WebPDecoderOptions.use_cropping(decoderOptions, 1);
            WebPDecoderOptions.crop_left(decoderOptions, region.intX());
            WebPDecoderOptions.crop_top(decoderOptions, region.intY());
            WebPDecoderOptions.crop_width(decoderOptions, region.intWidth());
            WebPDecoderOptions.crop_height(decoderOptions, region.intHeight());
        }
        if (Math.abs(scales[0] - 1) > DELTA ||
                Math.abs(scales[1] - 1) > DELTA) {
            Size fullSize = getSize(imageIndex);
            WebPDecoderOptions.use_scaling(decoderOptions, 1);
            WebPDecoderOptions.scaled_width(decoderOptions,
                    (int) Math.round(fullSize.width() * scales[0]));
            WebPDecoderOptions.scaled_height(decoderOptions,
                    (int) Math.round(fullSize.height() * scales[1]));
            // If x & y scales are different, base the reduction factor on
            // the largest one.
            final double maxScale = Arrays.stream(scales).max().orElse(1);
            reductionFactor.factor =
                    ReductionFactor.forScale(maxScale, 0.001).factor;
        }

        final Stopwatch lapWatch = new Stopwatch();
        int code = WebPDecode(imageSegment, imageLength, decoderConfig);
        handleVP8StatusCode("WebPDecode", code);
        LOGGER.trace("read(int, ...): decoded image in {}", lapWatch);

        VarHandle dataHandle = decoderConfigStruct.varHandle(
                MemoryLayout.PathElement.groupElement("output"),
                MemoryLayout.PathElement.groupElement("u"),
                MemoryLayout.PathElement.groupElement("RGBA"),
                MemoryLayout.PathElement.groupElement("rgba"));
        VarHandle sizeHandle = decoderConfigStruct.varHandle(
                MemoryLayout.PathElement.groupElement("output"),
                MemoryLayout.PathElement.groupElement("u"),
                MemoryLayout.PathElement.groupElement("RGBA"),
                MemoryLayout.PathElement.groupElement("size"));
        VarHandle outputWidthHandle = decoderConfigStruct.varHandle(
                MemoryLayout.PathElement.groupElement("output"),
                MemoryLayout.PathElement.groupElement("width"));
        VarHandle outputHeightHandle = decoderConfigStruct.varHandle(
                MemoryLayout.PathElement.groupElement("output"),
                MemoryLayout.PathElement.groupElement("height"));
        long size  = (long) sizeHandle.get(decoderConfig, 0);
        int width  = (int) outputWidthHandle.get(decoderConfig, 0);
        int height = (int) outputHeightHandle.get(decoderConfig, 0);
        MemorySegment decodedImageSegment =
                (MemorySegment) dataHandle.get(decoderConfig, 0);

        lapWatch.reset();
        byte[] bytes = decodedImageSegment.asSlice(0, size)
                .toArray(ValueLayout.JAVA_BYTE);
        LOGGER.trace("read(int, ...): copied image into Java heap in {}", lapWatch);

        // Free the decoding buffer
        long offset = decoderConfigStruct.byteOffset(
                MemoryLayout.PathElement.groupElement("output"));
        WebPFreeDecBuffer(decoderConfig.asSlice(offset));

        BufferedImage image = newBufferedImage(width, height, bytes);
        LOGGER.trace("read(int, ...): total time: {}", elapsedWatch);
        return image;
    }

    @Override
    public Metadata readMetadata(int imageIndex) throws IOException {
        if (metadata != null) {
            return metadata;
        }
        validateImageIndex(imageIndex);
        readInfo(); // effectively validates the image format
        final Stopwatch watch = new Stopwatch();
        metadata              = new MutableMetadata();

        // Allocate & initialize a WebPData struct.
        StructLayout webPDataStruct = (StructLayout) WebPData.layout();
        MemorySegment webPData      = arena.allocate(webPDataStruct);
        WebPData.bytes(webPData, imageSegment);
        WebPData.size(webPData, imageLength);
        // Allocate a WebPDemuxer.
        MemorySegment demuxState = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment demuxer = WebPDemuxInternal(
                webPData, 0,
                demuxState, demux_h.WEBP_DEMUX_ABI_VERSION());
        // These flags will tell us what features are present in the image.
        int flags = WebPDemuxGetI(
                demuxer, demux_h.WEBP_FF_FORMAT_FLAGS());
        StructLayout chunkIterStruct = (StructLayout) WebPChunkIterator.layout();
        MemorySegment chunkIterator  = arena.allocate(chunkIterStruct);

        if ((flags & demux_h.ICCP_FLAG()) != 0) {
            byte[] bytes = readMetadataChunk("ICCP", demuxer, chunkIterator);
            this.iccProfile = ICC_Profile.getInstance(bytes);
        }
        if ((flags & demux_h.EXIF_FLAG()) != 0) {
            byte[] bytes = readMetadataChunk("EXIF", demuxer, chunkIterator);
            DirectoryReader reader = new DirectoryReader();
            reader.addTagSet(new EXIFBaselineTIFFTagSet());
            reader.addTagSet(new EXIFTagSet());
            reader.addTagSet(new EXIFGPSTagSet());
            reader.addTagSet(new EXIFInteroperabilityTagSet());
            try (ImageInputStream is = new ByteArrayImageInputStream(bytes)) {
                reader.setSource(is);
                metadata.setEXIF(reader.readFirst());
            }
        }
        if ((flags & demux_h.XMP_FLAG()) != 0) {
            // NOTE THE SPACE!!!
            byte[] bytes = readMetadataChunk("XMP ", demuxer, chunkIterator);
            String rawXMP = new String(bytes, StandardCharsets.UTF_8);
            metadata.setXMP(rawXMP);
        }
        WebPDemuxDelete(demuxer);
        LOGGER.trace("readMetadata(): completed in {}", watch);
        return metadata;
    }

    private byte[] readMetadataChunk(String chunkName,
                                     MemorySegment demuxer,
                                     MemorySegment chunkIterSgmt) {
        WebPDemuxGetChunk(
                demuxer, arena.allocateFrom(chunkName), 1, chunkIterSgmt);
        MemorySegment chunk = WebPChunkIterator.chunk(chunkIterSgmt);

        long chunkSize = WebPData.size(chunk);
        MemorySegment chunkBytes = WebPData.bytes(chunk);
        byte[] bytes = chunkBytes.asSlice(0, chunkSize).toArray(ValueLayout.JAVA_BYTE);
        WebPDemuxReleaseChunkIterator(chunkIterSgmt);
        return bytes;
    }

    //endregion
    //region Private methods

    /**
     * Reads the source image into a native memory segment,
     */
    private void bufferImageData() throws IOException {
        if (imageSegment != null && imageLength > 0) {
            return;
        }
        final Stopwatch watch = new Stopwatch();
        byte[] imageBytes;
        if (imageFile != null) {
            imageBytes = Files.readAllBytes(imageFile);
        } else {
            ImageInputStream iis;
            if (inputStream != null) {
                iis = inputStream;
            } else {
                throw new IOException("Source not set");
            }
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[16384];
                int n;
                while ((n = iis.read(chunk)) != -1) {
                    os.write(chunk, 0, n);
                }
                imageBytes = os.toByteArray();
            } finally {
                iis.close();
            }
        }
        LOGGER.trace("bufferImageData(): read image into heap in {}", watch);

        watch.reset();
        imageLength  = imageBytes.length;
        imageSegment = arena.allocate(imageLength);
        imageSegment.asByteBuffer().put(imageBytes);
        LOGGER.trace("bufferImageData(): copied image into native memory in {}",
                watch);
    }

    /**
     * Builds a custom {@link BufferedImage} backed directly by the decoded
     * bytes returned from libwebp with no copying.
     */
    private BufferedImage newBufferedImage(int width, int height,
                                           byte[] samples) {
        final Stopwatch watch = new Stopwatch();
        ComponentOrder order  = hasAlpha ? ComponentOrder.ARGB : ComponentOrder.RGB;
        BufferedImage image   = Java2DUtils.newImage(width, height, samples, order);
        if (iccProfile != null) {
            try {
                image = Java2DUtils.convertToSRGB(image, iccProfile);
            } catch (IllegalArgumentException e) {
                if (("Numbers of source Raster bands and source color space " +
                        "components do not match").equals(e.getMessage())) {
                    LOGGER.debug("Failed to apply ICC profile: {}", e.getMessage());
                } else {
                    throw e;
                }
            }
        }
        LOGGER.trace("newBufferedImage() completed in {}", watch);
        return image;
    }

    private void readInfo() throws IOException {
        if (width > 0) {
            return;
        }
        bufferImageData();

        // Allocate a WebPBitstreamFeatures struct.
        StructLayout featuresStruct = (StructLayout) WebPBitstreamFeatures.layout();
        MemorySegment featuresSgmt  = arena.allocate(featuresStruct);

        int code = WebPGetFeaturesInternal(
                imageSegment, imageLength, featuresSgmt,
                demux_h.WEBP_DECODER_ABI_VERSION());
        handleVP8StatusCode("WebPGetFeatures", code);

        width    = WebPBitstreamFeatures.width(featuresSgmt);
        height   = WebPBitstreamFeatures.height(featuresSgmt);
        hasAlpha = (WebPBitstreamFeatures.has_alpha(featuresSgmt) == 1);
    }

    private static void handleVP8StatusCode(String function,
                                            int code) throws IOException {
        String message = function + "() returned VP8StatusCode " + code + ": ";
        if (code == decode_h.VP8_STATUS_BITSTREAM_ERROR()) {
            throw new SourceFormatException(message + "Bitstream error");
        } else if (code == decode_h.VP8_STATUS_INVALID_PARAM()) {
            throw new IOException(message + "Invalid parameter");
        } else if (code == decode_h.VP8_STATUS_SUSPENDED()) {
            throw new IOException(message + "Suspended");
        } else if (code == decode_h.VP8_STATUS_NOT_ENOUGH_DATA()) {
            throw new SourceFormatException(message + "Not enough data");
        } else if (code == decode_h.VP8_STATUS_OUT_OF_MEMORY()) {
            throw new IOException(message + "Out of memory");
        } else if (code == decode_h.VP8_STATUS_UNSUPPORTED_FEATURE()) {
            throw new IOException(message + "Unsupported feature");
        } else if (code == decode_h.VP8_STATUS_USER_ABORT()) {
            throw new IOException(message + "Aborted by user");
        }
    }

    private void validateImageIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException();
        }
    }

    private boolean isMultithreading() {
        Configuration config = Configuration.forApplication();
        return config.getBoolean(Key.WEBPDECODER_MULTITHREADING.key(),
                false);
    }

}
