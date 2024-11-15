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

import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.codec.tiff.Directory;
import is.galia.image.Size;
import is.galia.image.Format;
import is.galia.image.MediaType;
import is.galia.image.Metadata;
import is.galia.image.Region;
import is.galia.image.ReductionFactor;
import is.galia.plugin.webp.test.TestUtils;
import is.galia.stream.PathImageInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WebPDecoderTest {

    private static final boolean SAVE_IMAGES = true;

    private final Arena arena = Arena.ofConfined();
    private WebPDecoder instance;

    @BeforeAll
    public static void beforeClass() {
        try (WebPDecoder decoder = new WebPDecoder()) {
            decoder.onApplicationStart();
        }
    }

    @BeforeEach
    public void setUp() {
        instance = new WebPDecoder();
        instance.setArena(arena);
        instance.initializePlugin();
        instance.setSource(TestUtils.getFixture("rgb.webp"));
    }

    @AfterEach
    public void tearDown() {
        instance.close();
        arena.close();
    }

    //region Plugin methods

    @Test
    void getPluginConfigKeys() {
        Set<String> keys = instance.getPluginConfigKeys();
        assertFalse(keys.isEmpty());
    }

    @Test
    void getPluginName() {
        assertEquals(WebPDecoder.class.getSimpleName(),
                instance.getPluginName());
    }

    //endregion
    //region Decoder methods

    /* detectFormat() */

    @Test
    void detectFormatWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.detectFormat());
    }

    @Test
    void detectFormatWithEmptyImage() throws Exception {
        instance.setSource(TestUtils.getFixture("empty"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithUnsupportedBytes() throws Exception {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithWebPBytes() throws Exception {
        assertEquals(WebPDecoder.FORMAT, instance.detectFormat());
    }

    /* getNumImages(int) */

    @Test
    void getNumImages() {
        assertEquals(1, instance.getNumImages());
    }

    /* getNumResolutions() */

    @Test
    void getNumResolutions() {
        assertEquals(1, instance.getNumResolutions());
    }

    /* getSize(int) */

    @Test
    void getSizeWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(SourceFormatException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getSize(1));
    }

    @Test
    void getSize() throws Exception {
        Size size = instance.getSize(0);
        assertEquals(100, size.intWidth());
        assertEquals(88, size.intHeight());
    }

    /* getSupportedFormats() */

    @Test
    void getSupportedFormats() {
        Set<Format> formats = instance.getSupportedFormats();
        assertEquals(1, formats.size());
        Format expected = new Format(
                "webp",
                "WebP",
                List.of(new MediaType("image", "webp")),
                List.of("webp"),
                true,
                false,
                true);
        Format actual = formats.stream().findAny().orElseThrow();
        assertEquals(expected, actual);
    }

    /* getTileSize(int) */

    @Test
    void getTileSizeWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(SourceFormatException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getTileSize(1));
    }

    @Test
    void getTileSize() throws Exception {
        Size size = instance.getTileSize(0);
        assertEquals(100, size.intWidth());
        assertEquals(88, size.intHeight());
    }

    /* read(int) */

    @Test
    void decode1WithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithInvalidImage() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(SourceFormatException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> instance.decode(1));
    }

    @Test
    void decode1FromFile() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(100, image.getWidth());
        assertEquals(88, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1FromStream() throws Exception {
        Path fixture = TestUtils.getFixture("rgb.webp");
        instance.setSource(new PathImageInputStream(fixture));
        BufferedImage image = instance.decode(0);
        assertEquals(100, image.getWidth());
        assertEquals(88, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGBA() throws Exception {
        instance.setSource(TestUtils.getFixture("alpha.webp"));
        BufferedImage image = instance.decode(0);
        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithICCProfile() throws Exception {
        instance.setSource(TestUtils.getFixture("colorspin.webp"));
        BufferedImage image = instance.decode(0);
        assertEquals(800, image.getWidth());
        assertEquals(800, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* read(int, ...) */

    @Test
    void decode2WithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () ->
                instance.decode(0, null, null, null, null, null));
    }

    @Test
    void decode2WithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.decode(0, null, null, null, null, null));
    }

    @Test
    void decode2WithInvalidImage() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(SourceFormatException.class, () ->
                instance.decode(0, null, null, null, null, null));
    }

    @Test
    void decode2WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class, () ->
                instance.decode(1, null, null, null, null, null));
    }

    @Test
    void decode2() throws Exception {
        Region region                   = new Region(0, 0, 100, 88);
        double[] scales                 = { 1, 1 };
        double[] diffScales             = { 1, 1 };
        ReductionFactor reductionFactor = new ReductionFactor();
        Set<DecoderHint> decoderHints   = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(0, region, scales, reductionFactor,
                diffScales, decoderHints);
        assertEquals(100, image.getWidth());
        assertEquals(88, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode2WithRegion() throws Exception {
        Region region                   = new Region(10, 10, 50, 44);
        double[] scales                 = { 1, 1 };
        ReductionFactor reductionFactor = new ReductionFactor();
        Set<DecoderHint> decoderHints   = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(
                0, region, scales, reductionFactor, null, decoderHints);
        assertEquals(50, image.getWidth());
        assertEquals(44, image.getHeight());
        assertFalse(decoderHints.contains(DecoderHint.IGNORED_REGION));
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode2WithScale() throws Exception {
        double[] scales                 = { 0.5, 0.5 };
        ReductionFactor reductionFactor = new ReductionFactor();
        Set<DecoderHint> decoderHints   = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(
                0, new Region(0, 0, 100, 88, true),
                scales, reductionFactor, null, decoderHints);
        assertEquals(50, image.getWidth());
        assertEquals(44, image.getHeight());
        assertFalse(decoderHints.contains(DecoderHint.IGNORED_SCALE));
        assertFalse(decoderHints.contains(DecoderHint.NEEDS_DIFFERENTIAL_SCALE));
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* readMetadata() */

    @Test
    void decodeMetadataWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () ->
                instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(SourceFormatException.class,
                () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithWithIllegalImageIndex() {
        instance.setSource(TestUtils.getFixture("png.png"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readMetadata(9999));
    }

    @Test
    void decodeMetadataWithEXIF() throws Exception {
        instance.setSource(TestUtils.getFixture("exif.webp"));
        Metadata metadata = instance.readMetadata(0);
        Optional<Directory> dir = metadata.getEXIF();
        assertTrue(dir.isPresent());
    }

    @Test
    void decodeMetadataWithXMP() throws Exception {
        instance.setSource(TestUtils.getFixture("xmp.webp"));
        Metadata metadata = instance.readMetadata(0);
        String xmp = metadata.getXMP().orElseThrow();
        assertTrue(xmp.startsWith("<rdf:RDF"));
        assertTrue(xmp.endsWith("</rdf:RDF>"));
    }

    /* readSequence() */

    @Test
    void decodeSequence() {
        assertThrows(UnsupportedOperationException.class,
                () -> instance.decodeSequence());
    }

}
