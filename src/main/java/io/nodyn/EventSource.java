/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nodyn;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bob McWhirter
 */
public class EventSource {

    private Map<String, Callback> callbacks = new HashMap<>();

    public Object emit(String event, CallbackResult result) {
        Object res = null;
        Callback callback = this.callbacks.get(event);
        if (callback != null) {
            try {
                res = callback.call(result);
            } catch (Exception ex) {
                if (result.getResult() instanceof ByteBuffer) {
                    hack((ByteBuffer) result.getResult());
                }
            }
        }
        return res;
    }

    // TODO: the SIGINT obviously does NOT reach the JVM, so we need to invoke it here, otherwise there won't be
    //       a proper shutdown
    private void hack(ByteBuffer buffer) {
        byte[] array = buffer.array();
        for (byte b : array) {
            if (b == 3) {
                // 3 = ETX (end of text) = CTRL-C
                System.exit(0);
            }

        }
    }

    public void on(String event, Callback callback) {
        if (callback == null) {
            this.callbacks.remove(event);
        } else {
            this.callbacks.put(event, callback);
        }
    }
}
