package io.nodyn.runtime;

import javax.script.ScriptEngine;
import javax.script.ScriptContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bob McWhirter
 */
public class NodynConfig {

    private final NodynClassLoader classLoader;
    private String evalString;
    private boolean help;
    private boolean version;
    private boolean print;
    private boolean interactive;

    private boolean noDeprecation;
    private boolean traceDeprecation;
    private boolean throwDeprecation;

    private List<String> execArgv = new ArrayList<>();

    private boolean debug;
    private int debugPort = 5858;
    private boolean debugWaitConnect;

    private boolean noMoreArgs;

    private Map<String, Object> defaultBindings;
    private List<String> defaultLibs;

    // if provided the supplied instance will be configured, otherwise a new instance will be created
    private ScriptEngine scriptEngine = null;

    /**
     * The ScriptContext to always use.
     */
    private ScriptContext scriptContext;

    // enables/disables the support for the eventloop/process
    private boolean processEnabled = true;

    public NodynConfig() {
        this.classLoader = new NodynClassLoader();
        this.defaultBindings = new HashMap<>();
        this.defaultLibs = new ArrayList<>();
    }

    public NodynConfig(String[] rawArgv) {
        this();
        parse( rawArgv );
    }

    public void setProcessEnabled(boolean enable) {
        this.processEnabled = enable;
    }

    public boolean isProcessEnabled() {
        return this.processEnabled;
    }

    public void setScriptEngine(ScriptEngine engine) {
        this.scriptEngine = engine;
    }

    public void setScriptContext(ScriptContext scriptContext) {
        this.scriptContext = scriptContext;
    }

    public ScriptContext getScriptContext() {
        return this.scriptContext;
    }


    public ScriptEngine getScriptEngine() {
        return this.scriptEngine;
    }

    @Override
    public String toString() {
        return "[NodynConfig: evalString=" + this.evalString + "; help=" + this.help + "; version=" + this.version + "; print=" + this.print + "; interactive=" + this.interactive + "; execArgv=" + this.execArgv + "]";
    }

    /**
     * Registers the supplied library so it will be loaded upon runtime initialization.
     *
     * @param library   The library that will be loaded during runtime initialization. Neither <code>null</code> nor empty.
     */
    public void addDefaultLibrary(String library) {
        this.defaultLibs.add(library);
    }

    /**
     * Returns a list of default libraries.
     *
     * @return   A list of default libraries. Not <code>null</code>.
     */
    public List<String> getDefaultLibraries() {
        return Collections.unmodifiableList(this.defaultLibs);
    }

    /**
     * Adds the supplied default binding for the runtime in question.
     *
     * @param key     The key to be used for the binding. Neither <code>null</code> nor empty.
     * @param value   The bound object. Maybe <code>null</code>.
     */
    public void putDefaultBinding(String key, Object value) {
        this.defaultBindings.put(key, value);
    }

    /**
     * Returns a map of bindings that will be used for the runtime.
     *
     * @return   A map of bindings that will be used for the runtime. Not <code>null</code>.
     */
    public Map<String, Object> getDefaultBindings() {
        return Collections.unmodifiableMap(defaultBindings);
    }

    public NodynClassLoader getClassLoader() {
        return this.classLoader;
    }

    private boolean shouldStop() {
        return this.help || this.version;
    }

    public boolean getPrint() {
        return this.print;
    }

    public boolean isHelp() {
        return this.help;
    }

    public boolean isVersion() {
        return this.version;
    }

    public boolean getDebug() {
        return this.debug;
    }

    public boolean getDebugWaitConnect() {
        return this.debugWaitConnect;
    }

    public int getDebugPort() {
        return this.debugPort;
    }

    public boolean getInteractive() {
        return this.interactive;
    }

    public boolean getNoDeprecation() {
        return this.noDeprecation;
    }

    public boolean getTraceDeprecation() {
        return this.traceDeprecation;
    }

    public boolean getThrowDeprecation() {
        return this.throwDeprecation;
    }

    public String[] getExecArgv() {
        return this.execArgv.toArray(new String[this.execArgv.size()]);
    }

    public String getEvalString() {
        return this.evalString;
    }

    protected void parse(String[] rawArgv) {

        int i = 0;

        while (i < rawArgv.length && ! shouldStop()) {
            i = parse( rawArgv, i );
        }

    }

    protected int parse(String[] rawArgv, int pos) {
        String arg = rawArgv[pos];

        if ( this.noMoreArgs ) {
            this.execArgv.add( arg );
            return pos+1;
        }

        int result = parseDebug(rawArgv, pos );
        if ( result != pos ) {
            return result;
        }

        switch ( arg ) {
            case "-v":
            case "--version":
                this.version = true;
                return pos+1;
            case "--help":
                this.help = true;
                return pos+1;
            case "-e":
            case "--eval":
                this.evalString = next( rawArgv, pos );
                return pos+2;
            case "-p":
            case "-pe":
            case "--print":
                this.evalString = next( rawArgv, pos );
                this.print = true;
                return pos+2;
            case "-i":
            case "--interactive":
                this.interactive = true;
                return pos+1;
            case "--no-deprecation":
                this.noDeprecation = true;
                return pos+1;
            case "--trace-deprecation":
                this.traceDeprecation = true;
                return pos+1;
            case "--throw-deprecation":
                this.throwDeprecation = true;
                return pos+1;
            default:
                this.noMoreArgs = true;
                return pos;
        }
    }

    protected int parseDebug(String[] rawArgv, int pos) {
        String arg = rawArgv[pos];

        if ( arg.equals( "--debug") ) {
            this.debug = true;
            return pos+1;
        }

        if ( arg.equals( "--debug-brk" ) ) {
            this.debug = true;
            this.debugWaitConnect = true;
            return pos+1;
        }

        if ( arg.startsWith( "--debug=" ) ) {
            try {
                this.debugPort = Integer.parseInt(arg.substring("--debug=".length()));
                this.debug = true;
            } catch (NumberFormatException e) {
                // ignore
            }
            return pos+1;
        }

        if ( arg.startsWith( "--debug-port=" ) ) {
            try {
                this.debugPort = Integer.parseInt(arg.substring("--debug-port=".length()));
                this.debug = true;
            } catch (NumberFormatException e) {
                // ignore
            }
            return pos+1;
        }

        if ( arg.startsWith( "--debug-brk=" ) ) {
            try {
                this.debugPort = Integer.parseInt(arg.substring("--debug-brk=".length()));
                this.debug = true;
                this.debugWaitConnect = true;
            } catch (NumberFormatException e) {
                // ignore
            }
            return pos+1;

        }

        return pos;
    }

    protected String next(String[] rawArgv, int pos) {
        if ( ( pos + 1 )  >=  rawArgv.length ) {
            throw new IllegalArgumentException( rawArgv[pos] + " requires an argument" );
        }

        return  rawArgv[pos+1];

    }

}
