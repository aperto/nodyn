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
package io.nodyn.runtime.nashorn;

import io.nodyn.NodeProcess;
import io.nodyn.Nodyn;
import io.nodyn.runtime.NodynConfig;
import io.nodyn.runtime.Program;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import javax.script.Bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Vertx;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.vertx.java.core.VertxFactory;

/**
 * @author Lance Ball
 */
public class NashornRuntime extends Nodyn {

    private static final Logger LOGGER = LoggerFactory.getLogger(NashornRuntime.class);

    private NashornScriptEngine engine;
    private ScriptContext global;
    private Program nativeRequire;

    private static final String NATIVE_REQUIRE = "nodyn/_native_require.js";

    public NashornRuntime(NodynConfig config) {
        this(config, VertxFactory.newVertx(), true);
    }

    public NashornRuntime(NodynConfig config, Vertx vertx, boolean controlLifeCycle) {
        super(config, vertx, controlLifeCycle);
        Thread.currentThread().setContextClassLoader(getConfiguration().getClassLoader());
        if (config.getScriptEngine() != null) {
            ScriptEngine scriptEngine = config.getScriptEngine();
            if (scriptEngine instanceof NashornScriptEngine) {
                engine = (NashornScriptEngine) scriptEngine;
            } else {
                LOGGER.warn(String.format("Cannot use ScriptEngine '%s' as '%s' is required",
                    scriptEngine.getClass().getName(), NashornScriptEngine.class.getName()));
            }
        }
        if (engine == null) {
            engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
        }
        global = engine.getContext();

        try {
            nativeRequire = compileNative(NATIVE_REQUIRE);
            nativeRequire.execute(global);
        } catch (ScriptException ex) {
            LOGGER.error("Failed to load " + NATIVE_REQUIRE, ex);
            System.exit(255);
        }
    }

    @Override
    public Object loadBinding(String name) {
        try {
            String pathName = "nodyn/bindings/" + name + ".js";
            return engine.eval("_native_require('" + pathName + "');", global);
        } catch (ScriptException e) {
            this.handleThrowable(e);
        }
        return false;
    }

    @Override
    public Program compile(String source, String fileName, boolean displayErrors) throws Throwable {
        // TODO: do something with the displayErrors parameter
        try {
            Program program = new NashornProgram(engine.compile(source), fileName);
            return program;
        } catch (ScriptException ex) {
            LOGGER.error("Cannot compile script " + fileName, ex);
            handleThrowable(ex);
        }
        return null;
    }

    @Override
    public void makeContext(Object init) {
        if (init != null) {
            ScriptContext context = new SimpleScriptContext();
            context.setBindings(engine.getBindings(ScriptContext.GLOBAL_SCOPE), ScriptContext.GLOBAL_SCOPE);
        } else {
            throw new RuntimeException("WTF");
        }
    }

    @Override
    public boolean isContext(Object ctx) {
        return NashornRuntime.extractContext(ctx) != null;
    }

    /**
     *
     * @param ctx
     * @return
     */
    protected static ScriptContext extractContext(Object ctx) {
        if (ctx instanceof ScriptContext) {
            return (ScriptContext) ctx;
        } else if (ctx instanceof JSObject) {
            return (ScriptContext) ((JSObject)ctx).getMember("__contextifyContext");
        }
        return null;
    }

    @Override
    public void handleThrowable(Throwable t) {
        LOGGER.error(t.getLocalizedMessage(), t);
        t.printStackTrace();
    }

    /**
     * Initialize on current thread, i.e. outside of the Nodyn event loop that otherwise does
     * initialization asynchronously in a different thread.
     */
    public void synchronousInitialize() {
        initialize();
    }

    @Override
    protected NodeProcess initialize() {

        Bindings bindings = engine.getBindings(ScriptContext.GLOBAL_SCOPE);
        bindings.put("__vertx", getVertx());
        bindings.put("__dirname", System.getProperty("user.dir"));
        bindings.put("__filename", Nodyn.NODE_JS);
        bindings.put("__nodyn", this);

        // apply default bindings
        this.getConfiguration().getDefaultBindings().forEach((key, value) -> bindings.putIfAbsent(key, value));

        if (this.getConfiguration().isProcessEnabled()) {
            return initializeWithProcess();
        } else {
            return initializeWithProcess();
        }

    }

    private NodeProcess initializeWithProcess() {
        NodeProcess result = null;

        result = new NodeProcess(this);

        getEventLoop().setProcess(result);

        try {

            engine.eval("global = this;");
            engine.eval("load(\"nashorn:mozilla_compat.js\");");

            for (String lib : this.getConfiguration().getDefaultLibraries()) {
                engine.eval(String.format("load(\"%s\");", lib));
            }

            // Adds ES6 capabilities not provided by DynJS to global scope
            compileNative(ES6_POLYFILL).execute(global);

            // Invoke the process function
            JSObject processFunction = (JSObject) compileNative(PROCESS).execute(global);
            JSObject jsProcess = (JSObject) processFunction.call(processFunction, result);

            // Invoke the node function
            JSObject nodeFunction = (JSObject) compileNative(NODE_JS).execute(global);
            nodeFunction.call(nodeFunction, jsProcess);
        } catch (ScriptException ex) {
            LOGGER.error("Cannot initialize", ex);
        }
        return result;

    }

    @Override
    protected Object runScript(String script) {
        try {
            return engine.eval(new FileReader(script));
        } catch (ScriptException | FileNotFoundException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
        return null;
    }

    @Override
    public Object getGlobalContext() {
        return global;
    }

    private Program compileNative(String fileName) throws ScriptException  {
        URL resource = getConfiguration().getClassLoader().getResource(fileName);
        if (resource == null) {
            LOGGER.warn("Missing resource: " + resource);
        }
        try (InputStreamReader is = new InputStreamReader(resource.openStream())) {
            return new NashornProgram(engine.compile(is), fileName);
        } catch (IOException ex) {
            throw new ScriptException(ex);
        }
    }

    class NodynJSObject extends AbstractJSObject {

        HashMap store = new HashMap();

        @Override
        public void setMember(String name, Object value) {
            store.put(name, value);
        }

        @Override
        public boolean hasMember(String name) {
            return store.containsKey(name);
        }

        @Override
        public Object getMember(String name) {
            return store.get(name);
        }
    }
}
