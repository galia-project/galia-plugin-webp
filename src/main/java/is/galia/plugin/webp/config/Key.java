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

package is.galia.plugin.webp.config;

import is.galia.operation.Encode;

public enum Key {

    WEBPDECODER_MULTITHREADING("decoder.WebPDecoder.multithreading"),

    WEBPENCODER_AUTOFILTER    (Encode.OPTION_PREFIX + "WebPEncoder.autofilter"),
    WEBPENCODER_LOSSLESS      (Encode.OPTION_PREFIX + "WebPEncoder.lossless"),
    WEBPENCODER_METHOD        (Encode.OPTION_PREFIX + "WebPEncoder.method"),
    WEBPENCODER_MULTITHREADING(Encode.OPTION_PREFIX + "WebPEncoder.multithreading"),
    WEBPENCODER_QUALITY       (Encode.OPTION_PREFIX + "WebPEncoder.quality");

    private final String key;

    Key(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key();
    }

}
