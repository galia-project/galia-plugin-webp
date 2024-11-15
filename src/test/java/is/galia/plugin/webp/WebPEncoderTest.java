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

import is.galia.codec.xmp.XMPUtils;
import is.galia.image.Metadata;
import is.galia.operation.Encode;
import is.galia.plugin.webp.config.Key;
import is.galia.plugin.webp.test.TestUtils;
import is.galia.processor.Java2DUtils;
import is.galia.stream.ByteArrayImageInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WebPEncoderTest {

    private static final boolean SAVE_IMAGES = true;

    private static final Path IMAGE_FIXTURE_PATH =
            TestUtils.getFixture("rgb-64x56x8-baseline.jpg");

    private final Arena arena         = Arena.ofConfined();
    private final WebPEncoder encoder = new WebPEncoder();
    private final WebPDecoder decoder = new WebPDecoder();
    private BufferedImage image;

    @BeforeAll
    public static void beforeClass() {
        try (WebPDecoder decoder = new WebPDecoder()) {
            decoder.onApplicationStart();
        }
        try (WebPEncoder encoder = new WebPEncoder()) {
            encoder.onApplicationStart();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        image = ImageIO.read(IMAGE_FIXTURE_PATH.toFile());
        encoder.setArena(arena);
        decoder.setArena(arena);
        encoder.initializePlugin();
        decoder.initializePlugin();
    }

    @AfterEach
    public void tearDown() {
        encoder.close();
        decoder.close();
        arena.close();
    }

    //endregion
    //region Plugin methods

    @Test
    void getPluginConfigKeys() {
        Set<String> keys = encoder.getPluginConfigKeys();
        assertFalse(keys.isEmpty());
    }

    @Test
    void getPluginName() {
        assertEquals(WebPEncoder.class.getSimpleName(),
                encoder.getPluginName());
    }

    //endregion
    //region Encoder methods

    /* write() */

    @Test
    void encodeWithBufferedImageTypeByteGray() throws Exception {
        image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_BYTE_GRAY);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");
        }
    }

    @Test
    void encodeWithBufferedImageTypeIntRGB() throws Exception {
        image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");
        }
    }

    @Test
    void encodeWithBufferedImageTypeIntARGB() throws Exception {
        image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");
        }
    }

    @Test
    void encodeWithBufferedImageType3ByteBGR() throws Exception {
        image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_3BYTE_BGR);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");
        }
    }

    @Test
    void encodeWithBufferedImageType4ByteABGR() throws Exception {
        image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_4BYTE_ABGR);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");
        }
    }

    @Test
    void encodeWithGray() throws Exception {
        image = ImageIO.read(TestUtils.getFixture("gray.jpg").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");

            try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
                decoder.setSource(iis);
                assertEquals(WebPDecoder.FORMAT, decoder.detectFormat());
                assertTrue(bytes.length > 500);
            }
        }
    }

    @Test
    void encodeWithRGB() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");

            try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
                decoder.setSource(iis);
                assertEquals(WebPDecoder.FORMAT, decoder.detectFormat());
                assertTrue(bytes.length > 1000);
            }
        }
    }

    @Test
    void encodeWithRGBA() throws Exception {
        image = ImageIO.read(TestUtils.getFixture("alpha.png").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(WebPDecoder.FORMAT));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");

            try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
                decoder.setSource(iis);
                assertEquals(WebPDecoder.FORMAT, decoder.detectFormat());
                assertTrue(bytes.length > 1000);
            }
        }
    }

    @Test
    void encodeWithXMP() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(WebPDecoder.FORMAT);
            String xmp = Files.readString(TestUtils.getFixture("xmp.xmp"));
            encode.setXMP(XMPUtils.trimXMP(xmp));
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            if (SAVE_IMAGES) TestUtils.save(bytes, "webp");

            try (ImageInputStream is = new ByteArrayImageInputStream(bytes);
                 WebPDecoder decoder = new WebPDecoder()) {
                decoder.setArena(arena);
                decoder.initializePlugin();
                decoder.setSource(is);
                Metadata metadata = decoder.readMetadata(0);
                metadata.getXMP().ifPresentOrElse(x -> {
                            assertTrue(x.startsWith("<rdf:RDF"));
                            assertTrue(x.endsWith("</rdf:RDF>"));
                }, Assertions::fail);
            }
        }
    }

    @Test
    void encodeWithQualityOption() throws Exception {
        byte[] image1, image2;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(WebPDecoder.FORMAT);
            encode.setOption(Key.WEBPENCODER_QUALITY.key(), 90);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image1 = outputStream.toByteArray();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(WebPDecoder.FORMAT);
            encode.setOption(Key.WEBPENCODER_QUALITY.key(), 10);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image2 = outputStream.toByteArray();
        }
        assertTrue(image1.length > image2.length);
    }

    @Test
    void encodeWithLosslessOption() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(WebPDecoder.FORMAT);
            encode.setOption(Key.WEBPENCODER_LOSSLESS.key(), true);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
                decoder.setSource(iis);
                assertEquals(WebPDecoder.FORMAT, decoder.detectFormat());
                assertTrue(bytes.length > 1000);
            }
        }
    }

}
